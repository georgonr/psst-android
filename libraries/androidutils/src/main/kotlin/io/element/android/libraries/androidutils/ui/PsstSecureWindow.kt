/*
 * PSST (D96): FLAG_SECURE has several owners — the PIN lock (LockScreenService.handleSecureFlag, before Android 13)
 * and every open room with screen protection. Each owner adds or removes its reason; the flag is set while at least
 * one reason is there, so one owner never clears it under another.
 */
package io.element.android.libraries.androidutils.ui

import android.view.Window
import android.view.WindowManager
import java.util.WeakHashMap

object PsstSecureWindow {
    private val reasons = WeakHashMap<Window, MutableSet<String>>()

    @Synchronized
    fun set(window: Window, reason: String, secure: Boolean) {
        val windowReasons = reasons.getOrPut(window) { mutableSetOf() }
        if (secure) windowReasons += reason else windowReasons -= reason
        if (windowReasons.isNotEmpty()) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    @Synchronized
    fun isSecure(window: Window): Boolean = reasons[window].orEmpty().isNotEmpty()
}
