package com.teammind.controller;

import com.teammind.dto.*;
import com.teammind.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent Controller
 * 
 * Agent 管理 API
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class AgentController {

    private final AgentService agentService;

    /**
     * 获取所有 Agent（市场 + 已安装）
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentDTO>>> listAgents() {
        List<AgentDTO> agents = agentService.listAgents();
        return ResponseEntity.ok(ApiResponse.success(agents));
    }

    /**
     * 获取已安装的 Agent
     */
    @GetMapping("/installed")
    public ResponseEntity<ApiResponse<List<AgentDTO>>> listInstalledAgents() {
        List<AgentDTO> agents = agentService.listInstalledAgents();
        return ResponseEntity.ok(ApiResponse.success(agents));
    }

    /**
     * 获取 Agent 详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AgentDTO>> getAgent(@PathVariable String id) {
        AgentDTO agent = agentService.getAgent(id);
        return ResponseEntity.ok(ApiResponse.success(agent));
    }

    /**
     * 创建自定义 Agent
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AgentDTO>> createAgent(
            @Valid @RequestBody CreateAgentRequest request) {
        AgentDTO agent = agentService.createAgent(request);
        return ResponseEntity.ok(ApiResponse.success(agent, "Agent created successfully"));
    }

    /**
     * 安装 Agent
     */
    @PostMapping("/{id}/install")
    public ResponseEntity<ApiResponse<AgentDTO>> installAgent(@PathVariable String id) {
        AgentDTO agent = agentService.installAgent(id);
        return ResponseEntity.ok(ApiResponse.success(agent, "Agent installed successfully"));
    }

    /**
     * 卸载 Agent
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> uninstallAgent(@PathVariable String id) {
        agentService.uninstallAgent(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Agent uninstalled successfully"));
    }

    /**
     * 切换 Agent 启用状态
     */
    @PutMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<AgentDTO>> toggleAgent(
            @PathVariable String id,
            @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Missing 'enabled' field"));
        }
        AgentDTO agent = agentService.toggleAgent(id, enabled);
        return ResponseEntity.ok(ApiResponse.success(agent));
    }

    /**
     * 触发进化
     */
    @PostMapping("/{id}/evolve")
    public ResponseEntity<ApiResponse<EvolutionResultDTO>> triggerEvolution(
            @PathVariable String id,
            @RequestBody EvolutionRequest request) {
        EvolutionResultDTO result = agentService.triggerEvolution(id, request);
        return ResponseEntity.ok(ApiResponse.success(result, "Evolution triggered successfully"));
    }

    /**
     * 获取进化历史
     */
    @GetMapping("/{id}/evolution/history")
    public ResponseEntity<ApiResponse<List<EvolutionResultDTO>>> getEvolutionHistory(
            @PathVariable String id) {
        List<EvolutionResultDTO> history = agentService.getEvolutionHistory(id);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    /**
     * 用户评分（真实进化评估闭环）
     */
    @PostMapping("/{id}/rate")
    public ResponseEntity<ApiResponse<AgentDTO>> rateAgent(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Object ratingObj = body.get("rating");
        if (ratingObj == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Missing 'rating' field"));
        }
        double rating = Double.parseDouble(String.valueOf(ratingObj));
        AgentDTO agent = agentService.rateAgent(id, rating);
        return ResponseEntity.ok(ApiResponse.success(agent, "Agent rated successfully"));
    }

    /**
     * 获取 Agent 真实执行指标（真实进化评估闭环）
     */
    @GetMapping("/{id}/metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAgentMetrics(@PathVariable String id) {
        Map<String, Object> metrics = agentService.getAgentMetrics(id);
        return ResponseEntity.ok(ApiResponse.success(metrics));
    }

    /**
     * 回滚进化
     */
    @PostMapping("/{agentId}/evolution/{recordId}/rollback")
    public ResponseEntity<ApiResponse<AgentDTO>> rollbackEvolution(
            @PathVariable String agentId,
            @PathVariable Long recordId) {
        AgentDTO agent = agentService.rollbackEvolution(recordId);
        return ResponseEntity.ok(ApiResponse.success(agent, "Evolution rolled back successfully"));
    }
}
