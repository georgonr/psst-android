/*
 * PSST (D68): "wake-up" push for device verification requests.
 *
 * Matrix never pushes to-device messages, so a verification request from another device of the same user
 * (m.key.verification.request) only arrives while psst is open. Our server (server/wake, nginx mirror) sends a push
 * with the marker room id "!psst-verification:<domain>" when such a request is sent. We show a notification that
 * opens psst; once open, the app syncs, receives the request and shows the incoming verification screen.
 * Nothing is fetched or synced here — a background sync could consume the to-device request in the notification
 * client instead of the main session.
 */
package io.element.android.libraries.push.impl.psst

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.element.android.libraries.designsystem.utils.CommonDrawables
import java.util.Locale

object PsstVerificationWake {
    const val MARKER_ROOM_PREFIX = "!psst-verification:"
    const val CHANNEL_ID = "PSST_VERIFICATION"
    private const val NOTIFICATION_ID = 7_431
    private const val TIMEOUT_MS = 10 * 60 * 1000L // requests are valid 10 minutes (Matrix spec)

    fun isWake(roomId: String) = roomId.startsWith(MARKER_ROOM_PREFIX)

    private fun sk() = Locale.getDefault().language in setOf("sk", "cs")

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                if (sk()) "Overenie zariadenia" else "Device verification",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = if (sk()) "Iné vaše zariadenie žiada o overenie" else "Another of your devices asks to be verified"
                enableVibration(true)
            }
        )
    }

    fun show(context: Context) {
        val app = context.applicationContext
        ensureChannel(app)
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        } ?: return
        val pi = PendingIntent.getActivity(app, NOTIFICATION_ID, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = if (sk()) "Žiadosť o overenie" else "Verification request"
        val text = if (sk()) {
            "Iné vaše zariadenie žiada o overenie. Otvorte psst a potvrďte ho."
        } else {
            "Another of your devices wants to be verified. Open psst to confirm it."
        }
        val n = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(CommonDrawables.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, n) }
            .onSuccess { PsstNotifLog.posted(app, "verification", NOTIFICATION_ID, n) }
            .onFailure { PsstNotifLog.add(app, "NOT POSTED verification: ${it::class.simpleName}") }
    }

    /** Called when psst comes to the foreground: the request is then shown in-app. */
    fun dismiss(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
