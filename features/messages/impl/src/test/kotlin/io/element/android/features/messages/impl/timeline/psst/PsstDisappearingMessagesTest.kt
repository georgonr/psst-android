/*
 * PSST (D95)
 */
package io.element.android.features.messages.impl.timeline.psst

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.room.psst.PsstRetention
import io.element.android.libraries.matrix.api.room.psst.PsstRoomSettings
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.OtherState
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import io.element.android.libraries.matrix.test.timeline.aMessageContent
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PsstDisappearingMessagesTest {
    private fun message(id: String, ts: Long) = MatrixTimelineItem.Event(
        uniqueId = UniqueId(id),
        event = anEventTimelineItem(timestamp = ts, content = aMessageContent()),
    )

    private fun state(id: String, ts: Long) = MatrixTimelineItem.Event(
        uniqueId = UniqueId(id),
        event = anEventTimelineItem(timestamp = ts, content = StateContent("", OtherState.Custom("m.room.retention", "{}"))),
    )

    @Test
    fun `off - nothing is hidden`() {
        val items = listOf(message("a", 0), message("b", 1))
        assertThat(items.withoutExpiredMessages(PsstRoomSettings(), nowMs = Long.MAX_VALUE / 2)).isEqualTo(items)
    }

    @Test
    fun `expired messages are hidden, state events and recent messages stay`() {
        val settings = PsstRoomSettings(retentionMaxLifetimeMs = PsstRetention.FIVE_MINUTES)
        val now = 1_000_000_000L
        val old = message("old", now - PsstRetention.FIVE_MINUTES - 1)
        val edge = message("edge", now - PsstRetention.FIVE_MINUTES)
        val recent = message("recent", now - 1_000)
        val oldState = state("state", now - PsstRetention.ONE_WEEK)
        val result = listOf(oldState, old, edge, recent).withoutExpiredMessages(settings, now)
        assertThat(result).containsExactly(oldState, edge, recent).inOrder()
    }

    @Test
    fun `a message disappears while the timeline is open`() = runTest {
        val start = 10_000_000L
        val item = message("m", start - PsstRetention.FIVE_MINUTES + 7_000)
        val settings = MutableStateFlow(PsstRoomSettings(retentionMaxLifetimeMs = PsstRetention.FIVE_MINUTES))
        flowOf(listOf<MatrixTimelineItem>(item))
            .hidingExpiredMessages(settings, clock = { start + currentTime })
            .test {
                assertThat(awaitItem()).containsExactly(item)
                advanceTimeBy(10_001)
                assertThat(awaitItem()).isEmpty()
                // Turning it off shows the (not yet purged) message again.
                settings.value = PsstRoomSettings()
                assertThat(awaitItem()).containsExactly(item)
                cancelAndIgnoreRemainingEvents()
            }
    }
}
