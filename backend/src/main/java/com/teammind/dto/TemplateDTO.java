package com.teammind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * Template DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateDTO {
    private String id;
    private String name;
    private String description;
    private String icon;
    private String category;
    private List<String> agents;
    private String configPath;
    private Boolean isPublic;
    private Integer usageCount;
    private String createdAt;
    private String updatedAt;
}
