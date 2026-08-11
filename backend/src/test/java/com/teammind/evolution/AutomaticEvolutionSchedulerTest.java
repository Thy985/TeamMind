package com.teammind.evolution;

import com.teammind.dto.EvolutionRequest;
import com.teammind.dto.EvolutionResultDTO;
import com.teammind.entity.Agent;
import com.teammind.entity.EvolutionRecord;
import com.teammind.entity.EvolutionRecord.EvolutionType;
import com.teammind.repository.AgentRepository;
import com.teammind.repository.EvolutionRecordRepository;
import com.teammind.service.AgentMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 自动进化调度器（Automatic Evolution Trigger）单元测试
 *
 * 验证\"基于真实指标阈值自动触发进化\"的产品能力，以及防抖冷却机制。
 */
class AutomaticEvolutionSchedulerTest {

    private AgentRepository agentRepository;
    private EvolutionRecordRepository evolutionRecordRepository;
    private EvolutionGateService gateService;
    private EvolutionEngine evolutionEngine;
    private AgentMetricsService agentMetricsService;
    private AutomaticEvolutionScheduler scheduler;

    @BeforeEach
    void setUp() {
        agentRepository = mock(AgentRepository.class);
        evolutionRecordRepository = mock(EvolutionRecordRepository.class);
        gateService = mock(EvolutionGateService.class);
        evolutionEngine = mock(EvolutionEngine.class);
        agentMetricsService = mock(AgentMetricsService.class);

        scheduler = new AutomaticEvolutionScheduler(
                agentRepository, evolutionRecordRepository, gateService, evolutionEngine, agentMetricsService);

        ReflectionTestUtils.setField(scheduler, "lowSuccessThreshold", 0.6);
        ReflectionTestUtils.setField(scheduler, "highTokenPerTask", 8000L);
        ReflectionTestUtils.setField(scheduler, "lowRatingThreshold", 3.0);
        ReflectionTestUtils.setField(scheduler, "cooldownMinutes", 30L);
        ReflectionTestUtils.setField(scheduler, "minMissions", 5);
    }

    private Agent buildAgent(long total, long successful, long tokens) {
        return Agent.builder()
                .id("agent-1")
                .name("Auto Agent")
                .totalMissions(total)
                .successfulMissions(successful)
                .totalTokensUsed(tokens)
                .userRating(4.5)
                .ratingCount(10L)
                .build();
    }

    @Test
    @DisplayName("成功率低于阈值且冷却已过时，触发 Prompt 优化自动进化")
    void tryAutoEvolve_lowSuccess_triggerPromptOptimization() {
        when(agentMetricsService.successRate(any(Agent.class))).thenReturn(0.4);
        when(gateService.evaluate(any(Agent.class), eq(EvolutionType.PROMPT_OPTIMIZATION)))
                .thenReturn(new EvolutionGateService.GateDecision(true, "ok"));
        when(evolutionRecordRepository.findFirstByAgentIdAndTypeAndIsRolledBackFalseOrderByCreatedAtDesc(
                eq("agent-1"), eq(EvolutionType.PROMPT_OPTIMIZATION)))
                .thenReturn(Optional.empty());
        when(evolutionEngine.evolve(any(Agent.class), eq(EvolutionType.PROMPT_OPTIMIZATION), any(EvolutionRequest.class)))
                .thenReturn(EvolutionResultDTO.builder().success(true).build());

        boolean triggered = scheduler.tryAutoEvolve(buildAgent(20, 8, 10000));

        assertTrue(triggered);
        verify(evolutionEngine).evolve(any(Agent.class), eq(EvolutionType.PROMPT_OPTIMIZATION), any(EvolutionRequest.class));
    }

    @Test
    @DisplayName("成功率正常时不触发成功率导向的进化")
    void tryAutoEvolve_healthySuccess_noTrigger() {
        when(agentMetricsService.successRate(any(Agent.class))).thenReturn(0.9);

        boolean triggered = scheduler.tryAutoEvolve(buildAgent(20, 18, 5000));

        assertFalse(triggered);
        verify(evolutionEngine, never()).evolve(any(Agent.class), any(), any());
    }

    @Test
    @DisplayName("样本量不足时不自动进化")
    void tryAutoEvolve_insufficientSamples_noTrigger() {
        boolean triggered = scheduler.tryAutoEvolve(buildAgent(2, 1, 1000));

        assertFalse(triggered);
        verify(evolutionEngine, never()).evolve(any(Agent.class), any(), any());
    }

    @Test
    @DisplayName("每任务 Token 过高时触发参数调优自动进化")
    void tryAutoEvolve_highToken_triggerParameterTuning() {
        when(agentMetricsService.successRate(any(Agent.class))).thenReturn(0.7);
        when(gateService.evaluate(any(Agent.class), eq(EvolutionType.PARAMETER_TUNING)))
                .thenReturn(new EvolutionGateService.GateDecision(true, "ok"));
        when(evolutionRecordRepository.findFirstByAgentIdAndTypeAndIsRolledBackFalseOrderByCreatedAtDesc(
                eq("agent-1"), eq(EvolutionType.PARAMETER_TUNING)))
                .thenReturn(Optional.empty());
        when(evolutionEngine.evolve(any(Agent.class), eq(EvolutionType.PARAMETER_TUNING), any(EvolutionRequest.class)))
                .thenReturn(EvolutionResultDTO.builder().success(true).build());

        // 20 个任务，总计 200000 token，平均 10000 > 8000
        boolean triggered = scheduler.tryAutoEvolve(buildAgent(20, 14, 200000));

        assertTrue(triggered);
        verify(evolutionEngine).evolve(any(Agent.class), eq(EvolutionType.PARAMETER_TUNING), any(EvolutionRequest.class));
    }

    @Test
    @DisplayName("同一类型在冷却期内不重复触发")
    void tryAutoEvolve_cooldownActive_noTrigger() {
        when(agentMetricsService.successRate(any(Agent.class))).thenReturn(0.4);

        EvolutionRecord recent = EvolutionRecord.builder()
                .id(1L)
                .agentId("agent-1")
                .type(EvolutionType.PROMPT_OPTIMIZATION)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();
        when(evolutionRecordRepository.findFirstByAgentIdAndTypeAndIsRolledBackFalseOrderByCreatedAtDesc(
                eq("agent-1"), eq(EvolutionType.PROMPT_OPTIMIZATION)))
                .thenReturn(Optional.of(recent));

        boolean triggered = scheduler.tryAutoEvolve(buildAgent(20, 8, 10000));

        assertFalse(triggered);
        verify(evolutionEngine, never()).evolve(any(Agent.class), any(), any());
    }
}
