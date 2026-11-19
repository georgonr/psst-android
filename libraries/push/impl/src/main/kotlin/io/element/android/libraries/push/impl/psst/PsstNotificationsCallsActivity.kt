/*
 * PSST (D72): Settings → "Upozornenia a hovory" / "Notifications & calls".
 *
 * One row per thing that messages and calls need. Every switch shows the REAL system state (re-read in onResume and
 * after every permission result), never a stored value — except "wake the screen", which is our own option combined
 * with the system permission. Turning a row on requests the runtime permission or opens exactly the system page;
 * "Zapnúť všetko potrebné" (and several taps) queue the rows and ask for them one after another, continuing when the
 * user comes back. Opened automatically (with missing rows highlighted) on first run and after every update when
 * something required is missing (PsstCallReadiness, MainActivity.onResume).
 */
package io.element.android.libraries.push.impl.psst

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import io.element.android.libraries.designsystem.utils.CommonDrawables
import java.util.Locale

class PsstNotificationsCallsActivity : Activity() {
    enum class Row(val required: Boolean) {
        NOTIFICATIONS(true), POPUP(true), LOCK_SCREEN(true), FULL_SCREEN(true), BATTERY(true),
        MESSAGE_SOUND(false), CALL_RINGING(false), WAKE_SCREEN(false), MICROPHONE(false), CAMERA(false),
    }

    private val sk = Locale.getDefault().language in setOf("sk", "cs")
    private fun t(skText: String, en: String) = if (sk) skText else en

    private val switches = mutableMapOf<Row, Switch>()
    private val rowViews = mutableMapOf<Row, LinearLayout>()
    private val badges = mutableMapOf<Row, TextView>()
    private val queue = ArrayDeque<Row>()
    private var inFlight: Row? = null
    private var highlight = false
    private var autoStart = false
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PsstNotifLog.appContext = applicationContext
        highlight = intent.getBooleanExtra(EXTRA_HIGHLIGHT, false)
        // opened automatically after install/update (D74): ask for the missing required items right away
        autoStart = highlight && savedInstanceState == null
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        column.addView(TextView(this).apply { text = t("Upozornenia a hovory", "Notifications & calls"); textSize = 22f })
        summary = TextView(this).apply { setPadding(0, pad / 2, 0, pad / 2) }
        column.addView(summary)
        fun button(label: String, onClick: () -> Unit) = column.addView(
            Button(this).apply { text = label; isAllCaps = false; setOnClickListener { onClick() } },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        button(t("Zapnúť všetko potrebné", "Turn on everything needed")) {
            Row.entries.filter { it.required && !state(it) }.forEach { enqueue(it) }
            if (queue.isEmpty() && inFlight == null) Toast.makeText(this, t("Všetko potrebné je zapnuté", "Everything needed is on"), Toast.LENGTH_SHORT).show()
        }
        for (row in Row.entries) column.addView(buildRow(row, pad))
        button(t("Poslať testovacie upozornenie", "Send test notification")) {
            PsstNotificationDiagnosticsActivity.postTest(applicationContext, chatLike = true)
            Toast.makeText(this, t("Upozornenie odoslané", "Notification sent"), Toast.LENGTH_SHORT).show()
        }
        button(t("Otestovať zvonenie", "Test ringing")) { testRinging() }
        button(t("Diagnostika upozornení", "Notification diagnostics")) {
            startActivity(Intent(this, PsstNotificationDiagnosticsActivity::class.java))
        }
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
        inFlight = null
        refresh()
        if (autoStart) {
            autoStart = false
            Row.entries.filter { it.required && !state(it) }.forEach { queue.addLast(it) }
            summary.postDelayed({ processNext() }, 1_200)
            return
        }
        processNext()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        inFlight = null
        refresh()
        processNext()
    }

    // ------------------------------------------------------------------------------------------------------------ UI

