package com.teammind.capability;

import lombok.Builder;
import lombok.Data;

/**
 * 能力声明 — Plugin 声明自己具备什么能力
 */
@Data
@Builder
public class Capability {
    /** 能力 ID（如 "implementation" / "code_review" / "security_review"） */
    String id;

    /** 能力质量等级 */
    Quality quality;

    /** 能力描述 */
    String description;

    /** 是否需要审批才能执行 */
    boolean requiresApproval;

    /** 审查方 pluginId 列表（空 = 不审查） */
    java.util.List<String> reviewBy;

    public enum Quality {
        EXCELLENT, GOOD, FAIR, POOR
    }
}
