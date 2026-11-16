/*
 * PSST (D73): "Rozsvietiť obrazovku pri novej správe" — briefly turns the display on when a message notification
 * alerts while the screen is off (Samsung One UI does not wake the screen for app notifications by itself).
 *
 * Mechanism: a 3-second SCREEN_BRIGHT wake lock with ACQUIRE_CAUSES_WAKEUP. Android 15+ gates this behind the
 * special app access "Turn screen on" (permission android.permission.TURN_SCREEN_ON, normal|appop; settings page
 * android.settings.TURN_SCREEN_ON_SETTINGS). Without it the wake lock is acquired but the screen stays off.
 * Respects Do Not Disturb (only when the interruption filter is "all") and only for alerting message notifications.
 */
package io.element.android.libraries.push.impl.psst

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri

object PsstScreenWake {
    private const val PREFS = "psst_notification_prefs"
    private const val KEY = "wake_screen_on_message"
    private const val OP_TURN_SCREEN_ON = "android:turn_screen_on"
    private const val WAKE_MS = 3_000L

    /** null = the system has no such permission (older Android): waking works without it. */
    fun permissionGranted(context: Context): Boolean? {
        // Android ≤ 14: TURN_SCREEN_ON is signature|privileged (apps can't hold it) and not enforced — verified on
        // API 34: the wake lock turns the screen on. Android 15+ makes it a user-grantable special app access.
        if (Build.VERSION.SDK_INT < 35) return null
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return null
        val permission = context.checkSelfPermission("android.permission.TURN_SCREEN_ON") == android.content.pm.PackageManager.PERMISSION_GRANTED
        val mode = runCatching { ops.unsafeCheckOpNoThrow(OP_TURN_SCREEN_ON, Process.myUid(), context.packageName) }.getOrNull()
            ?: return permission
        // MODE_DEFAULT = decided by the (install-time) permission — the case on Android 14 (verified: the screen wakes)
        return mode == AppOpsManager.MODE_ALLOWED || (mode == AppOpsManager.MODE_DEFAULT && permission)
    }

    /** Default: on when the permission is granted (or not needed). */
    fun isEnabled(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getBoolean(KEY, permissionGranted(context) != false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }

    /** Effective state shown by the toggle: user wants it AND the system allows it. */
    fun isActive(context: Context): Boolean = isEnabled(context) && permissionGranted(context) != false

    /** Per-app page only on Android 15+: on Android 14 (AOSP emulator) that page crashes Settings → use the list. */
    fun permissionSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 35) {
            Intent("android.settings.TURN_SCREEN_ON_SETTINGS", "package:${context.packageName}".toUri())
        } else {
            permissionListIntent()
        }

    fun permissionListIntent(): Intent = Intent("android.settings.TURN_SCREEN_ON_SETTINGS")

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    /** Called for every posted notification (NotificationDisplayer). */
    fun maybeWake(context: Context, n: Notification) {
        val isMessage = n.category == Notification.CATEGORY_MESSAGE
        val summary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val onlyOnce = n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
        if (!isMessage || summary || onlyOnce) return
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        if (pm.isInteractive) return
        if (!isEnabled(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            PsstNotifLog.add(context, "WAKE skipped: Do Not Disturb (filter=${nm.currentInterruptionFilter})")
            return
        }
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = nm.getNotificationChannel(n.channelId)
            if (channel != null && channel.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        }
        runCatching {
            @Suppress("DEPRECATION")
            val lock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "psst:message-wake",
            )
            lock.acquire(WAKE_MS)
            PsstNotifLog.add(context, "WAKE screen: permission=${permissionGranted(context)} screenOnNow=${pm.isInteractive}")
        }.onFailure { PsstNotifLog.add(context, "WAKE failed: ${it::class.simpleName}") }
    }
}
