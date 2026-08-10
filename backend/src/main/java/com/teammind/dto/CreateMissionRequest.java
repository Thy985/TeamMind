package com.teammind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * 创建 Mission 请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMissionRequest {
    private String title;
    private String description;
    private List<String> agentIds;
    private String templateId;
}
