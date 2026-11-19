/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.location.impl.common

import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

/**
 * Common configuration values for the map.
 */
object MapDefaults {
    val defaultCameraPosition = CameraPosition(
        center = Position(0.0, 0.0),
        zoom = 0.0,
    )
    const val DEFAULT_ZOOM = 15.0

    // PSST (D87): location permissions removed from the app (Google Play); nothing to request.
    val permissions = emptyList<String>()
}
