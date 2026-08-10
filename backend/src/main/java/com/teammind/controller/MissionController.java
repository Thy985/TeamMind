package com.teammind.controller;

import com.teammind.dto.*;
import com.teammind.service.MissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Mission Controller
 * 
 * 任务管理 API
 */
@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class MissionController {

    private final MissionService missionService;

    /**
     * 创建任务
     */
    @PostMapping
    public ResponseEntity<ApiResponse<MissionDTO>> createMission(
            @Valid @RequestBody CreateMissionRequest request) {
        MissionDTO mission = missionService.createMission(request);
        return ResponseEntity.ok(ApiResponse.success(mission, "Mission created successfully"));
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MissionDTO>> getMission(@PathVariable String id) {
        MissionDTO mission = missionService.getMission(id);
        return ResponseEntity.ok(ApiResponse.success(mission));
    }

    /**
     * 获取任务列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<MissionHistoryDTO>>> listMissions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PaginatedResponse<MissionHistoryDTO> missions = missionService.listMissions(page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(missions));
    }

    /**
     * 更新任务
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MissionDTO>> updateMission(
            @PathVariable String id,
            @RequestBody UpdateMissionRequest request) {
        MissionDTO mission = missionService.updateMission(id, request);
        return ResponseEntity.ok(ApiResponse.success(mission, "Mission updated successfully"));
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMission(@PathVariable String id) {
        missionService.deleteMission(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Mission deleted successfully"));
    }

    /**
     * 克隆任务
     */
    @PostMapping("/{id}/clone")
    public ResponseEntity<ApiResponse<MissionDTO>> cloneMission(@PathVariable String id) {
        MissionDTO mission = missionService.cloneMission(id);
        return ResponseEntity.ok(ApiResponse.success(mission, "Mission cloned successfully"));
    }

    /**
     * 启动任务
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<MissionDTO>> startMission(@PathVariable String id) {
        MissionDTO mission = missionService.startMission(id);
        return ResponseEntity.ok(ApiResponse.success(mission, "Mission started"));
    }

    /**
     * 暂停任务
     */
    @PostMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<MissionDTO>> pauseMission(@PathVariable String id) {
        MissionDTO mission = missionService.pauseMission(id);
        return ResponseEntity.ok(ApiResponse.success(mission, "Mission paused"));
    }

    /**
     * 重试节点
     */
    @PostMapping("/{id}/nodes/{nodeId}/retry")
    public ResponseEntity<ApiResponse<MissionDTO>> retryNode(
            @PathVariable String id,
            @PathVariable String nodeId) {
        MissionDTO mission = missionService.retryNode(id, nodeId);
        return ResponseEntity.ok(ApiResponse.success(mission, "Node retry triggered"));
    }

    /**
     * 跳过节点
     */
    @PostMapping("/{id}/nodes/{nodeId}/skip")
    public ResponseEntity<ApiResponse<MissionDTO>> skipNode(
            @PathVariable String id,
            @PathVariable String nodeId) {
        MissionDTO mission = missionService.skipNode(id, nodeId);
        return ResponseEntity.ok(ApiResponse.success(mission, "Node skipped"));
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> stats = missionService.getStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
