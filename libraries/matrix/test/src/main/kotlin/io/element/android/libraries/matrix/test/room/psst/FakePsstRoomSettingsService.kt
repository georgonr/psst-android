/*
 * PSST (D95, D96)
 */
package io.element.android.libraries.matrix.test.room.psst

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.psst.PsstRoomSettings
import io.element.android.libraries.matrix.api.room.psst.PsstRoomSettingsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePsstRoomSettingsService(
    initialSettings: PsstRoomSettings = PsstRoomSettings(),
    private val setRetentionResult: (RoomId, Long?) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    private val setScreenProtectionResult: (RoomId, Boolean) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    private val sendScreenshotNoticeResult: (RoomId, String) -> Result<Unit> = { _, _ -> Result.success(Unit) },
) : PsstRoomSettingsService {
    val settings = MutableStateFlow(initialSettings)
    val sentScreenshotNotices = mutableListOf<Pair<RoomId, String>>()

    override fun settingsFlow(roomId: RoomId): Flow<PsstRoomSettings> = settings

    override fun cachedSettings(roomId: RoomId): PsstRoomSettings = settings.value

    override suspend fun setRetention(roomId: RoomId, maxLifetimeMs: Long?): Result<Unit> =
        setRetentionResult(roomId, maxLifetimeMs).onSuccess { settings.value = settings.value.copy(retentionMaxLifetimeMs = maxLifetimeMs) }

    override suspend fun setScreenProtection(roomId: RoomId, enabled: Boolean): Result<Unit> =
        setScreenProtectionResult(roomId, enabled).onSuccess { settings.value = settings.value.copy(screenProtection = enabled) }

    override suspend fun sendScreenshotNotice(roomId: RoomId, body: String): Result<Unit> =
        sendScreenshotNoticeResult(roomId, body).onSuccess { sentScreenshotNotices += roomId to body }
}
