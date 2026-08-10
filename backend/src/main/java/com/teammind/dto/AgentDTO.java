package com.teammind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Agent DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentDTO {
    private String id;
    private String name;
    private String description;
    private String icon;
    private String version;
    private String author;
    private Integer downloadCount;
    private Double rating;
    private String status;
    private List<String> permissions;
    private String configPath;
    private String currentPrompt;
    private Integer evolutionVersion;
    private Double evolutionScore;
    private Long totalMissions;
    private Long successfulMissions;
    private Long totalTokensUsed;
    private Double userRating;
    private Long ratingCount;
    private Boolean installed;
    private Boolean enabled;
    private String installedAt;
    private Map<String, Object> testReport;
}
