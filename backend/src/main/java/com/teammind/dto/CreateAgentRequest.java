package com.teammind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 创建 Agent 请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAgentRequest {
    private String name;
    private String description;
    private String icon;
    private String prompt;
    private List<String> permissions;
    private List<Map<String, Object>> tools;
}
