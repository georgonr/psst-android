/*
 * PSST (D67): small on-device log of pushes and posted notifications for the notification diagnostics screen
 * (Settings → Notifications → psst diagnostics). Only technical data: times, channel, importance, flags,
 * shortened ids — no message text, no names.
 */
package io.element.android.libraries.push.impl.psst

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PsstNotifLog {
    const val FILE_NAME = "psst-notification-log.txt"
    private const val MAX_LINES = 400
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile var appContext: Context? = null

    fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    fun add(line: String) {
        appContext?.let { add(it, line) }
    }

    @Synchronized
    fun add(context: Context, line: String) {
        runCatching {
            val f = file(context)
            val lines = if (f.exists()) f.readLines() else emptyList()
            val entry = "${fmt.format(Date())} $line"
            f.writeText((lines.takeLast(MAX_LINES - 1) + entry).joinToString("\n", postfix = "\n"))
        }
    }

    /** Short, stable, non-reversible label for an id (room/event) so log lines can be correlated. */
    fun short(id: String?): String = if (id == null) "-" else "#" + Integer.toHexString(id.hashCode()).takeLast(5)

    fun posted(context: Context, tag: String?, id: Int, n: Notification) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val ch = if (Build.VERSION.SDK_INT >= 26) nm.getNotificationChannel(n.channelId) else null
        val gab = if (Build.VERSION.SDK_INT >= 26) n.groupAlertBehavior else 0
        val shortcut = if (Build.VERSION.SDK_INT >= 26) n.shortcutId != null else false
        val onlyOnce = n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
        val summary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val ageS = (System.currentTimeMillis() - n.`when`) / 1000
        add(
            context,
            "POST tag=${short(tag)} id=$id ch=${n.channelId ?: "-"} chImp=${ch?.importance ?: "missing"} " +
                "cat=${n.category ?: "-"} summary=$summary gab=${gabName(gab)} onlyAlertOnce=$onlyOnce " +
                "conv=$shortcut vis=${n.visibility} whenAge=${ageS}s ${deviceState(context)}"
        )
    }

    fun deviceState(context: Context): String {
        val nm = context.getSystemService(NotificationManager::class.java)
        val km = context.getSystemService(KeyguardManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        return "screenOn=${pm.isInteractive} locked=${km.isKeyguardLocked} filter=${nm.currentInterruptionFilter} " +
            "enabled=${nm.areNotificationsEnabled()}"
    }

    private fun gabName(v: Int) = when (v) {
        Notification.GROUP_ALERT_ALL -> "ALL"
        Notification.GROUP_ALERT_SUMMARY -> "SUMMARY"
        Notification.GROUP_ALERT_CHILDREN -> "CHILDREN"
        else -> v.toString()
    }
}
