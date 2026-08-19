#!/usr/bin/env python3
"""Debug opencode ACP flow."""
import asyncio, sys, shutil, time
from acp import spawn_agent_process, text_block, PROTOCOL_VERSION
from acp.schema import ClientCapabilities, Implementation

async def main():
    class C:
        def on_connect(self, conn):
            self._conn = conn
            print('ON_CONNECT', flush=True)
        async def session_update(self, sid, up, **kw):
            kind = getattr(up, 'sessionUpdate', '?')
            print(f'  UPDATE: {kind}', flush=True)
        async def request_permission(self, opts, sid, tc, **kw):
            from acp.schema import AllowedOutcome, RequestPermissionResponse
            return RequestPermissionResponse(
                outcome=AllowedOutcome(outcome='selected', option_id='allow_once'))
        async def ext_notification(self, m, p): pass
        async def ext_method(self, m, p): return {}

    c = C()
    opencode = shutil.which('opencode')
    start = time.time()
    async with spawn_agent_process(c, opencode, 'acp', cwd=r'D:\Projects\Active\TeamMind') as (conn, proc):
        print(f'PID={proc.pid} elapsed={time.time()-start:.1f}s', flush=True)
        await conn.initialize(
            protocol_version=PROTOCOL_VERSION,
            client_capabilities=ClientCapabilities(),
            client_info=Implementation(name='teammind', title='TeamMind', version='0.1.0'),
        )
        print(f'INIT OK', flush=True)

        print('NEW_SESSION...', flush=True)
        try:
            sess = await asyncio.wait_for(
                conn.new_session(cwd=r'D:\Projects\Active\TeamMind', mcpServers=[]),
                timeout=60.0)
            sid = sess.session_id
            print(f'SESSION={sid} elapsed={time.time()-start:.1f}s', flush=True)
        except Exception as e:
            print(f'SESSION FAILED: {e} elapsed={time.time()-start:.1f}s', flush=True)
            return

        print('PROMPT...', flush=True)
        resp = await asyncio.wait_for(
            conn.prompt(session_id=sid, prompt=[text_block('Say hello.')]),
            timeout=60.0)
        print(f'RESP stop_reason={resp.stop_reason} elapsed={time.time()-start:.1f}s', flush=True)

        await conn.close_session(session_id=sid)
        print(f'DONE elapsed={time.time()-start:.1f}s', flush=True)

asyncio.run(main())
