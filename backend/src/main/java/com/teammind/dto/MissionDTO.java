package com.teammind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Mission 详情 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissionDTO {
    private String id;
    private String title;
    private String description;
    private String status;
    private String createdAt;
    private String updatedAt;
    private String completedAt;
    private List<Map<String, Object>> nodes;
    private List<Map<String, Object>> edges;
    private List<Map<String, Object>> logs;
    private Map<String, Object> result;
}
