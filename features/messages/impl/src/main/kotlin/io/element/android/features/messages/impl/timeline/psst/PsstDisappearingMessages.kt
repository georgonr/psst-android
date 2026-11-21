/*
 * PSST (D95): disappearing messages, client side. The psst server purges expired messages itself (Synapse retention,
 * every 5 minutes); until then — and offline — the app must not show a message whose
 * origin_server_ts + max_lifetime < now. State events (and membership / profile changes) stay.
 */
package io.element.android.features.messages.impl.timeline.psst

import io.element.android.libraries.matrix.api.room.psst.PsstRoomSettings
import io.element.android.libraries.matrix.api.room.psst.PsstRoomStateTypes
import io.element.android.libraries.matrix.api.timeline.item.event.OtherState
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.EventContent
import io.element.android.libraries.matrix.api.timeline.item.event.FailedToParseStateContent
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileChangeContent
import io.element.android.libraries.matrix.api.timeline.item.event.RoomMembershipContent
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Messages (not state events) are what disappears: text, media, voice, polls, locations, undecryptable ones… */
internal fun EventContent.isDisappearingContent(): Boolean = when (this) {
    is StateContent,
    is RoomMembershipContent,
    is ProfileChangeContent,
    is FailedToParseStateContent -> false
    else -> true
}

/**
 * PSST (D96): custom state events are let through by the SDK filter (see RustTimelineEventFilterFactory) so that the
 * psst room settings show in the timeline; every other custom state event stays hidden, as upstream.
 */
internal fun List<MatrixTimelineItem>.withoutForeignCustomState(): List<MatrixTimelineItem> = filterNot { item ->
    val content = ((item as? MatrixTimelineItem.Event)?.event?.content as? StateContent)?.content
    content is OtherState.Custom && content.eventType !in PsstRoomStateTypes.all
}

internal fun List<MatrixTimelineItem>.withoutExpiredMessages(settings: PsstRoomSettings, nowMs: Long): List<MatrixTimelineItem> {
    if (settings.retentionMaxLifetimeMs == null) return this
    return filterNot { item ->
        item is MatrixTimelineItem.Event &&
            item.event.content.isDisappearingContent() &&
            settings.isExpired(originServerTs = item.event.timestamp, nowMs = nowMs)
    }
}

/**
 * The timeline items without the expired messages, re-checked every [checkInterval] while disappearing messages are on
 * (works offline: wall clock only). A new list is emitted only when a message has to disappear, so the timeline is not
 * rebuilt on every check. A short interval also covers resuming the app after the device slept (coroutine delays do
 * not advance in deep sleep).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<List<MatrixTimelineItem>>.hidingExpiredMessages(
    settings: Flow<PsstRoomSettings>,
    clock: () -> Long = System::currentTimeMillis,
    checkInterval: Duration = 5.seconds,
): Flow<List<MatrixTimelineItem>> = combine(this, settings) { items, roomSettings -> items to roomSettings }
    .flatMapLatest { (items, roomSettings) ->
        if (roomSettings.retentionMaxLifetimeMs == null) {
            flowOf(items)
        } else {
            flow {
                var lastHiddenCount = -1
                while (true) {
                    val visible = items.withoutExpiredMessages(roomSettings, clock())
                    val hiddenCount = items.size - visible.size
                    if (hiddenCount != lastHiddenCount) {
                        lastHiddenCount = hiddenCount
                        emit(visible)
                    }
                    delay(checkInterval)
                }
            }
        }
    }
