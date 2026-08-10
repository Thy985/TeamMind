package com.teammind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Mission 历史摘要 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissionHistoryDTO {
    private String id;
    private String title;
    private String status;
    private String createdAt;
    private String completedAt;
    private String preview;
}
