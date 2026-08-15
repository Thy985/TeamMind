package com.teammind.common;

/**
 * Evidence 生命周期状态
 *
 *   CLAIMED → COLLECTED → VERIFIED
 *                 ↘ INVALIDATED
 *   CLAIMED --------→ INVALIDATED
 *   COLLECTED ------> INVALIDATED
 *
 * 证据失效场景：关联的 Artifact 被后续变更覆盖、commit hash 变化等。
 */
public enum EvidenceStatus {
    /** Agent 声称该证据存在（未经验证） */
    CLAIMED,
    /** Verifier 已收集到证据 */
    COLLECTED,
    /** 证据经独立验证可信 */
    VERIFIED,
    /** 因后续变更而失效（需重新验证） */
    INVALIDATED
}
