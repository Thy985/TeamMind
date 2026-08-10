package com.teammind.llm;

import com.teammind.llm.LLMRequest.Message;
import com.teammind.repository.LLMCallRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 追踪服务
 * 
 * 记录和统计 LLM 调用情况
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMTrackingService {

    private final LLMCallRecordRepository recordRepository;

    /**
     * 记录 LLM 调用
     */
    @Async
    public void recordCall(LLMRequest request, LLMResponse response, 
                          String callType, String agentId, String missionId) {
        try {
            LLMCallRecord record = LLMCallRecord.builder()
                    .callId(response.getId())
                    .provider(response.getProvider())
                    .model(response.getModel())
                    .callType(callType)
                    .agentId(agentId)
                    .missionId(missionId)
                    .success(response.isSuccess())
                    .errorMessage(response.getError())
                    .latencyMs(response.getLatencyMs())
                    .createdAt(LocalDateTime.now())
                    .build();

            if (response.getUsage() != null) {
                record.setPromptTokens(response.getUsage().getPromptTokens());
                record.setCompletionTokens(response.getUsage().getCompletionTokens());
                record.setTotalTokens(response.getUsage().getTotalTokens());
            }

            // 计算成本
            record.calculateCost();

            // 摘要
            record.setRequestSummary(summarizeRequest(request));
            record.setResponseSummary(summarizeResponse(response));

            recordRepository.save(record);

            log.debug("Recorded LLM call: provider={}, model={}, tokens={}, cost=${}",
                    record.getProvider(), record.getModel(), 
                    record.getTotalTokens(), record.getEstimatedCost());

        } catch (Exception e) {
            log.error("Failed to record LLM call", e);
        }
    }

    /**
     * 获取使用统计
     */
    public Map<String, Object> getUsageStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("totalCalls", recordRepository.count());
        stats.put("successfulCalls", recordRepository.getSuccessfulCallCount());
        stats.put("failedCalls", recordRepository.getFailedCallCount());
        stats.put("totalTokens", recordRepository.getTotalTokens());
        stats.put("totalCost", recordRepository.getTotalCost());
        stats.put("averageLatency", recordRepository.getAverageLatency());

        // 按提供商统计
        List<Object[]> providerStats = recordRepository.getStatsByProvider();
        Map<String, Map<String, Object>> byProvider = new HashMap<>();
        for (Object[] row : providerStats) {
            Map<String, Object> pStats = new HashMap<>();
            pStats.put("calls", row[1]);
            pStats.put("tokens", row[2]);
            pStats.put("cost", row[3]);
            byProvider.put((String) row[0], pStats);
        }
        stats.put("byProvider", byProvider);

        // 按模型统计
        List<Object[]> modelStats = recordRepository.getStatsByModel();
        Map<String, Map<String, Object>> byModel = new HashMap<>();
        for (Object[] row : modelStats) {
            Map<String, Object> mStats = new HashMap<>();
            mStats.put("calls", row[1]);
            mStats.put("tokens", row[2]);
            mStats.put("cost", row[3]);
            byModel.put((String) row[0], mStats);
        }
        stats.put("byModel", byModel);

        return stats;
    }

    /**
     * 获取今日统计
     */
    public Map<String, Object> getTodayStats() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("todayTokens", recordRepository.getTotalTokensSince(startOfDay));
        stats.put("todayCost", recordRepository.getTotalCostSince(startOfDay));

        return stats;
    }

    /**
     * 获取 Agent 使用统计
     */
    public Map<String, Object> getAgentStats(String agentId) {
        List<LLMCallRecord> records = recordRepository.findByAgentIdOrderByCreatedAtDesc(agentId);

        long totalTokens = records.stream()
                .filter(r -> r.getTotalTokens() != null)
                .mapToLong(LLMCallRecord::getTotalTokens)
                .sum();

        double totalCost = records.stream()
                .filter(r -> r.getEstimatedCost() != null)
                .mapToDouble(LLMCallRecord::getEstimatedCost)
                .sum();

        double avgLatency = records.stream()
                .filter(r -> r.getLatencyMs() != null)
                .mapToLong(LLMCallRecord::getLatencyMs)
                .average()
                .orElse(0);

        return Map.of(
                "agentId", agentId,
                "totalCalls", records.size(),
                "totalTokens", totalTokens,
                "totalCost", totalCost,
                "averageLatency", avgLatency
        );
    }

    /**
     * 摘要请求
     */
    private String summarizeRequest(LLMRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (Message msg : request.getMessages()) {
            String content = msg.getContent();
            if (content != null && content.length() > 100) {
                content = content.substring(0, 100) + "...";
            }
            sb.append(msg.getRole()).append(": ").append(content).append("; ");
        }

        return sb.length() > 500 ? sb.substring(0, 500) + "..." : sb.toString();
    }

    /**
     * 摘要响应
     */
    private String summarizeResponse(LLMResponse response) {
        if (response.getContent() == null) {
            return response.getError();
        }

        String content = response.getContent();
        return content.length() > 500 ? content.substring(0, 500) + "..." : content;
    }
}
