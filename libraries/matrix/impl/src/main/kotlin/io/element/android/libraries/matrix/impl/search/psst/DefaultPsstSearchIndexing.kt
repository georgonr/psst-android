/*
 * PSST (D80): see PsstSearchIndexing.
 */
package io.element.android.libraries.matrix.impl.search.psst

import android.util.Log
import android.os.SystemClock
import android.os.Process
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.search.SearchBackfillStrategy
import io.element.android.libraries.matrix.api.search.psst.PsstSearchIndexing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultPsstSearchIndexing(
    private val matrixClient: MatrixClient,
) : PsstSearchIndexing {
    private val service get() = matrixClient.searchBackfillService
    private val indexing = MutableStateFlow(false)
    override val isIndexing: StateFlow<Boolean> = indexing.asStateFlow()

    /** Elapsed-realtime of the last complete sweep in this process; a sweep re-checks every room, so not too often. */
    private var lastCompletedAt = 0L

    override suspend fun runWhileVisible() {
        if (!matrixClient.isMessageSearchAvailable) return
        // Let the first sync and the room list settle before adding (low priority) back-paginations.
        delay(START_DELAY)
        if (lastCompletedAt != 0L && System.currentTimeMillis() - lastCompletedAt < RERUN_AFTER.inWholeMilliseconds) return
        val task = if (service.isSearchBackfillRunning()) {
            null
        } else {
            service.startSearchBackfill(SearchBackfillStrategy.FOREGROUND)
                .onFailure { Timber.w(it, "PSST: search backfill not started") }
                .getOrNull()
        }
        val started = SystemClock.elapsedRealtime()
        val cpuStart = Process.getElapsedCpuTime()
        try {
            Timber.i("PSST: search backfill running (history indexing)")
            Log.i("PsstSearch", "history indexing started (own run: ${task != null})")
            while (service.isSearchBackfillRunning()) {
                indexing.value = true
                delay(POLL)
            }
            if (task != null) {
                lastCompletedAt = System.currentTimeMillis()
                Timber.i("PSST: search backfill finished")
            }
            Log.i(
                "PsstSearch",
                "history indexing finished in ${SystemClock.elapsedRealtime() - started} ms, " +
                    "app CPU ${Process.getElapsedCpuTime() - cpuStart} ms",
            )
        } finally {
            // Leaving the screen: stop our run (the periodic background job continues on a charger).
            task?.close()
            indexing.value = service.isSearchBackfillRunning()
        }
    }

    override suspend fun observe() {
        while (true) {
            indexing.value = service.isSearchBackfillRunning()
            delay(POLL)
        }
    }

    private companion object {
        val START_DELAY = 8.seconds
        val POLL = 2.seconds
        val RERUN_AFTER = 6.hours
    }
}
