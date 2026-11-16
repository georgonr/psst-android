/*
 * PSST: setup wizard so that messages and calls reach a phone like a phone call / SMS would (DECISIONS D57, D61).
 *
 * Steps, in this order, each only when something is actually missing:
 *  1. notifications allowed for psst, and the message channel may pop up (importance HIGH),
 *  2. notifications shown on the lock screen (channel not "secret", lock screen notifications not switched off),
 *  3. full-screen notifications (USE_FULL_SCREEN_INTENT, Android 14+; Samsung leaves it OFF for APK installs),
 *  4. no battery optimisation (Samsung otherwise puts the app to sleep).
 *
 * Settings are opened from the activity WITHOUT FLAG_ACTIVITY_NEW_TASK, so Back returns to psst. MainActivity.onResume
 * calls [maybePrompt] again: it re-checks the real state and continues with the next missing step by itself.
 * Each step is asked at most once per app version (plus again after a call rang without full-screen permission).
 */
package io.element.android.features.call.impl.psst

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
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

    @Volatile private var dialogShowing = false

    private fun sk(): Boolean = Locale.getDefault().language in setOf("sk", "cs")

    private fun askedKey(step: Step) = "asked_${step.name.lowercase()}_version"

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

    private fun messageChannelSettings(context: Context): Intent {
        val channel = messageChannel(context)
        return if (channel != null && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channel.id)
        } else {
            appNotificationSettings(context)
        }
    }

    /** For the reminder notification (started from outside an activity → NEW_TASK is required there). */
    fun fullScreenIntentSettings(context: Context): Intent =
        fullScreenIntentSettingsInTask(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun fullScreenIntentSettingsInTask(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())
        } else {
            appNotificationSettings(context)
        }

    @SuppressLint("BatteryLife")
    private fun batterySettings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

    // ---- wizard -------------------------------------------------------------------------------------------------

    /**
     * Called from MainActivity.onResume (also when the user comes back from a settings screen): shows the first
     * step that is still missing and was not asked yet in this app version. Returns the step shown, if any.
     */
    fun maybePrompt(activity: Activity): Step? {
        if (dialogShowing || activity.isFinishing) return null
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = versionCode(activity)
        if (canUseFullScreenIntent(activity)) prefs.edit().putBoolean(KEY_FSI_NOTIFIED, false).apply()
        for (step in Step.entries) {
            if (isReady(activity, step)) continue
            val remind = step == Step.FULL_SCREEN && prefs.getBoolean(KEY_FSI_REMIND, false)
            if (prefs.getLong(askedKey(step), -1) == version && !remind) continue
            prefs.edit().putLong(askedKey(step), version).apply()
            if (step == Step.FULL_SCREEN) prefs.edit().putBoolean(KEY_FSI_REMIND, false).apply()
            Timber.i("PSST setup wizard: step %s", step)
            show(activity, step)
            return step
        }
        return null
    }

    private fun show(activity: Activity, step: Step) {
        val (title, message) = texts(step)
        val intent = when (step) {
            Step.NOTIFICATIONS -> messageChannelSettings(activity)
            Step.LOCK_SCREEN -> messageChannelSettings(activity)
            Step.FULL_SCREEN -> fullScreenIntentSettingsInTask(activity)
            Step.BATTERY -> batterySettings(activity)
        }
        val fallback = when (step) {
            Step.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            else -> appNotificationSettings(activity)
        }
        var openedSettings = false
        dialogShowing = true
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(if (sk()) "Otvoriť nastavenia" else "Open settings") { _, _ ->
                openedSettings = runCatching { activity.startActivity(intent) }
                    .recoverCatching { activity.startActivity(fallback) }
                    .onFailure { Timber.w(it, "PSST setup wizard: cannot open settings for %s", step) }
                    .isSuccess
            }
            .setNegativeButton(if (sk()) "Neskôr" else "Later", null)
            .setOnDismissListener {
                dialogShowing = false
                // "Later" (or settings could not be opened): continue with the next step right away.
                // After "Open settings" the next step comes in onResume, when the user returns to psst.
                if (!openedSettings && !activity.isFinishing) maybePrompt(activity)
            }
            .show()
    }

    private fun texts(step: Step): Pair<String, String> = if (sk()) {
        when (step) {
            Step.NOTIFICATIONS -> "Upozornenia na správy" to
                "Aby sa nová správa ukázala aj ako vyskakovacie okno navrchu obrazovky, povoľte psst upozornenia " +
                "a pre „Správy“ zapnite „Vyskakovacie okno“ (pop-up). Potom sa vráťte späť."
            Step.LOCK_SCREEN -> "Správy na zamknutej obrazovke" to
                "Upozornenia psst sú skryté na zamknutej obrazovke. V nastaveniach kanála „Správy“ zapnite zobrazovanie " +
                "na zamknutej obrazovke (a v nastaveniach telefónu Zamknutá obrazovka → Upozornenia). Potom sa vráťte späť."
            Step.FULL_SCREEN -> "Hovory na celú obrazovku" to
                "Aby sa pri prichádzajúcom hovore rozsvietil displej a aj na zamknutom telefóne sa ukázalo, kto volá, " +
                "s tlačidlami Prijať a Odmietnuť, zapnite pre psst „Upozornenia na celú obrazovku“. Potom sa vráťte späť."
            Step.BATTERY -> "Spoľahlivé hovory a správy" to
                "Aby telefón psst neuspal a hovory aj správy prichádzali hneď, povoľte psst bežať bez obmedzenia batérie."
        }
    } else {
        when (step) {
            Step.NOTIFICATIONS -> "Message notifications" to
                "So that new messages also pop up at the top of the screen, allow notifications for psst and turn on " +
                "\"Pop up\" for \"Messages\". Then come back."
            Step.LOCK_SCREEN -> "Messages on the lock screen" to
                "psst notifications are hidden on the lock screen. In the \"Messages\" channel settings allow showing them " +
                "on the lock screen (and in the phone's Lock screen → Notifications settings). Then come back."
            Step.FULL_SCREEN -> "Full-screen calls" to
                "To light up the screen and show who is calling with Accept and Decline even on a locked phone, " +
                "allow psst to use full-screen notifications. Then come back."
            Step.BATTERY -> "Reliable calls and messages" to
                "So that the phone does not put psst to sleep and calls and messages arrive immediately, " +
                "allow psst to run without battery restrictions."
        }
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
            context, NOTIFICATION_ID, fullScreenIntentSettings(context),
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
