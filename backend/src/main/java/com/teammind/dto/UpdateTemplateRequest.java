package com.teammind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * 更新模板请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTemplateRequest {
    private String name;
    private String description;
    private String icon;
    private String category;
    private List<String> agents;
    private Boolean isPublic;
}
