/*
 * PSST (D67): Settings → Notifications → "Diagnostika upozornení (psst)".
 * Shows what the phone really does with psst notifications — system switches, channels (importance as the user/OEM
 * left it), active notifications with their effective (conversation) channel and the log of pushes and
 * posted notifications — offers two self-tests (posted after 10 s so the phone can be locked) and saves everything
 * to one text file (Downloads) + share sheet with one button. No message text or names are recorded.
 */
package io.element.android.libraries.push.impl.psst

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import io.element.android.libraries.designsystem.utils.CommonDrawables
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PsstNotificationDiagnosticsActivity : Activity() {
    private lateinit var output: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val sk = Locale.getDefault().language in setOf("sk", "cs")
    private fun t(skText: String, en: String) = if (sk) skText else en

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PsstNotifLog.appContext = applicationContext
        val pad = (16 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        column.addView(TextView(this).apply {
            text = t("Diagnostika upozornení", "Notification diagnostics")
            textSize = 22f
        })
        column.addView(TextView(this).apply {
            text = t(
                "1) Stlačte test, do 10 s zamknite telefón (alebo nechajte odomknutý) a sledujte, či príde vyskakovacie okno, " +
                    "zvuk a záznam na zamknutej obrazovke. 2) „Uložiť a odoslať“ uloží správu do Stiahnuté a ponúkne odoslanie.",
                "1) Tap a test, lock the phone within 10 s (or keep it unlocked) and watch for a pop-up, sound and the lock " +
                    "screen. 2) “Save and send” stores the report in Downloads and opens the share sheet.",
            )
            setPadding(0, pad / 2, 0, pad / 2)
        })
        fun button(label: String, onClick: () -> Unit) = column.addView(
            Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { onClick() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        button(t("Test: správa ako z chatu (o 10 s)", "Test: chat-like message (in 10 s)")) { scheduleTest(chatLike = true) }
        button(t("Test: jednoduché upozornenie (o 10 s)", "Test: plain notification (in 10 s)")) { scheduleTest(chatLike = false) }
        button(t("Nastavenia upozornení psst", "psst notification settings")) {
            runCatching {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
        button(t("Uložiť a odoslať", "Save and send")) { export() }
        button(t("Obnoviť", "Refresh")) { refresh() }
        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(0, pad, 0, 0)
        }
        column.addView(output)
        val scroll = ScrollView(this).apply { addView(column) }
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        output.text = buildReport()
    }

    // ---------------------------------------------------------------------------------------------------------- tests

    private fun scheduleTest(chatLike: Boolean) {
        PsstNotifLog.add(this, "TEST scheduled chatLike=$chatLike ${PsstNotifLog.deviceState(this)}")
        Toast.makeText(this, t("Test príde o 10 sekúnd", "Test arrives in 10 seconds"), Toast.LENGTH_SHORT).show()
        val app = applicationContext
        handler.postDelayed({ postTest(app, chatLike) }, 10_000)
    }

    // --------------------------------------------------------------------------------------------------------- report

    private fun buildReport(): String = buildString {
        val nm = getSystemService(NotificationManager::class.java)
        appendLine("psst ${packageManager.getPackageInfo(packageName, 0).versionName} · ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
        appendLine("device ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) Android ${Build.VERSION.RELEASE} SDK ${Build.VERSION.SDK_INT} build ${Build.DISPLAY}")
        oneUi()?.let { appendLine("One UI $it") }
        appendLine()
        appendLine("== system")
        appendLine("notifications enabled: ${nm.areNotificationsEnabled()}")
        if (Build.VERSION.SDK_INT >= 33) {
            appendLine("POST_NOTIFICATIONS granted: ${checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED}")
        }
        if (Build.VERSION.SDK_INT >= 34) appendLine("full-screen intents allowed: ${nm.canUseFullScreenIntent()}")
        appendLine("interruption filter (1=all): ${nm.currentInterruptionFilter}")
        if (Build.VERSION.SDK_INT >= 28) appendLine("notifications paused (app suspended): ${nm.areNotificationsPaused()}")
        appendLine("state now: ${PsstNotifLog.deviceState(this@PsstNotificationDiagnosticsActivity)}")
        val pm = getSystemService(PowerManager::class.java)
        appendLine("battery unrestricted: ${pm.isIgnoringBatteryOptimizations(packageName)}")
        if (Build.VERSION.SDK_INT >= 28) {
            appendLine("background restricted: ${getSystemService(ActivityManager::class.java).isBackgroundRestricted}")
            appendLine("standby bucket: ${getSystemService(UsageStatsManager::class.java).appStandbyBucket}")
        }
        settingsMatching().forEach { appendLine(it) }
        appendLine()
        appendLine("== channels")
        if (Build.VERSION.SDK_INT >= 26) {
            nm.notificationChannelGroups.forEach { g ->
                if (Build.VERSION.SDK_INT >= 28) appendLine("group ${g.id} blocked=${g.isBlocked}")
            }
            nm.notificationChannels.sortedBy { it.id }.forEach { appendLine(describe(it)) }
        }
        appendLine()
        appendLine("== active notifications")
        nm.activeNotifications.forEach { sbn ->
            val n = sbn.notification
            // PSST (D83): no notification listener any more → the effective channel is read directly: a conversation
            // can have its own channel (API 30+) whose importance/sound the user or One UI changed separately
            val effective = if (Build.VERSION.SDK_INT >= 30 && n.channelId != null && n.shortcutId != null) {
                runCatching { nm.getNotificationChannel(n.channelId, n.shortcutId) }.getOrNull()
            } else {
                null
            }
            appendLine(
                "id=${sbn.id} tag=${PsstNotifLog.short(sbn.tag)} ch=${if (Build.VERSION.SDK_INT >= 26) n.channelId else "-"} " +
                    "summary=${n.flags and Notification.FLAG_GROUP_SUMMARY != 0} age=${(System.currentTimeMillis() - sbn.postTime) / 1000}s" +
                    (effective?.let { "\n    CHANNEL ${describe(it)}" } ?: "")
            )
        }
        appendLine()
        appendLine("== log (newest last)")
        val f = PsstNotifLog.file(this@PsstNotificationDiagnosticsActivity)
        if (f.exists()) append(f.readText()) else appendLine("(empty)")
    }

    private fun describe(c: NotificationChannel): String {
        if (Build.VERSION.SDK_INT < 26) return ""
        val sb = StringBuilder("${c.id} \"${c.name}\" imp=${c.importance} lockVis=${c.lockscreenVisibility} ")
        sb.append("sound=${c.sound != null} vib=${c.shouldVibrate()} badge=${c.canShowBadge()} dnd=${c.canBypassDnd()}")
        if (Build.VERSION.SDK_INT >= 29) sb.append(" userSetImp=${c.hasUserSetImportance()}")
        if (Build.VERSION.SDK_INT >= 30) {
            c.parentChannelId?.let { sb.append(" parent=$it conv=${PsstNotifLog.short(c.conversationId)}") }
            sb.append(" demoted=${c.isDemoted} important=${c.isImportantConversation}")
        }
        return sb.toString()
    }

    private fun oneUi(): String? = runCatching {
        val v = Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null)
        val x = v - 90000
        "${x / 10000}.${(x % 10000) / 100}"
    }.getOrNull()

    /** System/Secure/Global settings whose names look notification-related (Samsung keeps pop-up/lock screen style here). */
    private fun settingsMatching(): List<String> {
        val re = Regex("notif|heads_up|popup|pop_up|lock_screen|lockscreen|zen|edge_lighting|brief", RegexOption.IGNORE_CASE)
        val out = mutableListOf<String>()
        for ((name, uri) in listOf("global" to Settings.Global.CONTENT_URI, "secure" to Settings.Secure.CONTENT_URI, "system" to Settings.System.CONTENT_URI)) {
            runCatching {
                contentResolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val k = c.getString(0) ?: continue
                        if (re.containsMatchIn(k)) out += "setting $name.$k=${c.getString(1)?.take(80)}"
                    }
                }
            }.onFailure { out += "setting $name: not readable (${it::class.simpleName})" }
        }
        // explicit keys (query may be filtered on newer Android)
        for (k in listOf("heads_up_notifications_enabled")) {
            runCatching { out += "get global.$k=${Settings.Global.getString(contentResolver, k)}" }
        }
        for (k in listOf("lock_screen_show_notifications", "lock_screen_allow_private_notifications", "lock_screen_show_silent_notifications", "notification_history_enabled")) {
            runCatching { out += "get secure.$k=${Settings.Secure.getString(contentResolver, k)}" }
        }
        return out.distinct()
    }

    private fun export() {
        val report = buildReport()
        val name = "psst-diagnostika-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt"
        val saved = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
                contentResolver.openOutputStream(uri)!!.use { it.write(report.toByteArray()) }
                true
            } else {
                false
            }
        }.getOrDefault(false)
        Toast.makeText(
            this,
            if (saved) t("Uložené do Stiahnuté/$name", "Saved to Downloads/$name") else t("Súbor sa nepodarilo uložiť", "Could not save the file"),
            Toast.LENGTH_LONG,
        ).show()
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, name)
            putExtra(Intent.EXTRA_TEXT, report)
        }
        runCatching { startActivity(Intent.createChooser(share, t("Odoslať diagnostiku", "Send diagnostics"))) }
    }

    companion object {
        private const val GROUP = "psst-test"

        private fun messagesChannelId(nm: NotificationManager): String? =
            if (Build.VERSION.SDK_INT >= 26) nm.notificationChannels.map { it.id }.filter { it.startsWith("PSST_MESSAGES") }.maxOrNull() else "x"

        fun postTest(app: Context, chatLike: Boolean) {
            val nm = app.getSystemService(NotificationManager::class.java)
            val channel = messagesChannelId(nm)
            if (channel == null) {
                PsstNotifLog.add(app, "TEST failed: messages channel missing")
                return
            }
            val open = app.packageManager.getLaunchIntentForPackage(app.packageName)
            val pi = PendingIntent.getActivity(app, 7_500, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val now = System.currentTimeMillis()
            val body = (if (Locale.getDefault().language in setOf("sk", "cs")) "Testovacia správa psst — " else "psst test message — ") + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(now))
            val builder = NotificationCompat.Builder(app, channel)
                .setSmallIcon(CommonDrawables.ic_notification)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setWhen(now)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            val id: Int
            if (chatLike) {
                // same shape as a real message notification: MessagingStyle, own group with a silent summary
                val me = Person.Builder().setName(if (Locale.getDefault().language in setOf("sk", "cs")) "Ja" else "Me").build()
                val other = Person.Builder().setName("psst test").build()
                builder.setStyle(
                    NotificationCompat.MessagingStyle(me).setConversationTitle("psst test").addMessage(body, now, other)
                )
                    .setGroup(GROUP)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                id = 7_501
            } else {
                builder.setContentTitle("psst test").setContentText(body).setPriority(NotificationCompat.PRIORITY_HIGH)
                id = 7_502
            }
            if (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                PsstNotifLog.add(app, "TEST not posted: permission missing")
                return
            }
            val n = builder.build()
            NotificationManagerCompat.from(app).notify("psst-test", id, n)
            PsstNotifLog.posted(app, "psst-test", id, n)
            PsstScreenWake.maybeWake(app, n)
            if (chatLike) {
                val summary = NotificationCompat.Builder(app, channel)
                    .setSmallIcon(CommonDrawables.ic_notification)
                    .setGroup(GROUP)
                    .setGroupSummary(true)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .build()
                NotificationManagerCompat.from(app).notify("psst-test", 7_503, summary)
                PsstNotifLog.posted(app, "psst-test", 7_503, summary)
            }
        }
    }
}
