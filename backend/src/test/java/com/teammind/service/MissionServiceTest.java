package com.teammind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.config.SQLiteWriteLockService;
import com.teammind.dto.CreateMissionRequest;
import com.teammind.dto.MissionDTO;
import com.teammind.dto.PaginatedResponse;
import com.teammind.dto.UpdateMissionRequest;
import com.teammind.entity.Mission;
import com.teammind.entity.Mission.MissionStatus;
import com.teammind.executor.MissionRuntimeManager;
import com.teammind.repository.MissionRepository;
import com.teammind.websocket.WSEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MissionService 单元测试
 *
 * 聚焦任务状态机（PENDING→RUNNING→PAUSED→...）与接口契约，
 * 以及创建/查询/更新/克隆/节点操作等业务规则。
 */
class MissionServiceTest {

    private MissionRepository missionRepository;
    private WSEventPublisher eventPublisher;
    private MissionRuntimeManager runtimeManager;
    private SQLiteWriteLockService writeLockService;
    private MissionService service;

    @BeforeEach
    void setUp() {
        missionRepository = mock(MissionRepository.class);
        eventPublisher = mock(WSEventPublisher.class);
        runtimeManager = mock(MissionRuntimeManager.class);
        writeLockService = mock(SQLiteWriteLockService.class);

        service = new MissionService(
                missionRepository,
                new ObjectMapper(),
                eventPublisher,
                runtimeManager,
                writeLockService
        );

        // 写锁放行：直接执行被保护的写操作
        when(writeLockService.executeWithLock(any(SQLiteWriteLockService.WriteOperation.class)))
                .thenAnswer(inv -> ((SQLiteWriteLockService.WriteOperation<?>) inv.getArgument(0)).execute());
    }

    private Mission buildMission(MissionStatus status) {
        return Mission.builder()
                .id("m-1")
                .title("Test Mission")
                .description("A test mission")
                .status(status)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .nodes(new java.util.ArrayList<>())
                .edges(new java.util.ArrayList<>())
                .logs(new java.util.ArrayList<>())
                .build();
    }

    // ==================== 创建 ====================

    @Test
    @DisplayName("创建任务应初始化为 PENDING 并发布创建事件")
    void createMission_initializesPendingAndPublishes() {
        CreateMissionRequest request = CreateMissionRequest.builder()
                .title("New Mission")
                .description("desc")
                .build();
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        MissionDTO dto = service.createMission(request);

        assertNotNull(dto.getId());
        assertEquals("New Mission", dto.getTitle());
        assertEquals("pending", dto.getStatus());
        verify(eventPublisher).publishMissionStarted(dto.getId());
    }

    @Test
    @DisplayName("创建任务不存在的任务查询应抛异常")
    void getMission_notFound_throws() {
        when(missionRepository.findById("nope")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.getMission("nope"));
    }

    @Test
    @DisplayName("获取任务应返回正确 DTO")
    void getMission_returnsDTO() {
        Mission mission = buildMission(MissionStatus.RUNNING);
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        MissionDTO dto = service.getMission("m-1");
        assertEquals("m-1", dto.getId());
        assertEquals("running", dto.getStatus());
    }

    // ==================== 状态机 ====================

    @Test
    @DisplayName("从 PENDING 启动任务应进入 RUNNING 并调用运行时启动")
    void startMission_pending_transitionsToRunning() {
        Mission mission = buildMission(MissionStatus.PENDING);
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        MissionDTO dto = service.startMission("m-1");

        assertEquals("running", dto.getStatus());
        verify(runtimeManager).startMission("m-1");
    }

    @Test
    @DisplayName("从 PAUSED 恢复启动任务应进入 RUNNING")
    void startMission_paused_transitionsToRunning() {
        Mission mission = buildMission(MissionStatus.PAUSED);
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        MissionDTO dto = service.startMission("m-1");
        assertEquals("running", dto.getStatus());
        verify(runtimeManager).startMission("m-1");
    }

