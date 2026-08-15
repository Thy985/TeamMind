package com.teammind.runtime;

import com.teammind.common.EvidenceStatus;
import com.teammind.common.EvidenceType;
import com.teammind.entity.Evidence;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Evidence 生命周期服务 — Phase 1A Runtime Contract
 *
 * 生命周期：CLAIMED → COLLECTED → VERIFIED
 *                              ↘ INVALIDATED
 * CLAIMED / COLLECTED / VERIFIED → INVALIDATED（任意中间态可失效）
 */
@Slf4j
@Component
public class EvidenceLifecycleService {

    /**
     * 创建一条新的 CLAIMED 证据。
     */
    public Evidence claim(String invocationId, EvidenceType type, String description) {
        var e = Evidence.builder()
                .id(java.util.UUID.randomUUID().toString())
                .invocationId(invocationId)
                .type(type)
                .status(EvidenceStatus.CLAIMED)
                .description(description)
                .data(Map.of())
                .collectedAt(LocalDateTime.now())
                .build();
        log.debug("Evidence CLAIMED: type={}, invocation={}", type, invocationId);
        return e;
    }

    /**
     * COLLECTED → VERIFIED
     * 人工或自动判定证据可信。
     */
    public Evidence verify(String evidenceId) {
        log.debug("Evidence VERIFIED: id={}", evidenceId);
        return Evidence.builder()
                .id(evidenceId)
                .status(EvidenceStatus.VERIFIED)
                .verifiedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 任何状态 → INVALIDATED
     * 关联的 Artifact 被后续变更覆盖时调用。
     */
    public Evidence invalidate(String evidenceId, String reason, String byWho) {
        log.debug("Evidence INVALIDATED: id={}, reason={}", evidenceId, reason);
        return Evidence.builder()
                .id(evidenceId)
                .status(EvidenceStatus.INVALIDATED)
                .invalidatedAt(LocalDateTime.now())
                .invalidatedBy(byWho)
                .build();
    }

    /**
     * 检查从 from 到 to 的转移是否合法。
     */
    public boolean canTransition(EvidenceStatus from, EvidenceStatus to) {
        if (from == to) return true;
        return switch (to) {
            case CLAIMED    -> from == null;
            case COLLECTED  -> from == EvidenceStatus.CLAIMED;
            case VERIFIED   -> from == EvidenceStatus.COLLECTED;
            case INVALIDATED -> from == EvidenceStatus.CLAIMED
                    || from == EvidenceStatus.COLLECTED
                    || from == EvidenceStatus.VERIFIED;
            default         -> false;
        };
    }

    /**
     * 获取给定状态下的可用命令。
     */
    public Set<String> getAvailableCommands(EvidenceStatus status) {
        if (status == null || status == EvidenceStatus.CLAIMED) return Set.of("collect");
        if (status == EvidenceStatus.COLLECTED) return Set.of("verify", "invalidate");
        if (status == EvidenceStatus.VERIFIED) return Set.of("invalidate");
        return Set.of();
    }
}
