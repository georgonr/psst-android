/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.location.api

import androidx.compose.runtime.staticCompositionLocalOf
import io.element.android.features.enterprise.api.remoteconfig.MapTilerConfig

/**
 * Provides the [MapTilerConfig] for the MapLibre maps, or null if no map provider is configured.
 */
val LocalMapTilerConfig = staticCompositionLocalOf { buildTimeMapTilerConfig() }

/**
 * The [MapTilerConfig] defined at build time, or null if no MapTiler API key has been provided.
 */
// PSST (D97): no MapTiler key — every build uses the psst map styles (see PsstMaps), so location is always available.
fun buildTimeMapTilerConfig(): MapTilerConfig? = PsstMaps.config
