#!/usr/bin/env python3
"""Test QwenPaw ACP with manual process management (no context manager)."""
import asyncio, sys, time, json, os

from acp import spawn_agent_process, text_block, PROTOCOL_VERSION
from acp.schema import ClientCapabilities, Implementation

async def main():
    events = []
    class C:
        def on_connect(self, conn):
            self._conn = conn
            print('ON_CONNECT', flush=True)
        async def session_update(self, sid, up, **kw):
            kind = getattr(up, 'sessionUpdate', '?')
            events.append(kind)
            if kind == 'agent_message_chunk':
                content = getattr(up, 'content', None)
                text = ''
                if isinstance(content, dict):
                    text = content.get('text', '')
                else:
                    text = getattr(content, 'text', '') or ''
                if text:
                    print(f'  TEXT: {text[:60]}', flush=True)
        async def request_permission(self, opts, sid, tc, **kw):
            from acp.schema import AllowedOutcome, RequestPermissionResponse
            return RequestPermissionResponse(
                outcome=AllowedOutcome(outcome='selected', option_id='allow_once'))
        async def ext_notification(self, m, p): pass
        async def ext_method(self, m, p): return {}

    c = C()
    start = time.time()
    cmd = [sys.executable, '-m', 'qwenpaw', 'acp', '--local-diagnostics']
    print(f'Starting QwenPaw ACP (this will take 2-3 minutes for workspace boot)...', flush=True)

    # Use spawn_agent_process but manage lifecycle manually
    ctx = spawn_agent_process(c, *cmd)
    conn, proc = await ctx.__aenter__()
    try:
        print(f'PID={proc.pid} elapsed={time.time()-start:.1f}s', flush=True)
        await conn.initialize(
            protocol_version=PROTOCOL_VERSION,
            client_capabilities=ClientCapabilities(),
            client_info=Implementation(name='teammind', title='TeamMind', version='0.1.0'),
        )
        print(f'INIT OK elapsed={time.time()-start:.1f}s', flush=True)

        # Warmup: create session (this is where workspace boot happens)
        print('WARMUP: creating session (may take 2-3 min)...', flush=True)
        sess = await conn.new_session(cwd=r'D:\Projects\Active\TeamMind', mcpServers=[])
        sid = sess.session_id
        warmup_time = time.time() - start
        print(f'SESSION={sid} WARMUP_DONE elapsed={warmup_time:.1f}s', flush=True)
        print(f'Events during warmup: {len(events)}', flush=True)

        # First prompt (should be fast after warmup)
        print('\n--- PROMPT 1: hello ---', flush=True)
        t1 = time.time()
        resp = await conn.prompt(session_id=sid, prompt=[text_block('Say hello in one sentence.')])
        p1_time = time.time() - t1
        print(f'RESP stop_reason={resp.stop_reason} PROMPT_TIME={p1_time:.1f}s', flush=True)
        print(f'Total elapsed: {time.time()-start:.1f}s', flush=True)
        print(f'Events: {len(events)}', flush=True)

        # Second prompt (should also be fast)
        print('\n--- PROMPT 2: why rust ---', flush=True)
        t2 = time.time()
        resp = await conn.prompt(session_id=sid, prompt=[text_block('Why is Rust good for systems programming?')])
        p2_time = time.time() - t2
        print(f'RESP stop_reason={resp.stop_reason} PROMPT_TIME={p2_time:.1f}s', flush=True)
        print(f'Total elapsed: {time.time()-start:.1f}s', flush=True)
        print(f'Events: {len(events)}', flush=True)

        await conn.close_session(session_id=sid)
        print(f'\nDONE! total={time.time()-start:.1f}s', flush=True)
    except Exception as e:
        print(f'ERROR: {e}', flush=True)
        import traceback
        traceback.print_exc()
    finally:
        await ctx.__aexit__(None, None, None)
        print('Connection closed', flush=True)

if __name__ == '__main__':
    asyncio.run(main())
