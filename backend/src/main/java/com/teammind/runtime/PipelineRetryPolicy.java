package com.teammind.runtime;

import lombok.Builder;
import lombok.Data;

/**
 * Pipeline 重试策略。
 */
@Data
@Builder
public class PipelineRetryPolicy {

    @Builder.Default
    private int maxAttempts = 3;

    @Builder.Default
    private long backoffMs = 5000;

    public static final PipelineRetryPolicy DEFAULT = PipelineRetryPolicy.builder()
            .maxAttempts(3)
            .backoffMs(5000)
            .build();
}
