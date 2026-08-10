package com.teammind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 更新 Mission 请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateMissionRequest {
    private String title;
    private String description;
    private String status;
    private List<Map<String, Object>> nodes;
    private List<Map<String, Object>> edges;
}