    private fun buildRow(row: Row, pad: Int): View {
        val (title, desc) = texts(row)
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = title; textSize = 16f; setTypeface(typeface, Typeface.BOLD) })
            addView(TextView(context).apply { text = desc; textSize = 13f })
            val badge = TextView(context).apply {
                text = t("Chýba — zapnite", "Missing — turn on"); textSize = 12f; setTextColor(Color.rgb(0xB4, 0x3C, 0x00)); visibility = View.GONE
            }
            badges[row] = badge
            addView(badge)
        }
        val sw = Switch(this).apply {
            isClickable = false // the row handles the click; the switch only shows the real state
            isFocusable = false
            contentDescription = title
        }
        switches[row] = sw
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(sw)
            isClickable = true
            setOnClickListener { onRowClick(row) }
            rowViews[row] = this
            tag = "psst-row-${row.name}"
        }
    }

    private fun refresh() {
        var missingRequired = 0
        for (row in Row.entries) {
            val on = state(row)
            switches[row]?.isChecked = on
            badges[row]?.visibility = if (!on && row.required) View.VISIBLE else View.GONE
            rowViews[row]?.setBackgroundColor(if (highlight && !on && row.required) Color.rgb(0xFF, 0xF1, 0xD6) else Color.TRANSPARENT)
            if (!on && row.required) missingRequired++
        }
        summary.text = if (missingRequired == 0) {
            t("Všetko potrebné pre správy a hovory je zapnuté. Prepínače ukazujú skutočný stav telefónu.",
                "Everything messages and calls need is on. The switches show the phone's real state.")
        } else {
            t("Chýba $missingRequired z 5 potrebných nastavení — ťuknite na riadok alebo „Zapnúť všetko potrebné“.",
                "$missingRequired of 5 required settings are missing — tap a row or “Turn on everything needed”.")
        }
    }

    private fun onRowClick(row: Row) {
        if (state(row)) {
            turnOff(row)
        } else {
            enqueue(row)
        }
    }

    private fun enqueue(row: Row) {
        if (row != inFlight && row !in queue) queue.addLast(row)
        processNext()
    }

    private fun processNext() {
        if (inFlight != null) return
        while (queue.isNotEmpty()) {
            val row = queue.removeFirst()
            if (state(row)) continue
            inFlight = row
            Toast.makeText(this, t("Zapnite: ", "Turn on: ") + texts(row).first + t(" — potom sa vráťte späť", " — then come back"), Toast.LENGTH_LONG).show()
            if (!turnOn(row)) {
                inFlight = null
                refresh()
                continue
            }
            return
        }
    }

    // --------------------------------------------------------------------------------------------- state & actions

    private val nm get() = getSystemService(NotificationManager::class.java)

    private fun channel(prefix: String): NotificationChannel? =
        if (Build.VERSION.SDK_INT >= 26) nm.notificationChannels.filter { it.id.startsWith(prefix) }.maxByOrNull { it.id } else null

    private fun messageChannel() = channel(MESSAGE_CHANNEL_PREFIX)
    private fun ringingChannel() = channel(RINGING_CHANNEL_PREFIX)

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun state(row: Row): Boolean = when (row) {
        Row.NOTIFICATIONS -> NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS))
        Row.POPUP -> state(Row.NOTIFICATIONS) && (messageChannel()?.importance ?: NotificationManager.IMPORTANCE_HIGH) >= NotificationManager.IMPORTANCE_HIGH
        Row.LOCK_SCREEN -> {
            val ch = messageChannel()
            val global = runCatching { Settings.Secure.getInt(contentResolver, "lock_screen_show_notifications", 1) }.getOrDefault(1)
            state(Row.NOTIFICATIONS) && ch?.lockscreenVisibility != Notification.VISIBILITY_SECRET && global != 0
        }
        Row.FULL_SCREEN -> Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
        Row.BATTERY -> getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        Row.MESSAGE_SOUND -> state(Row.NOTIFICATIONS) && messageChannel().let { it == null || it.sound != null || it.shouldVibrate() } &&
            (messageChannel()?.importance ?: NotificationManager.IMPORTANCE_DEFAULT) >= NotificationManager.IMPORTANCE_DEFAULT
        Row.CALL_RINGING -> state(Row.NOTIFICATIONS) && ringingChannel().let {
            it == null || (it.importance >= NotificationManager.IMPORTANCE_HIGH && it.sound != null)
        }
        Row.WAKE_SCREEN -> PsstScreenWake.isActive(this)
        Row.MICROPHONE -> granted(Manifest.permission.RECORD_AUDIO)
        Row.CAMERA -> granted(Manifest.permission.CAMERA)
    }

    private fun appNotificationSettings() =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

    private fun channelSettings(ch: NotificationChannel?): Intent =
        if (ch != null && Build.VERSION.SDK_INT >= 26 && NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, ch.id)
        } else {
            appNotificationSettings()
        }

    private fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

    /** Start the first intent that resolves (no NEW_TASK → Back returns here). */
    private fun open(vararg intents: Intent): Boolean {
        for (i in intents) {
            if (runCatching { startActivity(i) }.isSuccess) return true
        }
        return false
    }

    private fun requestOrSettings(permission: String, prefsKey: String): Boolean {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val askedBefore = prefs.getBoolean(prefsKey, false)
        // After a permanent denial Android shows nothing anymore → open the app page instead.
        return if (!askedBefore || shouldShowRequestPermissionRationale(permission)) {
            prefs.edit().putBoolean(prefsKey, true).apply()
            requestPermissions(arrayOf(permission), 1)
            true
        } else {
            open(appDetails())
        }
    }

    /**
     * PSST (D87): no REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (Google Play allows it only for chat apps that cannot use FCM),
     * so psst cannot ask for the exemption directly. Explain the two taps, then open the app's own system page
     * (App info), where Battery / App battery usage → Unrestricted lives on Pixel, Samsung and most other phones.
     */
    private fun showBatteryHelp(): Boolean {
        AlertDialog.Builder(this)
            .setTitle(t("Bez obmedzenia batérie", "No battery restriction"))
            .setMessage(
                t(
                    "Otvorí sa stránka „Informácie o aplikácii“ pre psst.\n\n" +
                        "1. Ťuknite na Batéria (alebo Využitie batérie aplikáciou).\n" +
                        "2. Zvoľte Bez obmedzení.\n" +
                        "3. Vráťte sa späť do psst.",
                    "The App info page for psst opens.\n\n" +
                        "1. Tap Battery (or App battery usage).\n" +
                        "2. Choose Unrestricted.\n" +
                        "3. Come back to psst.",
                )
            )
            .setPositiveButton(t("Otvoriť nastavenia", "Open settings"), null)
            .setNegativeButton(t("Teraz nie", "Not now")) { _, _ -> }
            .setOnDismissListener {
                // "Not now" / Back: drop this row and continue with the rest of the queue
                if (inFlight == Row.BATTERY && !batteryOpening) {
                    inFlight = null
                    refresh()
                    processNext()
                }
                batteryOpening = false
            }
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        batteryOpening = true
                        dialog.dismiss()
                        if (!open(appDetails(), Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
                            inFlight = null
                            refresh()
                        }
                    }
                }
            }
            .show()
        return true
    }

    private var batteryOpening = false

    /** Returns true if something was started (permission dialog or settings screen). */
    private fun turnOn(row: Row): Boolean = when (row) {
        Row.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            requestOrSettings(Manifest.permission.POST_NOTIFICATIONS, "asked_post_notifications") || open(appNotificationSettings())
        } else {
            open(appNotificationSettings(), appDetails())
        }
        Row.POPUP, Row.MESSAGE_SOUND -> open(channelSettings(messageChannel()), appNotificationSettings())
        Row.LOCK_SCREEN -> if (messageChannel()?.lockscreenVisibility == Notification.VISIBILITY_SECRET || !state(Row.NOTIFICATIONS)) {
            open(channelSettings(messageChannel()), appNotificationSettings())
        } else {
            open(Intent("android.settings.NOTIFICATION_SETTINGS"), appNotificationSettings())
        }
        Row.FULL_SCREEN -> Build.VERSION.SDK_INT >= 34 &&
            open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri()), appNotificationSettings())
        Row.BATTERY -> showBatteryHelp()
        Row.CALL_RINGING -> open(channelSettings(ringingChannel()), appNotificationSettings())
        Row.WAKE_SCREEN -> {
            PsstScreenWake.setEnabled(this, true)
            if (PsstScreenWake.permissionGranted(this) == false) {
                Toast.makeText(this, t("Zapnite pre psst „Zapnúť obrazovku“", "Allow psst to “Turn screen on”"), Toast.LENGTH_LONG).show()
                open(PsstScreenWake.permissionSettingsIntent(this), PsstScreenWake.permissionListIntent(), appDetails())
            } else {
                false // nothing to open; refresh shows it on
            }
        }
        Row.MICROPHONE -> requestOrSettings(Manifest.permission.RECORD_AUDIO, "asked_record_audio")
        Row.CAMERA -> requestOrSettings(Manifest.permission.CAMERA, "asked_camera")
    }

    private fun turnOff(row: Row) {
        when (row) {
            Row.WAKE_SCREEN -> {
                PsstScreenWake.setEnabled(this, false)
                refresh()
            }
            Row.MICROPHONE, Row.CAMERA -> open(appDetails())
            Row.BATTERY -> open(appDetails(), Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            Row.FULL_SCREEN -> open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri()), appNotificationSettings())
            Row.CALL_RINGING -> open(channelSettings(ringingChannel()))
            Row.POPUP, Row.MESSAGE_SOUND, Row.LOCK_SCREEN -> open(channelSettings(messageChannel()))
            Row.NOTIFICATIONS -> open(appNotificationSettings())
        }
    }

    // ------------------------------------------------------------------------------------------------------- tests

    private fun testRinging() {
        val ch = ringingChannel()
        if (ch == null || !state(Row.NOTIFICATIONS)) {
            Toast.makeText(this, t("Najprv povoľte upozornenia", "Allow notifications first"), Toast.LENGTH_LONG).show()
            return
        }
        val n = NotificationCompat.Builder(this, ch.id)
            .setSmallIcon(CommonDrawables.ic_notification)
            .setContentTitle(t("Test zvonenia psst", "psst ringing test"))
            .setContentText(t("Takto zvoní prichádzajúci hovor (8 s)", "This is how an incoming call rings (8 s)"))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setTimeoutAfter(8_000)
            .setAutoCancel(true)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(this).notify("psst-test", 7_510, n)
        PsstNotifLog.posted(this, "psst-test-ring", 7_510, n)
    }

    private fun texts(row: Row): Pair<String, String> = when (row) {
        Row.NOTIFICATIONS -> t("Upozornenia povolené", "Notifications allowed") to
            t("psst smie zobrazovať upozornenia na správy a hovory.", "psst may show notifications for messages and calls.")
        Row.POPUP -> t("Vyskakovacie okno", "Pop-up") to
            t("Nová správa sa ukáže navrchu obrazovky, nie len v lište.", "New messages pop up at the top of the screen, not only in the shade.")
        Row.LOCK_SCREEN -> t("Na zamknutej obrazovke", "On the lock screen") to
            t("Správy sa zobrazia aj na zamknutom telefóne.", "Messages are shown on the locked phone too.")
        Row.FULL_SCREEN -> t("Hovory na celú obrazovku", "Full-screen calls") to
            t("Prichádzajúci hovor rozsvieti displej s tlačidlami Prijať a Odmietnuť.", "An incoming call lights up the screen with Accept and Decline.")
        Row.BATTERY -> t("Bez obmedzenia batérie", "No battery restriction") to
            t("Telefón psst neuspí, správy a hovory prídu hneď.", "The phone won't put psst to sleep; messages and calls arrive immediately.")
        Row.MESSAGE_SOUND -> t("Zvuk a vibrácie správ", "Message sound and vibration") to
            t("Nová správa zaznie alebo zavibruje.", "New messages make a sound or vibrate.")
        Row.CALL_RINGING -> t("Zvonenie hovorov", "Call ringing") to
            t("Prichádzajúci hovor zvoní vyzváňacím tónom.", "Incoming calls ring with the ringtone.")
        Row.WAKE_SCREEN -> t("Rozsvietiť obrazovku pri novej správe", "Wake the screen for new messages") to
            t("Displej sa na chvíľu rozsvieti (nie v režime Nerušiť). Vyžaduje povolenie „Zapnúť obrazovku“.",
                "The display turns on briefly (not in Do Not Disturb). Needs the “Turn screen on” permission.")
        Row.MICROPHONE -> t("Mikrofón", "Microphone") to t("Pre hovory a hlasové správy.", "For calls and voice messages.")
        Row.CAMERA -> t("Kamera", "Camera") to t("Pre videohovory a fotky.", "For video calls and photos.")
    }

    companion object {
        const val EXTRA_HIGHLIGHT = "psst_highlight_missing"
        private const val PREFS = "psst_notification_prefs"
        private const val MESSAGE_CHANNEL_PREFIX = "PSST_MESSAGES"
        private const val RINGING_CHANNEL_PREFIX = "RINGING_CALL_NOTIFICATION_CHANNEL_ID"
    }
}
