#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""QwenPaw ACP Bridge for TeamMind — POC v1.

Synchronous bridge for simplicity. Protocol (JSONL):
  IN:  {"action":"prompt","prompt":"...","cwd":"/path"}
  OUT: {"type":"chunk","text":"..."}
  OUT: {"type":"done","stop_reason":"end_turn|cancelled"}
  OUT: {"type":"error","message":"..."}
  OUT: {"type":"ready","agent":"...","mode":"..."}
"""
from __future__ import annotations

import json
import os
import sys
import threading
import time
from pathlib import Path

# ─── Mock agent ──────────────────────────────────────────────────────

class EchoAgent:
    """Simple echo agent for POC validation."""

    RESPONSES = {
        "hello": "Hello! I'm QwenPaw (mock). I can help with research and consultation tasks.",
        "why": ("Based on analysis:\n1. Simplicity — minimal dependencies\n"
                "2. Type safety — Rust ownership model\n"
                "3. Ecosystem — works with existing CLI tools"),
    }

    def respond(self, prompt: str, cwd: str) -> list[str]:
        """Return a list of text chunks (simulates streaming)."""
        lower = prompt.lower()
        if "hello" in lower or "hi" in lower:
            text = self.RESPONSES["hello"]
        elif "why" in lower or "reason" in lower:
            text = self.RESPONSES["why"]
        else:
            text = (f"I received your prompt: \"{prompt[:100]}\".\n"
                    f"Working directory: {cwd}\n\n"
                    f"As a research/consultant agent, I can:\n"
                    "- Analyze code and documentation\n"
                    "- Review architecture decisions\n"
                    "- Provide recommendations\n"
                    "- Answer questions about the project")

        # Chunk for streaming simulation
        chunk_size = 40
        return [text[i:i + chunk_size] for i in range(0, len(text), chunk_size)]


# ─── Bridge logic ────────────────────────────────────────────────────

def emit(out_fd, event: dict):
    line = json.dumps(event, ensure_ascii=False) + "\n"
    out_fd.write(line.encode("utf-8"))
    out_fd.flush()


def run_bridge(in_fd, out_fd):
    """Main bridge loop — runs in a thread."""
    agent = EchoAgent()

    # Signal ready
    emit(out_fd, {"type": "ready", "agent": "qwenpaw-mock", "mode": "echo-fallback"})

    for raw_line in iter(in_fd.readline, b""):
        line = raw_line.decode("utf-8").strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except json.JSONDecodeError:
            emit(out_fd, {"type": "error", "message": "Invalid JSON"})
            continue

        action = msg.get("action")
        if action == "prompt":
            prompt = msg.get("prompt", "")
            cwd = msg.get("cwd", os.getcwd())
            if not prompt:
                emit(out_fd, {"type": "error", "message": "Empty prompt"})
                continue

            # Stream response
            chunks = agent.respond(prompt, cwd)
            for chunk in chunks:
                emit(out_fd, {"type": "chunk", "text": chunk})
                time.sleep(0.02)  # Simulate streaming delay
            emit(out_fd, {"type": "done", "stop_reason": "end_turn"})

        elif action == "cancel":
            emit(out_fd, {"type": "done", "stop_reason": "cancelled"})

        elif action == "ping":
            emit(out_fd, {"type": "pong"})

        else:
            emit(out_fd, {"type": "error", "message": f"Unknown action: {action}"})

    emit(out_fd, {"type": "closed"})


def main():
    # Use binary mode for raw pipe I/O
    in_fp = sys.stdin.buffer
    out_fp = sys.stdout.buffer

    # Run bridge in a thread so we can also handle signals
    bridge_thread = threading.Thread(
        target=run_bridge, args=(in_fp, out_fp), daemon=True
    )
    bridge_thread.start()
    bridge_thread.join()


if __name__ == "__main__":
    main()
