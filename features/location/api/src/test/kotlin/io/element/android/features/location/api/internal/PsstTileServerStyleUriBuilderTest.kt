/*
 * PSST (D97)
 */
package io.element.android.features.location.api.internal

import com.google.common.truth.Truth.assertThat
import io.element.android.features.location.api.PsstMaps
import io.element.android.features.location.api.buildTimeMapTilerConfig
import org.junit.Test

class PsstTileServerStyleUriBuilderTest {
    @Test
    fun `psst styles, no MapTiler`() {
        val config = buildTimeMapTilerConfig()!!
        val builder = PsstTileServerStyleUriBuilder()
        assertThat(builder.build(config, darkMode = false)).isEqualTo("https://app.psst.pro/maps/styles/liberty")
        assertThat(builder.build(config, darkMode = true)).isEqualTo("https://app.psst.pro/maps/styles/dark")
        assertThat(PsstMaps.ATTRIBUTION).isEqualTo("© OpenMapTiles © OpenStreetMap contributors")
    }
}
