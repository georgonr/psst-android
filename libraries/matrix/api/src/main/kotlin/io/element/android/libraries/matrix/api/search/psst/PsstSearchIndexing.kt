/*
 * PSST (D80): keeps the local message search index filled while psst is on screen, and tells the search screens
 * whether older history is still being indexed.
 *
 * Upstream runs the history backfill only while the search screen is open, or in a background job that needs a
 * charger and Wi-Fi. psst also runs it (FOREGROUND strategy: paced, lowest priority after interactive requests)
 * while the app is visible, so the history is searchable without first opening search and waiting.
 */
package io.element.android.libraries.matrix.api.search.psst

import kotlinx.coroutines.flow.StateFlow

interface PsstSearchIndexing {
    /** True while the history of the joined rooms is being indexed (any search backfill running in this process). */
    val isIndexing: StateFlow<Boolean>

    /**
     * Run the foreground search backfill (unless one ran recently) and update [isIndexing] until it finishes.
     * Cancelling the caller (the app left the screen) cancels the backfill.
     */
    suspend fun runWhileVisible()

    /** False when a complete sweep of this device finished less than a day ago (new messages are indexed live). */
    fun isSweepDue(): Boolean

    /** Refresh [isIndexing] while a search screen is shown (a backfill may have been started elsewhere). */
    suspend fun observe()
}
