package com.teammind.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResolutionService 单元测试
 *
 * 验证决议投票聚合与多数一致判定逻辑：
 *  1. 单 Agent 投票
 *  2. 多个 Agent 投票达成多数一致后返回获胜选项
 *  3. 平票时返回 null（未达成一致）
 *  4. 同一 Agent 改票会撤销旧选项
 *  5. 决议清理
 */
class ResolutionServiceTest {

    private final ResolutionService service = new ResolutionService();

    @Test
    @DisplayName("单 Agent 投票后达到全部一致")
    void singleVoteReachesConsensus() {
        service.recordVote("res-1", "agent-a", "option-1");
        assertEquals("option-1", service.resolveIfConsensus("res-1"));
    }

    @Test
    @DisplayName("多数一致后返回获胜选项")
    void majorityVoteResolves() {
        service.recordVote("res-2", "agent-a", "option-1");
        service.recordVote("res-2", "agent-b", "option-1");
        service.recordVote("res-2", "agent-c", "option-2");
        assertEquals("option-1", service.resolveIfConsensus("res-2"));
    }

    @Test
    @DisplayName("平票时不达成一致")
    void tieDoesNotResolve() {
        service.recordVote("res-3", "agent-a", "option-1");
        service.recordVote("res-3", "agent-b", "option-2");
        assertNull(service.resolveIfConsensus("res-3"));
    }

    @Test
    @DisplayName("同一 Agent 改票会撤销旧选项")
    void agentChangingVoteRevokesPrevious() {
        service.recordVote("res-4", "agent-a", "option-1");
        service.recordVote("res-4", "agent-b", "option-2");
        // agent-a 改票到 option-2，此时 option-1 票数归零
        service.recordVote("res-4", "agent-a", "option-2");
        Map<String, Integer> votes = service.recordVote("res-4", "agent-c", "option-2");
        assertEquals(2, votes.get("option-2"));
        assertEquals("option-2", service.resolveIfConsensus("res-4"));
    }

    @Test
    @DisplayName("决议清理后不再保留投票状态")
    void clearRemovesResolutionState() {
        service.recordVote("res-5", "agent-a", "option-1");
        service.clearResolution("res-5");
        assertNull(service.resolveIfConsensus("res-5"));
    }

    @Test
    @DisplayName("空参数投票返回空结果且不抛异常")
    void nullArgumentsAreIgnored() {
        Map<String, Integer> votes = service.recordVote(null, null, null);
        assertNotNull(votes);
        assertTrue(votes.isEmpty());
    }
}
