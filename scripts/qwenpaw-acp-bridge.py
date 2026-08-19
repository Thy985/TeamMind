#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""QwenPaw ACP Bridge for TeamMind — Gate 2: Persistent Session Support.

Protocol (JSONL over stdin/stdout):
  IN:  {"action":"prompt","prompt":"...","cwd":"/path","session_id":"optional"}
  OUT: {"type":"chunk","text":"..."}
  OUT: {"type":"done","stop_reason":"end_turn|cancelled"}
  OUT: {"type":"error","message":"..."}
  OUT: {"type":"ready","agent":"...","mode":"real|mock"}

Features:
  - Persistent QwenPaw ACP process (avoids workspace boot delay)
  - Session reuse across prompts
  - Auto-reconnect on disconnect
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
import shutil
import sys
import time

logger = logging.getLogger("qwenpaw-bridge")

# ─── Try real ACP ────────────────────────────────────────────────────
try:
    from acp import PROTOCOL_VERSION, spawn_agent_process, text_block
    from acp.schema import ClientCapabilities, Implementation
    HAS_ACP = True
except ImportError:
    HAS_ACP = False


# ─── Mock agent ──────────────────────────────────────────────────────

class EchoAgent:
    RESPONSES = {
        "hello": "Hello! I'm QwenPaw (mock mode).",
        "why": ("Based on analysis:\n1. Simplicity\n2. Type safety\n3. Ecosystem"),
    }

    def respond(self, prompt, cwd):
        lower = prompt.lower()
        if "hello" in lower:
            text = self.RESPONSES["hello"]
        elif "why" in lower:
            text = self.RESPONSES["why"]
        else:
            text = f"Received: \"{prompt[:100]}\". Working dir: {cwd}"
        return [text[i:i+40] for i in range(0, len(text), 40)]


def run_mock_bridge(in_fp, out_fp):
    agent = EchoAgent()
    emit(out_fp, {"type": "ready", "agent": "qwenpaw-mock", "mode": "echo-fallback"})

    for raw in iter(in_fp.readline, b""):
        line = raw.decode("utf-8").strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except json.JSONDecodeError:
            emit(out_fp, {"type": "error", "message": "Invalid JSON"})
            continue

        action = msg.get("action")
        if action == "prompt":
            prompt = msg.get("prompt", "")
            cwd = msg.get("cwd", os.getcwd())
            if not prompt:
                emit(out_fp, {"type": "error", "message": "Empty prompt"})
                continue
            for chunk in agent.respond(prompt, cwd):
                emit(out_fp, {"type": "chunk", "text": chunk})
                time.sleep(0.02)
            emit(out_fp, {"type": "done", "stop_reason": "end_turn"})
        elif action == "cancel":
            emit(out_fp, {"type": "done", "stop_reason": "cancelled"})
        elif action == "ping":
            emit(out_fp, {"type": "pong"})

    emit(out_fp, {"type": "closed"})


def emit(out_fp, event):
    line = json.dumps(event, ensure_ascii=False) + "\n"
    out_fp.write(line.encode("utf-8"))
    out_fp.flush()


# ─── Real ACP bridge (persistent) ────────────────────────────────────