    @Test
    @DisplayName("从 COMPLETED 状态不能启动任务")
    void startMission_completed_throws() {
        Mission mission = buildMission(MissionStatus.COMPLETED);
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.startMission("m-1"));
        assertTrue(ex.getMessage().contains("cannot be started"));
        verify(runtimeManager, never()).startMission(anyString());
    }

    @Test
    @DisplayName("从 RUNNING 暂停任务应进入 PAUSED")
    void pauseMission_running_transitionsToPaused() {
        Mission mission = buildMission(MissionStatus.RUNNING);
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        MissionDTO dto = service.pauseMission("m-1");
        assertEquals("paused", dto.getStatus());
        verify(runtimeManager).pauseMission("m-1");
    }

    @Test
    @DisplayName("从 PENDING 不能暂停任务")
    void pauseMission_pending_throws() {
        Mission mission = buildMission(MissionStatus.PENDING);
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.pauseMission("m-1"));
        assertTrue(ex.getMessage().contains("cannot be paused"));
        verify(runtimeManager, never()).pauseMission(anyString());
    }

    @Test
    @DisplayName("从 PAUSED 恢复任务应进入 RUNNING")
    void resumeMission_paused_transitionsToRunning() {
        Mission mission = buildMission(MissionStatus.PAUSED);
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        MissionDTO dto = service.resumeMission("m-1");
        assertEquals("running", dto.getStatus());
        verify(runtimeManager).resumeMission("m-1");
    }

    @Test
    @DisplayName("从 RUNNING 不能恢复任务")
    void resumeMission_running_throws() {
        Mission mission = buildMission(MissionStatus.RUNNING);
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.resumeMission("m-1"));
        assertTrue(ex.getMessage().contains("cannot be resumed"));
        verify(runtimeManager, never()).resumeMission(anyString());
    }

    @Test
    @DisplayName("从 RUNNING 取消任务应进入 FAILED 并传播取消")
    void cancelMission_running_transitionsToFailed() {
        Mission mission = buildMission(MissionStatus.RUNNING);
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        MissionDTO dto = service.cancelMission("m-1");
        assertEquals("failed", dto.getStatus());
        verify(runtimeManager).cancelMission("m-1");
    }

    @Test
    @DisplayName("从 COMPLETED 不能取消任务")
    void cancelMission_completed_throws() {
        Mission mission = buildMission(MissionStatus.COMPLETED);
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.cancelMission("m-1"));
        assertTrue(ex.getMessage().contains("cannot be cancelled"));
        verify(runtimeManager, never()).cancelMission(anyString());
    }

    @Test
    @DisplayName("从 FAILED 不能取消任务")
    void cancelMission_failed_throws() {
        Mission mission = buildMission(MissionStatus.FAILED);
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.cancelMission("m-1"));
        assertTrue(ex.getMessage().contains("cannot be cancelled"));
        verify(runtimeManager, never()).cancelMission(anyString());
    }

    // ==================== 更新 ====================

    @Test
    @DisplayName("更新任务应应用标题与状态字段")
    void updateMission_appliesFields() {
        Mission mission = buildMission(MissionStatus.PENDING);
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateMissionRequest request = UpdateMissionRequest.builder()
                .title("Updated Title")
                .status("running")
                .build();

        MissionDTO dto = service.updateMission("m-1", request);
        assertEquals("Updated Title", dto.getTitle());
        assertEquals("running", dto.getStatus());
    }

    // ==================== 克隆 ====================

    @Test
    @DisplayName("克隆任务应以 PENDING 状态创建并追加 (Clone)")
    void cloneMission_createsPendingClone() {
        Mission mission = buildMission(MissionStatus.COMPLETED);
        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        MissionDTO dto = service.cloneMission("m-1");
        assertEquals("Test Mission (Clone)", dto.getTitle());
        assertEquals("pending", dto.getStatus());
    }

    // ==================== 节点操作 ====================

    @Test
    @DisplayName("重试节点应将目标节点状态置为 running 并写日志")
    void retryNode_marksNodeRunningAndLogs() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("status", "failed");
        Map<String, Object> node = new java.util.HashMap<>();
        node.put("id", "n1");
        node.put("data", data);
        Mission mission = buildMission(MissionStatus.RUNNING);
        mission.setNodes(new java.util.ArrayList<>(List.of(node)));

        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        MissionDTO dto = service.retryNode("m-1", "n1");

        @SuppressWarnings("unchecked")
        Map<String, Object> retriedNode = ((List<Map<String, Object>>) dto.getNodes()).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> retriedData = (Map<String, Object>) retriedNode.get("data");
        assertEquals("running", retriedData.get("status"));
        assertFalse(dto.getLogs().isEmpty());
    }

    @Test
    @DisplayName("跳过节点应将目标节点状态置为 success 并写日志")
    void skipNode_marksNodeSuccessAndLogs() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("status", "pending");
        Map<String, Object> node = new java.util.HashMap<>();
        node.put("id", "n2");
        node.put("data", data);
        Mission mission = buildMission(MissionStatus.RUNNING);
        mission.setNodes(new java.util.ArrayList<>(List.of(node)));

        when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        MissionDTO dto = service.skipNode("m-1", "n2");

        @SuppressWarnings("unchecked")
        Map<String, Object> skippedNode = ((List<Map<String, Object>>) dto.getNodes()).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> skippedData = (Map<String, Object>) skippedNode.get("data");
        assertEquals("success", skippedData.get("status"));
        assertFalse(dto.getLogs().isEmpty());
    }

    // ==================== 列表与统计 ====================

    @Test
    @DisplayName("任务列表应返回分页数据")
    void listMissions_returnsPaginated() {
        Mission m1 = buildMission(MissionStatus.PENDING);
        Page<Mission> page = new PageImpl<>(List.of(m1));
        when(missionRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(page);

        PaginatedResponse<?> response = service.listMissions(1, 20);
        assertEquals(1, response.getItems().size());
        assertEquals(1L, response.getTotal());
    }

    @Test
    @DisplayName("统计信息应基于状态计数计算成功率")
    void getStats_computesSuccessRate() {
        when(missionRepository.count()).thenReturn(4L);
        when(missionRepository.countByStatus(MissionStatus.COMPLETED)).thenReturn(2L);
        when(missionRepository.countByStatus(MissionStatus.RUNNING)).thenReturn(1L);
        when(missionRepository.countByStatus(MissionStatus.FAILED)).thenReturn(1L);

        Map<String, Object> stats = service.getStats();
        assertEquals(4L, stats.get("totalMissions"));
        assertEquals(50, stats.get("successRate"));
        assertEquals(1L, stats.get("activeMissions"));
        assertEquals(1L, stats.get("failedMissions"));
    }

    @Test
    @DisplayName("无任务时成功率为 0")
    void getStats_noMissions_successRateZero() {
        when(missionRepository.count()).thenReturn(0L);
        when(missionRepository.countByStatus(MissionStatus.COMPLETED)).thenReturn(0L);
        when(missionRepository.countByStatus(MissionStatus.RUNNING)).thenReturn(0L);
        when(missionRepository.countByStatus(MissionStatus.FAILED)).thenReturn(0L);

        Map<String, Object> stats = service.getStats();
        assertEquals(0, stats.get("successRate"));
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("删除任务应调用 repository 的 deleteById")
    void deleteMission_delegatesToRepository() {
        service.deleteMission("m-1");
        verify(missionRepository).deleteById("m-1");
    }
}
