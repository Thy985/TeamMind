package com.teammind.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 控制器
 * 
 * 处理客户端的 WebSocket 消息
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final WSEventPublisher eventPublisher;
    private final ResolutionService resolutionService;

    /**
     * 订阅任务
     */
    @MessageMapping("/subscribe")
    public void subscribeToMission(@Payload Map<String, Object> message,
                                   SimpMessageHeaderAccessor headerAccessor) {
        String missionId = (String) message.get("missionId");
        if (missionId != null) {
            log.info("Client subscribed to mission: {}", missionId);
            // 可以在这里存储会话信息
        }
    }

    /**
     * 取消订阅任务
     */
    @MessageMapping("/unsubscribe")
    public void unsubscribeFromMission(@Payload Map<String, Object> message) {
        String missionId = (String) message.get("missionId");
        if (missionId != null) {
            log.info("Client unsubscribed from mission: {}", missionId);
        }
    }

    /**
     * 处理决议投票
     */
    @MessageMapping("/resolution/vote")
    public void handleResolutionVote(@Payload Map<String, Object> message) {
        String resolutionId = (String) message.get("resolutionId");
        String optionId = (String) message.get("optionId");
        String agentId = (String) message.get("agentId");
        String missionId = (String) message.get("missionId");

        if (resolutionId == null || optionId == null) {
            log.warn("Invalid vote message (missing resolutionId/optionId): {}", message);
            return;
        }

        log.info("Vote received: resolution={}, option={}, agent={}", resolutionId, optionId, agentId);

        // 记录投票并广播投票更新
        Map<String, Integer> votes = resolutionService.recordVote(resolutionId, agentId, optionId);
        eventPublisher.publishResolutionVote(missionId, resolutionId, agentId, optionId);

        // 检查是否达成多数一致，若达成则广播决议结果并清理状态
        String resolvedOption = resolutionService.resolveIfConsensus(resolutionId);
        if (resolvedOption != null) {
            eventPublisher.publishResolutionResolved(missionId, resolutionId, resolvedOption, new HashMap<>(votes));
            resolutionService.clearResolution(resolutionId);
            log.info("Resolution {} resolved with option {}", resolutionId, resolvedOption);
        }
    }

    /**
     * 心跳响应
     */
    @MessageMapping("/ping")
    public Map<String, Object> handlePing(@Payload Map<String, Object> message) {
        return Map.of("type", "pong", "timestamp", System.currentTimeMillis());
    }
}
