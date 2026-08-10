package com.teammind.service;

import com.teammind.entity.Agent;
import com.teammind.repository.AgentRepository;
import com.teammind.repository.MissionRepository;
import com.teammind.repository.EvolutionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Agent 真实指标服务
 *
 * 建立"真实进化评估闭环"：用任务成功率、Token 成本、用户评分等
 * 真实执行指标驱动 Agent 进化评估，替代原有的启发式打分。
 *
 * 设计：
 *  - recordTaskResult: 任务结束后回写真实执行指标到 Agent
 *  - rateAgent:        记录用户对 Agent 的评分
 *  - calculateEvolutionBenefit: 基于真实指标计算进化收益分数，
 *    该分数被 EvolutionEngine 用于替代启发式打分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMetricsService {

    private final AgentRepository agentRepository;
    private final MissionRepository missionRepository;
    private final EvolutionRecordRepository evolutionRecordRepository;

    /**
     * 回写单次任务执行结果到 Agent 的真实指标
     *
     * @param agentId     Agent ID
     * @param success     任务是否成功
     * @param tokensUsed  本次任务消耗的 Token 数（可为 0）
     */
    @Transactional
    public void recordTaskResult(String agentId, boolean success, long tokensUsed) {
        if (agentId == null) {
            return;
        }
        agentRepository.findById(agentId).ifPresent(agent -> {
            long total = (agent.getTotalMissions() != null ? agent.getTotalMissions() : 0L) + 1;
            long successful = (agent.getSuccessfulMissions() != null ? agent.getSuccessfulMissions() : 0L)
                    + (success ? 1 : 0);
            long tokens = (agent.getTotalTokensUsed() != null ? agent.getTotalTokensUsed() : 0L)
                    + Math.max(0, tokensUsed);

            agent.setTotalMissions(total);
            agent.setSuccessfulMissions(successful);
            agent.setTotalTokensUsed(tokens);
            agentRepository.save(agent);

            log.debug("Recorded task result for agent={}: success={}, tokens={}", agentId, success, tokensUsed);
        });
    }

    /**
     * 用户评分（0~5）
     */
    @Transactional
    public Agent rateAgent(String agentId, double rating) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found: " + agentId));

        double clamped = Math.max(0, Math.min(5, rating));
        long count = (agent.getRatingCount() != null ? agent.getRatingCount() : 0L) + 1;
        double prevAvg = agent.getUserRating() != null ? agent.getUserRating() : 0.0;

        // 增量更新均值：newAvg = (prevAvg * (count-1) + newRating) / count
        double newAvg = (prevAvg * (count - 1) + clamped) / count;

        agent.setUserRating(Math.round(newAvg * 100) / 100.0);
        agent.setRatingCount(count);
        agent = agentRepository.save(agent);

        log.info("Rated agent={}: {}/5 (avg now {}, {} ratings)", agentId, clamped, agent.getUserRating(), count);
        return agent;
    }

    /**
     * 获取 Agent 的真实执行指标
     */
    public Map<String, Object> getAgentMetrics(String agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found: " + agentId));

        long total = agent.getTotalMissions() != null ? agent.getTotalMissions() : 0L;
        long successful = agent.getSuccessfulMissions() != null ? agent.getSuccessfulMissions() : 0L;
        long tokens = agent.getTotalTokensUsed() != null ? agent.getTotalTokensUsed() : 0L;

        double successRate = total > 0 ? (double) successful / total : 0.0;
        long avgTokensPerTask = total > 0 ? tokens / total : 0L;

        return Map.of(
                "agentId", agentId,
                "totalMissions", total,
                "successfulMissions", successful,
                "successRate", Math.round(successRate * 10000) / 100.0,
                "totalTokensUsed", tokens,
                "avgTokensPerTask", avgTokensPerTask,
                "userRating", agent.getUserRating() != null ? agent.getUserRating() : 0.0,
                "ratingCount", agent.getRatingCount() != null ? agent.getRatingCount() : 0L
        );
    }

    /**
     * 基于真实指标计算进化收益分数（替代启发式打分）
     *
     * 综合三个真实维度，输出 0~1 的收益分：
     *  - 任务成功率（successRate）：反映功能是否真正可用
     *  - Token 成本效率（tokenEfficiency）：token 越少越高效
     *  - 用户评分（rating）：反映真实用户满意度
     *
     * 与 Agent 现有进化分数累计，供进化决策与历史对比使用。
     */
    public double calculateEvolutionBenefit(Agent agent) {
        long total = agent.getTotalMissions() != null ? agent.getTotalMissions() : 0L;
        long successful = agent.getSuccessfulMissions() != null ? agent.getSuccessfulMissions() : 0L;
        long tokens = agent.getTotalTokensUsed() != null ? agent.getTotalTokensUsed() : 0L;
        double rating = agent.getUserRating() != null ? agent.getUserRating() : 0.0;

        // 1. 成功率维度（权重 0.5）：样本不足时以中性值 0.5 兜底，避免早期波动
        double successRate = total > 0 ? (double) successful / total : 0.5;
        double successScore = 0.5 * successRate;

        // 2. Token 成本效率维度（权重 0.3）：每任务 token 越少越高效
        //    基准 5000 token/任务，超过则效率递减；无样本时给中性值 0.5
        double tokenEfficiency;
        if (total > 0) {
            double avgTokens = (double) tokens / total;
            // 简单线性：<=2000 token 给 1.0，>=10000 token 给 0.0
            tokenEfficiency = Math.max(0.0, Math.min(1.0, (10000.0 - avgTokens) / 8000.0));
        } else {
            tokenEfficiency = 0.5;
        }
        double tokenScore = 0.3 * tokenEfficiency;

        // 3. 用户评分维度（权重 0.2）：评分越高越好，无评分给中性值
        double ratingScore = rating > 0 ? 0.2 * (rating / 5.0) : 0.1;

        double benefit = successScore + tokenScore + ratingScore;

        // 归一化到 0~1
        double maxPossible = 0.5 + 0.3 + 0.2;
        double normalized = benefit / maxPossible;

        log.debug("Evolution benefit for agent={}: successRate={}, tokenEff={}, rating={} -> {}",
                agent.getId(), Math.round(successRate * 100) / 100.0,
                Math.round(tokenEfficiency * 100) / 100.0, rating,
                Math.round(normalized * 100) / 100.0);

        return Math.round(normalized * 100) / 100.0;
    }
}
