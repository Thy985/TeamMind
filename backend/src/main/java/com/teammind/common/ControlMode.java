package com.teammind.common;

/**
 * 三级控制模式
 *
 * Autopilot：信任系统，仅高风险操作需确认
 * Supervised：关键节点需用户确认（默认模式）
 * Manual：每一步都需用户确认
 */
public enum ControlMode {
    AUTOMATED,
    SUPERVISED,
    MANUAL
}
