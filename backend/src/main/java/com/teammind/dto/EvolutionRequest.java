package com.teammind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * Agent 进化请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvolutionRequest {
    private String type;
    private String reason;
    private Map<String, Object> context;
    private Boolean automatic;
}
