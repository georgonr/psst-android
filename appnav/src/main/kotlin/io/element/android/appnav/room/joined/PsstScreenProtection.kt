/*
 * PSST (D96): screen protection of a room (eu.macet.psst.screen_protection). While such a room is open — its timeline,
 * details, members, media viewer: everything under JoinedRoomLoadedFlowNode — the activity window has FLAG_SECURE
 * (no screenshots, no screen recording, blank in recents). On Android 14+ a screenshot attempt is also detected
 * (Activity.ScreenCaptureCallback) and the others are told with the psst screenshot notice, at most once per 10 s.
 */
package io.element.android.appnav.room.joined

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import io.element.android.libraries.androidutils.ui.PsstSecureWindow
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.psst.PsstRoomSettingsService
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

@Composable
internal fun PsstScreenProtectionEffect(
    activity: Activity,
    roomId: RoomId,
    matrixClient: MatrixClient,
    service: PsstRoomSettingsService,
    scope: CoroutineScope,
) {
    DisposableEffect(activity, roomId) {
        val reason = "room:${roomId.value}"
        PsstSecureWindow.set(activity.window, reason, secure = true)
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Activity.ScreenCaptureCallback {
                PsstScreenshotNotifier.onScreenshot(activity, roomId, matrixClient, service, scope)
            }.also { activity.registerScreenCaptureCallback(activity.mainExecutor, it) }
        } else {
            null
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && callback != null) {
                activity.unregisterScreenCaptureCallback(callback)
            }
            PsstSecureWindow.set(activity.window, reason, secure = false)
        }
    }
}

internal object PsstScreenshotNotifier {
    private const val MIN_INTERVAL_MS = 10_000L
    private val lastSent = ConcurrentHashMap<RoomId, Long>()

    /** True when a notice may be sent now (at most one per [MIN_INTERVAL_MS] per room); records the attempt. */
    fun tryAcquire(roomId: RoomId, nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        val previous = lastSent[roomId]
        if (previous != null && nowMs - previous < MIN_INTERVAL_MS) return false
        lastSent[roomId] = nowMs
        return true
    }

    fun onScreenshot(
        activity: Activity,
        roomId: RoomId,
        matrixClient: MatrixClient,
        service: PsstRoomSettingsService,
        scope: CoroutineScope,
    ) {
        // Only while the protection is on (the callback is unregistered when it is turned off, this is a safety net).
        if (!service.cachedSettings(roomId).screenProtection) return
        if (!tryAcquire(roomId)) return
        val profile = matrixClient.userProfile.value
        val name = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.userId.value
        // In the sender's language, as the spec asks.
        val body = activity.getString(CommonStrings.psst_screenshot_notice, name)
        scope.launch {
            service.sendScreenshotNotice(roomId, body)
                .onFailure { Timber.w(it, "PSST: screenshot notice not sent") }
        }
    }
}
