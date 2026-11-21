/*
 * PSST (D96)
 */
package io.element.android.appnav.room.joined

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import org.junit.Test

class PsstScreenshotNotifierTest {
    @Test
    fun `at most one screenshot notice per 10 s per room`() {
        val room = RoomId("!psst-rate:example.org")
        val other = RoomId("!psst-other:example.org")
        assertThat(PsstScreenshotNotifier.tryAcquire(room, nowMs = 100_000)).isTrue()
        assertThat(PsstScreenshotNotifier.tryAcquire(room, nowMs = 105_000)).isFalse()
        assertThat(PsstScreenshotNotifier.tryAcquire(other, nowMs = 105_000)).isTrue()
        assertThat(PsstScreenshotNotifier.tryAcquire(room, nowMs = 110_000)).isTrue()
    }
}
