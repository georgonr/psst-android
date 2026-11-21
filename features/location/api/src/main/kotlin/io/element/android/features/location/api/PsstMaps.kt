/*
 * PSST (D97): maps without MapTiler — OpenFreeMap (OpenStreetMap data) behind the psst proxy on app.psst.pro, so the
 * users' IP addresses do not reach a third party. The proxy rewrites every URL inside the styles (tiles, sprites,
 * glyphs) to https://app.psst.pro/maps/…. No API key.
 */
package io.element.android.features.location.api

import io.element.android.features.enterprise.api.remoteconfig.MapTilerConfig

object PsstMaps {
    const val LIGHT_STYLE_URL = "https://app.psst.pro/maps/styles/liberty"
    const val DARK_STYLE_URL = "https://app.psst.pro/maps/styles/dark"

    /** Must stay visible on every map, the static timeline thumbnails included (OpenMapTiles / ODbL). */
    const val ATTRIBUTION = "© OpenMapTiles © OpenStreetMap contributors"

    fun styleUrl(darkMode: Boolean): String = if (darkMode) DARK_STYLE_URL else LIGHT_STYLE_URL

    /**
     * The map configuration of every psst build. The upstream plumbing passes a [MapTilerConfig] around and treats
     * "no config" as "location not available"; psst keeps that plumbing and puts its full style URLs in the style ids.
     */
    val config: MapTilerConfig = MapTilerConfig(
        apiKey = "",
        lightStyleId = LIGHT_STYLE_URL,
        darkStyleId = DARK_STYLE_URL,
        baseUrl = null,
    )
}
