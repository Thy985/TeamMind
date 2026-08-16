package com.teammind.common;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ReadinessResult — Plugin 就绪状态 + 诊断信息
 */
@Builder
public record ReadinessResult(
        String pluginId,
        ReadinessState state,
        String diagnosis,           // 为什么是这个状态
        Double readinessScore,      // 0-1，READY=1.0, DEGRADED=0.5, UNAVAILABLE=0.0
        List<String> failedChecks,  // 未通过的依赖检查项
        Map<String, Object> details // 详细诊断信息（endpoint, version, etc.）
) {
    public static ReadinessResult ready(String pluginId) {
        return ReadinessResult.builder()
                .pluginId(pluginId)
                .state(ReadinessState.READY)
                .diagnosis("All dependencies healthy")
                .readinessScore(1.0)
                .failedChecks(List.of())
                .details(Map.of())
                .build();
    }

    public static ReadinessResult unavailable(String pluginId, String reason, List<String> failedChecks) {
        return ReadinessResult.builder()
                .pluginId(pluginId)
                .state(ReadinessState.UNAVAILABLE)
                .diagnosis(reason)
                .readinessScore(0.0)
                .failedChecks(failedChecks)
                .details(Map.of())
                .build();
    }

    public static ReadinessResult degraded(String pluginId, String reason, Map<String, Object> details) {
        return ReadinessResult.builder()
                .pluginId(pluginId)
                .state(ReadinessState.DEGRADED)
                .diagnosis(reason)
                .readinessScore(0.5)
                .failedChecks(List.of())
                .details(details)
                .build();
    }

    public static ReadinessResult blocked(String pluginId, String reason) {
        return ReadinessResult.builder()
                .pluginId(pluginId)
                .state(ReadinessState.BLOCKED)
                .diagnosis(reason)
                .readinessScore(0.0)
                .failedChecks(List.of(reason))
                .details(Map.of())
                .build();
    }

    public boolean isRunnable() {
        return state == ReadinessState.READY || state == ReadinessState.DEGRADED;
    }

    public boolean isUnavailable() {
        return state == ReadinessState.UNAVAILABLE || state == ReadinessState.BLOCKED;
    }
}
