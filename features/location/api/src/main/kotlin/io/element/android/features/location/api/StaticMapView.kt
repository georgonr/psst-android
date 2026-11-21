/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.location.api

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import io.element.android.features.location.api.internal.PsstStaticMapRenderer
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.enterprise.api.remoteconfig.MapTilerConfig
import io.element.android.features.location.api.internal.StaticMapPlaceholder
import io.element.android.features.location.api.internal.centerBottomEdge
import io.element.android.libraries.designsystem.components.LocationPin
import io.element.android.libraries.designsystem.components.PinVariant
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.utils.CommonDrawables

/**
 * Shows a static map image downloaded via a third party service's static maps API.
 *
 * Handles 5 distinct cases:
 * 1. Stale location (pinVariant is StaleLocation) - shows stale map with stale pin, no fetching
 * 2. Null location - shows blurred placeholder, no pin, no loading
 * 3. Null mapTilerConfig (no map provider configured) - shows blurred placeholder, no pin, no fetching
 * 4. Loading (location != null, fetching) - shows blurred placeholder with loading indicator
 * 5. Success (location != null, loaded) - shows actual map with pin
 */
@Composable
fun StaticMapView(
    mapTilerConfig: MapTilerConfig?,
    location: Location?,
    zoom: Double,
    pinVariant: PinVariant,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    darkMode: Boolean = !ElementTheme.isLightTheme,
) {
    // Using BoxWithConstraints to:
    // 1) Size the inner Image to the same Dp size of the outer BoxWithConstraints.
    // 2) Request the static map image of the exact required size in Px to fill the AsyncImage.
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Case 1: Stale location - show stale map with stale pin, no fetching
        when {
            pinVariant is PinVariant.StaleLocation -> {
                StaleMapContent(
                    pinVariant = pinVariant,
                    contentDescription = contentDescription,
                    width = maxWidth,
                    height = maxHeight,
                )
            }
            // Case 2: Null location - show blurred placeholder, no pin, no loading
            // Case 3: No map provider configured - show blurred placeholder, no pin, no fetching
            location == null || mapTilerConfig == null && !LocalInspectionMode.current -> {
                StaticMapPlaceholder(
                    painter = painterResource(R.drawable.blurred_map),
                    canReload = false,
                    contentDescription = contentDescription,
                    width = maxWidth,
                    height = maxHeight,
                    onLoadMapClick = {}
                )
            }
            // Cases 4 & 5: Non-null location - fetch map
            else -> LoadableMapContent(
                mapTilerConfig = mapTilerConfig,
                location = location,
                zoom = zoom,
                pinVariant = pinVariant,
                contentDescription = contentDescription,
                darkMode = darkMode,
            )
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.StaleMapContent(
    pinVariant: PinVariant,
    contentDescription: String?,
    width: Dp,
    height: Dp,
) {
    Box(contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.stale_map),
            contentDescription = contentDescription,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.size(width = width, height = height)
        )
        LocationPin(variant = pinVariant, modifier = Modifier.centerBottomEdge(this@StaleMapContent))
    }
}

/**
 * PSST (D97): rendered on the device from the psst map styles with MapLibre's snapshotter (see
 * [PsstStaticMapRenderer]); the attribution required by OpenMapTiles / OpenStreetMap is drawn on the image.
 */
@Composable
private fun BoxWithConstraintsScope.LoadableMapContent(
    @Suppress("UNUSED_PARAMETER") mapTilerConfig: MapTilerConfig?,
    location: Location,
    zoom: Double,
    pinVariant: PinVariant,
    contentDescription: String?,
    darkMode: Boolean,
) {
    val density = LocalDensity.current
    val size = DpSize(maxWidth, maxHeight)
    var retryHash by remember { mutableIntStateOf(0) }
    val isInspection = LocalInspectionMode.current
    var image by remember(location, zoom, darkMode, size, density) {
        mutableStateOf(if (isInspection) null else PsstStaticMapRenderer.cached(location, zoom, darkMode, size, density))
    }
    var failed by remember(location, zoom, darkMode, size, density) { mutableStateOf(false) }
    if (!isInspection) {
        LaunchedEffect(location, zoom, darkMode, size, density, retryHash) {
            if (image != null || maxWidth <= 0.dp || maxHeight <= 0.dp) return@LaunchedEffect
            failed = false
            try {
                image = PsstStaticMapRenderer.render(location, zoom, darkMode, size, density)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.w(e, "PSST: static map not rendered")
                failed = true
            }
        }
    }
    val bitmap = image
    when {
        isInspection -> {
            Image(
                painter = painterResource(CommonDrawables.sample_map),
                contentDescription = contentDescription,
                modifier = Modifier.size(width = maxWidth, height = maxHeight),
                contentScale = ContentScale.Crop,
            )
            LocationPin(variant = pinVariant, modifier = Modifier.centerBottomEdge(this))
            PsstMapAttribution(modifier = Modifier.align(Alignment.BottomEnd))
        }
        bitmap != null -> {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.size(width = maxWidth, height = maxHeight),
                contentScale = ContentScale.Crop,
            )
            LocationPin(variant = pinVariant, modifier = Modifier.centerBottomEdge(this))
            PsstMapAttribution(modifier = Modifier.align(Alignment.BottomEnd))
        }
        else -> {
            StaticMapPlaceholder(
                painter = painterResource(R.drawable.blurred_map),
                canReload = failed,
                contentDescription = contentDescription,
                width = maxWidth,
                height = maxHeight,
                onLoadMapClick = { retryHash++ }
            )
        }
    }
}

/** PSST (D97): "© OpenMapTiles © OpenStreetMap contributors", visible on every static map. */
@Composable
private fun PsstMapAttribution(modifier: Modifier = Modifier) {
    Text(
        text = PsstMaps.ATTRIBUTION,
        style = ElementTheme.typography.fontBodyXsRegular.copy(fontSize = 9.sp),
        color = Color.Black,
        maxLines = 1,
        modifier = modifier
            .background(Color.White.copy(alpha = 0.7f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@PreviewsDayNight
@Composable
internal fun StaticMapViewPreview() = ElementPreview {
    StaticMapView(
        mapTilerConfig = null,
        location = Location(0.0, 0.0),
        zoom = 0.0,
        contentDescription = null,
        pinVariant = PinVariant.PinnedLocation,
        modifier = Modifier.size(400.dp),
    )
}
