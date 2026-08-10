package com.teammind.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.config.SQLiteWriteLockService;
import com.teammind.entity.Mission;
import com.teammind.entity.Mission.MissionStatus;
import com.teammind.executor.AgentExecutionResult.ExecutionStatus;
import com.teammind.llm.LLMTrackingService;
import com.teammind.repository.AgentRepository;
import com.teammind.repository.MissionRepository;
import com.teammind.service.AgentMetricsService;
import com.teammind.websocket.WSEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MissionRuntimeManager 单元测试
 *
 * 覆盖依赖图构建、拓扑排序、任务结果汇总，
 * 以及"真实进化评估闭环"的指标回写逻辑。
 */
class MissionRuntimeManagerTest {

    private MissionRepository missionRepository;
    private AgentRepository agentRepository;
    private AgentExecutionEngine executionEngine;
    private LLMTrackingService trackingService;
    private WSEventPublisher eventPublisher;
    private ExecutorService executorService;
    private SQLiteWriteLockService writeLockService;
    private AgentMetricsService agentMetricsService;
    private MissionRuntimeManager manager;

    @BeforeEach
    void setUp() {
        missionRepository = mock(MissionRepository.class);
        agentRepository = mock(AgentRepository.class);
        executionEngine = mock(AgentExecutionEngine.class);
        trackingService = mock(LLMTrackingService.class);
        eventPublisher = mock(WSEventPublisher.class);
        executorService = mock(ExecutorService.class);
        writeLockService = mock(SQLiteWriteLockService.class);
        agentMetricsService = mock(AgentMetricsService.class);

        manager = new MissionRuntimeManager(
                missionRepository,
                agentRepository,
                executionEngine,
                trackingService,
                eventPublisher,
                executorService,
                writeLockService,
                agentMetricsService
        );
    }

    private Mission buildMission() {
        return Mission.builder()
                .id("mission-1")
                .title("Test Mission")
                .status(MissionStatus.RUNNING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private AgentExecutionResult buildResult(boolean success, int tokens) {
        AgentExecutionResult result = AgentExecutionResult.builder()
                .executionId(UUID.randomUUID().toString())
                .agentId("agent-1")
                .success(success)
                .status(success ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED)
                .response(success ? "ok" : "fail")
                .tokenUsage(AgentExecutionResult.TokenUsage.builder()
                        .promptTokens(tokens)
                        .completionTokens(tokens / 2)
                        .totalTokens(tokens)
                        .build())
                .build();
        return result;
    }

    @Test
    @DisplayName("依赖图构建应正确反映边关系")
    void buildDependencyGraph_buildsEdges() {
        List<Map<String, Object>> nodes = List.of(
                node("n1"), node("n2"), node("n3")
        );
        List<Map<String, Object>> edges = List.of(
                edge("n1", "n2"), edge("n1", "n3")
        );

        @SuppressWarnings("unchecked")
        Map<String, Set<String>> deps = (Map<String, Set<String>>) ReflectionTestUtils.invokeMethod(
                manager, "buildDependencyGraph", nodes, edges);

        assertNotNull(deps);
        assertTrue(deps.get("n2").contains("n1"));
        assertTrue(deps.get("n3").contains("n1"));
        assertTrue(deps.get("n1").isEmpty());
    }

    @Test
    @DisplayName("拓扑排序应保证依赖节点在前")
    void topologicalSort_respectsDependencies() {
        List<Map<String, Object>> nodes = List.of(
                node("n3"), node("n2"), node("n1")
        );
        List<Map<String, Object>> edges = List.of(
                edge("n1", "n2"), edge("n1", "n3")
        );

        @SuppressWarnings("unchecked")
        Map<String, Set<String>> deps = (Map<String, Set<String>>) ReflectionTestUtils.invokeMethod(
                manager, "buildDependencyGraph", nodes, edges);

        @SuppressWarnings("unchecked")
        List<String> order = (List<String>) ReflectionTestUtils.invokeMethod(
                manager, "topologicalSort", nodes, deps);

        assertNotNull(order);
        assertEquals(3, order.size());
        // n1 必须在 n2、n3 之前
        int idx1 = order.indexOf("n1");
        int idx2 = order.indexOf("n2");
        int idx3 = order.indexOf("n3");
        assertTrue(idx1 < idx2, "n1 should be before n2");
        assertTrue(idx1 < idx3, "n1 should be before n3");
    }

    @Test
    @DisplayName("任务结果汇总应包含各 Agent 的输出、token 与状态")
    void buildMissionResult_aggregatesAgentOutputs() {
        Mission mission = buildMission();
        MissionRuntimeManager.MissionRuntime runtime =
                new MissionRuntimeManager.MissionRuntime(mission);

        runtime.addResult("agent-1", buildResult(true, 100));
        runtime.addResult("agent-2", buildResult(false, 50));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                manager, "buildMissionResult", runtime);

        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));

        @SuppressWarnings("unchecked")
        Map<String, Object> outputs = (Map<String, Object>) result.get("agentOutputs");
        assertNotNull(outputs);
        assertEquals(2, outputs.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> agent1 = (Map<String, Object>) outputs.get("agent-1");
        assertTrue((Boolean) agent1.get("success"));
        assertEquals(100, agent1.get("tokens"));
    }

    @Test
    @DisplayName("任务结束后应回写真实执行指标到 AgentMetricsService")
    void recordAgentMetrics_writesRealMetrics() {
        Mission mission = buildMission();
        MissionRuntimeManager.MissionRuntime runtime =
                new MissionRuntimeManager.MissionRuntime(mission);

        runtime.addResult("agent-1", buildResult(true, 100));

        ReflectionTestUtils.invokeMethod(manager, "recordAgentMetrics", runtime, true);

        verify(agentMetricsService, times(1))
                .recordTaskResult("agent-1", true, 100);
    }

    @Test
    @DisplayName("任务失败时，即使单个 Agent 成功也应记为整体失败指标")
    void recordAgentMetrics_missionFailureMarksAgentAsFailed() {
        Mission mission = buildMission();
        MissionRuntimeManager.MissionRuntime runtime =
                new MissionRuntimeManager.MissionRuntime(mission);

        // 单个 Agent 执行成功，但整体任务失败
        runtime.addResult("agent-1", buildResult(true, 80));

        ReflectionTestUtils.invokeMethod(manager, "recordAgentMetrics", runtime, false);

        verify(agentMetricsService, times(1))
                .recordTaskResult("agent-1", false, 80);
    }

    @Test
    @DisplayName("无结果时不应回写任何指标")
    void recordAgentMetrics_emptyResults_doesNothing() {
        Mission mission = buildMission();
        MissionRuntimeManager.MissionRuntime runtime =
                new MissionRuntimeManager.MissionRuntime(mission);

        ReflectionTestUtils.invokeMethod(manager, "recordAgentMetrics", runtime, true);

        verify(agentMetricsService, never()).recordTaskResult(anyString(), anyBoolean(), anyLong());
    }

    // ==================== 测试辅助 ====================

    private Map<String, Object> node(String id) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", id);
        n.put("type", "agent");
        return n;
    }

    private Map<String, Object> edge(String source, String target) {
        Map<String, Object> e = new HashMap<>();
        e.put("source", source);
        e.put("target", target);
        return e;
    }
}
