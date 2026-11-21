/*
 * PSST (D95, D96): see PsstRoomSettingsService.
 */
package io.element.android.libraries.matrix.impl.room.psst

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.psst.PsstRoomSettings
import io.element.android.libraries.matrix.api.room.psst.PsstRoomSettingsService
import io.element.android.libraries.matrix.api.room.psst.PsstRoomStateContent
import io.element.android.libraries.matrix.api.room.psst.PsstRoomStateTypes
import io.element.android.libraries.matrix.impl.RustMatrixClient
import io.element.android.libraries.matrix.impl.rtc.stateEventSets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.RequestConfigOverride
import org.matrix.rustcomponents.sdk.Room
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultPsstRoomSettingsService(
    private val matrixClient: MatrixClient,
    private val dispatchers: CoroutineDispatchers,
    @ApplicationContext context: Context,
) : PsstRoomSettingsService {
    private val client: Client get() = (matrixClient as RustMatrixClient).innerClient
    private val retry = RequestConfigOverride(retryLimit = 3uL, timeout = null)

    // Last known values per room, kept across restarts so expired messages stay hidden offline.
    private val prefs = context.getSharedPreferences("psst_room_settings_${matrixClient.sessionId.value.hashCode()}", Context.MODE_PRIVATE)
    private val states = ConcurrentHashMap<RoomId, MutableStateFlow<PsstRoomSettings>>()
    private val observed = ConcurrentHashMap.newKeySet<RoomId>()

    override fun cachedSettings(roomId: RoomId): PsstRoomSettings = stateOf(roomId).value

    override fun settingsFlow(roomId: RoomId): Flow<PsstRoomSettings> {
        val state = stateOf(roomId)
        return state.asStateFlow().onStart { observe(roomId) }
    }

    private fun stateOf(roomId: RoomId): MutableStateFlow<PsstRoomSettings> = states.getOrPut(roomId) {
        MutableStateFlow(
            PsstRoomSettings(
                retentionMaxLifetimeMs = prefs.getLong("${roomId.value}|retention", 0L).takeIf { it > 0 },
                screenProtection = prefs.getBoolean("${roomId.value}|screen", false),
            )
        )
    }

    /** Once per room and session: sync updates of both state types + one read from the homeserver. */
    private fun observe(roomId: RoomId) {
        if (!observed.add(roomId)) return
        val scope = matrixClient.sessionCoroutineScope
        scope.launch {
            val room = withContext(dispatchers.io) { runCatchingExceptions { client.getRoom(roomId.value) }.getOrNull() }
            if (room == null) {
                observed.remove(roomId)
                return@launch
            }
            var open = 2
            fun release() {
                if (--open == 0) room.close()
            }
            room.stateEventSets(PsstRoomStateTypes.RETENTION, dispatchers)
                .onEach { events ->
                    events.firstOrNull { it.stateKey == "" }?.let { update(roomId, retentionJson = it.contentJson) }
                }
                .onCompletion { release() }
                .launchIn(scope)
            room.stateEventSets(PsstRoomStateTypes.SCREEN_PROTECTION, dispatchers)
                .onEach { events ->
                    events.firstOrNull { it.stateKey == "" }?.let { update(roomId, screenJson = it.contentJson) }
                }
                .onCompletion { release() }
                .launchIn(scope)
            fetchFromServer(roomId)
        }
    }

    private suspend fun fetchFromServer(roomId: RoomId) = withContext(dispatchers.io) {
        for (type in listOf(PsstRoomStateTypes.RETENTION, PsstRoomStateTypes.SCREEN_PROTECTION)) {
            val result = runCatchingExceptions { getState(roomId, type) }
            result.onSuccess { content ->
                // null = 404, the state was never set: off.
                when (type) {
                    PsstRoomStateTypes.RETENTION -> update(roomId, retentionJson = content ?: "{}")
                    else -> update(roomId, screenJson = content ?: "{}")
                }
            }.onFailure {
                Timber.w(it, "PSST: room setting $type not read from the homeserver")
            }
        }
    }

    /** GET /rooms/{roomId}/state/{type}/ — content JSON, or null when the state does not exist (404). */
    private fun getState(roomId: RoomId, type: String): String? {
        val session = client.session()
        val url = URL(
            session.homeserverUrl.trimEnd('/') + "/_matrix/client/v3/rooms/" + Uri.encode(roomId.value) +
                "/state/" + Uri.encode(type) + "/"
        )
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            when (val code = connection.responseCode) {
                200 -> connection.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                else -> error("HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun update(roomId: RoomId, retentionJson: String? = null, screenJson: String? = null) {
        val state = stateOf(roomId)
        var value = state.value
        val edit = prefs.edit()
        if (retentionJson != null) {
            val lifetime = PsstRoomStateContent.retentionMaxLifetime(retentionJson)
            value = value.copy(retentionMaxLifetimeMs = lifetime)
            edit.putLong("${roomId.value}|retention", lifetime ?: 0L)
        }
        if (screenJson != null) {
            val enabled = PsstRoomStateContent.screenProtectionEnabled(screenJson)
            value = value.copy(screenProtection = enabled)
            edit.putBoolean("${roomId.value}|screen", enabled)
        }
        edit.apply()
        state.value = value
    }

    override suspend fun setRetention(roomId: RoomId, maxLifetimeMs: Long?): Result<Unit> =
        sendState(roomId, PsstRoomStateTypes.RETENTION, PsstRoomStateContent.retentionContent(maxLifetimeMs))
            .onSuccess { update(roomId, retentionJson = PsstRoomStateContent.retentionContent(maxLifetimeMs)) }

    override suspend fun setScreenProtection(roomId: RoomId, enabled: Boolean): Result<Unit> =
        sendState(roomId, PsstRoomStateTypes.SCREEN_PROTECTION, PsstRoomStateContent.screenProtectionContent(enabled))
            .onSuccess { update(roomId, screenJson = PsstRoomStateContent.screenProtectionContent(enabled)) }

    override suspend fun sendScreenshotNotice(roomId: RoomId, body: String): Result<Unit> = withRoom(roomId) { room ->
        room.sendRaw("m.room.message", PsstRoomStateContent.screenshotNoticeContent(body), retry)
    }

    private suspend fun sendState(roomId: RoomId, type: String, content: String): Result<Unit> = withRoom(roomId) { room ->
        room.sendStateEventRaw(type, "", content, retry)
    }

    private suspend fun withRoom(roomId: RoomId, block: suspend (Room) -> Unit): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            val room = client.getRoom(roomId.value) ?: error("Not a known room: $roomId")
            room.use { block(it) }
        }.onFailure { Timber.e(it, "PSST: room setting not sent") }
    }
}
