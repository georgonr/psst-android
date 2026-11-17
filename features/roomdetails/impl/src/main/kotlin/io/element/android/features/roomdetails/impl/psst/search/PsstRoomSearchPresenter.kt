/*
 * PSST (D80): "Hľadať v konverzácii" — message search limited to one room, over the same local index as the global
 * search (upstream MessageSearchService with a roomId: a client-side filter of the global result stream, so this
 * presenter keeps paginating until it has enough results of this room or the index has no more).
 */
package io.element.android.features.roomdetails.impl.psst.search

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.search.MessageSearchPaginationState
import io.element.android.libraries.matrix.api.search.MessageSearchService
import io.element.android.libraries.matrix.api.search.psst.PsstSearchIndexing
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.MessageTypeWithAttachment
import io.element.android.libraries.matrix.api.timeline.item.event.getDisambiguatedDisplayName
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.text.DateFormat
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

@Inject
class PsstRoomSearchPresenter(
    private val room: JoinedRoom,
    private val matrixClient: MatrixClient,
    private val messageSearchService: MessageSearchService,
    private val psstSearchIndexing: PsstSearchIndexing,
) : Presenter<PsstRoomSearchState> {
    @Composable
    override fun present(): PsstRoomSearchState {
        val scope = rememberCoroutineScope()
        val queryState = rememberTextFieldState()
        val search = remember { messageSearchService.createMessageSearch(scope = scope, roomId = room.roomId) }
        val results by search.results.collectAsState()
        val pagination by search.paginationState.collectAsState()
        val indexing by psstSearchIndexing.isIndexing.collectAsState()
        var searchedQuery by remember { mutableStateOf("") }
        var loadMoreTick by remember { mutableIntStateOf(0) }
        var filling by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { psstSearchIndexing.observe() }

        // One coroutine owns the cursor: a new query (or "load more") cancels the previous fill. The SDK ranks all
        // rooms together, so page on until this room has PAGE more results or the index has no more.
        LaunchedEffect(Unit) {
            snapshotFlow { queryState.text.toString().trim() to loadMoreTick }.distinctUntilChanged().collectLatest { (q, _) ->
                val target = if (q != searchedQuery || q.isEmpty()) {
                    delay(250.milliseconds)
                    search.setQuery(q).onFailure { Timber.w(it, "PSST: room search failed") }
                    searchedQuery = q
                    PAGE
                } else {
                    search.results.value.size + PAGE
                }
                if (q.isEmpty()) return@collectLatest
                filling = true
                try {
                    var guard = 0
                    while (search.results.value.size < target && guard++ < MAX_PAGES) {
                        // the pagination state arrives through a listener: wait until the SDK is idle again
                        val idle = withTimeoutOrNull(5_000) {
                            search.paginationState.first { it is MessageSearchPaginationState.Idle } as MessageSearchPaginationState.Idle
                        } ?: break
                        if (idle.endReached) break
                        search.paginate().onFailure { Timber.w(it, "PSST: room search paging failed") }
                    }
                } finally {
                    filling = false
                }
            }
        }

        val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
        val items = remember(results) {
            results.map { r ->
                val content = r.content as? MessageContent
                val text = when (val type = content?.type) {
                    is MessageTypeWithAttachment -> listOfNotNull(type.caption, type.filename).joinToString(" · ")
                    else -> content?.body.orEmpty()
                }
                PsstRoomSearchItem(
                    eventId = r.eventId,
                    senderName = if (matrixClient.isMe(r.senderId)) null else r.senderProfile.getDisambiguatedDisplayName(r.senderId),
                    text = text,
                    date = dateFormat.format(Date(r.timestamp)),
                )
            }.toImmutableList()
        }
        val loading = searchedQuery.isNotEmpty() && (filling || pagination is MessageSearchPaginationState.Loading)

        fun handle(event: PsstRoomSearchEvent) {
            when (event) {
                PsstRoomSearchEvent.LoadMore -> if (!loading) loadMoreTick++
                PsstRoomSearchEvent.Clear -> scope.launch { queryState.edit { replace(0, length, "") } }
            }
        }

        return PsstRoomSearchState(
            roomName = room.info().name,
            queryState = queryState,
            searchedQuery = searchedQuery,
            items = items,
            isLoading = loading,
            endReached = (pagination as? MessageSearchPaginationState.Idle)?.endReached == true,
            isIndexing = indexing,
            eventSink = ::handle,
        )
    }

    private companion object {
        const val PAGE = 20

        /** Upper bound of global pages fetched for one fill (20 results each). */
        const val MAX_PAGES = 100
    }
}
