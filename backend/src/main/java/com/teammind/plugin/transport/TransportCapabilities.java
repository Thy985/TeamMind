package com.teammind.plugin.transport;

/**
 * TransportCapabilities — 声明某个 Transport 支持的能力
 *
 * 用于 Router 决策：根据 Task 需求选择最合适的 Transport。
 *
 * 示例：
 *   Task 要求 permission_control + structured_file_changes
 *   → LegacyTransport.capabilities().supports("permission") → false
 *   → ACPTransport.capabilities().supports("permission") → true
 *   → Router 选择 ACPTransport
 */
public record TransportCapabilities(
    boolean prompt,           // 支持提交 prompt
    boolean stream,           // 支持流式事件
    boolean cancel,           // 支持取消
    boolean permission,       // 支持结构化权限请求
    boolean fileChange,       // 支持结构化文件变更
    boolean sessionResume,    // 支持 session 恢复
    boolean plan,             // 支持 plan/preview
    boolean subagent,         // 支持 nested subagent
    int maxTokens,            // 上下文窗口限制
    double accuracyScore      // 经验准确率 (0.0 - 1.0)
) {
    /** Legacy CLI transport 的默认能力声明 */
    public static final TransportCapabilities LEGACY_MINIMAL = new TransportCapabilities(
        true,   // prompt
        true,   // stream
        true,   // cancel
        false,  // permission — legacy 无法结构化处理权限
        false,  // fileChange — legacy 只能从 stdout 猜测
        false,  // sessionResume
        false,  // plan
        false,  // subagent
        128_000,// max tokens (Codex default)
        0.85    // accuracy
    );

    /** ACP transport 的完整能力声明（以 Codex ACP 为基准） */
    public static final TransportCapabilities ACP_FULL = new TransportCapabilities(
        true,   // prompt
        true,   // stream
        true,   // cancel
        true,   // permission — ACP 提供结构化 permission request
        true,   // fileChange — ACP 提供结构化 file_change
        true,   // sessionResume
        true,   // plan — ACP 提供 plan 事件
        true,   // subagent — ACP 提供 nested subagent transcript
        200_000,// max tokens (Codex default)
        0.92    // accuracy
    );

    /**
     * 检查是否支持所有指定能力
     */
    public boolean supports(String... requiredCapabilities) {
        if (requiredCapabilities == null || requiredCapabilities.length == 0) {
            return true;
        }
        for (String cap : requiredCapabilities) {
            switch (cap.toLowerCase()) {
                case "prompt" -> { if (!this.prompt) return false; }
                case "stream" -> { if (!this.stream) return false; }
                case "cancel" -> { if (!this.cancel) return false; }
                case "permission" -> { if (!this.permission) return false; }
                case "file_change" -> { if (!this.fileChange) return false; }
                case "session_resume" -> { if (!this.sessionResume) return false; }
                case "plan" -> { if (!this.plan) return false; }
                case "subagent" -> { if (!this.subagent) return false; }
                default -> { /* unknown capability, ignore */ }
            }
        }
        return true;
    }

    /**
     * 检查此 Transport 是否比另一个更好（基于能力覆盖）
     */
    public boolean isSuperiorTo(TransportCapabilities other) {
        // 当前覆盖 all other 的能力，且至少多一个
        boolean coversAll = (!other.prompt() || this.prompt())
                && (!other.stream() || this.stream())
                && (!other.cancel() || this.cancel())
                && (!other.permission() || this.permission())
                && (!other.fileChange() || this.fileChange())
                && (!other.sessionResume() || this.sessionResume())
                && (!other.plan() || this.plan())
                && (!other.subagent() || this.subagent());
        boolean strictlyBetter = this.prompt() || this.stream() || this.cancel()
                || this.permission() || this.fileChange() || this.sessionResume()
                || this.plan() || this.subagent();
        return coversAll && strictlyBetter;
    }
}
