package site.kpeclub.optimizer

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager

/**
 * Removes notifications of user-installed apps.
 * Never touches: calls, alarms, system apps, the phone app, ongoing notifications.
 */
class NotifBlocker : NotificationListenerService() {

    override fun onListenerConnected() {
        try {
            activeNotifications?.forEach { handle(it) }
        } catch (e: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    private fun handle(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences("kpe", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("block", false)) return

        val pkg = sbn.packageName
        if (pkg == packageName || sbn.isOngoing) return

        // Keep calls and alarms (WhatsApp call, etc.)
        val cat = sbn.notification.category
        if (cat == Notification.CATEGORY_CALL || cat == Notification.CATEGORY_ALARM) return

        // Keep the phone app
        if (pkg == defaultDialer()) return

        // Keep system apps
        try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return
        } catch (e: Exception) {
            return
        }

        cancelNotification(sbn.key)
    }

    private fun defaultDialer(): String? = try {
        (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
    } catch (e: Exception) {
        null
    }
}
