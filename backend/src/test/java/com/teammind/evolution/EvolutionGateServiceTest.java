package com.teammind.evolution;

import com.teammind.entity.Agent;
import com.teammind.entity.EvolutionRecord;
import com.teammind.entity.EvolutionRecord.EvolutionType;
import com.teammind.repository.EvolutionRecordRepository;
import com.teammind.service.AgentMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 进化门禁决策（Evolution Gate）单元测试
 *
 * 验证产品化的\"进化准入控制\"：在消耗 LLM 成本之前，基于 Agent 真实
 * 执行指标判断是否放行，并输出可解释的决策原因。
 */
class EvolutionGateServiceTest {

    private EvolutionRecordRepository evolutionRecordRepository;
    private AgentMetricsService agentMetricsService;
    private EvolutionGateService gate;

    @BeforeEach
    void setUp() {
        evolutionRecordRepository = mock(EvolutionRecordRepository.class);
        agentMetricsService = mock(AgentMetricsService.class);
        gate = new EvolutionGateService(evolutionRecordRepository, agentMetricsService);

        ReflectionTestUtils.setField(gate, "gateEnabled", true);
        ReflectionTestUtils.setField(gate, "minMissions", 5);
        ReflectionTestUtils.setField(gate, "cooldownMinutes", 30L);
        ReflectionTestUtils.setField(gate, "highSuccessThreshold", 0.9);
    }

    private Agent buildAgent(long total, long successful) {
        return Agent.builder()
                .id("agent-1")
                .name("Gate Agent")
                .totalMissions(total)
                .successfulMissions(successful)
                .build();
    }

    @Test
    @DisplayName("样本量不足时应拒绝进化")
    void evaluate_insufficientSamples_denied() {
        when(agentMetricsService.successRate(any(Agent.class))).thenReturn(0.5);

        EvolutionGateService.GateDecision decision =
                gate.evaluate(buildAgent(3, 2), EvolutionType.PROMPT_OPTIMIZATION);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("Insufficient execution samples"));
        verify(evolutionRecordRepository, never()).findFirstByAgentIdAndIsRolledBackFalseOrderByCreatedAtDesc(anyString());
    }

    @Test
    @DisplayName("冷却期内应拒绝进化")
    void evaluate_cooldownActive_denied() {
        when(agentMetricsService.successRate(any(Agent.class))).thenReturn(0.5);

        EvolutionRecord recent = EvolutionRecord.builder()
                .id(1L)
                .agentId("agent-1")
                .type(EvolutionType.PROMPT_OPTIMIZATION)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();
        when(evolutionRecordRepository.findFirstByAgentIdAndIsRolledBackFalseOrderByCreatedAtDesc("agent-1"))
                .thenReturn(Optional.of(recent));

        EvolutionGateService.GateDecision decision =
                gate.evaluate(buildAgent(10, 8), EvolutionType.PROMPT_OPTIMIZATION);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("cooldown active"));
    }

    @Test
    @DisplayName("成功率已高时不应再进行成功率导向的进化")
    void evaluate_highSuccessRate_deniedForSuccessOriented() {
        when(agentMetricsService.successRate(any(Agent.class))).thenReturn(0.95);
        when(evolutionRecordRepository.findFirstByAgentIdAndIsRolledBackFalseOrderByCreatedAtDesc("agent-1"))
                .thenReturn(Optional.empty());

        EvolutionGateService.GateDecision decision =
                gate.evaluate(buildAgent(20, 19), EvolutionType.PROMPT_OPTIMIZATION);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("already performs well"));
    }

    @Test
    @DisplayName("指标正常且冷却已过时应放行")
    void evaluate_healthyAgent_allowed() {
        when(agentMetricsService.successRate(any(Agent.class))).thenReturn(0.7);
        when(evolutionRecordRepository.findFirstByAgentIdAndIsRolledBackFalseOrderByCreatedAtDesc("agent-1"))
                .thenReturn(Optional.empty());

        EvolutionGateService.GateDecision decision =
                gate.evaluate(buildAgent(20, 14), EvolutionType.PROMPT_OPTIMIZATION);

        assertTrue(decision.allowed());
    }

    @Test
    @DisplayName("门禁关闭时应放行")
    void evaluate_gateDisabled_allowed() {
        ReflectionTestUtils.setField(gate, "gateEnabled", false);

        EvolutionGateService.GateDecision decision =
                gate.evaluate(buildAgent(1, 1), EvolutionType.PROMPT_OPTIMIZATION);

        assertTrue(decision.allowed());
        assertEquals("Evolution gate is disabled", decision.reason());
    }

    @Test
    @DisplayName("成功率高的 Agent 不应被拓扑进化（非成功率导向）拒绝")
    void evaluate_highSuccess_topologyEvolutionAllowed() {
        when(agentMetricsService.successRate(any(Agent.class))).thenReturn(0.95);
        when(evolutionRecordRepository.findFirstByAgentIdAndIsRolledBackFalseOrderByCreatedAtDesc("agent-1"))
                .thenReturn(Optional.empty());

        // TOPOLOGY_EVOLUTION 不以提升成功率为目标，不应被拒绝
        EvolutionGateService.GateDecision decision =
                gate.evaluate(buildAgent(20, 19), EvolutionType.TOPOLOGY_EVOLUTION);

        assertTrue(decision.allowed());
    }
}
