#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""QwenPaw ACP Bridge for TeamMind — POC v2.

Protocol (JSONL over stdin/stdout):
  IN:  {"action":"prompt","prompt":"...","cwd":"/path","session_id":"optional"}
  OUT: {"type":"chunk","text":"..."}
  OUT: {"type":"tool","name":"...","input":{...}}
  OUT: {"type":"permission","request_id":"...","title":"...","options":[...]}
  OUT: {"type":"done","stop_reason":"end_turn|cancelled"}
  OUT: {"type":"error","message":"..."}
  OUT: {"type":"ready","agent":"...","mode":"mock|real"}

When QwenPaw ACP SDK is available and workspace boots fast, uses real agent.
Otherwise falls back to echo mock (for POC validation).
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
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
        "hello": "Hello! I'm QwenPaw (mock). I can help with research and consultation tasks.",
        "why": ("Based on analysis:\n1. Simplicity — minimal dependencies\n"
                "2. Type safety — Rust ownership model\n"
                "3. Ecosystem — works with existing CLI tools"),
    }

    def respond(self, prompt, cwd):
        lower = prompt.lower()
        if "hello" in lower or "hi" in lower:
            text = self.RESPONSES["hello"]
        elif "why" in lower or "reason" in lower:
            text = self.RESPONSES["why"]
        else:
            text = (f"I received: \"{prompt[:100]}\".\nWorking dir: {cwd}\n\n"
                    f"As a research/consultant agent, I can:\n"
                    "- Analyze code and documentation\n"
                    "- Review architecture decisions\n"
                    "- Provide recommendations\n"
                    "- Answer project questions")
        return [text[i:i+40] for i in range(0, len(text), 40)]


# ─── Bridge helpers ──────────────────────────────────────────────────

def emit(out_fp, event):
    line = json.dumps(event, ensure_ascii=False) + "\n"
    out_fp.write(line.encode("utf-8"))
    out_fp.flush()


# ─── Mock bridge (synchronous) ───────────────────────────────────────

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
        else:
            emit(out_fp, {"type": "error", "message": f"Unknown action: {action}"})

    emit(out_fp, {"type": "closed"})


# ─── Real ACP bridge ─────────────────────────────────────────────────

async def _real_bridge_task(in_fp, out_fp):
    """Run the real ACP bridge in a background task."""
    cmd = [sys.executable, "-m", "qwenpaw", "acp", "--local-diagnostics"]

    class _Client:
        def __init__(self, out):
            self._out = out
            self._conn = None

        def on_connect(self, conn):
            self._conn = conn

        async def _emit(self, event):
            emit(self._out, event)

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
                    await self._emit({"type": "chunk", "text": text})
            elif kind == "agent_thought_chunk":
                await self._emit({"type": "think", "text": "[thinking]"})
            elif kind in ("tool_call_start", "tool_call_update"):
                await self._emit({"type": "tool",
                                  "name": getattr(up, "name", "tool"),
                                  "input": getattr(up, "raw_input", None)})

        async def request_permission(self, opts, sid, tc, **kw):
            from acp.schema import AllowedOutcome, RequestPermissionResponse
            await self._emit({"type": "permission",
                              "title": getattr(tc, "title", "?"),
                              "auto_approved": True})
            return RequestPermissionResponse(
                outcome=AllowedOutcome(outcome="selected", option_id="allow_once"))

        async def ext_notification(self, m, p):
            pass

        async def ext_method(self, m, p):
            return {}

    client = _Client(out_fp)
    session_id = None

    try:
        async with spawn_agent_process(client, *cmd) as (conn, proc):
            await conn.initialize(
                protocol_version=PROTOCOL_VERSION,
                client_capabilities=ClientCapabilities(),
                client_info=Implementation(name="teammind", title="TeamMind", version="0.1.0"),
            )
            emit(out_fp, {"type": "ready", "agent": "qwenpaw", "mode": "real", "pid": proc.pid})

            # Read stdin line by line (thread-based, avoids Windows pipe issues)
            loop = asyncio.get_event_loop()
            while True:
                try:
                    raw_line = await asyncio.wait_for(
                        loop.run_in_executor(None, in_fp.readline),
                        timeout=3.0)
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
                        if not session_id:
                            sess = await asyncio.wait_for(
                                conn.new_session(cwd=cwd, mcp_servers=[]),
                                timeout=15.0)
                            session_id = sess.session_id
                        await conn.prompt(session_id=session_id,
                                          prompt=[text_block(prompt)])
                        emit(out_fp, {"type": "done", "stop_reason": "end_turn"})
                    except asyncio.TimeoutError:
                        emit(out_fp, {"type": "error",
                                      "message": "QwenPaw workspace boot too slow (use --runtime-provider)"})
                    except Exception as e:
                        emit(out_fp, {"type": "error", "message": str(e)})
                elif action == "cancel":
                    emit(out_fp, {"type": "done", "stop_reason": "cancelled"})
                elif action == "ping":
                    emit(out_fp, {"type": "pong"})

    except Exception as e:
        emit(out_fp, {"type": "error", "message": f"Real ACP failed: {e}"})

    emit(out_fp, {"type": "closed"})


def run_real_bridge(in_fp, out_fp):
    """Run real ACP bridge, catch errors, fall back to mock."""
    asyncio.run(_real_bridge_task(in_fp, out_fp))


def main():
    """Run bridge.

    Modes:
      real  (default): Use real QwenPaw ACP. Errors if unavailable.
      mock: Use EchoAgent for POC testing only.
    """
    import argparse
    parser = argparse.ArgumentParser(description="QwenPaw ACP Bridge for TeamMind")
    parser.add_argument("--mode", choices=["real", "mock"], default="real",
                        help="Bridge mode (default: real)")
    args = parser.parse_args()

    in_fp = sys.stdin.buffer
    out_fp = sys.stdout.buffer

    if args.mode == "mock":
        run_mock_bridge(in_fp, out_fp)
        return

    # Real mode: no silent fallback
    if not HAS_ACP:
        emit(out_fp, {"type": "error",
                      "message": "ACP SDK not available. Install with: pip install agent-client-protocol"})
        sys.exit(1)

    logger.info("Attempting real QwenPaw ACP connection...")
    try:
        asyncio.run(_real_bridge_task(in_fp, out_fp))
    except Exception as e:
        emit(out_fp, {"type": "error", "message": f"Real ACP failed: {e}"})
        sys.exit(1)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, stream=sys.stderr)
    main()
