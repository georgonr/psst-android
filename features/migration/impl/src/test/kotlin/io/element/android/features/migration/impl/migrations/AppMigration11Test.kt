/*
 * Copyright 2026 PSST, derived from Element X Android.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.migration.impl.migrations

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.libraries.workmanager.api.WorkManagerRequestType
import io.element.android.libraries.workmanager.test.FakeWorkManagerScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppMigration11Test {
    private val retired = "@alice:chat.example.org"
    private val current = "@bob:psst.pro"

    @Test
    fun `fresh install does nothing`() = runTest {
        val store = InMemorySessionStore(initialList = listOf(aSessionData(sessionId = retired)))
        AppMigration11(store, FakeWorkManagerScheduler()).migrate(isFreshInstall = true)
        assertThat(store.getAllSessions().map { it.userId }).containsExactly(retired)
    }

    @Test
    fun `upgrade removes sessions of other servers and keeps psst pro sessions`() = runTest {
        val cancelled = mutableListOf<Pair<SessionId, WorkManagerRequestType?>>()
        val store = InMemorySessionStore(
            initialList = listOf(
                aSessionData(sessionId = retired, sessionPath = "", cachePath = ""),
                aSessionData(sessionId = current, sessionPath = "", cachePath = ""),
            )
        )
        AppMigration11(store, FakeWorkManagerScheduler(cancelLambda = { id, type -> cancelled += id to type }))
            .migrate(isFreshInstall = false)
        assertThat(store.getAllSessions().map { it.userId }).containsExactly(current)
        assertThat(cancelled).containsExactly(SessionId(retired) to null)
    }

    @Test
    fun `server part of the user id decides`() {
        assertThat(AppMigration11.isPsstSession("@a:psst.pro")).isTrue()
        assertThat(AppMigration11.isPsstSession("@a:chat.psst.pro")).isFalse()
        assertThat(AppMigration11.isPsstSession("@a:other.example")).isFalse()
        assertThat(AppMigration11.isPsstSession("no-colon")).isFalse()
    }
}
