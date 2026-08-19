#!/usr/bin/env python3
"""Test QwenPaw ACP method names."""
import asyncio, sys, time, json

async def main():
    start = time.time()
    proc = await asyncio.create_subprocess_exec(
        sys.executable, '-m', 'qwenpaw', 'acp', '--local-diagnostics',
        stdin=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )

    async def read_stderr():
        while True:
            line = await proc.stderr.readline()
            if not line: break
            text = line.decode().strip()
            if text and 'ACP' in text:
                print(f'[log] {text}', flush=True)

    asyncio.create_task(read_stderr())

    # Send initialize
    init_req = {'jsonrpc': '2.0', 'id': 1, 'method': 'initialize',
                'params': {'protocolVersion': 1, 'clientCapabilities': {},
                           'clientInfo': {'name': 'test', 'version': '0.1'}}}
    proc.stdin.write((json.dumps(init_req) + '\n').encode())
    await proc.stdin.drain()
    line = await asyncio.wait_for(proc.stdout.readline(), timeout=30.0)
    init_resp = json.loads(line.decode())
    result = init_resp.get('result', {})
    print(f'INIT result keys: {list(result.keys())}', flush=True)
    caps = result.get('agentCapabilities', {})
    print(f'Agent capabilities: {json.dumps(caps, indent=2)}', flush=True)

    # Try different method names for session creation
    methods_to_try = [
        'new_session',
        'session/new_session',
        'sessions/new',
        'acp/new_session',
    ]
    for method in methods_to_try:
        req = {'jsonrpc': '2.0', 'id': 3, 'method': method,
               'params': {'cwd': r'D:\Projects\Active\TeamMind', 'mcpServers': []}}
        proc.stdin.write((json.dumps(req) + '\n').encode())
        await proc.stdin.drain()
        try:
            line = await asyncio.wait_for(proc.stdout.readline(), timeout=5.0)
            resp = json.loads(line.decode())
            err = resp.get('error', {})
            res = resp.get('result', {})
            print(f'{method}: error={err.get("message","none")[:50]} result_keys={list(res.keys()) if isinstance(res, dict) else type(res).__name__}', flush=True)
        except asyncio.TimeoutError:
            print(f'{method}: TIMEOUT', flush=True)

    proc.terminate()
    await proc.wait()
    print(f'DONE elapsed={time.time()-start:.1f}s', flush=True)

asyncio.run(main())
