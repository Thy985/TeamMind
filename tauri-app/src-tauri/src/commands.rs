//! TeamMind Tauri Commands — 所有桌面端可用的 API 入口
//!
//! 每个函数都是 Spring Boot REST API 的代理。
//! 通过 Feature Flag 切换，未来可以替换为原生 Rust 实现。

use serde::{Deserialize, Serialize};
use tauri::State;

// Re-export process_supervisor commands for use in run()
#[allow(unused_imports)]
use crate::process_supervisor::{process_spawn, process_is_alive, process_read_stdout, process_cancel, process_wait_exit};

// ─── State ────────────────────────────────────────────────────────

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

// ─── Shared Types ─────────────────────────────────────────────────

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

// ─── Entry Point ──────────────────────────────────────────────────

/// 注册所有 Tauri commands 并启动应用
pub fn run<S: Send + Default + 'static>() {
    tauri::builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_store::init())
        .manage(RuntimeState::new())
        .invoke_handler(tauri::generate_handler![
            health_check,
            runtime_invoke,
            runtime_stream,
            project_list, project_get, project_create, project_delete,
            task_list, task_get, task_create, task_pause, task_resume,
            task_cancel, task_retry, task_approve,
            task_events, task_activity,
            mc_overview, mc_running, mc_history, mc_profile,
            mc_recommendation, mc_drift, mc_recalculate,
            mc_control_mode, mc_set_control_mode,
            agent_list, agent_get, agent_toggle,
            knowledge_save, knowledge_get_by_task,
            // M2: Process Supervisor (Rust Provider)
            process_spawn, process_is_alive, process_read_stdout, process_cancel, process_wait_exit,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

// ─── Health ───────────────────────────────────────────────────────

#[tauri::command]
pub async fn health_check(state: State<RuntimeState>) -> ApiResponse<serde_json::Value> {
    let client = reqwest::Client::new();
    let url = format!("{}/api/health", state.backend_url);
    match client.get(&url).send().await {
        Ok(resp) => ApiResponse::ok(serde_json::json!({
            "service": "teammind",
            "mode": "tauri-proxy",
            "backend_url": state.backend_url,
            "backend_status": resp.status().as_u16(),
        })),
        Err(e) => ApiResponse::err(&format!("Backend unreachable: {}", e)),
    }
}

// ─── Generic Proxy ────────────────────────────────────────────────

#[tauri::command]
pub async fn runtime_invoke(
    state: State<RuntimeState>,
    req: InvokeRequest,
) -> ApiResponse<serde_json::Value> {
    let url = format!("{}{}", state.backend_url, req.path);
    let client = reqwest::Client::new();
    let resp = match req.body {
        Some(body) => client.post(&url).json(&body).send().await,
        None => client.post(&url).send().await,
    };
    match resp {
        Ok(r) if r.status().is_success() => {
            match r.json::<serde_json::Value>().await {
                Ok(data) => ApiResponse::ok(data),
                Err(_) => ApiResponse::ok(serde_json::json!({"status": r.status().as_u16()})),
            }
        }
        Ok(r) => ApiResponse::err(&format!("HTTP {}", r.status())),
        Err(e) => ApiResponse::err(&e.to_string()),
    }
}

#[tauri::command]
pub async fn runtime_stream(
    state: State<RuntimeState>,
    req: GetRequest,
) -> ApiResponse<serde_json::Value> {
    let mut url = format!("{}{}", state.backend_url, req.path);
    if let Some(params) = req.params {
        let query: Vec<String> = params
            .as_object()
            .map(|m| m.iter().map(|(k, v)| format!("{}={}", k, v)).collect())
            .unwrap_or_default();
        if !query.is_empty() {
            url.push_str(&format!("?{}", query.join("&")));
        }
    }
    let client = reqwest::Client::new();
    match client.get(&url).send().await {
        Ok(r) if r.status().is_success() => {
            match r.json::<serde_json::Value>().await {
                Ok(data) => ApiResponse::ok(data),
                Err(_) => ApiResponse::ok(serde_json::json!({"status": r.status().as_u16()})),
            }
        }
        Ok(r) => ApiResponse::err(&format!("HTTP {}", r.status())),
        Err(e) => ApiResponse::err(&e.to_string()),
    }
}

// ─── Project Commands ─────────────────────────────────────────────

#[tauri::command]
pub async fn project_list(state: State<RuntimeState>) -> ApiResponse<serde_json::Value> {
    runtime_stream(state, GetRequest { path: "/api/projects".into(), params: None }).await
}

#[tauri::command]
pub async fn project_get(state: State<RuntimeState>, id: String) -> ApiResponse<serde_json::Value> {
    runtime_stream(state, GetRequest { path: format!("/api/projects/{}", id), params: None }).await
}

#[tauri::command]
pub async fn project_create(
    state: State<RuntimeState>,
    name: String,
) -> ApiResponse<serde_json::Value> {
    runtime_invoke(state, InvokeRequest {
        path: "/api/projects".into(),
        body: Some(serde_json::json!({ "name": name })),
    }).await
}

#[tauri::command]
pub async fn project_delete(state: State<RuntimeState>, id: String) -> ApiResponse<serde_json::Value> {
    let url = format!("{}/api/projects/{}", state.backend_url, id);
    match reqwest::Client::new().delete(&url).send().await {
        Ok(r) if r.status().is_success() => ApiResponse::ok(serde_json::json!({ "deleted": id })),
        Ok(r) => ApiResponse::err(&format!("HTTP {}", r.status())),
        Err(e) => ApiResponse::err(&e.to_string()),
    }
}

// ─── Task Commands ────────────────────────────────────────────────

#[tauri::command]
pub async fn task_list(state: State<RuntimeState>, project_id: String) -> ApiResponse<serde_json::Value> {
    runtime_stream(state, GetRequest {
        path: format!("/api/projects/{}/tasks", project_id),
        params: None,
    }).await
}

#[tauri::command]
pub async fn task_get(state: State<RuntimeState>, task_id: String) -> ApiResponse<serde_json::Value> {
    runtime_stream(state, GetRequest {
        path: format!("/api/tasks/{}", task_id),
        params: None,
    }).await
}

#[tauri::command]
pub async fn task_create(
    state: State<RuntimeState>,
    project_id: String,
    objective: String,
) -> ApiResponse<serde_json::Value> {
    runtime_invoke(state, InvokeRequest {
        path: format!("/api/projects/{}/tasks", project_id),
        body: Some(serde_json::json!({ "objective": objective })),
    }).await
}

#[tauri::command]
pub async fn task_pause(state: State<RuntimeState>, task_id: String) -> ApiResponse<serde_json::Value> {
    runtime_invoke(state, InvokeRequest {
        path: format!("/api/tasks/{}/pause", task_id), body: None,
    }).await
}

#[tauri::command]
pub async fn task_resume(state: State<RuntimeState>, task_id: String) -> ApiResponse<serde_json::Value> {
    runtime_invoke(state, InvokeRequest {
        path: format!("/api/tasks/{}/resume", task_id), body: None,
    }).await
}

#[tauri::command]
pub async fn task_cancel(state: State<RuntimeState>, task_id: String) -> ApiResponse<serde_json::Value> {
    runtime_invoke(state, InvokeRequest {
        path: format!("/api/tasks/{}/cancel", task_id), body: None,
    }).await
}

#[tauri::command]
pub async fn task_retry(state: State<RuntimeState>, task_id: String) -> ApiResponse<serde_json::Value> {
    runtime_invoke(state, InvokeRequest {
        path: format!("/api/tasks/{}/retry", task_id), body: None,
    }).await
}

#[tauri::command]
pub async fn task_approve(
    state: State<RuntimeState>,
    task_id: String,
    body: serde_json::Value,
) -> ApiResponse<serde_json::Value> {
    runtime_invoke(state, InvokeRequest {
        path: format!("/api/tasks/{}/approve", task_id),
        body: Some(body),
    }).await
}

// ─── Task Events ──────────────────────────────────────────────────

#[tauri::command]
pub async fn task_events(
    state: State<RuntimeState>,
    task_id: String,
    after: Option<i64>,
) -> ApiResponse<serde_json::Value> {
    let path = match after {
        Some(ts) => format!("/api/tasks/{}/events?after={}", task_id, ts),
        None => format!("/api/tasks/{}/events", task_id),
    };
    runtime_stream(state, GetRequest { path, params: None }).await
}

#[tauri::command]
pub async fn task_activity(
    state: State<RuntimeState>,
    task_id: String,
) -> ApiResponse<serde_json::Value> {
    runtime_stream(state, GetRequest {
        path: format!("/api/tasks/{}/activity", task_id),
        params: None,
    }).await
}

// ─── Mission Control Commands ─────────────────────────────────────

macro_rules! mc_get {
    ($fn:ident, $path:expr) => {
        #[tauri::command]
        pub async fn $fn(state: State<RuntimeState>, project_id: String) -> ApiResponse<serde_json::Value> {
            runtime_stream(state, GetRequest {
                path: format!($path, project_id),
                params: None,
            }).await
        }
    };
    ($fn:ident, $path:expr, $limit:ident) => {
        #[tauri::command]
        pub async fn $fn(state: State<RuntimeState>, project_id: String, $limit: Option<i32>) -> ApiResponse<serde_json::Value> {
            let limit = $limit.unwrap_or(20);
            runtime_stream(state, GetRequest {
                path: format!($path, project_id, limit),
                params: None,
            }).await
        }
    };
}

mc_get!(mc_overview, "/api/mission-control/project/{}/overview");
mc_get!(mc_running, "/api/mission-control/project/{}/running");
mc_get!(mc_history, "/api/mission-control/project/{}/history?limit={}", limit);
mc_get!(mc_profile, "/api/mission-control/project/{}/profile");
mc_get!(mc_recommendation, "/api/mission-control/project/{}/recommendation");
mc_get!(mc_drift, "/api/mission-control/project/{}/drift");

#[tauri::command]
pub async fn mc_recalculate(
    state: State<RuntimeState>,
    project_id: String,
) -> ApiResponse<serde_json::Value> {
    runtime_invoke(state, InvokeRequest {
        path: format!("/api/mission-control/project/{}/recalculate", project_id),
        body: None,
    }).await
}

mc_get!(mc_control_mode, "/api/mission-control/project/{}/control-mode");

#[tauri::command]
pub async fn mc_set_control_mode(
    state: State<RuntimeState>,
    project_id: String,
    mode: String,
) -> ApiResponse<serde_json::Value> {
    runtime_invoke(state, InvokeRequest {
        path: format!("/api/mission-control/project/{}/control-mode", project_id),
        body: Some(serde_json::json!({ "controlMode": mode })),
    }).await
}

// ─── Agent Commands ───────────────────────────────────────────────

#[tauri::command]
pub async fn agent_list(state: State<RuntimeState>) -> ApiResponse<serde_json::Value> {
    runtime_stream(state, GetRequest { path: "/api/agents".into(), params: None }).await
}

#[tauri::command]
pub async fn agent_get(state: State<RuntimeState>, id: String) -> ApiResponse<serde_json::Value> {
    runtime_stream(state, GetRequest { path: format!("/api/agents/{}", id), params: None }).await
}

#[tauri::command]
pub async fn agent_toggle(
    state: State<RuntimeState>,
    id: String,
    enabled: bool,
) -> ApiResponse<serde_json::Value> {
    runtime_invoke(state, InvokeRequest {
        path: format!("/api/agents/{}/enabled", id),
        body: Some(serde_json::json!({ "enabled": enabled })),
    }).await
}

// ─── Knowledge Commands ───────────────────────────────────────────

#[tauri::command]
pub async fn knowledge_save(
    state: State<RuntimeState>,
    data: serde_json::Value,
) -> ApiResponse<serde_json::Value> {
    runtime_invoke(state, InvokeRequest {
        path: "/api/knowledge".into(),
        body: Some(data),
    }).await
}

#[tauri::command]
pub async fn knowledge_get_by_task(
    state: State<RuntimeState>,
    task_id: String,
) -> ApiResponse<serde_json::Value> {
    runtime_stream(state, GetRequest {
        path: format!("/api/knowledge/task/{}", task_id),
        params: None,
    }).await
}
