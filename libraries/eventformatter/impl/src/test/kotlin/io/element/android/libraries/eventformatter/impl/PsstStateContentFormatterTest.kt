/*
 * PSST (D95, D96): timeline texts of the psst room settings (spec 26.11.1).
 */
package io.element.android.libraries.eventformatter.impl

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.eventformatter.impl.mode.RenderingMode
import io.element.android.libraries.matrix.api.timeline.item.event.OtherState
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import io.element.android.services.toolbox.impl.strings.AndroidStringProvider
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class PsstStateContentFormatterTest : RobolectricTest() {
    private fun format(type: String, content: String, mode: RenderingMode = RenderingMode.Timeline): String? {
        val context = RuntimeEnvironment.getApplication() as Context
        return StateContentFormatter(AndroidStringProvider(context.resources)).format(
            stateContent = StateContent("", OtherState.Custom(type, content)),
            senderDisambiguatedDisplayName = "Jana",
            senderIsYou = false,
            renderingMode = mode,
        )?.toString()
    }

    @Test
    @Config(qualifiers = "en")
    fun `english texts`() {
        assertThat(format("m.room.retention", """{"max_lifetime":300000}""")).isEqualTo("Jana turned on disappearing messages: 5 minutes")
        assertThat(format("m.room.retention", "{}")).isEqualTo("Jana turned off disappearing messages")
        assertThat(format("eu.macet.psst.screen_protection", """{"enabled":true}""")).isEqualTo("Jana turned on screen protection")
        assertThat(format("eu.macet.psst.screen_protection", """{"enabled":false}""")).isEqualTo("Jana turned off screen protection")
        assertThat(format("m.room.retention", "{}", RenderingMode.RoomList)).isNull()
    }

    @Test
    @Config(qualifiers = "sk")
    fun `slovak texts`() {
        assertThat(format("m.room.retention", """{"max_lifetime":604800000}""")).isEqualTo("Jana zapína miznúce správy: 1 týždeň")
        assertThat(format("m.room.retention", "{}")).isEqualTo("Jana vypína miznúce správy")
        assertThat(format("eu.macet.psst.screen_protection", """{"enabled":true}""")).isEqualTo("Jana zapína ochranu obrazovky")
        assertThat(format("eu.macet.psst.screen_protection", """{"enabled":false}""")).isEqualTo("Jana vypína ochranu obrazovky")
    }
}
