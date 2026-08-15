package com.teammind.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Task 创建请求 DTO
 */
@Data
@Builder
public class TaskCreateRequest {
    private String projectId;
    private String objective;
    private String taskTypeId;
    private String pipelineId;
    private String assignedAgentId;
    private Integer maxRetries;
}
