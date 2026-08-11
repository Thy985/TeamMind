package com.teammind.evolution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.dto.EvolutionRequest;
import com.teammind.dto.EvolutionResultDTO;
import com.teammind.entity.Agent;
import com.teammind.entity.EvolutionRecord;
import com.teammind.entity.EvolutionRecord.EvolutionType;
import com.teammind.llm.LLMResponse;
import com.teammind.llm.LLMService;
import com.teammind.repository.EvolutionRecordRepository;
import com.teammind.service.AgentMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EvolutionEngine 单元测试
 *
 * 覆盖进化引擎的核心分支：禁用、无 LLM、Prompt 优化、工具生成、知识更新。
 * 重点验证"真实进化评估闭环"——进化评分由 AgentMetricsService 的真实指标驱动，
 * 而非启发式打分。
 */
class EvolutionEngineTest {

    private EvolutionRecordRepository evolutionRecordRepository;
    private LLMService llmService;
    private AgentMetricsService agentMetricsService;
    private EvolutionEngine engine;

    @BeforeEach
    void setUp() {
        evolutionRecordRepository = mock(EvolutionRecordRepository.class);
        llmService = mock(LLMService.class);
        agentMetricsService = mock(AgentMetricsService.class);

        engine = new EvolutionEngine(
                evolutionRecordRepository,
                llmService,
                new ObjectMapper(),
                agentMetricsService
        );

        // 默认启用进化
        ReflectionTestUtils.setField(engine, "evolutionEnabled", true);
        ReflectionTestUtils.setField(engine, "maxHistoryVersions", 50);
        ReflectionTestUtils.setField(engine, "minFeedbackCount", 5);
    }

    private Agent buildAgent() {
        return Agent.builder()
                .id("agent-1")
                .name("Test Agent")
                .currentPrompt("Original prompt")
                .permissions(List.of("read:code"))
                .evolutionVersion(1)
                .evolutionScore(0.0)
                .totalMissions(10L)
                .successfulMissions(8L)
                .totalTokensUsed(10000L)
                .build();
    }

    @Test
    @DisplayName("进化被禁用时应返回失败")
    void evolve_whenDisabled_returnsFailure() {
        ReflectionTestUtils.setField(engine, "evolutionEnabled", false);

        EvolutionResultDTO result = engine.evolve(
                buildAgent(), EvolutionType.PROMPT_OPTIMIZATION, EvolutionRequest.builder().build());

        assertNotNull(result);
        assertFalse(result.getSuccess());
        assertEquals("Evolution is disabled", result.getDescription());
    }

    @Test
    @DisplayName("无可用 LLM 客户端时应返回失败")
    void evolve_whenNoLLMClient_returnsFailure() {
        when(llmService.hasAvailableClient()).thenReturn(false);

        EvolutionResultDTO result = engine.evolve(
                buildAgent(), EvolutionType.PROMPT_OPTIMIZATION, EvolutionRequest.builder().build());

        assertNotNull(result);
        assertFalse(result.getSuccess());
        assertEquals("No LLM client available", result.getDescription());
    }

    @Test
    @DisplayName("Prompt 优化成功时，进化评分应由真实指标驱动（AgentMetricsService）")
    void optimizePrompt_success_usesRealMetricsForScoring() {
        when(llmService.hasAvailableClient()).thenReturn(true);
        when(llmService.optimizePrompt(anyString(), anyString()))
                .thenReturn(LLMResponse.success("Optimized prompt with ## structure", "deepseek-v3.2", "qianfan"));
        // 真实指标评分返回 0.72
        when(agentMetricsService.calculateEvolutionBenefit(any(Agent.class))).thenReturn(0.72);

        EvolutionRecord savedRecord = EvolutionRecord.builder()
                .id(1L)
                .agentId("agent-1")
                .type(EvolutionType.PROMPT_OPTIMIZATION)
                .fromVersion(1)
                .toVersion(2)
                .scoreChange(0.72)
                .build();
        when(evolutionRecordRepository.save(any(EvolutionRecord.class))).thenReturn(savedRecord);

        EvolutionResultDTO result = engine.evolve(
                buildAgent(), EvolutionType.PROMPT_OPTIMIZATION, EvolutionRequest.builder().build());

        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertEquals(0.72, result.getScoreChange());
        assertEquals(2, result.getToVersion());

        // 关键断言：评分来自真实指标服务，而非启发式规则
        verify(agentMetricsService, times(1)).calculateEvolutionBenefit(any(Agent.class));
        verify(evolutionRecordRepository, times(1)).save(any(EvolutionRecord.class));
    }

