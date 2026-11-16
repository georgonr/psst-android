/*
 * PSST: new-version check for the sideloaded APK (DECISIONS D63).
 *
 * psst for Android is installed from https://app.macet.eu/download (no store), so nothing tells the user that a newer
 * build exists. At most once per 12 h (from MainActivity.onResume) this reads the public
 * https://app.macet.eu/download/latest.json (the same file the download page and the desktop check use) and, when
 * android.build is newer than the installed build, offers to open the download page. The same build is offered again
 * only after 3 days. No user data is sent; the request is a plain GET without cookies.
 */
package io.element.android.features.call.impl.psst

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

object PsstUpdateCheck {
    private const val LATEST_URL = "https://app.macet.eu/download/latest.json"
    private const val PAGE_URL = "https://app.macet.eu/download/"
    private const val PREFS = "psst_update_check"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_OFFERED_BUILD = "offered_build"
    private const val KEY_OFFERED_AT = "offered_at"
    private const val CHECK_INTERVAL_MS = 12L * 3600 * 1000
    private const val REMIND_AFTER_MS = 3L * 24 * 3600 * 1000

    @Volatile private var running = false

    private fun sk(): Boolean = Locale.getDefault().language in setOf("sk", "cs")

    /** Installed psst build number: versionCode = (21 000 000 + build) × 10 + ABI (BUILD.md "Verzovanie"). */
    fun installedBuild(context: Context): Long {
        val code = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        return code / 10 - 21_000_000
    }

    fun maybeCheck(activity: Activity, force: Boolean = false) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (running || (!force && now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MS)) return
        running = true
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        val installed = installedBuild(activity)
        thread(name = "psst-update-check", isDaemon = true) {
            try {
                val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 10_000; useCaches = false
                }
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val available = JSONObject(json).optJSONObject("android")?.optLong("build", -1) ?: -1
                Timber.i("psst update check: installed b%d, available b%d", installed, available)
                if (installed <= 0 || available <= installed) return@thread
                val offeredBuild = prefs.getLong(KEY_OFFERED_BUILD, -1)
                if (!force && offeredBuild == available && now - prefs.getLong(KEY_OFFERED_AT, 0) < REMIND_AFTER_MS) return@thread
                Handler(Looper.getMainLooper()).post { offer(activity, installed, available) }
            } catch (e: Exception) {
                Timber.w(e, "psst update check failed")
            } finally {
                running = false
            }
        }
    }

    private fun offer(activity: Activity, installed: Long, available: Long) {
        if (activity.isFinishing || activity.isDestroyed) return
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_OFFERED_BUILD, available).putLong(KEY_OFFERED_AT, System.currentTimeMillis()).apply()
        AlertDialog.Builder(activity)
            .setTitle(if (sk()) "Nová verzia psst" else "New psst version")
            .setMessage(
                if (sk()) {
                    "K dispozícii je psst build $available (máte b$installed). Stiahnite si ho zo stránky app.macet.eu/download " +
                        "a nainštalujte cez pôvodnú appku — správy aj prihlásenie zostanú."
                } else {
                    "psst build $available is available (you have b$installed). Download it from app.macet.eu/download " +
                        "and install it over the current app — your messages and sign-in are kept."
                }
            )
            .setPositiveButton(if (sk()) "Stiahnuť" else "Download") { _, _ ->
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, PAGE_URL.toUri())) }
            }
            .setNegativeButton(if (sk()) "Neskôr" else "Later", null)
            .show()
    }
}