class RealACPBridge:
    """Persistent QwenPaw ACP bridge with session reuse."""

    def __init__(self, out_fp, backend=None, runtime_provider=None):
        self._out = out_fp
        self._conn = None
        self._proc = None
        self._session_id = None
        self._backend = backend
        self._runtime_provider = runtime_provider
        self._ready = False

    async def connect(self):
        """Connect to ACP agent."""
        if self._backend == "opencode":
            opencode = shutil.which("opencode")
            if not opencode:
                emit(self._out, {"type": "error", "message": "opencode not found"})
                return False
            cmd = [opencode, "acp"]
        elif self._backend == "qwenpaw":
            cmd = [sys.executable, "-m", "qwenpaw", "acp", "--local-diagnostics"]
            if self._runtime_provider:
                cmd.extend(["--runtime-provider", self._runtime_provider])
        else:
            # Default: try opencode first (fast), then qwenpaw
            opencode = shutil.which("opencode")
            if opencode:
                cmd = [opencode, "acp"]
            else:
                cmd = [sys.executable, "-m", "qwenpaw", "acp", "--local-diagnostics"]
                if self._runtime_provider:
                    cmd.extend(["--runtime-provider", self._runtime_provider])

        class _Client:
            def __init__(self, bridge):
                self._bridge = bridge
            def on_connect(self, conn):
                self._bridge._conn = conn

            async def session_update(self, sid, up, **kw):
                kind = getattr(up, "sessionUpdate", "")
                if kind == "agent_message_chunk":
                    content = getattr(up, "content", None)
                    text = ""
                    if isinstance(content, dict):
                        text = content.get("text", "")
                    else:
                        text = getattr(content, "text", "") or ""
                    if text:
                        emit(self._bridge._out, {"type": "chunk", "text": text})
                elif kind == "agent_thought_chunk":
                    emit(self._bridge._out, {"type": "think", "text": "[thinking]"})
                elif kind in ("tool_call_start", "tool_call_update"):
                    emit(self._bridge._out, {"type": "tool",
                                      "name": getattr(up, "name", "tool"),
                                      "input": getattr(up, "raw_input", None)})

            async def request_permission(self, opts, sid, tc, **kw):
                from acp.schema import AllowedOutcome, RequestPermissionResponse
                emit(self._bridge._out, {"type": "permission",
                              "title": getattr(tc, "title", "?"),
                              "auto_approved": True})
                return RequestPermissionResponse(
                    outcome=AllowedOutcome(outcome="selected", option_id="allow_once"))

            async def ext_notification(self, m, p): pass
            async def ext_method(self, m, p): return {}

        client = _Client(self)
        try:
            ctx = spawn_agent_process(client, *cmd)
            self._conn, self._proc = await asyncio.wait_for(ctx.__aenter__(), timeout=30.0)
            init_resp = await self._conn.initialize(
                protocol_version=PROTOCOL_VERSION,
                client_capabilities=ClientCapabilities(),
                client_info=Implementation(name="teammind", title="TeamMind", version="0.1.0"),
            )
            # Extract agent name from initialize response
            agent_name = "?"
            if hasattr(init_resp, 'agent_info') and init_resp.agent_info:
                agent_name = init_resp.agent_info.name
            emit(self._out, {"type": "ready", "agent": agent_name, "mode": "real", "backend": self._backend or "auto"})
            self._ready = True
            return True
        except Exception as e:
            emit(self._out, {"type": "error", "message": f"Connection failed: {e}"})
            return False

    async def warmup(self, cwd):
        """Pre-create session to avoid boot delay on first prompt."""
        try:
            sess = await asyncio.wait_for(
                self._conn.new_session(cwd=cwd, mcpServers=[]),
                timeout=180.0,  # Give workspace time to boot
            )
            self._session_id = sess.session_id
            logger.info("Warmup complete: session=%s", self._session_id)
            return True
        except asyncio.TimeoutError:
            logger.error("Warmup timeout after 180s")
            return False
        except Exception as e:
            logger.error("Warmup failed: %s", e)
            return False

    async def prompt(self, text, cwd, session_id=None):
        """Send a prompt using persistent session."""
        try:
            sid = session_id or self._session_id
            if not sid:
                # Fallback: create new session (will be slow if workspace not ready)
                sess = await asyncio.wait_for(
                    self._conn.new_session(cwd=cwd, mcpServers=[]),
                    timeout=30.0,
                )
                sid = sess.session_id
                self._session_id = sid

            await self._conn.prompt(session_id=sid, prompt=[text_block(text)])
            emit(self._out, {"type": "done", "stop_reason": "end_turn"})
        except asyncio.TimeoutError:
            emit(self._out, {"type": "error",
                          "message": "Session/prompt timeout (workspace may still be booting)"})
        except Exception as e:
            emit(self._out, {"type": "error", "message": str(e)})

    async def cancel(self):
        if self._session_id:
            try:
                await self._conn.cancel(session_id=self._session_id)
            except Exception:
                pass
        emit(self._out, {"type": "done", "stop_reason": "cancelled"})

    async def close(self):
        if self._conn and self._session_id:
            try:
                await self._conn.close_session(session_id=self._session_id)
            except Exception:
                pass
        if self._proc:
            self._proc.kill()
        emit(self._out, {"type": "closed"})