    @Test
    @DisplayName("Prompt 优化时 LLM 返回失败应返回失败")
    void optimizePrompt_whenLLMFails_returnsFailure() {
        when(llmService.hasAvailableClient()).thenReturn(true);
        when(llmService.optimizePrompt(anyString(), anyString()))
                .thenReturn(LLMResponse.failure("LLM error", "qianfan"));

        EvolutionResultDTO result = engine.evolve(
                buildAgent(), EvolutionType.PROMPT_OPTIMIZATION, EvolutionRequest.builder().build());

        assertNotNull(result);
        assertFalse(result.getSuccess());
        assertTrue(result.getDescription().contains("LLM optimization failed"));
    }

    @Test
    @DisplayName("无 Prompt 可优化时返回失败")
    void optimizePrompt_noPrompt_returnsFailure() {
        when(llmService.hasAvailableClient()).thenReturn(true);

        Agent agent = buildAgent();
        agent.setCurrentPrompt(null);

        EvolutionResultDTO result = engine.evolve(
                agent, EvolutionType.PROMPT_OPTIMIZATION, EvolutionRequest.builder().build());

        assertNotNull(result);
        assertFalse(result.getSuccess());
        assertEquals("No current prompt to optimize", result.getDescription());
    }

    @Test
    @DisplayName("工具生成成功时，进化评分应由真实指标驱动")
    void generateTool_success_usesRealMetrics() {
        when(llmService.hasAvailableClient()).thenReturn(true);
        when(llmService.generateTool(anyString(), anyString()))
                .thenReturn(LLMResponse.success(
                        "{\"name\": \"code_analyzer\", \"description\": \"analyzes code\", \"code\": \"...\"}",
                        "deepseek-v3.2", "qianfan"));
        when(agentMetricsService.calculateEvolutionBenefit(any(Agent.class))).thenReturn(0.61);

        EvolutionRecord savedRecord = EvolutionRecord.builder()
                .id(2L)
                .agentId("agent-1")
                .type(EvolutionType.TOOL_GENERATION)
                .fromVersion(1)
                .toVersion(2)
                .scoreChange(0.61)
                .build();
        when(evolutionRecordRepository.save(any(EvolutionRecord.class))).thenReturn(savedRecord);

        Agent agent = buildAgent();
        EvolutionResultDTO result = engine.evolve(
                agent, EvolutionType.TOOL_GENERATION, EvolutionRequest.builder().build());

        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertEquals(0.61, result.getScoreChange());
        // 工具已附加到 Agent
        assertNotNull(agent.getTools());
        assertEquals(1, agent.getTools().size());
        // 关键：自动推断可执行能力类型（toolType），使 AgentExecutionEngine 能调度（生成即可用）
        assertEquals("code_analyzer", agent.getTools().get(0).get("toolType"));
    }

    @Test
    @DisplayName("知识更新无内容时返回失败")
    void updateKnowledge_noContent_returnsFailure() {
        when(llmService.hasAvailableClient()).thenReturn(true);

        EvolutionResultDTO result = engine.evolve(
                buildAgent(), EvolutionType.KNOWLEDGE_UPDATE,
                EvolutionRequest.builder().context(Map.of("knowledge", "")).build());

        assertNotNull(result);
        assertFalse(result.getSuccess());
        assertEquals("No knowledge content provided", result.getDescription());
    }
}
