/*
 * PSST (D95, D96): "Disappearing messages" and "Screen protection" in the room details (DM and group).
 * Editable by whoever may send the state event (power levels: events[type] or state_default; in a DM both members
 * have 100), read-only otherwise.
 */
package io.element.android.features.roomdetails.impl.psst.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.powerlevels.permissionsAsState
import io.element.android.libraries.matrix.api.room.psst.PsstRoomSettingsService
import io.element.android.libraries.matrix.api.room.psst.PsstRoomStateTypes
import kotlinx.coroutines.launch

@Immutable
data class PsstRoomSettingsState(
    /** null = off. */
    val retentionMaxLifetimeMs: Long?,
    val screenProtection: Boolean,
    val canChangeRetention: Boolean,
    val canChangeScreenProtection: Boolean,
    val isSaving: Boolean,
    val hasError: Boolean,
    val eventSink: (PsstRoomSettingsEvent) -> Unit,
)

sealed interface PsstRoomSettingsEvent {
    data class SetRetention(val maxLifetimeMs: Long?) : PsstRoomSettingsEvent
    data class SetScreenProtection(val enabled: Boolean) : PsstRoomSettingsEvent
    data object DismissError : PsstRoomSettingsEvent
}

fun aPsstRoomSettingsState(
    retentionMaxLifetimeMs: Long? = null,
    screenProtection: Boolean = false,
    canChange: Boolean = true,
) = PsstRoomSettingsState(
    retentionMaxLifetimeMs = retentionMaxLifetimeMs,
    screenProtection = screenProtection,
    canChangeRetention = canChange,
    canChangeScreenProtection = canChange,
    isSaving = false,
    hasError = false,
    eventSink = {},
)

@Inject
class PsstRoomSettingsPresenter(
    private val room: JoinedRoom,
    private val service: PsstRoomSettingsService,
) : Presenter<PsstRoomSettingsState> {
    @Composable
    override fun present(): PsstRoomSettingsState {
        val scope = rememberCoroutineScope()
        val settings by remember { service.settingsFlow(room.roomId) }.collectAsState(service.cachedSettings(room.roomId))
        val permissions by room.permissionsAsState(false to false) { perms ->
            perms.canOwnUserSendState(StateEventType.RoomRetention) to
                perms.canOwnUserSendState(StateEventType.Custom(PsstRoomStateTypes.SCREEN_PROTECTION))
        }
        var isSaving by remember { mutableStateOf(false) }
        var hasError by remember { mutableStateOf(false) }

        fun run(block: suspend () -> Result<Unit>) {
            if (isSaving) return
            isSaving = true
            scope.launch {
                block().onFailure { hasError = true }
                isSaving = false
            }
        }

        fun handleEvent(event: PsstRoomSettingsEvent) {
            when (event) {
                is PsstRoomSettingsEvent.SetRetention -> if (permissions.first) {
                    run { service.setRetention(room.roomId, event.maxLifetimeMs) }
                }
                is PsstRoomSettingsEvent.SetScreenProtection -> if (permissions.second) {
                    run { service.setScreenProtection(room.roomId, event.enabled) }
                }
                PsstRoomSettingsEvent.DismissError -> hasError = false
            }
        }

        return PsstRoomSettingsState(
            retentionMaxLifetimeMs = settings.retentionMaxLifetimeMs,
            screenProtection = settings.screenProtection,
            canChangeRetention = permissions.first,
            canChangeScreenProtection = permissions.second,
            isSaving = isSaving,
            hasError = hasError,
            eventSink = ::handleEvent,
        )
    }
}
