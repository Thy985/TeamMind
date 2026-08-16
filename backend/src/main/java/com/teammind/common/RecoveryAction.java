package com.teammind.common;

/**
 * Recovery 操作的安全级别
 *
 * SAFE:      仅检查，无副作用（如 --version）
 * DANGEROUS: 启动进程 / 安装依赖，需要 Permission Policy 审批
 * IRREVERSIBLE: 不可逆操作，必须人工确认
 */
public enum RecoveryAction {
    SAFE,
    DANGEROUS,
    IRREVERSIBLE
}
