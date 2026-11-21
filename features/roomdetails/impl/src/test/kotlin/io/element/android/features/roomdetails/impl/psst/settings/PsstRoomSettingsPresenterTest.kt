/*
 * PSST (D95, D96)
 */
package io.element.android.features.roomdetails.impl.psst.settings

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.psst.PsstRetention
import io.element.android.features.roomdetails.impl.aJoinedRoom
import io.element.android.libraries.matrix.test.room.powerlevels.FakeRoomPermissions
import io.element.android.libraries.matrix.test.room.psst.FakePsstRoomSettingsService
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PsstRoomSettingsPresenterTest {
    @Test
    fun `allowed - changes are sent`() = runTest {
        val service = FakePsstRoomSettingsService()
        val room = aJoinedRoom(roomPermissions = FakeRoomPermissions(canSendState = { true }))
        PsstRoomSettingsPresenter(room, service).test {
            val initial = awaitItem().let { if (!it.canChangeRetention) awaitItem() else it }
            assertThat(initial.retentionMaxLifetimeMs).isNull()
            assertThat(initial.canChangeRetention).isTrue()
            assertThat(initial.canChangeScreenProtection).isTrue()
            initial.eventSink(PsstRoomSettingsEvent.SetRetention(PsstRetention.ONE_HOUR))
            val updated = consumeUntil { it.retentionMaxLifetimeMs == PsstRetention.ONE_HOUR && !it.isSaving }
            updated.eventSink(PsstRoomSettingsEvent.SetScreenProtection(true))
            consumeUntil { it.screenProtection && !it.isSaving }
            assertThat(service.settings.value.retentionMaxLifetimeMs).isEqualTo(PsstRetention.ONE_HOUR)
            assertThat(service.settings.value.screenProtection).isTrue()
        }
    }

    @Test
    fun `not allowed - read only, nothing is sent`() = runTest {
        val service = FakePsstRoomSettingsService(setRetentionResult = { _, _ -> error("must not be called") })
        val room = aJoinedRoom(
            roomPermissions = FakeRoomPermissions(canSendState = { it != StateEventType.RoomRetention && it !is StateEventType.Custom }),
        )
        PsstRoomSettingsPresenter(room, service).test {
            val state = awaitItem()
            assertThat(state.canChangeRetention).isFalse()
            assertThat(state.canChangeScreenProtection).isFalse()
            state.eventSink(PsstRoomSettingsEvent.SetRetention(PsstRetention.ONE_DAY))
            expectNoEvents()
            assertThat(service.settings.value.retentionMaxLifetimeMs).isNull()
        }
    }

    @Test
    fun `failure - error shown and dismissed`() = runTest {
        val service = FakePsstRoomSettingsService(setScreenProtectionResult = { _, _ -> Result.failure(Exception("boom")) })
        val room = aJoinedRoom(roomPermissions = FakeRoomPermissions(canSendState = { true }))
        PsstRoomSettingsPresenter(room, service).test {
            val initial = awaitItem().let { if (!it.canChangeScreenProtection) awaitItem() else it }
            initial.eventSink(PsstRoomSettingsEvent.SetScreenProtection(true))
            val error = consumeUntil { it.hasError && !it.isSaving }
            error.eventSink(PsstRoomSettingsEvent.DismissError)
            consumeUntil { !it.hasError }
        }
    }
}

private suspend fun app.cash.turbine.ReceiveTurbine<PsstRoomSettingsState>.consumeUntil(
    predicate: (PsstRoomSettingsState) -> Boolean,
): PsstRoomSettingsState {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
