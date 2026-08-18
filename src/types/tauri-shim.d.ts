// M1: Tauri types are only available in tauri-app context.
// Root src/ code should not hard-depend on Tauri types.
declare module '@tauri-apps/api/core' {
  export function invoke<T = unknown>(command: string, args?: unknown): Promise<T>
}
declare module '@tauri-apps/api/event' {
  export function listen<T = unknown>(event: string, handler: (msg: { payload: T }) => void): Promise<() => void>
}
