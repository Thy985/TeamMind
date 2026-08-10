package com.teammind.llm;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * LLM 调用记录
 * 
 * 用于追踪和统计 LLM 使用情况
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "llm_calls")
public class LLMCallRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 调用 ID（来自 LLM 提供商）
     */
    private String callId;

    /**
     * 提供商
     */
    private String provider;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * 调用类型
     */
    private String callType;  // chat, evolution, agent_task

    /**
     * 关联的 Agent ID
     */
    private String agentId;

    /**
     * 关联的 Mission ID
     */
    private String missionId;

    /**
     * 输入 Token 数
     */
    private Integer promptTokens;

    /**
     * 输出 Token 数
     */
    private Integer completionTokens;

    /**
     * 总 Token 数
     */
    private Integer totalTokens;

    /**
     * 估算成本（美元）
     */
    private Double estimatedCost;

    /**
     * 响应延迟（毫秒）
     */
    private Long latencyMs;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 请求摘要（不存储完整内容以节省空间）
     */
    @Column(length = 500)
    private String requestSummary;

    /**
     * 响应摘要
     */
    @Column(length = 500)
    private String responseSummary;

    /**
     * 额外元数据
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> metadata;

    /**
     * 调用时间
     */
    private LocalDateTime createdAt;

    /**
     * 预计算成本
     */
    public void calculateCost() {
        if (promptTokens == null || completionTokens == null) {
            return;
        }

        // 成本计算（基于不同模型的定价）
        double inputCostPer1k = getInputCostPer1k(model, provider);
        double outputCostPer1k = getOutputCostPer1k(model, provider);

        double inputCost = (promptTokens / 1000.0) * inputCostPer1k;
        double outputCost = (completionTokens / 1000.0) * outputCostPer1k;

        this.estimatedCost = inputCost + outputCost;
    }

    private double getInputCostPer1k(String model, String provider) {
        if ("anthropic".equals(provider)) {
            if (model.contains("opus")) return 0.015;
            if (model.contains("sonnet")) return 0.003;
            if (model.contains("haiku")) return 0.00025;
            return 0.008;
        }
        // OpenAI pricing
        if (model.contains("gpt-4-turbo")) return 0.01;
        if (model.contains("gpt-4-32k")) return 0.06;
        if (model.contains("gpt-4")) return 0.03;
        if (model.contains("gpt-3.5-turbo-16k")) return 0.003;
        if (model.contains("gpt-3.5")) return 0.0005;
        return 0.001;  // 默认
    }

    private double getOutputCostPer1k(String model, String provider) {
        if ("anthropic".equals(provider)) {
            if (model.contains("opus")) return 0.075;
            if (model.contains("sonnet")) return 0.015;
            if (model.contains("haiku")) return 0.00125;
            return 0.024;
        }
        // OpenAI pricing
        if (model.contains("gpt-4-turbo")) return 0.03;
        if (model.contains("gpt-4-32k")) return 0.12;
        if (model.contains("gpt-4")) return 0.06;
        if (model.contains("gpt-3.5-turbo-16k")) return 0.004;
        if (model.contains("gpt-3.5")) return 0.0015;
        return 0.002;
    }
}
