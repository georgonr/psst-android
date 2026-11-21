/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.location.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.enterprise.api.remoteconfig.MapTilerConfig
import io.element.android.features.location.api.LocationService
import io.element.android.features.location.api.buildTimeMapTilerConfig
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PSST (D97): always the psst map styles (PsstMaps). A MapTiler configuration published by a homeserver
 * (enterprise remote config) is ignored: psst does not use MapTiler and its maps must go through the psst proxy.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultLocationService : LocationService {
    override val mapTilerConfig: StateFlow<MapTilerConfig?> = MutableStateFlow(buildTimeMapTilerConfig()).asStateFlow()
}
