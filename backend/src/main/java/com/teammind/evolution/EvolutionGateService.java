package com.teammind.evolution;

import com.teammind.entity.Agent;
import com.teammind.entity.EvolutionRecord;
import com.teammind.entity.EvolutionRecord.EvolutionType;
import com.teammind.repository.EvolutionRecordRepository;
import com.teammind.service.AgentMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 进化门禁决策服务（Evolution Gate）
 *
 * 将"进化/工具执行"从"无条件执行"演进为"有准入决策的产品能力"：
 * 在真正调用 LLM 消耗成本之前，先基于 Agent 的**真实执行指标**判断本次
 * 进化是否值得放行，并给出可解释的决策原因。
 *
 * 门禁维度：
 *  1. 样本量门槛  —— 任务样本不足时指标不可靠，拒绝进化（避免在噪声上盲目优化）
 *  2. 进化冷却期  —— 距上次进化过近时拒绝，避免频繁反复进化导致能力震荡
 *  3. 性能基线    —— Agent 当前已足够优秀时，拒绝"画蛇添足"式的优化，防止能力倒退
 *  4. 目标相关性  —— 进化目标与当前真实短板是否匹配（例如成功率已高时无需再优化 Prompt）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvolutionGateService {

    private final EvolutionRecordRepository evolutionRecordRepository;
    private final AgentMetricsService agentMetricsService;

    @Value("${teammind.evolution.gate.enabled:true}")
    private boolean gateEnabled;

    @Value("${teammind.evolution.gate.min-missions:5}")
    private int minMissions;

    @Value("${teammind.evolution.gate.cooldown-minutes:30}")
    private long cooldownMinutes;

    @Value("${teammind.evolution.gate.high-success-threshold:0.9}")
    private double highSuccessThreshold;

    /**
     * 门禁决策结果
     */
    public record GateDecision(boolean allowed, String reason) {
        static GateDecision allow(String reason) {
            return new GateDecision(true, reason);
        }

        static GateDecision deny(String reason) {
            return new GateDecision(false, reason);
        }
    }

    /**
     * 评估本次进化是否应被放行。
     *
     * @param agent  待进化的 Agent
     * @param type   进化类型
     * @return       门禁决策
     */
    public GateDecision evaluate(Agent agent, EvolutionType type) {
        if (!gateEnabled) {
            return GateDecision.allow("Evolution gate is disabled");
        }

        // 1. 样本量门槛
        long total = agent.getTotalMissions() != null ? agent.getTotalMissions() : 0L;
        if (total < minMissions) {
            return GateDecision.deny(String.format(
                    "Insufficient execution samples: only %d mission(s), need at least %d to reliably measure evolution impact",
                    total, minMissions));
        }

        // 2. 冷却期：基于最近一次进化记录判断
        Optional<EvolutionRecord> last = evolutionRecordRepository
                .findFirstByAgentIdAndIsRolledBackFalseOrderByCreatedAtDesc(agent.getId());
        if (last.isPresent() && last.get().getCreatedAt() != null) {
            long minutesSince = Duration.between(last.get().getCreatedAt(), LocalDateTime.now()).toMinutes();
            if (minutesSince < cooldownMinutes) {
                return GateDecision.deny(String.format(
                        "Evolution cooldown active: last evolution %d minute(s) ago, need to wait %d minute(s)",
                        minutesSince, cooldownMinutes));
            }
        }

        // 3. 目标相关性：成功率已达高基线时，不再进行"提升成功率"导向的进化
        double successRate = agentMetricsService.successRate(agent);
        boolean isSuccessOriented = targetsSuccessRate(type);
        if (isSuccessOriented && successRate >= highSuccessThreshold) {
            return GateDecision.deny(String.format(
                    "Agent already performs well (success rate %.0f%% >= %.0f%%), no success-rate-oriented evolution needed",
                    successRate * 100, highSuccessThreshold * 100));
        }

        return GateDecision.allow(String.format(
                "Passed gate: %d mission(s), success rate %.0f%%, cooldown satisfied",
                total, successRate * 100));
    }

    /**
     * 判断该进化类型是否以"提升成功率"为主要目标。
     */
    private boolean targetsSuccessRate(EvolutionType type) {
        return type == EvolutionType.PROMPT_OPTIMIZATION
                || type == EvolutionType.PARAMETER_TUNING;
    }
}
