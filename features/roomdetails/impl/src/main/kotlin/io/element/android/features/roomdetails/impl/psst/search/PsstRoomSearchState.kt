/*
 * PSST (D80): state of "Hľadať v konverzácii".
 */
package io.element.android.features.roomdetails.impl.psst.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import io.element.android.libraries.matrix.api.core.EventId
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class PsstRoomSearchState(
    val roomName: String?,
    val queryState: TextFieldState,
    /** The query the [items] belong to (the field may already hold a newer one). */
    val searchedQuery: String,
    val items: ImmutableList<PsstRoomSearchItem>,
    val isLoading: Boolean,
    val endReached: Boolean,
    val isIndexing: Boolean,
    val eventSink: (PsstRoomSearchEvent) -> Unit,
)

@Immutable
data class PsstRoomSearchItem(
    val eventId: EventId,
    /** null = sent by me. */
    val senderName: String?,
    val text: String,
    val date: String,
)

sealed interface PsstRoomSearchEvent {
    data object LoadMore : PsstRoomSearchEvent
    data object Clear : PsstRoomSearchEvent
}
