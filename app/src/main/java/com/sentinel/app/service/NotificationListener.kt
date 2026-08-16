package com.sentinel.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 通知监听服务 - 监听课堂相关App的通知（如学习通、钉钉等）
 * 可以在通知中提取老师发送的消息或问题
 */
class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"
        private const val MAX_NOTIFICATIONS = 50

        // 课堂相关App包名列表
        val CLASSROOM_APPS = listOf(
            "com.alibaba.android.rimet",   // 钉钉
            "com.chaoxing.mobile",          // 学习通
            "com.tencent.edu",              // 腾讯课堂
            "com.baidu.duer.supercourse",   // 百度智慧课堂
            "com.tencent.wework",           // 企业微信
            "com.tencent.tim",              // TIM
            "com.tencent.mobileqq",         // QQ
        )
    }

    private val activeNotifications = mutableListOf<StatusBarNotification>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val packageName = sbn.packageName

        // 只处理课堂相关App的通知
        if (packageName in CLASSROOM_APPS) {
            Log.d(TAG, "Classroom notification from: $packageName")
            Log.d(TAG, "Title: ${sbn.notification?.extras?.getString(android.app.Notification.EXTRA_TITLE)}")
            Log.d(TAG, "Text: ${sbn.notification?.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)}")

            // 更新活跃通知列表
            activeNotifications.add(sbn)
            if (activeNotifications.size > MAX_NOTIFICATIONS) {
                activeNotifications.removeAt(0)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        activeNotifications.removeAll { it.id == sbn.id }
    }

    override fun onListenerConnected() {
        Log.d(TAG, "Notification listener connected")
        // 获取当前所有活跃通知
        val active = activeNotifications
        onNotificationPosted(null)
    }

    /**
     * 获取最近的通知文本
     */
    fun getRecentNotifications(): List<String> {
        return activeNotifications.mapNotNull { sbn ->
            sbn.notification?.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        }
    }
}