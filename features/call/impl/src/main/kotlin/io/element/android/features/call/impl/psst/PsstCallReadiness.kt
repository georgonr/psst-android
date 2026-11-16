/*
 * PSST: setup wizard so that messages and calls reach a phone like a phone call / SMS would (DECISIONS D57, D61).
 *
 * Steps, in this order, each only when something is actually missing:
 *  1. notifications allowed for psst, and the message channel may pop up (importance HIGH),
 *  2. notifications shown on the lock screen (channel not "secret", lock screen notifications not switched off),
 *  3. full-screen notifications (USE_FULL_SCREEN_INTENT, Android 14+; Samsung leaves it OFF for APK installs),
 *  4. no battery optimisation (Samsung otherwise puts the app to sleep).
 *
 * D74: when one of them is missing, MainActivity.onResume opens Settings → "Upozornenia a hovory"
 * (PsstNotificationsCallsActivity, D72) with the missing rows highlighted, once per app version (first run and after
 * every update) and again after a call could not ring full screen; that screen asks for them one after another.
 */
package io.element.android.features.call.impl.psst

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import io.element.android.libraries.designsystem.utils.CommonDrawables
import timber.log.Timber
import java.util.Locale

object PsstCallReadiness {
    private const val PREFS = "psst_call_readiness"
    private const val KEY_FSI_REMIND = "fsi_remind_after_missed_call"
    private const val KEY_FSI_NOTIFIED = "fsi_missed_call_notified"
    private const val CHANNEL_ID = "PSST_CALL_SETUP"
    private const val NOTIFICATION_ID = 0x5057

    /** Prefix of the message channel ids (libraries/push/impl … NotificationChannels.kt, MESSAGES channel). */
    private const val MESSAGE_CHANNEL_PREFIX = "PSST_MESSAGES_V4"

    enum class Step { NOTIFICATIONS, LOCK_SCREEN, FULL_SCREEN, BATTERY }

    private const val KEY_SCREEN_SHOWN_VERSION = "screen_shown_version"
    private const val SCREEN_CLASS = "io.element.android.libraries.push.impl.psst.PsstNotificationsCallsActivity"
    private const val EXTRA_HIGHLIGHT = "psst_highlight_missing"

    private fun sk(): Boolean = Locale.getDefault().language in setOf("sk", "cs")

    // ---- state checks -------------------------------------------------------------------------------------------

    private fun messageChannel(context: Context): NotificationChannel? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return context.getSystemService<NotificationManager>()?.notificationChannels
            ?.firstOrNull { it.id.startsWith(MESSAGE_CHANNEL_PREFIX) }
    }

    /** Notifications allowed for the app and the message channel may pop up on screen. */
    fun notificationsReady(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = messageChannel(context) ?: return true
        return channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    /** Message notifications are not hidden from the lock screen (per channel or globally). */
    fun lockScreenReady(context: Context): Boolean {
        val channel = messageChannel(context)
        if (channel != null && channel.lockscreenVisibility == Notification.VISIBILITY_SECRET) return false
        // Hidden-but-readable secure setting (AOSP + Samsung); unreadable → assume on.
        val global = runCatching {
            Settings.Secure.getInt(context.contentResolver, "lock_screen_show_notifications", 1)
        }.getOrDefault(1)
        return global != 0
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() ?: true
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    fun isReady(context: Context, step: Step): Boolean = when (step) {
        Step.NOTIFICATIONS -> notificationsReady(context)
        Step.LOCK_SCREEN -> lockScreenReady(context)
        Step.FULL_SCREEN -> canUseFullScreenIntent(context)
        Step.BATTERY -> isIgnoringBatteryOptimizations(context)
    }

    private fun versionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    // ---- settings intents (no NEW_TASK when started from the activity → Back returns to psst) --------------------

    private fun appNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** For the reminder notification (started from outside an activity → NEW_TASK is required there). */
    fun fullScreenIntentSettings(context: Context): Intent =
        fullScreenIntentSettingsInTask(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun fullScreenIntentSettingsInTask(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())
        } else {
            appNotificationSettings(context)
        }

    // ---- wizard -------------------------------------------------------------------------------------------------

    /** Settings → "Upozornenia a hovory" (libraries/push/impl … PsstNotificationsCallsActivity, D72). */
    fun settingsScreenIntent(context: Context, highlight: Boolean): Intent =
        Intent().setClassName(context, SCREEN_CLASS).putExtra(EXTRA_HIGHLIGHT, highlight)

    /**
     * Called from MainActivity.onResume. D74: when something required is missing, open the "Upozornenia a hovory"
     * screen (missing rows highlighted) — once per app version (first run and after every update), and again after
     * a call could not ring full screen. The screen itself asks for the missing items one after another.
     *
     * Replaces the dialog chain of D57/D61, which could stop for the rest of the process: its "dialog showing" flag
     * was static and was only reset by the dialog's dismiss callback, which never runs when the activity is destroyed
     * while the dialog is up (One UI recreates psst's activity when returning from some settings pages), and each step
     * was marked "asked" when shown, not when answered — so after an update the full-screen step could be skipped.
     */
    fun maybePrompt(activity: Activity): Step? {
        if (activity.isFinishing) return null
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = versionCode(activity)
        if (canUseFullScreenIntent(activity)) prefs.edit().putBoolean(KEY_FSI_NOTIFIED, false).putBoolean(KEY_FSI_REMIND, false).apply()
        val missing = Step.entries.firstOrNull { !isReady(activity, it) } ?: return null
        val remind = prefs.getBoolean(KEY_FSI_REMIND, false) && !canUseFullScreenIntent(activity)
        if (prefs.getLong(KEY_SCREEN_SHOWN_VERSION, -1) == version && !remind) return null
        val started = runCatching { activity.startActivity(settingsScreenIntent(activity, highlight = true)) }
            .onFailure { Timber.w(it, "PSST: cannot open notifications & calls screen") }
            .isSuccess
        if (!started) return null
        prefs.edit().putLong(KEY_SCREEN_SHOWN_VERSION, version).putBoolean(KEY_FSI_REMIND, false).apply()
        Timber.i("PSST: notifications & calls screen opened, first missing: %s", missing)
        return missing
    }

    /** Called when a ringing call is shown: if it could not go full screen, remind the user (once until fixed). */
    fun onIncomingCallShown(context: Context) {
        if (canUseFullScreenIntent(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FSI_REMIND, true).apply()
        if (prefs.getBoolean(KEY_FSI_NOTIFIED, false)) return
        prefs.edit().putBoolean(KEY_FSI_NOTIFIED, true).apply()
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, if (sk()) "Nastavenie hovorov" else "Call setup", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pi = PendingIntent.getActivity(
            context, NOTIFICATION_ID, settingsScreenIntent(context, highlight = true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (sk()) {
            "Hovor sa nemohol zobraziť na celú obrazovku. Klepnite a zapnite pre psst „Upozornenia na celú obrazovku“."
        } else {
            "The call could not be shown full screen. Tap to allow psst to use full-screen notifications."
        }
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(CommonDrawables.ic_notification)
                .setContentTitle(if (sk()) "Hovory na celú obrazovku sú vypnuté" else "Full-screen calls are off")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        )
    }
}
