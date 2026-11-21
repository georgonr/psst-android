/*
 * PSST (D95, D96)
 */
package io.element.android.libraries.matrix.api.room.psst

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PsstRoomStateContentTest {
    @Test
    fun `retention content`() {
        assertThat(PsstRoomStateContent.retentionContent(300_000)).isEqualTo("""{"max_lifetime":300000}""")
        assertThat(PsstRoomStateContent.retentionContent(null)).isEqualTo("{}")
    }

    @Test
    fun `retention parsing`() {
        assertThat(PsstRoomStateContent.retentionMaxLifetime("""{"max_lifetime":604800000}""")).isEqualTo(604_800_000L)
        assertThat(PsstRoomStateContent.retentionMaxLifetime("{}")).isNull()
        assertThat(PsstRoomStateContent.retentionMaxLifetime("""{"max_lifetime":0}""")).isNull()
        assertThat(PsstRoomStateContent.retentionMaxLifetime("""{"max_lifetime":"x"}""")).isNull()
        assertThat(PsstRoomStateContent.retentionMaxLifetime("not json")).isNull()
        assertThat(PsstRoomStateContent.retentionMaxLifetime(null)).isNull()
    }

    @Test
    fun `screen protection`() {
        assertThat(PsstRoomStateContent.screenProtectionContent(true)).isEqualTo("""{"enabled":true}""")
        assertThat(PsstRoomStateContent.screenProtectionEnabled("""{"enabled":true}""")).isTrue()
        assertThat(PsstRoomStateContent.screenProtectionEnabled("""{"enabled":false}""")).isFalse()
        assertThat(PsstRoomStateContent.screenProtectionEnabled("{}")).isFalse()
    }

    @Test
    fun `screenshot notice and event content`() {
        assertThat(PsstRoomStateContent.screenshotNoticeContent("📸 Jana took a screenshot"))
            .isEqualTo("""{"msgtype":"m.notice","body":"📸 Jana took a screenshot","eu.macet.psst.screenshot":true}""")
        assertThat(PsstRoomStateContent.contentOfEvent("""{"type":"m.room.retention","content":{"max_lifetime":3600000}}"""))
            .isEqualTo("""{"max_lifetime":3600000}""")
    }

    @Test
    fun `expiry`() {
        val settings = PsstRoomSettings(retentionMaxLifetimeMs = 1000)
        assertThat(settings.isExpired(originServerTs = 0, nowMs = 1000)).isFalse()
        assertThat(settings.isExpired(originServerTs = 0, nowMs = 1001)).isTrue()
        assertThat(PsstRoomSettings().isExpired(originServerTs = 0, nowMs = Long.MAX_VALUE)).isFalse()
    }
}
