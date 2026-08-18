#!/usr/bin/env python3
import asyncio, sys
from acp import spawn_agent_process, PROTOCOL_VERSION
from acp.schema import ClientCapabilities, Implementation

class C:
    def on_connect(self, conn):
        print("ON_CONNECT", flush=True)
        self._conn = conn

    async def session_update(self, sid, up, **kw):
        kind = getattr(up, "sessionUpdate", "?")
        if kind == "agent_message_chunk":
            content = getattr(up, "content", None)
            text = ""
            if isinstance(content, dict):
                text = content.get("text", "")
            else:
                text = getattr(content, "text", "") or ""
            if text:
                print(f"  TEXT: {text[:100]}", flush=True)
        elif kind:
            print(f"  UPDATE: {kind}", flush=True)

    async def request_permission(self, opts, sid, tc, **kw):
        from acp.schema import AllowedOutcome, RequestPermissionResponse
        print(f"  PERMISSION: auto-allow", flush=True)
        return RequestPermissionResponse(
            outcome=AllowedOutcome(outcome="selected", option_id="allow_once")
        )

    async def ext_notification(self, m, p):
        pass

    async def ext_method(self, m, p):
        return {}

async def main():
    c = C()
    print("SPAWNING...", flush=True)
    async with spawn_agent_process(
        c,
        sys.executable, "-m", "qwenpaw", "acp", "--local-diagnostics"
    ) as (conn, proc):
        print(f"ENTERED: pid={proc.pid}", flush=True)
        print("INITIALIZING...", flush=True)
        init = await conn.initialize(
            protocol_version=PROTOCOL_VERSION,
            client_capabilities=ClientCapabilities(),
            client_info=Implementation(name="teammind", title="TeamMind", version="0.1.0"),
        )
        agent = init.agent_info.name if init.agent_info else "?"
        print(f"INIT OK: agent={agent} ver={init.protocol_version}", flush=True)

        print("NEW_SESSION...", flush=True)
        sess = await conn.new_session(cwd=r"D:\Projects\Active\TeamMind")
        sid = sess.session_id
        print(f"SESSION: {sid}", flush=True)

        print("\n--- Prompt: hello ---", flush=True)
        resp = await conn.prompt(session_id=sid, prompt=[text_block("Say hello in one sentence.")])
        print(f"stop_reason={resp.stop_reason}", flush=True)

        print("\n--- Prompt: why rust ---", flush=True)
        resp = await conn.prompt(session_id=sid, prompt=[text_block("Why did TeamMind choose Rust?")])
        print(f"stop_reason={resp.stop_reason}", flush=True)

        await conn.close_session(session_id=sid)
        print("\nDONE!", flush=True)

if __name__ == "__main__":
    from acp import text_block
    asyncio.run(main())
