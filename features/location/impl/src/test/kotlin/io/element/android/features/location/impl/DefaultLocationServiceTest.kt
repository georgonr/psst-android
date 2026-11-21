/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.location.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.features.location.api.PsstMaps
import org.junit.Test

class DefaultLocationServiceTest {
    @Test
    fun `PSST - the psst map styles are always used and the service is available`() {
        val locationService = DefaultLocationService()
        assertThat(locationService.mapTilerConfig.value).isEqualTo(PsstMaps.config)
        assertThat(locationService.isServiceAvailable().value).isTrue()
    }
}
