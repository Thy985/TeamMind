package com.teammind.runtime;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * YAML 反序列化的 Pipeline 顶层定义。
 *
 * 示例 (review-loop.yaml):
 * <pre>
 * name: "review-loop"
 * description: "Implement → Review → Verify"
 * steps:
 *   - name: implement
 *     role: LEAD
 *     agent: codex
 *     prompt: |
 *       任务：{{objective}}
 *       约束：{{constraints}}
 *     output: CODE_DIFF
 *     handoff: review
 *   - name: review
 *     role: REVIEWER
 *     agent: claude-code
 *     prompt: |
 *       请审查：{{artifacts.implement.summary}}
 *     output: REVIEW_FINDINGS
 *     on_critical: request_approval
 *     on_success: verify
 * </pre>
 */
@Data
@Builder
public class PipelineDefinition {

    private String name;
    private String description;

    private List<PipelineStepDefinition> steps;

    /** 重试策略（可选） */
    @Builder.Default
    private PipelineRetryPolicy retry = PipelineRetryPolicy.DEFAULT;

    /**
     * 根据 step name 查找步骤定义
     */
    public PipelineStepDefinition getStep(String stepName) {
        return steps.stream()
                .filter(s -> s.getName().equals(stepName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取下一步的 step name（基于当前 step 的 handoff 字段）
     */
    public String nextStepName(String currentStepName) {
        PipelineStepDefinition current = getStep(currentStepName);
        if (current == null || current.getHandoff() == null) {
            return null;
        }
        return current.getHandoff();
    }
}
