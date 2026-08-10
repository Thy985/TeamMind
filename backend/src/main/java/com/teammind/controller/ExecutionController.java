package com.teammind.controller;

import com.teammind.dto.ApiResponse;
import com.teammind.executor.*;
import com.teammind.llm.LLMTrackingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 执行控制器
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class ExecutionController {

    private final AgentExecutionEngine executionEngine;
    private final MissionRuntimeManager runtimeManager;
    private final LLMTrackingService trackingService;

    /**
     * 执行 Agent 任务
     */
    @PostMapping("/agents/{id}/execute")
    public ResponseEntity<ApiResponse<AgentExecutionResult>> executeAgent(
            @PathVariable String id,
            @RequestBody ExecutionRequest request) {

        AgentExecutionContext context = AgentExecutionContext.builder()
                .executionId(UUID.randomUUID().toString())
                .agentId(id)
                .missionId(request.getMissionId())
                .nodeId(request.getNodeId())
                .userRequest(request.getPrompt())
                .input(request.getInput())
                .dependencies(request.getDependencies())
                .maxIterations(request.getMaxIterations() != null ? request.getMaxIterations() : 10)
                .createdAt(LocalDateTime.now())
                .build();

        AgentExecutionResult result = executionEngine.execute(context).join();

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 流式执行 Agent（SSE）
     */
    @GetMapping(value = "/agents/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExecuteAgent(
            @PathVariable String id,
            @RequestParam(required = false) String prompt,
            @RequestParam(required = false) String missionId) {

        AgentExecutionContext context = AgentExecutionContext.builder()
                .executionId(UUID.randomUUID().toString())
                .agentId(id)
                .missionId(missionId)
                .userRequest(prompt)
                .createdAt(LocalDateTime.now())
                .build();

        SseEmitter emitter = new SseEmitter(300000L);

        executionEngine.execute(context).thenAccept(result -> {
            try {
                emitter.send(SseEmitter.event().name("result").data(result));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 启动任务（异步）
     */
    @PostMapping("/missions/{id}/run")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runMission(@PathVariable String id) {
        runtimeManager.startMission(id);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("missionId", id, "status", "started"),
                "Mission execution started"
        ));
    }

    /**
     * 暂停任务
     */
    @PostMapping("/missions/{id}/pause-execution")
    public ResponseEntity<ApiResponse<Void>> pauseMissionExecution(@PathVariable String id) {
        runtimeManager.pauseMission(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Mission paused"));
    }

    /**
     * 恢复任务
     */
    @PostMapping("/missions/{id}/resume-execution")
    public ResponseEntity<ApiResponse<Void>> resumeMissionExecution(@PathVariable String id) {
        runtimeManager.resumeMission(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Mission resumed"));
    }

    /**
     * 获取任务运行状态
     */
    @GetMapping("/missions/{id}/runtime")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMissionRuntime(@PathVariable String id) {
        MissionRuntimeManager.MissionRuntime runtime = runtimeManager.getMissionRuntime(id).orElse(null);
        
        if (runtime != null) {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of(
                            "missionId", id,
                            "paused", runtime.isPaused(),
                            "cancelled", runtime.isCancelled(),
                            "results", runtime.getResults().keySet()
                    )
            ));
        }
        
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("missionId", id, "status", "not_running")
        ));
    }

    /**
     * 获取 LLM 使用统计
     */
    @GetMapping("/usage/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUsageStats() {
        Map<String, Object> stats = trackingService.getUsageStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 获取今日使用统计
     */
    @GetMapping("/usage/today")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTodayStats() {
        Map<String, Object> stats = trackingService.getTodayStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 获取 Agent 使用统计
     */
    @GetMapping("/agents/{id}/usage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAgentUsage(@PathVariable String id) {
        Map<String, Object> stats = trackingService.getAgentStats(id);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 执行请求 DTO
     */
    @Data
    public static class ExecutionRequest {
        private String prompt;
        private String missionId;
        private String nodeId;
        private Map<String, Object> input;
        private Map<String, Object> dependencies;
        private Integer maxIterations;
    }
}