async def run_real_bridge(in_fp, out_fp, backend=None, runtime_provider=None,
                          warmup=False, warmup_cwd=None):
    """Run persistent ACP bridge."""
    bridge = RealACPBridge(out_fp, backend, runtime_provider)
    connected = await bridge.connect()
    if not connected:
        return

    # Warmup: pre-create session
    if warmup:
        cwd = warmup_cwd or os.getcwd()
        logger.info("Warming up session (this may take 120-180s)...")
        ok = await bridge.warmup(cwd)
        if ok:
            emit(out_fp, {"type": "status", "message": "warmup_complete",
                          "session_id": bridge._session_id})
        else:
            emit(out_fp, {"type": "error", "message": "Warmup failed"})

    loop = asyncio.get_event_loop()
    session_id = bridge._session_id

    while True:
        try:
            raw_line = await asyncio.wait_for(
                loop.run_in_executor(None, in_fp.readline),
                timeout=3.0
            )
        except (asyncio.TimeoutError, OSError):
            break
        if not raw_line:
            break
        line = raw_line.decode("utf-8").strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except json.JSONDecodeError:
            continue

        action = msg.get("action")
        if action == "prompt":
            prompt = msg.get("prompt", "")
            cwd = msg.get("cwd", os.getcwd())
            if not prompt:
                emit(out_fp, {"type": "error", "message": "Empty prompt"})
                continue
            try:
                await bridge.prompt(prompt, cwd, session_id)
            except Exception as e:
                emit(out_fp, {"type": "error", "message": str(e)})
        elif action == "warmup":
            # Pre-create session to avoid boot delay on first prompt
            cwd = msg.get("cwd", os.getcwd())
            emit(out_fp, {"type": "status", "message": "warming_up"})
            ok = await bridge.warmup(cwd)
            if ok:
                session_id = bridge._session_id
                emit(out_fp, {"type": "status", "message": "ready", "session_id": session_id})
            else:
                emit(out_fp, {"type": "error", "message": "Warmup failed"})
        elif action == "cancel":
            await bridge.cancel()
        elif action == "ping":
            emit(out_fp, {"type": "pong"})
        elif action == "close":
            break

    await bridge.close()


def main():
    import argparse
    parser = argparse.ArgumentParser(description="QwenPaw ACP Bridge for TeamMind")
    parser.add_argument("--mode", choices=["real", "mock"], default="real")
    parser.add_argument("--backend", choices=["qwenpaw", "opencode"])
    parser.add_argument("--runtime-provider", choices=["openai-env"])
    parser.add_argument("--warmup", action="store_true",
                        help="Pre-warm session on startup (avoid first-prompt delay)")
    parser.add_argument("--warmup-cwd", default=None,
                        help="Working directory for warmup")
    args = parser.parse_args()

    in_fp = sys.stdin.buffer
    out_fp = sys.stdout.buffer

    if args.mode == "mock":
        run_mock_bridge(in_fp, out_fp)
        return

    if not HAS_ACP:
        emit(out_fp, {"type": "error",
                      "message": "ACP SDK not available. pip install agent-client-protocol"})
        sys.exit(1)

    try:
        asyncio.run(run_real_bridge(in_fp, out_fp, args.backend, args.runtime_provider,
                                     args.warmup, args.warmup_cwd))
    except Exception as e:
        emit(out_fp, {"type": "error", "message": f"Bridge error: {e}"})


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, stream=sys.stderr)
    main()
