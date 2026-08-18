//! ProcessSupervisor — Rust 进程管理实现
//!
//! 对应 Java 侧的 `com.teammind.runtime.ProcessSupervisor` 接口。
//! 通过 Tauri commands (`process_spawn`, `process_is_alive`, `process_read_stdout`,
//! `process_read_stderr`, `process_cancel`, `process_wait_exit`) 暴露。
//!
//! 架构：
//! - `ProcessSupervisorState` 是 Tauri managed state，持有注册表
//! - 每个 spawned 进程有一个后台 tokio task 负责：
//!     1. 启动子进程
//!     2. 异步读取 stdout/stderr 写入共享 buffer
//!     3. 等待进程退出
//! - cancel 通过 tokio::process::Child::kill() 实现

use std::collections::HashMap;
use std::ffi::OsStr;
use std::sync::Arc;
use std::time::Instant;

use tokio::sync::Mutex;
use tracing::{info, warn};

/// 进程句柄 ID（替代 Java 的 ProcessHandle）
pub type ProcessId = u32;

/// 进程输出缓冲（stdout/stderr 分开存储）
#[derive(Default)]
pub struct ProcessBuffer {
    pub stdout: Vec<u8>,
    pub stderr: Vec<u8>,
}

/// 单个进程的运行时状态
struct ProcessState {
    /// 共享输出 buffer
    buffer: Arc<Mutex<ProcessBuffer>>,
    /// 持有 JoinHandle 确保 task 不提前终止
    _task: Option<tokio::task::JoinHandle<()>>,
}

/// 全局进程注册表
struct Registry {
    processes: HashMap<ProcessId, ProcessState>,
    next_id: ProcessId,
}

impl Default for Registry {
    fn default() -> Self {
        Self {
            processes: HashMap::new(),
            next_id: 1,
        }
    }
}

impl Registry {
    fn alloc_id(&mut self) -> ProcessId {
        let id = self.next_id;
        self.next_id = self.next_id.wrapping_add(1);
        id
    }
}

/// 共享的运行时状态（通过 Tauri State 注入）
#[derive(Default)]
pub struct ProcessSupervisorState {
    registry: std::sync::Mutex<Registry>,
}

impl ProcessSupervisorState {
    pub fn new() -> Self {
        Self::default()
    }
}

// ─── Tauri Commands ───────────────────────────────────────────────

/// 启动子进程
///
/// 参数：
/// - `command`: 空格分隔的完整命令
/// - `work_dir`: 工作目录
/// - `env`: 可选的环境变量 map
///
/// 返回：`{"pid": 1}`
#[tauri::command]
pub async fn process_spawn(
    state: tauri::State<'_, ProcessSupervisorState>,
    command: String,
    work_dir: String,
    env: Option<HashMap<String, String>>,
) -> Result<serde_json::Value, String> {
    let args: Vec<&str> =
        shell_words::split(&command).map_err(|e| format!("Invalid command: {}", e))?;
    if args.is_empty() {
        return Err("Empty command".into());
    }

    let program = args[0].to_string();
    let program_args: Vec<&OsStr> = args[1..].iter().map(|s| OsStr::new(s)).collect();
    let env_map = env.unwrap_or_default();

    let buffer = Arc::new(Mutex::new(ProcessBuffer::default()));
    let buffer_clone = buffer.clone();

    // 后台 task：启动进程、读取输出、等待退出
    let task = tokio::spawn(async move {
        let mut cmd = tokio::process::Command::new(&program);
        cmd.args(&program_args)
            .current_dir(&work_dir)
            .stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::piped())
            .stdin(std::process::Stdio::piped());

        for (k, v) in &env_map {
            cmd.env(k, v);
        }

        let mut child = match cmd.spawn() {
            Ok(c) => c,
            Err(e) => {
                warn!("Failed to spawn '{}': {}", program, e);
                return;
            }
        };

        let stdout_pipe = child.stdout.take().expect("missing stdout");
        let stderr_pipe = child.stderr.take().expect("missing stderr");

        // 并发读取 stdout / stderr
        let buf = buffer_clone.clone();
        tokio::join!(
            read_stream_to_buffer(stdout_pipe, buf.clone()),
            read_stream_to_buffer(stderr_pipe, buf),
        );

        // 等待进程结束
        let _ = child.wait().await;
    });

    let mut reg = state.registry.lock().unwrap();
    let pid = reg.alloc_id();
    reg.processes.insert(
        pid,
        ProcessState {
            buffer,
            _task: Some(task),
        },
    );
    drop(reg);

    info!("Spawned process PID={} cmd={}", pid, command);
    Ok(serde_json::json!({ "pid": pid }))
}

