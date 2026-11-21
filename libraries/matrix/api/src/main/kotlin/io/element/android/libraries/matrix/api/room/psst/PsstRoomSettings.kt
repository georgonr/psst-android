/*
 * PSST (D95, D96): room settings of psst 26.11.1 kept in room state, shared by all psst clients
 * (docs/psst-26.11.1-spec.md in georgonr/psst).
 *
 * - Disappearing messages: `m.room.retention` (MSC1763), state key "", content `{"max_lifetime": <ms>}`, off = `{}`.
 *   Synapse purges the messages itself; the apps stop showing them once `origin_server_ts + max_lifetime < now`.
 * - Screen protection: `eu.macet.psst.screen_protection`, state key "", content `{"enabled": true|false}`.
 * - Screenshot notice: an `m.notice` with `"eu.macet.psst.screenshot": true` (at most one per 10 s per room).
 */
package io.element.android.libraries.matrix.api.room.psst

import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object PsstRoomStateTypes {
    const val RETENTION = "m.room.retention"
    const val SCREEN_PROTECTION = "eu.macet.psst.screen_protection"
    const val SCREENSHOT_NOTICE_KEY = "eu.macet.psst.screenshot"

    val all = setOf(RETENTION, SCREEN_PROTECTION)
}

/** The values offered in the settings (spec 26.11.1 §1). Other values set by other clients are shown as "custom". */
object PsstRetention {
    const val FIVE_MINUTES = 300_000L
    const val ONE_HOUR = 3_600_000L
    const val ONE_DAY = 86_400_000L
    const val ONE_WEEK = 604_800_000L
    val choices = listOf(FIVE_MINUTES, ONE_HOUR, ONE_DAY, ONE_WEEK)
}

data class PsstRoomSettings(
    /** `max_lifetime` of `m.room.retention` in milliseconds, null when disappearing messages are off. */
    val retentionMaxLifetimeMs: Long? = null,
    val screenProtection: Boolean = false,
) {
    /** True when a message sent at [originServerTs] must not be shown any more at [nowMs]. */
    fun isExpired(originServerTs: Long, nowMs: Long): Boolean {
        val lifetime = retentionMaxLifetimeMs ?: return false
        return originServerTs + lifetime < nowMs
    }
}

interface PsstRoomSettingsService {
    /**
     * The settings of the room: last known values at once (kept on the device, so this works offline), then every
     * change from sync. The current state is also fetched from the homeserver once per room and process, because the
     * sync does not request these state types for rooms whose state was set before this device joined.
     */
    fun settingsFlow(roomId: RoomId): Flow<PsstRoomSettings>

    /** Last known settings, without waiting (room list). */
    fun cachedSettings(roomId: RoomId): PsstRoomSettings

    /** [maxLifetimeMs] null turns disappearing messages off. */
    suspend fun setRetention(roomId: RoomId, maxLifetimeMs: Long?): Result<Unit>

    suspend fun setScreenProtection(roomId: RoomId, enabled: Boolean): Result<Unit>

    /** Sends the screenshot notice (`m.notice` + `eu.macet.psst.screenshot`) with the given body. */
    suspend fun sendScreenshotNotice(roomId: RoomId, body: String): Result<Unit>
}

object PsstRoomStateContent {
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(contentJson: String?): JsonObject? =
        contentJson?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }

    /** `max_lifetime` of an `m.room.retention` content, null when absent, not a positive number, or unreadable. */
    fun retentionMaxLifetime(contentJson: String?): Long? =
        (parse(contentJson)?.get("max_lifetime") as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }

    fun screenProtectionEnabled(contentJson: String?): Boolean =
        (parse(contentJson)?.get("enabled") as? JsonPrimitive)?.booleanOrNull ?: false

    fun retentionContent(maxLifetimeMs: Long?): String = buildJsonObject {
        if (maxLifetimeMs != null) put("max_lifetime", maxLifetimeMs)
    }.toString()

    fun screenProtectionContent(enabled: Boolean): String = buildJsonObject {
        put("enabled", enabled)
    }.toString()

    fun screenshotNoticeContent(body: String): String = buildJsonObject {
        put("msgtype", "m.notice")
        put("body", body)
        put(PsstRoomStateTypes.SCREENSHOT_NOTICE_KEY, true)
    }.toString()

    /** The `content` object of a full event JSON (timeline debug info), as a string. */
    fun contentOfEvent(eventJson: String?): String? = parse(eventJson)?.get("content")?.toString()
}
