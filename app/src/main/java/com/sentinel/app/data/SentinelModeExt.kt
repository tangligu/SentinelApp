package com.sentinel.app.data

/**
 * SentinelMode 扩展 - 获取各模式的概率阈值
 */
fun SentinelMode.threshold(): Float = when (this) {
    SentinelMode.CONSERVATIVE -> 0.70f
    SentinelMode.BALANCED -> 0.50f
    SentinelMode.AGGRESSIVE -> 0.30f
}

/**
 * 获取模式对应的描述性颜色值（ARGB）
 */
fun SentinelMode.color(): Long = when (this) {
    SentinelMode.CONSERVATIVE -> 0xFF4CAF50  // 绿色
    SentinelMode.BALANCED -> 0xFFFF9800      // 橙色
    SentinelMode.AGGRESSIVE -> 0xFFF44336    // 红色
}