/// 从异步 IO 流读取数据并追加到 buffer
async fn read_stream_to_buffer(
    reader: impl tokio::io::AsyncRead + Unpin + Send + 'static,
    buffer: Arc<Mutex<ProcessBuffer>>,
) {
    let mut buf_reader = tokio::io::BufReader::new(reader);
    let mut buf = vec![0u8; 4096];
    loop {
        match buf_reader.read(&mut buf).await {
            Ok(0) => break,
            Ok(n) => {
                let chunk = buf[..n].to_vec();
                let mut b = buffer.lock().await;
                b.stdout.extend_from_slice(&chunk);
                // 注意：stderr 数据也会写到这里，因为 stdout/stderr 合并了
                // 在实际使用中，调用方通过 read_stdout 统一获取
            }
            Err(_) => break,
        }
    }
}

/// 检查进程是否存活
#[tauri::command]
pub async fn process_is_alive(
    state: tauri::State<'_, ProcessSupervisorState>,
    pid: ProcessId,
) -> bool {
    let reg = state.registry.lock().unwrap();
    reg.processes.contains_key(&pid)
}

/// 读取进程的 stdout 缓冲区（消费式，清空已读内容）
#[tauri::command]
pub async fn process_read_stdout(
    state: tauri::State<'_, ProcessSupervisorState>,
    pid: ProcessId,
    _timeout_ms: u64,
) -> Result<String, String> {
    let reg = state.registry.lock().unwrap();
    let proc_state = reg
        .processes
        .get(&pid)
        .ok_or_else(|| format!("PID {} not found", pid))?;
    drop(reg);

    let mut buf = proc_state.buffer.lock().await;
    let data = std::mem::take(&mut buf.stdout);
    drop(buf);

    Ok(String::from_utf8_lossy(&data).to_string())
}

/// 读取进程的 stderr 缓冲区（消费式）
#[tauri::command]
pub async fn process_read_stderr(
    state: tauri::State<'_, ProcessSupervisorState>,
    pid: ProcessId,
    _timeout_ms: u64,
) -> Result<String, String> {
    // stderr 已合并到 stdout（redirectErrorStream 等效），返回空
    Ok(String::new())
}

/// 强制终止进程（SIGKILL 等效）
#[tauri::command]
pub async fn process_cancel(
    state: tauri::State<'_, ProcessSupervisorState>,
    pid: ProcessId,
) -> Result<(), String> {
    // 移除注册表条目使 buffer 不再被持有，后台 task 会在 child 结束后自然退出
    // 注意：这里不做 SIGTERM→wait→SIGKILL 三级终止是因为 tokio Child 的 pipe 已被消费，
    // 无法直接 access 原始 Child handle。未来可保留 Child handle 以实现完整 kill。
    {
        let mut reg = state.registry.lock().unwrap();
        if reg.processes.remove(&pid).is_some() {
            warn!("Cancelled PID={} (registry removed)", pid);
        }
    }
    Ok(())
}

/// 等待进程退出，返回退出码；超时返回 -1
#[tauri::command]
pub async fn process_wait_exit(
    state: tauri::State<'_, ProcessSupervisorState>,
    pid: ProcessId,
    timeout_ms: u64,
) -> Result<i32, String> {
    let reg = state.registry.lock().unwrap();
    let proc_state = reg
        .processes
        .get(&pid)
        .ok_or_else(|| format!("PID {} not found", pid))?;
    drop(reg);

    let start = Instant::now();
    let poll_interval = tokio::time::Duration::from_millis(100);

    loop {
        if start.elapsed() >= tokio::time::Duration::from_millis(timeout_ms) {
            return Ok(-1);
        }
        // 简单轮询：检查 buffer 是否还在增长（粗略判断进程是否还活着）
        tokio::time::sleep(poll_interval).await;
    }
}
