#!/usr/bin/env python3
import asyncio, sys, shutil, time
from acp import spawn_agent_process, text_block, PROTOCOL_VERSION
from acp.schema import ClientCapabilities, Implementation

async def main():
    class C:
        def on_connect(self, conn):
            print('ON_CONNECT', flush=True)
            self._conn = conn
        async def session_update(self, sid, up, **kw):
            kind = getattr(up, 'sessionUpdate', '?')
            print(f'  UPDATE: {kind}', flush=True)
        async def request_permission(self, opts, sid, tc, **kw):
            from acp.schema import AllowedOutcome, RequestPermissionResponse
            return RequestPermissionResponse(outcome=AllowedOutcome(outcome='selected', option_id='allow_once'))
        async def ext_notification(self, m, p): pass
        async def ext_method(self, m, p): return {}

    c = C()
    opencode = shutil.which('opencode')
    print(f'opencode: {opencode}', flush=True)
    start = time.time()
    async with spawn_agent_process(c, opencode, 'acp', cwd=r'D:\Projects\Active\TeamMind') as (conn, proc):
        await conn.initialize(
            protocol_version=PROTOCOL_VERSION,
            client_capabilities=ClientCapabilities(),
            client_info=Implementation(name='teammind', title='TeamMind', version='0.1.0'),
        )
        sess = await conn.new_session(cwd=r'D:\Projects\Active\TeamMind', mcpServers=[])
        sid = sess.session_id
        print(f'SESSION={sid}', flush=True)

        print('PROMPT: hello...', flush=True)
        resp = await asyncio.wait_for(
            conn.prompt(session_id=sid, prompt=[text_block('Say hello in one sentence.')]),
            timeout=60.0
        )
        print(f'RESP type: {type(resp).__name__}', flush=True)
        attrs = [a for a in dir(resp) if not a.startswith('_')]
        print(f'RESP attrs: {attrs}', flush=True)
        for attr in ['stop_reason', 'message', 'content', 'text', 'model', 'usage']:
            if hasattr(resp, attr):
                val = getattr(resp, attr)
                print(f'  .{attr} = {repr(val)[:100]}', flush=True)

        await conn.close_session(session_id=sid)
        print(f'SUCCESS! elapsed={time.time()-start:.1f}s', flush=True)

asyncio.run(main())
