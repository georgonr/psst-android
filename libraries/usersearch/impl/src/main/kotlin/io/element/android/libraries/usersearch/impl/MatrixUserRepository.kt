/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.usersearch.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.MatrixPatterns
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.usersearch.api.UserListDataSource
import io.element.android.libraries.usersearch.api.UserRepository
import io.element.android.libraries.usersearch.api.UserSearchResult
import io.element.android.libraries.usersearch.api.UserSearchResultState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@ContributesBinding(SessionScope::class)
class MatrixUserRepository(
    private val client: MatrixClient,
    private val dataSource: UserListDataSource
) : UserRepository {
    override fun search(query: String): Flow<UserSearchResultState> = flow {
        // PSST (D88): the server's user directory is off (no listing, no partial-name search). A bare account name
        // ("jana" or "@jana") is looked up as the exact user @jana:<our server>; shown only if that profile exists.
        val exactUserId = psstExactUserId(query)
        val shouldQueryProfile = MatrixPatterns.isUserId(query) && !client.isMe(UserId(query))
        val shouldFetchSearchResults = query.length >= MINIMUM_SEARCH_LENGTH
        // If the search term is a MXID that's not ours, we'll show a 'fake' result for that user, then update it when we get search results.
        val fakeSearchResult = if (shouldQueryProfile) {
            UserSearchResult(MatrixUser(UserId(query)))
        } else {
            null
        }
        if (shouldQueryProfile || shouldFetchSearchResults) {
            emit(UserSearchResultState(isSearching = shouldFetchSearchResults, results = listOfNotNull(fakeSearchResult)))
        }
        if (shouldFetchSearchResults) {
            val results = fetchSearchResults(query, shouldQueryProfile, exactUserId)
            emit(results)
        } else if (exactUserId != null) {
            delay(DEBOUNCE_TIME_MILLIS)
            dataSource.getProfile(exactUserId)?.let {
                emit(UserSearchResultState(results = listOf(UserSearchResult(it)), isSearching = false))
            }
        }
    }

    private fun psstExactUserId(query: String): UserId? {
        val localpart = query.trim().removePrefix("@").lowercase()
        if (localpart.isEmpty() || !PSST_LOCALPART.matches(localpart)) return null
        val serverName = client.sessionId.value.substringAfter(':', "")
        if (serverName.isEmpty()) return null
        val candidate = "@$localpart:$serverName"
        if (!MatrixPatterns.isUserId(candidate)) return null
        return UserId(candidate).takeIf { !client.isMe(it) }
    }

    private suspend fun fetchSearchResults(query: String, shouldQueryProfile: Boolean, exactUserId: UserId?): UserSearchResultState {
        // Debounce
        delay(DEBOUNCE_TIME_MILLIS)
        val results = dataSource
            .search(query, MAXIMUM_SEARCH_RESULTS)
            .filter { !client.isMe(it.userId) }
            .map { UserSearchResult(it) }
            .toMutableList()

        // If the query is another user's MXID and the result doesn't contain that user ID, query the profile information explicitly
        if (shouldQueryProfile && results.none { it.matrixUser.userId.value == query }) {
            results.add(
                0,
                dataSource.getProfile(UserId(query))
                    ?.let { UserSearchResult(it) }
                    ?: UserSearchResult(MatrixUser(UserId(query)), isUnresolved = true)
            )
        }

        // PSST (D88): exact account name typed without the server part
        if (exactUserId != null && results.none { it.matrixUser.userId == exactUserId }) {
            dataSource.getProfile(exactUserId)?.let { results.add(0, UserSearchResult(it)) }
        }

        return UserSearchResultState(results = results, isSearching = false)
    }

    companion object {
        private const val DEBOUNCE_TIME_MILLIS = 250L
        private const val MINIMUM_SEARCH_LENGTH = 3
        private const val MAXIMUM_SEARCH_RESULTS = 10L
        private val PSST_LOCALPART = Regex("[a-z0-9._=/+-]+")
    }
}
