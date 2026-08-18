//! TeamMind Tauri Commands 鈥?鎵€鏈夋闈㈢鍙敤鐨?API 鍏ュ彛
//!
//! M1: HTTP proxy (async 鈫?Result<ApiResponse, String>)
//! M2: ProcessSupervisor (async 鈫?Result)
//! M2.5: ACP Event Stream (async 鈫?Result)
//!
//! All #[tauri::command] must be in this module.

use std::collections::HashMap;
use std::ffi::OsStr;
use std::sync::Arc;

use serde::{Deserialize, Serialize};
use tokio::io::{AsyncBufReadExt, AsyncReadExt, BufReader};
use tokio::sync::{Mutex, Notify};
use tracing::{info, warn};

// 鈹€鈹€鈹€ State Types 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

#[derive(Default)]
pub struct RuntimeState {
    pub backend_url: String,
}

impl RuntimeState {
    pub fn new() -> Self {
        Self {
            backend_url: std::env::var("TEAMMIND_BACKEND_URL")
                .unwrap_or_else(|_| "http://localhost:8080".to_string()),
        }
    }
}

// M2 types
pub type ProcessId = u32;

#[derive(Default)]
pub struct ProcessBuffer {
    pub stdout: Vec<u8>,
    pub stderr: Vec<u8>,
}

/// Per-process state (buffer + child handle + exit code cache + cancellation flag)
struct ProcessState {
    buffer: Arc<Mutex<ProcessBuffer>>,
    child: Arc<Mutex<Option<tokio::process::Child>>>,
    /// Cached exit code after process exits; Some(set), None (not yet known)
    exit_code: std::sync::Mutex<Option<i32>>,
    /// True once cancel() has been called — used by wait_exit for final cleanup
    cancelled: std::sync::Mutex<bool>,
}

/// Global registry: pid → process state + JoinHandle (tasks kept until drained)
struct Registry {
    processes: HashMap<ProcessId, ProcessState>,
    tasks: HashMap<ProcessId, tokio::task::JoinHandle<i32>>,
    next_id: ProcessId,
}

