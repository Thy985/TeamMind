package com.teammind.dto;

import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 进化结果 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvolutionResultDTO {
    private Long recordId;
    private String agentId;
    private String type;
    private Integer fromVersion;
    private Integer toVersion;
    private String description;
    private Double scoreChange;
    private Boolean success;
    private String rollbackUrl;

    /**
     * 进化前真实指标快照（用于前后对比验收）
     */
    private Map<String, Object> metricsBefore;

    /**
     * 进化后真实指标快照（用于前后对比验收）
     */
    private Map<String, Object> metricsAfter;
}
