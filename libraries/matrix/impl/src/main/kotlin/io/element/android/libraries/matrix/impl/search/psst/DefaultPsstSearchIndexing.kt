/*
 * PSST (D80): see PsstSearchIndexing.
 */
package io.element.android.libraries.matrix.impl.search.psst

import io.element.android.libraries.di.annotations.ApplicationContext
import android.content.Context
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
    @ApplicationContext private val context: Context,
) : PsstSearchIndexing {
    private val service get() = matrixClient.searchBackfillService
    private val indexing = MutableStateFlow(false)
    override val isIndexing: StateFlow<Boolean> = indexing.asStateFlow()

    /**
     * Wall-clock time of the last complete sweep of this session, kept across restarts: a sweep re-reads the stored
     * history of every room (measured: ~1 min of CPU for ~1000 messages on the emulator), while new messages are
     * indexed as they arrive anyway. So at most once per [RERUN_AFTER].
     */
    private val prefs get() = context.getSharedPreferences("psst_search_indexing", Context.MODE_PRIVATE)
    // per device: a new login (new device, new index) must index its history again
    private val lastCompletedKey get() = "last_completed_${matrixClient.sessionId.value}_${matrixClient.deviceId.value}"

    override suspend fun runWhileVisible() {
        if (!matrixClient.isMessageSearchAvailable) return
        // Let the first sync and the room list settle before adding (low priority) back-paginations.
        delay(START_DELAY)
        if (!isSweepDue()) return
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
                prefs.edit().putLong(lastCompletedKey, System.currentTimeMillis()).apply()
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

    override fun isSweepDue(): Boolean {
        val lastCompletedAt = prefs.getLong(lastCompletedKey, 0L)
        return lastCompletedAt == 0L || System.currentTimeMillis() - lastCompletedAt >= RERUN_AFTER.inWholeMilliseconds
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
        val RERUN_AFTER = 24.hours
    }
}
