/*
 * PSST (D67): optional notification listener (the user enables it in the diagnostics screen). It only looks at
 * psst's own notifications and records how the SYSTEM ranked them — effective importance, effective channel
 * (a conversation may get its own channel), lock screen override, suppressed visual effects (pop-up blocked by
 * DND/system) — the data an app cannot otherwise see and that explains "sound but no pop-up".
 */
package io.element.android.libraries.push.impl.psst

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PsstNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (sbn.packageName != packageName) return
        PsstNotifLog.add(this, "RANK " + describe(sbn, rankingMap))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (sbn.packageName != packageName) return
        PsstNotifLog.add(this, "REMOVED id=${sbn.id} tag=${PsstNotifLog.short(sbn.tag)} reason=$reason")
    }

    companion object {
        @Volatile var instance: PsstNotificationListener? = null

        fun describe(sbn: StatusBarNotification, map: RankingMap): String {
            val r = Ranking()
            if (!map.getRanking(sbn.key, r)) return "id=${sbn.id} (no ranking)"
            val sb = StringBuilder("id=${sbn.id} tag=${PsstNotifLog.short(sbn.tag)} imp=${r.importance}")
            if (Build.VERSION.SDK_INT >= 26) sb.append(" ch=${r.channel?.id} chImp=${r.channel?.importance}")
            if (Build.VERSION.SDK_INT >= 30) sb.append(" conv=${r.isConversation}")
            sb.append(" lockVis=${r.lockscreenVisibilityOverride} suppressed=0x${Integer.toHexString(r.suppressedVisualEffects)}")
            sb.append(" ambient=${r.isAmbient} intercepted=${!r.matchesInterruptionFilter()}")
            if (Build.VERSION.SDK_INT >= 29) sb.append(" bubble=${r.canBubble()}")
            if (Build.VERSION.SDK_INT >= 26) sb.append(" suspended=${r.isSuspended}")
            sb.append(" group=${sbn.isGroup} summary=${sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0}")
            return sb.toString()
        }
    }

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
    }
}