impl Default for Registry {
    fn default() -> Self {
        Self {
            processes: HashMap::new(),
            tasks: HashMap::new(),
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

#[derive(Default)]
pub struct ProcessSupervisorState {
    pub registry: std::sync::Mutex<Registry>,
}

impl ProcessSupervisorState {
    pub fn new() -> Self { Self::default() }
}

// M2.5 types
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct AcpEvent {
    pub event_type: String,
    pub task_id: String,
    pub plugin_id: String,
    pub role: String,
    pub metadata: HashMap<String, serde_json::Value>,
}

impl AcpEvent {
    pub fn new(
        event_type: &str,
        task_id: &str,
        plugin_id: &str,
        metadata: HashMap<String, serde_json::Value>,
    ) -> Self {
        Self {
            event_type: event_type.to_string(),
            task_id: task_id.to_string(),
            plugin_id: plugin_id.to_string(),
            role: "EXECUTOR".to_string(),
            metadata,
        }
    }
}

#[derive(Default)]
pub struct EventsBuffer {
    events: Vec<AcpEvent>,
    notify: Notify,
}

impl EventsBuffer {
    pub fn new() -> Self { Self::default() }
    pub async fn push(&mut self, event: AcpEvent) {
        self.events.push(event);
        self.notify.notify_waiters();
    }
    pub async fn drain(&mut self) -> Vec<AcpEvent> {
        if self.events.is_empty() { return Vec::new(); }
        std::mem::take(&mut self.events)
    }
    pub async fn wait_for_events(&self, timeout: std::time::Duration) -> bool {
        tokio::time::timeout(timeout, self.notify.notified()).await.is_ok()
    }
    pub fn len(&self) -> usize { self.events.len() }
}

#[derive(Default)]
pub struct AcpStreamerState {
    sessions: std::sync::Mutex<HashMap<u32, Arc<Mutex<EventsBuffer>>>>,
    next_id: std::sync::atomic::AtomicU32,
}

impl AcpStreamerState {
    pub fn new() -> Self {
        Self {
            sessions: std::sync::Mutex::new(HashMap::new()),
            next_id: std::sync::atomic::AtomicU32::new(1),
        }
    }
    pub fn alloc_id(&self) -> u32 {
        let id = self.next_id.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
        id
    }
    pub fn register(&self, id: u32, buffer: Arc<Mutex<EventsBuffer>>) {
        let mut sessions = self.sessions.lock().unwrap();
        sessions.insert(id, buffer);
    }
    pub fn get(&self, id: u32) -> Option<Arc<Mutex<EventsBuffer>>> {
        let sessions = self.sessions.lock().unwrap();
        sessions.get(&id).cloned()
    }
    pub fn unregister(&self, id: u32) -> bool {
        let mut sessions = self.sessions.lock().unwrap();
        sessions.remove(&id).is_some()
    }
}

// 鈹€鈹€鈹€ Shared Response Type 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

#[derive(Serialize)]
pub struct ApiResponse<T: Serialize> {
    pub success: bool,
    pub data: Option<T>,
    pub error: Option<String>,
}

impl<T: Serialize> ApiResponse<T> {
    pub fn ok(data: T) -> Self {
        Self { success: true, data: Some(data), error: None }
    }
    pub fn err(msg: &str) -> Self {
        Self { success: false, data: None, error: Some(msg.to_string()) }
    }
}

#[derive(Deserialize)]
pub struct InvokeRequest { path: String, body: Option<serde_json::Value> }

#[derive(Deserialize)]
pub struct GetRequest { path: String, params: Option<serde_json::Value> }

// 鈹€鈹€鈹€ Entry Point 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .manage(RuntimeState::new())
        .manage(ProcessSupervisorState::new())
        .manage(AcpStreamerState::new())
        .invoke_handler(tauri::generate_handler![
            health_check, 
            project_list, project_get, project_create, project_delete,
            task_list, task_get, task_create, task_pause, task_resume,
            task_cancel, task_retry, task_approve, task_events, task_activity,
            mc_overview, mc_running, mc_history, mc_profile,
            mc_recommendation, mc_drift, mc_recalculate,
            mc_control_mode, mc_set_control_mode,
            agent_list, agent_get, agent_toggle,
            knowledge_save, knowledge_get_by_task,
            process_spawn, process_is_alive, process_read_stdout,
            process_read_stderr, process_cancel, process_wait_exit,
            acp_spawn_stream, acp_read_events, acp_close_stream,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

// 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?// M1: HTTP Proxy
// 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
#[tauri::command]
async fn health_check(state: tauri::State<'_, RuntimeState>) -> Result<ApiResponse<serde_json::Value>, String> {
    let url = format!("{}/api/health", state.backend_url);
    match reqwest::Client::new().get(&url).send().await {
        Ok(resp) => Ok(ApiResponse::ok(serde_json::json!({
            "service": "teammind", "mode": "tauri-proxy",
            "backend_url": state.backend_url, "backend_status": resp.status().as_u16(),
        }))),
        Err(e) => Ok(ApiResponse::err(&format!("Backend unreachable: {}", e))),
    }
}

async fn do_invoke(state: &RuntimeState, req: InvokeRequest) -> Result<ApiResponse<serde_json::Value>, String> {
    let url = format!("{}{}", state.backend_url, req.path);
    let client = reqwest::Client::new();
    let resp = match req.body {
        Some(body) => client.post(&url).json(&body).send().await,
        None => client.post(&url).send().await,
    };
    match resp {
        Ok(r) if r.status().is_success() => {
            let status = r.status().as_u16();
            match r.bytes().await {
                Ok(b) => match serde_json::from_slice::<serde_json::Value>(&b) {
                    Ok(data) => Ok(ApiResponse::ok(data)),
                    Err(_) => Ok(ApiResponse::ok(serde_json::json!({"status": status}))),
                },
                Err(_) => Ok(ApiResponse::ok(serde_json::json!({"status": status}))),
            }
        }
        Ok(r) => Ok(ApiResponse::err(&format!("HTTP {}", r.status()))),
        Err(e) => Ok(ApiResponse::err(&e.to_string())),
    }
}

async fn do_stream(state: &RuntimeState, req: GetRequest) -> Result<ApiResponse<serde_json::Value>, String> {
    let mut url = format!("{}{}", state.backend_url, req.path);
    if let Some(params) = req.params {
        let query: Vec<String> = params.as_object()
            .map(|m| m.iter().map(|(k, v)| format!("{}={}", k, v)).collect())
            .unwrap_or_default();
        if !query.is_empty() { url.push_str(&format!("?{}", query.join("&"))); }
    }
    match reqwest::Client::new().get(&url).send().await {
        Ok(r) if r.status().is_success() => {
            let status = r.status().as_u16();
            match r.bytes().await {
                Ok(b) => match serde_json::from_slice::<serde_json::Value>(&b) {
                    Ok(data) => Ok(ApiResponse::ok(data)),
                    Err(_) => Ok(ApiResponse::ok(serde_json::json!({"status": status}))),
                },
                Err(_) => Ok(ApiResponse::ok(serde_json::json!({"status": status}))),
            }
        }
        Ok(r) => Ok(ApiResponse::err(&format!("HTTP {}", r.status()))),
        Err(e) => Ok(ApiResponse::err(&e.to_string())),
    }
}

// 鈹€鈹€鈹€ Project 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

#[tauri::command] async fn project_list(s: tauri::State<'_, RuntimeState>) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:"/api/projects".into(),params:None}).await }
#[tauri::command] async fn project_get(s: tauri::State<'_, RuntimeState>, id: String) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:format!("/api/projects/{}",id),params:None}).await }
#[tauri::command] async fn project_create(s: tauri::State<'_, RuntimeState>, name: String) -> Result<ApiResponse<serde_json::Value>, String> { do_invoke(&s, InvokeRequest{path:"/api/projects".into(),body:Some(serde_json::json!({"name":name}))}).await }
#[tauri::command] async fn project_delete(s: tauri::State<'_, RuntimeState>, id: String) -> Result<ApiResponse<serde_json::Value>, String> {
    let url = format!("{}/api/projects/{}", s.backend_url, id);
    match reqwest::Client::new().delete(&url).send().await {
        Ok(r) if r.status().is_success() => Ok(ApiResponse::ok(serde_json::json!({"deleted":id}))),
        Ok(r) => Ok(ApiResponse::err(&format!("HTTP {}",r.status()))),
        Err(e) => Ok(ApiResponse::err(&e.to_string())),
    }
}

// 鈹€鈹€鈹€ Task 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

#[tauri::command] async fn task_list(s: tauri::State<'_, RuntimeState>, pid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:format!("/api/projects/{}/tasks",pid),params:None}).await }
#[tauri::command] async fn task_get(s: tauri::State<'_, RuntimeState>, tid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:format!("/api/tasks/{}",tid),params:None}).await }
#[tauri::command] async fn task_create(s: tauri::State<'_, RuntimeState>, pid: String, objective: String) -> Result<ApiResponse<serde_json::Value>, String> { do_invoke(&s, InvokeRequest{path:format!("/api/projects/{}/tasks",pid),body:Some(serde_json::json!({"objective":objective}))}).await }
#[tauri::command] async fn task_pause(s: tauri::State<'_, RuntimeState>, tid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_invoke(&s, InvokeRequest{path:format!("/api/tasks/{}/pause",tid),body:None}).await }
#[tauri::command] async fn task_resume(s: tauri::State<'_, RuntimeState>, tid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_invoke(&s, InvokeRequest{path:format!("/api/tasks/{}/resume",tid),body:None}).await }
#[tauri::command] async fn task_cancel(s: tauri::State<'_, RuntimeState>, tid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_invoke(&s, InvokeRequest{path:format!("/api/tasks/{}/cancel",tid),body:None}).await }
#[tauri::command] async fn task_retry(s: tauri::State<'_, RuntimeState>, tid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_invoke(&s, InvokeRequest{path:format!("/api/tasks/{}/retry",tid),body:None}).await }
#[tauri::command] async fn task_approve(s: tauri::State<'_, RuntimeState>, tid: String, body: serde_json::Value) -> Result<ApiResponse<serde_json::Value>, String> { do_invoke(&s, InvokeRequest{path:format!("/api/tasks/{}/approve",tid),body:Some(body)}).await }
#[tauri::command] async fn task_events(s: tauri::State<'_, RuntimeState>, tid: String, after: Option<i64>) -> Result<ApiResponse<serde_json::Value>, String> {
    let path = match after { Some(t) => format!("/api/tasks/{}/events?after={}", tid, t), None => format!("/api/tasks/{}/events", tid) };
    do_stream(&s, GetRequest{path, params:None}).await
}
#[tauri::command] async fn task_activity(s: tauri::State<'_, RuntimeState>, tid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:format!("/api/tasks/{}/activity",tid),params:None}).await }

// 鈹€鈹€鈹€ Mission Control 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

#[tauri::command] async fn mc_overview(s: tauri::State<'_, RuntimeState>, pid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:format!("/api/mission-control/project/{}/overview",pid),params:None}).await }
#[tauri::command] async fn mc_running(s: tauri::State<'_, RuntimeState>, pid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:format!("/api/mission-control/project/{}/running",pid),params:None}).await }
#[tauri::command] async fn mc_history(s: tauri::State<'_, RuntimeState>, pid: String, limit: Option<i32>) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:format!("/api/mission-control/project/{}/history?limit={}",pid,limit.unwrap_or(20)),params:None}).await }
#[tauri::command] async fn mc_profile(s: tauri::State<'_, RuntimeState>, pid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:format!("/api/mission-control/project/{}/profile",pid),params:None}).await }
#[tauri::command] async fn mc_recommendation(s: tauri::State<'_, RuntimeState>, pid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:format!("/api/mission-control/project/{}/recommendation",pid),params:None}).await }
#[tauri::command] async fn mc_drift(s: tauri::State<'_, RuntimeState>, pid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:format!("/api/mission-control/project/{}/drift",pid),params:None}).await }
#[tauri::command] async fn mc_recalculate(s: tauri::State<'_, RuntimeState>, pid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_invoke(&s, InvokeRequest{path:format!("/api/mission-control/project/{}/recalculate",pid),body:None}).await }
#[tauri::command] async fn mc_control_mode(s: tauri::State<'_, RuntimeState>, pid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:format!("/api/mission-control/project/{}/control-mode",pid),params:None}).await }
#[tauri::command] async fn mc_set_control_mode(s: tauri::State<'_, RuntimeState>, pid: String, mode: String) -> Result<ApiResponse<serde_json::Value>, String> { do_invoke(&s, InvokeRequest{path:format!("/api/mission-control/project/{}/control-mode",pid),body:Some(serde_json::json!({"controlMode":mode}))}).await }

// 鈹€鈹€鈹€ Agent 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

#[tauri::command] async fn agent_list(s: tauri::State<'_, RuntimeState>) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:"/api/agents".into(),params:None}).await }
#[tauri::command] async fn agent_get(s: tauri::State<'_, RuntimeState>, id: String) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:format!("/api/agents/{}",id),params:None}).await }
#[tauri::command] async fn agent_toggle(s: tauri::State<'_, RuntimeState>, id: String, enabled: bool) -> Result<ApiResponse<serde_json::Value>, String> { do_invoke(&s, InvokeRequest{path:format!("/api/agents/{}/enabled",id),body:Some(serde_json::json!({"enabled":enabled}))}).await }

// 鈹€鈹€鈹€ Knowledge 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

#[tauri::command] async fn knowledge_save(s: tauri::State<'_, RuntimeState>, data: serde_json::Value) -> Result<ApiResponse<serde_json::Value>, String> { do_invoke(&s, InvokeRequest{path:"/api/knowledge".into(),body:Some(data)}).await }
#[tauri::command] async fn knowledge_get_by_task(s: tauri::State<'_, RuntimeState>, tid: String) -> Result<ApiResponse<serde_json::Value>, String> { do_stream(&s, GetRequest{path:format!("/api/knowledge/task/{}",tid),params:None}).await }

// 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?// M2: ProcessSupervisor
// 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
#[tauri::command]
async fn process_spawn(
    state: tauri::State<'_, ProcessSupervisorState>,
    command: String,
    work_dir: String,
    env: Option<HashMap<String, String>>,
) -> Result<serde_json::Value, String> {
    let args: Vec<String> = shell_words::split(&command).map_err(|e| format!("Invalid command: {}", e))?;
    if args.is_empty() { return Err("Empty command".into()); }

    let program = args[0].clone();
    let program_args: Vec<String> = args[1..].to_vec();
    let env_map = env.unwrap_or_default();

    let buffer = Arc::new(Mutex::new(ProcessBuffer::default()));
    let child_handle = Arc::new(Mutex::new(None::<tokio::process::Child>));
    let buffer_clone = buffer.clone();
    let child_clone = child_handle.clone();

    // Spawn the background task; capture JoinHandle
    let task = tokio::spawn(async move {
        let pargs: Vec<&OsStr> = program_args.iter().map(|s| OsStr::new(s)).collect();
        let mut cmd = tokio::process::Command::new(&program);
        cmd.args(&pargs).current_dir(&work_dir)
            .stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::piped())
            .stdin(std::process::Stdio::piped());
        for (k, v) in &env_map { cmd.env(k, v); }

        let mut child = match cmd.spawn() {
            Ok(c) => c,
            Err(e) => { warn!("Failed to spawn '{}': {}", program, e); return 1; }
        };

        // Extract pipes BEFORE storing child
        let stdout_pipe = child.stdout.take().expect("missing stdout");
        let stderr_pipe = child.stderr.take().expect("missing stderr");

        // Store child for cancel
        { let mut ch = child_clone.lock().await; *ch = Some(child); }

        tokio::join!(
            read_stream_to_buffer(stdout_pipe, buffer_clone.clone(), false),
            read_stream_to_buffer(stderr_pipe, buffer_clone, true),
        );

        // Wait via stored child handle
        let mut ch = child_clone.lock().await;
        if let Some(ref mut c) = *ch {
            c.wait().await.ok().and_then(|s| s.code()).unwrap_or(-1)
        } else {
            -1
        }
    });

    // Register (process entry kept in registry until drain/close)
    let mut reg = state.registry.lock().unwrap();
    let pid = reg.alloc_id();
    reg.processes.insert(pid, ProcessState {
        buffer,
        child: child_handle,
        exit_code: std::sync::Mutex::new(None),
        cancelled: std::sync::Mutex::new(false),
    });
    reg.tasks.insert(pid, task);
    drop(reg);

    info!("Spawned process PID={} cmd={}", pid, command);
    Ok(serde_json::json!({ "pid": pid }))
}

async fn read_stream_to_buffer(
    reader: impl tokio::io::AsyncRead + Unpin + Send + 'static,
    buffer: Arc<Mutex<ProcessBuffer>>,
    is_stderr: bool,
) {
    let mut br = tokio::io::BufReader::new(reader);
    let mut buf = vec![0u8; 4096];
    loop {
        match br.read(&mut buf).await {
            Ok(0) => break,
            Ok(n) => {
                let chunk = buf[..n].to_vec();
                let mut b = buffer.lock().await;
                if is_stderr { b.stderr.extend_from_slice(&chunk); }
                else { b.stdout.extend_from_slice(&chunk); }
            }
            Err(_) => break,
        }
    }
}

/// Drain buffer up to `timeout_ms` milliseconds; returns whatever is available.
async fn drain_buffer(buf: &Arc<Mutex<ProcessBuffer>>, timeout_ms: u64) -> Vec<u8> {
    if timeout_ms == 0 {
        let mut b = buf.lock().await;
        return std::mem::take(&mut b.stdout); // non-blocking drain
    }
    let deadline = tokio::time::Instant::now() + tokio::time::Duration::from_millis(timeout_ms);
    loop {
        let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
        if remaining.is_zero() { break; }
        let drain = tokio::time::timeout(remaining, {
            let buf = buf.clone();
            async move {
                let mut b = buf.lock().await;
                std::mem::take(&mut b.stdout)
            }
        }).await;
        match drain {
            Ok(data) if !data.is_empty() => return data,
            Ok(_) => { /* empty or timeout */ break; }
            Err(_) => break, // timeout expired
        }
    }
    // Final non-blocking read
    let mut b = buf.lock().await;
    std::mem::take(&mut b.stdout)
}

#[tauri::command]
async fn process_is_alive(state: tauri::State<'_, ProcessSupervisorState>, pid: ProcessId) -> Result<bool, String> {
    Ok(state.registry.lock().unwrap().processes.contains_key(&pid))
}

#[tauri::command]
async fn process_read_stdout(state: tauri::State<'_, ProcessSupervisorState>, pid: ProcessId, timeout_ms: u64) -> Result<String, String> {
    let buf = {
        let reg = state.registry.lock().unwrap();
        reg.processes.get(&pid).ok_or_else(|| format!("PID {} not found", pid))?.buffer.clone()
    };
    let data = drain_buffer(&buf, timeout_ms).await;
    Ok(String::from_utf8_lossy(&data).to_string())
}

#[tauri::command]
async fn process_read_stderr(state: tauri::State<'_, ProcessSupervisorState>, pid: ProcessId, timeout_ms: u64) -> Result<String, String> {
    let buf = {
        let reg = state.registry.lock().unwrap();
        reg.processes.get(&pid).ok_or_else(|| format!("PID {} not found", pid))?.buffer.clone()
    };
    // Reuse drain logic for stderr
    if timeout_ms == 0 {
        let data = { let mut b = buf.lock().await; std::mem::replace(&mut b.stderr, Vec::new()) };
        return Ok(String::from_utf8_lossy(&data).to_string());
    }
    let deadline = tokio::time::Instant::now() + tokio::time::Duration::from_millis(timeout_ms);
    loop {
        let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
        if remaining.is_zero() { break; }
        let drain = tokio::time::timeout(remaining, {
            let buf = buf.clone();
            async move {
                let mut b = buf.lock().await;
                std::mem::take(&mut b.stderr)
            }
        }).await;
        match drain {
            Ok(data) if !data.is_empty() => return Ok(String::from_utf8_lossy(&data).to_string()),
            Ok(_) => break,
            Err(_) => break,
        }
    }
    let data = { let mut b = buf.lock().await; std::mem::replace(&mut b.stderr, Vec::new()) };
    Ok(String::from_utf8_lossy(&data).to_string())
}

/// Drain the process entry from the registry after wait_exit has cached the exit code.
/// Safe to call multiple times (idempotent). Called automatically by wait_exit when
/// the JoinHandle completes; callers can also invoke this explicitly to release memory.
#[tauri::command]
async fn process_drain(state: tauri::State<'_, ProcessSupervisorState>, pid: ProcessId) -> Result<(), String> {
    let mut reg = state.registry.lock().unwrap();
    reg.processes.remove(&pid);
    reg.tasks.remove(&pid);
    Ok(())
}

#[tauri::command]
async fn process_cancel(state: tauri::State<'_, ProcessSupervisorState>, pid: ProcessId) -> Result<(), String> {
    // Signal cancellation BEFORE killing, so wait_exit knows to drain and remove
    {
        let reg = state.registry.lock().unwrap();
        if let Some(ps) = reg.processes.get(&pid) {
            *ps.cancelled.lock().unwrap() = true;
        }
    }
    // Kill the process
    {
        let child = {
            let reg = state.registry.lock().unwrap();
            reg.processes.get(&pid).map(|s| s.child.clone())
        };
        if let Some(child) = child {
            let mut c = child.lock().await;
            if let Some(ref mut ch) = *c {
                if let Err(e) = ch.kill().await { warn!("kill() failed for PID={}: {}", pid, e); }
            }
            *c = None;
        }
    }
    info!("Cancelled PID={}", pid);
    Ok(())
}

#[tauri::command]
async fn process_wait_exit(state: tauri::State<'_, ProcessSupervisorState>, pid: ProcessId, timeout_ms: u64) -> Result<i32, String> {
    // Take the JoinHandle without removing the process entry (idempotent + cancel-safe)
    let task = {
        let mut reg = state.registry.lock().unwrap();
        reg.tasks.remove(&pid)
    };
    let Some(task) = task else {
        // Process already completed — return cached exit code if available
        let ec = {
            let reg = state.registry.lock().unwrap();
            reg.processes.get(&pid).and_then(|ps| *ps.exit_code.lock().unwrap())
        };
        return Ok(ec.unwrap_or(-1));
    };

    let result = tokio::time::timeout(
        tokio::time::Duration::from_millis(timeout_ms),
        task,
    ).await;

    let code = match result {
        Ok(code) => code.unwrap_or(-1),
        Err(_) => { warn!("PID={} wait timeout", pid); -1 }
    };

    // Cache exit code and clean up background reader task references
    {
        let mut reg = state.registry.lock().unwrap();
        if let Some(ps) = reg.processes.get_mut(&pid) {
            *ps.exit_code.lock().unwrap() = Some(code);
        }
        // Remove reader JoinHandle so it can be dropped
        reg.tasks.remove(&pid);
    }
    info!("PID={} exited with code={}", pid, code);
    Ok(code)
}

// 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?// M2.5: ACP Event Stream
// 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
fn sval(node: &serde_json::Value, key: &str) -> String {
    node.get(key).and_then(|v| v.as_str()).unwrap_or("").to_string()
}

fn parse_acp_event(node: &serde_json::Value, task_id: &str, plugin_id: &str) -> Vec<AcpEvent> {
    let Some(type_val) = node.get("type").and_then(|v| v.as_str()) else { return vec![]; };
    let mut events = Vec::new();
    match type_val {
        "thread.started" => {
            let mut m = HashMap::new();
            m.insert("thread_id".into(), sval(node, "thread_id").into());
            m.insert("source".into(), "codex_thread_started".into());
            events.push(AcpEvent::new("PROCESS_STARTED", task_id, plugin_id, m));
        }
        "turn.started" => {
            let mut m = HashMap::new(); m.insert("source".into(), "codex_turn_started".into());
            events.push(AcpEvent::new("AGENT_THINKING", task_id, plugin_id, m));
        }
        "turn.completed" => {
            let mut m = HashMap::new(); m.insert("source".into(), "codex_turn_completed".into());
            if let Some(usage) = node.get("usage") {
                if let Some(v) = usage.get("input_tokens").and_then(|x| x.as_u64()) { m.insert("input_tokens".into(), v.into()); }
                if let Some(v) = usage.get("output_tokens").and_then(|x| x.as_u64()) { m.insert("output_tokens".into(), v.into()); }
            }
            events.push(AcpEvent::new("TASK_COMPLETED", task_id, plugin_id, m));
        }
        "item.completed" => { if let Some(item) = node.get("item") { events.extend(parse_codex_item(item, task_id, plugin_id)); } }
        "item.started" => { if let Some(item) = node.get("item") { events.extend(parse_codex_item_started(item, task_id, plugin_id)); } }
        "system" => events.extend(parse_claude_system(node, task_id, plugin_id)),
        "assistant" => events.extend(parse_claude_assistant(node, task_id, plugin_id)),
        "result" => events.extend(parse_claude_result(node, task_id, plugin_id)),
        _ => {
            let mut m = HashMap::new(); m.insert("raw_type".into(), type_val.into());
            events.push(AcpEvent::new("AGENT_CHUNK", task_id, plugin_id, m));
        }
    }
    events
}

fn parse_codex_item(item: &serde_json::Value, task_id: &str, plugin_id: &str) -> Vec<AcpEvent> {
    let mut events = Vec::new();
    let item_type = sval(item, "type");
    let item_id = sval(item, "id");
    match item_type.as_str() {
        "agent_message" => {
            let text = sval(item, "text");
            if !text.is_empty() {
                let mut m = HashMap::new();
                m.insert("content".into(), text[..text.len().min(200)].into());
                m.insert("item_id".into(), item_id.into()); m.insert("source".into(), "codex_agent_message".into());
                events.push(AcpEvent::new("AGENT_CHUNK", task_id, plugin_id, m));
            }
        }
        "reasoning" => {
            let text = sval(item, "text");
            if !text.is_empty() {
                let mut m = HashMap::new();
                m.insert("content".into(), text[..text.len().min(200)].into());
                m.insert("source".into(), "codex_reasoning".into());
                events.push(AcpEvent::new("AGENT_THINKING", task_id, plugin_id, m));
            }
        }
        "error" => {
            let mut m = HashMap::new();
            m.insert("message".into(), sval(item, "message").into());
            m.insert("item_id".into(), item_id.into()); m.insert("source".into(), "codex_error".into());
            events.push(AcpEvent::new("ERROR_RECOVERABLE", task_id, plugin_id, m));
        }
        "command_execution" => {
            let status = sval(item, "status");
            let exit_code = item.get("exit_code").and_then(|v| v.as_i64());
            let output = sval(item, "aggregated_output");
            let command = item.get("command").map(|v| v.to_string()).unwrap_or_default();
            if status == "completed" {
                let et = if exit_code == Some(0) { "TOOL_RESULT" } else { "ERROR_RECOVERABLE" };
                let mut m = HashMap::new();
                m.insert("command".into(), command.into());
                if let Some(code) = exit_code { m.insert("exit_code".into(), code.into()); }
                if !output.is_empty() { m.insert("output".into(), output[..output.len().min(500)].into()); }
                m.insert("source".into(), "codex_command_execution".into());
                events.push(AcpEvent::new(et, task_id, plugin_id, m));
            } else if status == "in_progress" {
                let mut m = HashMap::new(); m.insert("command".into(), command.into());
                m.insert("source".into(), "codex_command_started".into());
                events.push(AcpEvent::new("TOOL_CALLED", task_id, plugin_id, m));
            }
        }
        _ => {
            let mut m = HashMap::new();
            m.insert("item_type".into(), item_type.into()); m.insert("item_id".into(), item_id.into());
            m.insert("source".into(), "codex_item".into());
            events.push(AcpEvent::new("AGENT_CHUNK", task_id, plugin_id, m));
        }
    }
    events
}

fn parse_codex_item_started(item: &serde_json::Value, task_id: &str, plugin_id: &str) -> Vec<AcpEvent> {
    if sval(item, "type") == "command_execution" {
        let command = item.get("command").map(|v| v.to_string()).unwrap_or_default();
        let mut m = HashMap::new();
        m.insert("command".into(), serde_json::Value::String(command));
        m.insert("source".into(), "codex_command_started".into());
        vec![AcpEvent::new("TOOL_CALLED", task_id, plugin_id, m)]
    } else { vec![] }
}

fn parse_claude_system(node: &serde_json::Value, task_id: &str, plugin_id: &str) -> Vec<AcpEvent> {
    let mut events = Vec::new();
    let subtype = sval(node, "subtype");
    match subtype.as_str() {
        "init" => {
            let mut m = HashMap::new();
            m.insert("session_id".into(), sval(node, "session_id").into());
            m.insert("model".into(), sval(node, "model").into());
            m.insert("permission_mode".into(), sval(node, "permissionMode").into());
            m.insert("source".into(), "claude_system_init".into());
            events.push(AcpEvent::new("PROCESS_STARTED", task_id, plugin_id, m));
        }
        "thinking_tokens" => {
            let mut m = HashMap::new();
            if let Some(v) = node.get("estimated_tokens").and_then(|x| x.as_u64()) { m.insert("estimated_tokens".into(), v.into()); }
            m.insert("source".into(), "claude_thinking".into());
            events.push(AcpEvent::new("AGENT_THINKING", task_id, plugin_id, m));
        }
        _ => {
            let mut m = HashMap::new(); m.insert("raw_subtype".into(), subtype.into());
            m.insert("source".into(), "claude_system".into());
            events.push(AcpEvent::new("AGENT_CHUNK", task_id, plugin_id, m));
        }
    }
    events
}

fn parse_claude_assistant(node: &serde_json::Value, task_id: &str, plugin_id: &str) -> Vec<AcpEvent> {
    let mut events = Vec::new();
    let Some(message) = node.get("message") else { return events; };
    let Some(content) = message.get("content") else { return events; };
    let Some(arr) = content.as_array() else { return events; };
    for part in arr {
        match sval(part, "type").as_str() {
            "text" => {
                let text = sval(part, "text");
                if !text.is_empty() {
                    let mut m = HashMap::new();
                    m.insert("content".into(), text[..text.len().min(200)].into());
                    m.insert("source".into(), "claude_assistant_text".into());
                    events.push(AcpEvent::new("AGENT_CHUNK", task_id, plugin_id, m));
                }
            }
            "thinking" => {
                let text = part.get("thinking").and_then(|v| v.as_str()).unwrap_or("");
                if !text.is_empty() {
                    let mut m = HashMap::new();
                    m.insert("content".into(), text[..text.len().min(200)].into());
                    m.insert("source".into(), "claude_thinking".into());
                    events.push(AcpEvent::new("AGENT_THINKING", task_id, plugin_id, m));
                }
            }
            "tool_use" => {
                let mut m = HashMap::new();
                m.insert("tool".into(), sval(part, "name").into());
                m.insert("input".into(), part.get("input").map(|v| v.to_string()).unwrap_or_default().into());
                m.insert("source".into(), "claude_tool_use".into());
                events.push(AcpEvent::new("TOOL_CALLED", task_id, plugin_id, m));
            }
            _ => {
                let mut m = HashMap::new();
                m.insert("part_type".into(), sval(part, "type").into());
                m.insert("source".into(), "claude_assistant".into());
                events.push(AcpEvent::new("AGENT_CHUNK", task_id, plugin_id, m));
            }
        }
    }
    events
}

fn parse_claude_result(node: &serde_json::Value, task_id: &str, plugin_id: &str) -> Vec<AcpEvent> {
    let subtype = sval(node, "subtype");
    let is_error = subtype == "error";
    let mut m = HashMap::new();
    m.insert("stop_reason".into(), sval(node, "stop_reason").into());
    if let Some(d) = node.get("duration_ms").and_then(|v| v.as_u64()) { m.insert("duration_ms".into(), d.into()); }
    if let Some(r) = node.get("result").and_then(|v| v.as_str()) { m.insert("result_length".into(), r.len().into()); }
    m.insert("source".into(), "claude_result".into());
    let et = if is_error { "TASK_FAILED" } else { "TASK_COMPLETED" };
    vec![AcpEvent::new(et, task_id, plugin_id, m)]
}

#[tauri::command]
async fn acp_spawn_stream(
    sup_state: tauri::State<'_, ProcessSupervisorState>,
    acp_state: tauri::State<'_, AcpStreamerState>,
    pid: u32,
    task_id: String,
    plugin_id: String,
    program: String,
    args: Vec<String>,
) -> Result<serde_json::Value, String> {
    {
        let reg = sup_state.registry.lock().unwrap();
        if !reg.processes.contains_key(&pid) {
            return Err(format!("PID {} not found in ProcessSupervisor", pid));
        }
    }
    let buffer = Arc::new(Mutex::new(EventsBuffer::new()));
    let stream_id = acp_state.alloc_id();
    acp_state.register(stream_id, buffer.clone());
    let tid = task_id.clone();
    tokio::spawn(spawn_event_stream_inner(pid, buffer, tid, plugin_id, program, args));
    Ok(serde_json::json!({ "stream_id": stream_id }))
}

async fn spawn_event_stream_inner(
    pid: u32,
    buffer: Arc<Mutex<EventsBuffer>>,
    task_id: String,
    plugin_id: String,
    program: String,
    args: Vec<String>,
) {
    if args.is_empty() { warn!("[ACP] Empty args for PID={}", pid); return; }
    let program_args: Vec<&str> = args.iter().map(|s| s.as_str()).collect();
    info!("[ACP] Starting event stream PID={} program={:?}", pid, program_args);
    let mut child = match tokio::process::Command::new(&program).args(&program_args)
        .stdout(std::process::Stdio::piped()).stderr(std::process::Stdio::piped()).spawn() {
        Ok(c) => c,
        Err(e) => {
            warn!("[ACP] Failed to spawn '{}': {}", program, e);
            let mut buf = buffer.lock().await;
            buf.push(AcpEvent::new("ERROR_CRITICAL", &task_id, &plugin_id,
                [("message".into(), format!("{}", e).into())].into_iter().collect())).await;
            return;
        }
    };
    let stdout = child.stdout.take().expect("missing stdout");
    let stderr = child.stderr.take().expect("missing stderr");
    let buf_clone = buffer.clone();
    let tid2 = task_id.clone();
    tokio::join!(
        read_and_parse_events(stdout, buffer.clone(), &task_id, &plugin_id),
        read_stderr_events(stderr, &buf_clone, &tid2, &plugin_id),
    );
    let exit_code = child.wait().await.ok().and_then(|s| s.code()).unwrap_or(-1);
    let et = if exit_code == 0 { "TASK_COMPLETED" } else { "TASK_FAILED" };
    let mut meta = HashMap::new();
    meta.insert("exit_code".into(), exit_code.into());
    meta.insert("source".into(), "process_exit".into());
    let mut buf = buffer.lock().await;
    buf.push(AcpEvent::new(et, &task_id, &plugin_id, meta)).await;
    info!("[ACP] PID={} exited with code={}", pid, exit_code);
}

async fn read_and_parse_events(
    reader: impl tokio::io::AsyncRead + Unpin + Send + 'static,
    buffer: Arc<Mutex<EventsBuffer>>,
    task_id: &str,
    plugin_id: &str,
) {
    let mut br = BufReader::new(reader);
    let mut line = String::new();
    loop {
        line.clear();
        match br.read_line(&mut line).await {
            Ok(0) => break,
            Ok(_) => {
                let line = line.trim().to_string();
                if line.is_empty() || !line.starts_with('{') {
                    if !line.is_empty() {
                        let mut m = HashMap::new();
                        m.insert("raw_output".into(), line[..line.len().min(200)].into());
                        m.insert("source".into(), "raw_stdout".into());
                        let mut buf = buffer.lock().await;
                        buf.push(AcpEvent::new("AGENT_CHUNK", task_id, plugin_id, m)).await;
                    }
                    continue;
                }
                match serde_json::from_str::<serde_json::Value>(&line) {
                    Ok(node) => {
                        let events = parse_acp_event(&node, task_id, plugin_id);
                        let mut buf = buffer.lock().await;
                        for event in events { buf.push(event).await; }
                    }
                    Err(e) => warn!("[ACP] Failed to parse: {}: {}", e, line),
                }
            }
            Err(e) => { warn!("[ACP] Read error: {}", e); break; }
        }
    }
}

async fn read_stderr_events(
    reader: impl tokio::io::AsyncRead + Unpin + Send + 'static,
    buffer: &Arc<Mutex<EventsBuffer>>,
    task_id: &str,
    plugin_id: &str,
) {
    let mut br = BufReader::new(reader);
    let mut buf = vec![0u8; 4096];
    loop {
        match br.read(&mut buf).await {
            Ok(0) => break,
            Ok(n) => {
                let text = String::from_utf8_lossy(&buf[..n]);
                if !text.trim().is_empty() {
                    let mut m = HashMap::new();
                    m.insert("stderr".into(), text.to_string().into());
                    m.insert("source".into(), "process_stderr".into());
                    let mut b = buffer.lock().await;
                    b.push(AcpEvent::new("ERROR_RECOVERABLE", task_id, plugin_id, m)).await;
                }
            }
            Err(_) => break,
        }
    }
}

#[tauri::command]
async fn acp_read_events(state: tauri::State<'_, AcpStreamerState>, stream_id: u32, timeout_ms: u64) -> Result<serde_json::Value, String> {
    let buffer = state.get(stream_id).ok_or_else(|| format!("Stream {} not found", stream_id))?;
    {
        let mut buf = buffer.lock().await;
        let events = buf.drain().await;
        if !events.is_empty() { return Ok(serde_json::json!({ "events": events })); }
    }
    let has_events = buffer.lock().await.wait_for_events(std::time::Duration::from_millis(timeout_ms)).await;
    let mut buf = buffer.lock().await;
    let events = buf.drain().await;
    Ok(serde_json::json!({ "events": events, "has_more": has_events && buf.len() > 0 }))
}

#[tauri::command]
async fn acp_close_stream(state: tauri::State<'_, AcpStreamerState>, stream_id: u32) -> Result<(), String> {
    if !state.unregister(stream_id) { return Err(format!("Stream {} not found", stream_id)); }
    Ok(())
}




