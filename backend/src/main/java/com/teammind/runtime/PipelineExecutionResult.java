package com.teammind.runtime;

import com.teammind.entity.Artifact;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 完整 Pipeline 执行结果。
 */
@Data
@Builder
public class PipelineExecutionResult {

    private String pipelineName;
    private String taskId;
    private String overallStatus;  // SUCCESS / FAILED / CANCELLED / NEEDS_APPROVAL

    private PipelineContext context;

    /** 各步骤结果 */
    private List<PipelineStepResult> stepResults;

    /** 汇总 artifact */
    private Artifact finalArtifact;

    /** 总耗时 */
    private long totalDurationMs;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    /** 是否需要人工审批 */
    public boolean needsApproval() {
        return "NEEDS_APPROVAL".equals(overallStatus);
    }

    /** 是否成功 */
    public boolean isSuccess() {
        return "SUCCESS".equals(overallStatus);
    }
}
