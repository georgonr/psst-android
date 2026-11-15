/*
 * PSST: make incoming calls behave like phone calls on locked phones (DECISIONS D57).
 *
 * Android 14+ only lets an app show an incoming call full screen over the lock screen (screen turns on, caller,
 * Accept / Decline) when it holds the "full-screen notifications" permission (USE_FULL_SCREEN_INTENT). Samsung and
 * other OEMs leave it OFF for apps installed from an APK; then the call only rings as a lock-screen notification.
 * Upstream Element X shows just a dismissible banner in the room list, which is easy to miss.
 *
 * This helper:
 *  - on first start and after every update asks for the permission (one sentence + opens the exact settings screen),
 *    then asks to exclude psst from battery optimisation (Samsung otherwise puts the app to sleep),
 *  - when a call rings while the permission is missing, posts a notification that opens the setting and makes
 *    the next app start ask again.
 */
package io.element.android.features.call.impl.psst

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import io.element.android.libraries.designsystem.utils.CommonDrawables
import java.util.Locale

object PsstCallReadiness {
    private const val PREFS = "psst_call_readiness"
    private const val KEY_FSI_ASKED_VERSION = "fsi_asked_version"
    private const val KEY_BATTERY_ASKED_VERSION = "battery_asked_version"
    private const val KEY_FSI_REMIND = "fsi_remind_after_missed_call"
    private const val KEY_FSI_NOTIFIED = "fsi_missed_call_notified"
    private const val CHANNEL_ID = "PSST_CALL_SETUP"
    private const val NOTIFICATION_ID = 0x5057

    @Volatile private var dialogShowing = false

    private fun sk(): Boolean = Locale.getDefault().language in setOf("sk", "cs")

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() ?: true
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    private fun versionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    fun fullScreenIntentSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @SuppressLint("BatteryLife")
    private fun batterySettings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

    /** Called from MainActivity.onResume: asks for what is missing, at most once per app version (or after a missed call). */
    fun maybePrompt(activity: Activity) {
        if (dialogShowing || activity.isFinishing) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = versionCode(activity)
        if (!canUseFullScreenIntent(activity)) {
            if (prefs.getLong(KEY_FSI_ASKED_VERSION, -1) != version || prefs.getBoolean(KEY_FSI_REMIND, false)) {
                prefs.edit().putLong(KEY_FSI_ASKED_VERSION, version).putBoolean(KEY_FSI_REMIND, false).apply()
                show(
                    activity,
                    title = if (sk()) "Hovory na celú obrazovku" else "Full-screen calls",
                    message = if (sk()) {
                        "Aby sa pri prichádzajúcom hovore rozsvietil displej a aj na zamknutom telefóne sa ukázalo, kto volá, " +
                            "s tlačidlami Prijať a Odmietnuť, zapnite pre psst „Upozornenia na celú obrazovku“."
                    } else {
                        "To light up the screen and show who is calling with Accept and Decline even on a locked phone, " +
                            "allow psst to use full-screen notifications."
                    },
                    intent = fullScreenIntentSettings(activity),
                )
                return
            }
        } else {
            prefs.edit().putBoolean(KEY_FSI_NOTIFIED, false).apply()
        }
        if (!isIgnoringBatteryOptimizations(activity) && prefs.getLong(KEY_BATTERY_ASKED_VERSION, -1) != version) {
            prefs.edit().putLong(KEY_BATTERY_ASKED_VERSION, version).apply()
            show(
                activity,
                title = if (sk()) "Spoľahlivé hovory a správy" else "Reliable calls and messages",
                message = if (sk()) {
                    "Aby telefón psst neuspal a hovory aj správy prichádzali hneď, povoľte psst bežať bez obmedzenia batérie."
                } else {
                    "So that the phone does not put psst to sleep and calls and messages arrive immediately, allow psst to run without battery restrictions."
                },
                intent = batterySettings(activity),
                fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        }
    }

    private fun show(activity: Activity, title: String, message: String, intent: Intent, fallback: Intent? = null) {
        dialogShowing = true
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(if (sk()) "Otvoriť nastavenia" else "Open settings") { _, _ ->
                runCatching { activity.startActivity(intent) }
                    .recoverCatching { fallback?.let { activity.startActivity(it) } ?: throw it }
            }
            .setNegativeButton(if (sk()) "Neskôr" else "Later", null)
            .setOnDismissListener { dialogShowing = false }
            .show()
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
