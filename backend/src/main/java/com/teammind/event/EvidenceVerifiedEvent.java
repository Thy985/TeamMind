package com.teammind.event;

import com.teammind.common.EvidenceType;
import com.teammind.common.EventType;
import java.util.Map;

/**
 * EvidenceVerified 事件 — 证据验证结果
 */
public class EvidenceVerifiedEvent {
    private final TeamMindEvent base;
    private final EvidenceType evidenceType;
    private final Boolean passed;
    private final String summary;
    private final Map<String, Object> details;

    public EvidenceVerifiedEvent(String taskId, String pluginId, String role,
                                 EvidenceType evidenceType, boolean passed, String summary) {
        this.base = TeamMindEvent.of(EventType.EVIDENCE_VERIFIED, taskId, pluginId, role,
                Map.of("evidenceType", evidenceType.name(), "passed", passed, "summary", summary));
        this.evidenceType = evidenceType;
        this.passed = passed;
        this.summary = summary;
        this.details = null;
    }

    public TeamMindEvent toEvent() { return base; }
    public EvidenceType evidenceType() { return evidenceType; }
    public Boolean passed() { return passed; }
    public String summary() { return summary; }
}
