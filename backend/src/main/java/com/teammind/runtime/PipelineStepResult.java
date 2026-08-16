package com.teammind.runtime;

import com.teammind.entity.Artifact;
import lombok.Builder;
import lombok.Data;

/**
 * 单个 Pipeline 步骤的执行结果。
 */
@Data
@Builder
public class PipelineStepResult {

    private String stepName;
    private String agentId;
    private String state;  // SUCCESS / FAILED / CRITICAL / SKIPPED

    private Artifact artifact;
    private String outputSummary;
    private String errorReason;

    private long durationMs;
    private int exitCode;

    /** 是否为 critical finding（需要人工审批） */
    @Builder.Default
    private boolean critical = false;

    /** 是否为 failed */
    public boolean isFailed() {
        return "FAILED".equals(state);
    }

    /** 是否成功 */
    public boolean isSuccess() {
        return "SUCCESS".equals(state);
    }

    /** 是否需要审批 */
    public boolean needsApproval() {
        return critical;
    }
}
