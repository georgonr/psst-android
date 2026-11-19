/*
 * Copyright 2026 PSST, derived from Element X Android.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.migration.impl.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import io.element.android.features.migration.impl.BuildConfig
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.workmanager.api.WorkManagerScheduler
import timber.log.Timber
import java.io.File

/**
 * PSST (DECISIONS.md D85): psst moved to a new server with a new server_name; no account, room or session was carried
 * over and the old server no longer exists. A session of any other server than [BuildConfig.PSST_SERVER_NAME] can never
 * sync again — restoring it would leave the app stuck on an unreachable server. Remove such sessions locally (no request
 * to the old server), so the app opens on the sign-in screen of the new server.
 */
@ContributesIntoSet(AppScope::class)
class AppMigration11(
    private val sessionStore: SessionStore,
    private val workManagerScheduler: WorkManagerScheduler,
) : AppMigration {
    override val order: Int = 11

    override suspend fun migrate(isFreshInstall: Boolean) {
        if (isFreshInstall) return
        for (session in sessionStore.getAllSessions()) {
            if (isPsstSession(session.userId)) continue
            Timber.w("PSST: removing the session of a retired server")
            workManagerScheduler.cancel(SessionId(session.userId))
            sessionStore.removeSession(session.userId)
            listOf(session.sessionPath, session.cachePath)
                .filter { it.isNotBlank() }
                .forEach { path -> runCatching { File(path).deleteRecursively() } }
        }
    }

    companion object {
        /** `@name:psst.pro` → true; a user ID of any other server → false. */
        fun isPsstSession(userId: String): Boolean = userId.substringAfter(':', "") == BuildConfig.PSST_SERVER_NAME
    }
}
