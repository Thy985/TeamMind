package com.teammind.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 决议投票服务
 *
 * 在内存中聚合多 Agent 对某个决议的投票，达成多数一致后通过 WebSocket 广播决议结果。
 * 用于替代原先 {@link WebSocketController#handleResolutionVote} 中的 TODO 占位逻辑。
 */
@Slf4j
@Service
public class ResolutionService {

    /**
     * resolutionId -> (optionId -> 票数)
     */
    private final Map<String, Map<String, Integer>> resolutionVotes = new ConcurrentHashMap<>();

    /**
     * resolutionId -> (agentId -> optionId)，记录每个 Agent 已投的选项（防重复投票）
     */
    private final Map<String, Map<String, String>> resolutionVoters = new ConcurrentHashMap<>();

    /**
     * 记录一次投票。返回该决议的投票汇总（optionId -> 票数）。
     */
    public Map<String, Integer> recordVote(String resolutionId, String agentId, String optionId) {
        if (resolutionId == null || optionId == null) {
            return Map.of();
        }

        Map<String, Integer> votes = resolutionVotes.computeIfAbsent(resolutionId, k -> new ConcurrentHashMap<>());
        Map<String, String> voters = resolutionVoters.computeIfAbsent(resolutionId, k -> new ConcurrentHashMap<>());

        // 同一个 Agent 重复投票则忽略（或覆盖为最新选择）
        if (agentId != null) {
            String previous = voters.put(agentId, optionId);
            if (previous != null && !previous.equals(optionId)) {
                // Agent 改票：撤销旧选项的一票
                votes.computeIfPresent(previous, (k, v) -> v > 1 ? v - 1 : null);
            }
        }

        votes.merge(optionId, 1, Integer::sum);
        log.debug("Resolution {} vote recorded: agent={}, option={}, votes={}", resolutionId, agentId, optionId, votes);
        return new HashMap<>(votes);
    }

    /**
     * 判断某个决议是否已达成多数一致（某选项获得超过总票数一半的票，且至少有一票）。
     * 返回该选项 ID；未达成则返回 null。
     */
    public String resolveIfConsensus(String resolutionId) {
        Map<String, Integer> votes = resolutionVotes.get(resolutionId);
        if (votes == null || votes.isEmpty()) {
            return null;
        }
        int total = votes.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            return null;
        }
        for (Map.Entry<String, Integer> entry : votes.entrySet()) {
            if (entry.getValue() * 2 > total) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 清理某个决议的投票状态（决议结束后调用）。
     */
    public void clearResolution(String resolutionId) {
        resolutionVotes.remove(resolutionId);
        resolutionVoters.remove(resolutionId);
    }
}
