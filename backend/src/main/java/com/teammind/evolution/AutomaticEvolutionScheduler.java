package com.teammind.evolution;

import com.teammind.entity.Agent;
import com.teammind.entity.EvolutionRecord.EvolutionType;
import com.teammind.repository.AgentRepository;
import com.teammind.repository.EvolutionRecordRepository;
import com.teammind.service.AgentMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 自动进化调度器（Automatic Evolution Trigger）
 *
 * 将"进化"从"人工手动触发"演进为"基于真实指标阈值自动触发"的产品能力。
 *
 * 触发规则（基于 Agent 真实执行指标）：
 *  1. 成功率过低   → 触发 PROMPT_OPTIMIZATION（优化 Prompt 提升能力）
 *  2. Token 效率低 → 触发 PARAMETER_TUNING（压缩成本）
 *  3. 用户评分低   → 触发 PROMPT_OPTIMIZATION（提升满意度）
 *
 * 防抖机制：每个类型在每个冷却周期内至多触发一次，且由进化门禁
 * （EvolutionGateService）二次把关，避免在样本不足/能力已达标时盲目进化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomaticEvolutionScheduler {

    private final AgentRepository agentRepository;
    private final EvolutionRecordRepository evolutionRecordRepository;
    private final EvolutionGateService gateService;
    private final EvolutionEngine evolutionEngine;
    private final AgentMetricsService agentMetricsService;

    @Value("${teammind.evolution.auto-scheduler.enabled:false}")
    private boolean autoSchedulerEnabled;

    @Value("${teammind.evolution.auto-scheduler.low-success-threshold:0.6}")
    private double lowSuccessThreshold;

    @Value("${teammind.evolution.auto-scheduler.high-token-per-task:8000}")
    private long highTokenPerTask;

    @Value("${teammind.evolution.auto-scheduler.low-rating-threshold:3.0}")
    private double lowRatingThreshold;

    @Value("${teammind.evolution.gate.cooldown-minutes:30}")
    private long cooldownMinutes;

    @Value("${teammind.evolution.gate.min-missions:5}")
    private int minMissions;

    /**
     * 定时扫描所有已启用 Agent，按真实指标触发自动进化。
     */
    @Scheduled(fixedDelayString = "${teammind.evolution.auto-scheduler.interval-ms:3600000}")
    public void autoEvolveTick() {
        if (!autoSchedulerEnabled) {
            return;
        }
        try {
            List<Agent> agents = agentRepository.findByInstalledTrueAndEnabledTrue();
            int triggered = 0;
            for (Agent agent : agents) {
                if (tryAutoEvolve(agent)) {
                    triggered++;
                }
            }
            if (triggered > 0) {
                log.info("Auto evolution tick finished: triggered {} evolution(s)", triggered);
            }
        } catch (Exception e) {
            log.error("Auto evolution tick failed", e);
        }
    }

    /**
     * 尝试对单个 Agent 触发自动进化（可独立测试）。
     *
     * @return 是否触发了进化
     */
    public boolean tryAutoEvolve(Agent agent) {
        long total = agent.getTotalMissions() != null ? agent.getTotalMissions() : 0L;

        // 样本量不足时不自动进化（指标不可靠）
        if (total < minMissions) {
            return false;
        }

        // 成功率过低 → Prompt 优化
        if (agentMetricsService.successRate(agent) < lowSuccessThreshold
                && canTrigger(agent, EvolutionType.PROMPT_OPTIMIZATION)
                && gateAllows(agent, EvolutionType.PROMPT_OPTIMIZATION)) {
            trigger(agent, EvolutionType.PROMPT_OPTIMIZATION,
                    "Auto-triggered: success rate below threshold " + lowSuccessThreshold);
            return true;
        }

        // Token 效率低 → 参数调优
        long tokens = agent.getTotalTokensUsed() != null ? agent.getTotalTokensUsed() : 0L;
        long avgTokensPerTask = total > 0 ? tokens / total : 0L;
        if (avgTokensPerTask > highTokenPerTask
                && canTrigger(agent, EvolutionType.PARAMETER_TUNING)
                && gateAllows(agent, EvolutionType.PARAMETER_TUNING)) {
            trigger(agent, EvolutionType.PARAMETER_TUNING,
                    "Auto-triggered: avg tokens/task " + avgTokensPerTask + " above threshold " + highTokenPerTask);
            return true;
        }

        // 用户评分低（有评分样本）→ Prompt 优化
        double rating = agent.getUserRating() != null ? agent.getUserRating() : 0.0;
        long ratingCount = agent.getRatingCount() != null ? agent.getRatingCount() : 0L;
        if (rating > 0 && ratingCount > 0 && rating < lowRatingThreshold
                && canTrigger(agent, EvolutionType.PROMPT_OPTIMIZATION)
                && gateAllows(agent, EvolutionType.PROMPT_OPTIMIZATION)) {
            trigger(agent, EvolutionType.PROMPT_OPTIMIZATION,
                    "Auto-triggered: user rating " + rating + " below threshold " + lowRatingThreshold);
            return true;
        }

        return false;
    }

    /**
     * 二次门禁把关：进化门禁明确拒绝时跳过（避免在冷却/样本/能力已达标时盲目进化）。
     */
    private boolean gateAllows(Agent agent, EvolutionType type) {
        try {
            EvolutionGateService.GateDecision decision = gateService.evaluate(agent, type);
            return decision == null || decision.allowed();
        } catch (Exception e) {
            log.warn("Gate check failed for auto evolution agent={}: {}", agent.getId(), e.getMessage());
            return true; // 门禁异常时保守放行，交由 engine 内部门禁兜底
        }
    }

    /**
     * 冷却期判断：该类型进化在最近冷却窗口内是否已触发过。
     */
    private boolean canTrigger(Agent agent, EvolutionType type) {
        // 最近一条该类型的成功进化记录
        Optional<com.teammind.entity.EvolutionRecord> last = evolutionRecordRepository
                .findFirstByAgentIdAndTypeAndIsRolledBackFalseOrderByCreatedAtDesc(
                        agent.getId(), type);
        if (last.isEmpty()) {
            return true;
        }
        com.teammind.entity.EvolutionRecord record = last.get();
        if (record.getCreatedAt() == null) {
            return true;
        }
        long minutes = Duration.between(record.getCreatedAt(), LocalDateTime.now()).toMinutes();
        return minutes >= cooldownMinutes;
    }

    private void trigger(Agent agent, EvolutionType type, String reason) {
        // 构造自动进化请求（automatic=true）
        var request = com.teammind.dto.EvolutionRequest.builder()
                .type(type.name())
                .reason(reason)
                .automatic(true)
                .build();
        var result = evolutionEngine.evolve(agent, type, request);
        log.info("Auto evolution {} for agent={}: success={}, reason={}",
                type, agent.getId(), result.getSuccess(), reason);
    }
}
