package com.teammind.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.config.SQLiteWriteLockService;
import com.teammind.dto.*;
import com.teammind.entity.Mission;
import com.teammind.entity.Mission.MissionStatus;
import com.teammind.executor.MissionRuntimeManager;
import com.teammind.repository.MissionRepository;
import com.teammind.websocket.WSEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Mission Service
 * 
 * 处理任务相关的业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionService {

    private final MissionRepository missionRepository;
    private final ObjectMapper objectMapper;
    private final WSEventPublisher eventPublisher;
    private final MissionRuntimeManager runtimeManager;
    private final SQLiteWriteLockService writeLockService;

    /**
     * 创建新任务
     */
    @Transactional
    public MissionDTO createMission(CreateMissionRequest request) {
        Mission mission = Mission.builder()
                .id(UUID.randomUUID().toString())
                .title(request.getTitle())
                .description(request.getDescription())
                .status(MissionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .nodes(new ArrayList<>())
                .edges(new ArrayList<>())
                .logs(new ArrayList<>())
                .build();

        mission = missionRepository.save(mission);

        // 发布任务创建事件
        eventPublisher.publishMissionStarted(mission.getId());

        return toDTO(mission);
    }

    /**
     * 获取任务详情
     */
    public MissionDTO getMission(String id) {
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mission not found: " + id));
        return toDTO(mission);
    }

    /**
     * 获取任务列表（分页）
     */
    public PaginatedResponse<MissionHistoryDTO> listMissions(int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<Mission> missionPage = missionRepository.findAllByOrderByCreatedAtDesc(pageRequest);

        List<MissionHistoryDTO> items = missionPage.getContent().stream()
                .map(this::toHistoryDTO)
                .toList();

        return PaginatedResponse.of(items, missionPage.getTotalElements(), page, pageSize);
    }

    /**
     * 更新任务
     */
    @Transactional
    public MissionDTO updateMission(String id, UpdateMissionRequest request) {
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mission not found: " + id));

        if (request.getTitle() != null) {
            mission.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            mission.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            mission.setStatus(MissionStatus.valueOf(request.getStatus().toUpperCase()));
        }
        if (request.getNodes() != null) {
            mission.setNodes(request.getNodes());
        }
        if (request.getEdges() != null) {
            mission.setEdges(request.getEdges());
        }
        mission.setUpdatedAt(LocalDateTime.now());

        mission = missionRepository.save(mission);
        return toDTO(mission);
    }

    /**
     * 删除任务
     */
    @Transactional
    public void deleteMission(String id) {
        missionRepository.deleteById(id);
    }

    /**
     * 克隆任务
     */
    @Transactional
    public MissionDTO cloneMission(String id) {
        Mission original = missionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mission not found: " + id));

        Mission cloned = Mission.builder()
                .id(UUID.randomUUID().toString())
                .title(original.getTitle() + " (Clone)")
                .description(original.getDescription())
                .status(MissionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .nodes(new ArrayList<>(original.getNodes()))
                .edges(new ArrayList<>(original.getEdges()))
                .logs(new ArrayList<>())
                .build();

        cloned = missionRepository.save(cloned);
        return toDTO(cloned);
    }

    /**
     * 启动任务
     */
    @Transactional
    public MissionDTO startMission(String id) {
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mission not found: " + id));

        if (mission.getStatus() != MissionStatus.PENDING && mission.getStatus() != MissionStatus.PAUSED) {
            throw new RuntimeException("Mission cannot be started from status: " + mission.getStatus());
        }

        mission.setStatus(MissionStatus.RUNNING);
        mission.setUpdatedAt(LocalDateTime.now());
        Mission finalMission = mission;
        mission = writeLockService.executeWithLock(() -> missionRepository.save(finalMission));

        // 启动任务执行（异步）
        runtimeManager.startMission(id);

        return toDTO(mission);
    }

    /**
     * 暂停任务
     */
    @Transactional
    public MissionDTO pauseMission(String id) {
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mission not found: " + id));

        if (mission.getStatus() != MissionStatus.RUNNING) {
            throw new RuntimeException("Mission cannot be paused from status: " + mission.getStatus());
        }

        mission.setStatus(MissionStatus.PAUSED);
        mission.setUpdatedAt(LocalDateTime.now());
        Mission finalMission = mission;
        mission = writeLockService.executeWithLock(() -> missionRepository.save(finalMission));

        // ✅ 同步暂停运行时：让执行循环感知暂停状态
        runtimeManager.pauseMission(id);

        return toDTO(mission);
    }

    /**
     * 恢复任务
     */
    @Transactional
    public MissionDTO resumeMission(String id) {
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mission not found: " + id));

        if (mission.getStatus() != MissionStatus.PAUSED) {
            throw new RuntimeException("Mission cannot be resumed from status: " + mission.getStatus());
        }

        mission.setStatus(MissionStatus.RUNNING);
        mission.setUpdatedAt(LocalDateTime.now());
        Mission finalMission = mission;
        mission = writeLockService.executeWithLock(() -> missionRepository.save(finalMission));

        // ✅ 同步恢复运行时
        runtimeManager.resumeMission(id);

        return toDTO(mission);
    }

    /**
     * 取消任务
     */
    @Transactional
    public MissionDTO cancelMission(String id) {
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mission not found: " + id));

        if (mission.getStatus() == MissionStatus.COMPLETED || mission.getStatus() == MissionStatus.FAILED) {
            throw new RuntimeException("Mission cannot be cancelled from status: " + mission.getStatus());
        }

        mission.setStatus(MissionStatus.FAILED);
        mission.setUpdatedAt(LocalDateTime.now());
        Mission finalMission = mission;
        mission = writeLockService.executeWithLock(() -> missionRepository.save(finalMission));

        // ✅ 同步取消运行时：让执行循环感知取消状态并传播取消
        runtimeManager.cancelMission(id);

        return toDTO(mission);
    }

    /**
     * 重试节点
     */
    @Transactional
    public MissionDTO retryNode(String missionId, String nodeId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new RuntimeException("Mission not found: " + missionId));

        // 更新节点状态
        List<Map<String, Object>> nodes = mission.getNodes();
        for (Map<String, Object> node : nodes) {
            if (nodeId.equals(node.get("id"))) {
                Map<String, Object> data = (Map<String, Object>) node.get("data");
                if (data != null) {
                    data.put("status", "running");
                }
            }
        }
        mission.setNodes(nodes);
        mission.setUpdatedAt(LocalDateTime.now());

        // 添加日志
        addLog(mission, "retry", nodeId, "Retrying node: " + nodeId);

        mission = missionRepository.save(mission);
        return toDTO(mission);
    }

    /**
     * 跳过节点
     */
    @Transactional
    public MissionDTO skipNode(String missionId, String nodeId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new RuntimeException("Mission not found: " + missionId));

        // 更新节点状态
        List<Map<String, Object>> nodes = mission.getNodes();
        for (Map<String, Object> node : nodes) {
            if (nodeId.equals(node.get("id"))) {
                Map<String, Object> data = (Map<String, Object>) node.get("data");
                if (data != null) {
                    data.put("status", "success");
                }
            }
        }
        mission.setNodes(nodes);
        mission.setUpdatedAt(LocalDateTime.now());

        // 添加日志
        addLog(mission, "task", nodeId, "Skipped node: " + nodeId);

        mission = missionRepository.save(mission);
        return toDTO(mission);
    }

    /**
     * 添加日志
     */
    private void addLog(Mission mission, String type, String agentId, String message) {
        Map<String, Object> log = new HashMap<>();
        log.put("id", UUID.randomUUID().toString());
        log.put("type", type);
        log.put("timestamp", LocalDateTime.now().toString());
        log.put("agentId", agentId);
        log.put("message", message);
        mission.getLogs().add(log);
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        long total = missionRepository.count();
        long completed = missionRepository.countByStatus(MissionStatus.COMPLETED);
        long running = missionRepository.countByStatus(MissionStatus.RUNNING);
        long failed = missionRepository.countByStatus(MissionStatus.FAILED);

        int successRate = total > 0 ? (int) ((completed * 100) / total) : 0;

        return Map.of(
                "totalMissions", total,
                "successRate", successRate,
                "activeMissions", running,
                "failedMissions", failed
        );
    }

    /**
     * 转换为 DTO
     */
    private MissionDTO toDTO(Mission mission) {
        return MissionDTO.builder()
                .id(mission.getId())
                .title(mission.getTitle())
                .description(mission.getDescription())
                .status(mission.getStatus().name().toLowerCase())
                .createdAt(formatDateTime(mission.getCreatedAt()))
                .updatedAt(formatDateTime(mission.getUpdatedAt()))
                .completedAt(formatDateTime(mission.getCompletedAt()))
                .nodes(mission.getNodes())
                .edges(mission.getEdges())
                .logs(mission.getLogs())
                .result(mission.getResult())
                .build();
    }

    /**
     * 转换为历史 DTO
     */
    private MissionHistoryDTO toHistoryDTO(Mission mission) {
        String preview = mission.getDescription();
        if (preview != null && preview.length() > 100) {
            preview = preview.substring(0, 100) + "...";
        }

        return MissionHistoryDTO.builder()
                .id(mission.getId())
                .title(mission.getTitle())
                .status(mission.getStatus().name().toLowerCase())
                .createdAt(formatDateTime(mission.getCreatedAt()))
                .completedAt(formatDateTime(mission.getCompletedAt()))
                .preview(preview)
                .build();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
