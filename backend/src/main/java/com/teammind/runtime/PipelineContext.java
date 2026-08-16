package com.teammind.runtime;

import com.teammind.entity.Artifact;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Pipeline 执行上下文 — 在步骤间传递状态。
 *
 * 包含：
 * - 当前步骤索引
 * - 各步骤产生的 artifacts map
 * - 发现的 findings
 * - handoff 历史记录
 * - 执行开始时间
 */
@Data
@Builder
public class PipelineContext {

    private String pipelineName;
    private String taskId;
    private String projectId;
    private String objective;

    /** 当前执行到的步骤名 */
    private String currentStep;

    /** 步骤索引（从 0 开始） */
    private int stepIndex;

    /** 各步骤产出的 artifact map: stepName -> Artifact */
    @Builder.Default
    private Map<String, Artifact> artifacts = new HashMap<>();

    /** 步骤级结果记录 */
    @Builder.Default
    private Map<String, PipelineStepResult> stepResults = new HashMap<>();

    /** 发现列表（用 String 替代 Finding，因为 Finding 类型不存在） */
    @Builder.Default
    private java.util.List<String> findings = new java.util.ArrayList<>();

    /** handoff 历史 */
    @Builder.Default
    private java.util.List<HandoffRecord> handoffHistory = new java.util.ArrayList<>();

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    /** 最终路由决策 */
    private com.teammind.capability.RoutingDecision lastRoutingDecision;

    /**
     * 记录一次 handoff
     */
    public void recordHandoff(String fromStep, String toStep, String reason) {
        handoffHistory.add(HandoffRecord.builder()
                .fromStep(fromStep)
                .toStep(toStep)
                .reason(reason)
                .timestamp(LocalDateTime.now())
                .build());
    }

    /**
     * 记录步骤结果
     */
    public void recordStepResult(String stepName, PipelineStepResult result) {
        stepResults.put(stepName, result);
        if (result.getArtifact() != null) {
            artifacts.put(stepName, result.getArtifact());
        }
    }

    /**
     * 获取下一步骤名
     */
    public String getNextStep(PipelineDefinition def) {
        if (def == null) return null;
        String next = def.nextStepName(currentStep);
        if (next != null) return next;

        // 尝试根据 stepResult 决定
        PipelineStepResult lastResult = stepResults.get(currentStep);
        if (lastResult != null) {
            var stepDef = def.getStep(currentStep);
            if (stepDef != null) {
                return stepDef.determineNext(lastResult);
            }
        }
        return null;
    }

    /**
     * 判断 pipeline 是否完成
     */
    public boolean isCompleted() {
        return completedAt != null;
    }

    @Data
    @Builder
    public static class HandoffRecord {
        private String fromStep;
        private String toStep;
        private String reason;
        private LocalDateTime timestamp;
    }
}
