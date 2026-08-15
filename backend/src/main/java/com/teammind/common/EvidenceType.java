package com.teammind.common;

/**
 * 证据类型 — Evidence Verifier 验证的类型
 */
public enum EvidenceType {
    /** Git diff 验证：Agent 声称修改了哪些文件，git status 是否真的存在 */
    GIT_DIFF,
    /** 测试执行验证：tests 是否真的通过 */
    TEST_EXECUTION,
    /** 文件存在验证：某个文件是否存在 */
    FILE_EXISTENCE,
    /** 命令退出码验证：命令是否以 0 退出 */
    COMMAND_EXIT
}
