/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.ftue.api.state

import kotlinx.coroutines.flow.StateFlow

/**
 * Service to manage the First Time User Experience state (aka Onboarding).
 */
interface FtueService {
    /** The current state of the FTUE. */
    val state: StateFlow<FtueState>

    /**
     * PSST (D81): this session was verified from an incoming request shown over the FTUE (QR code or emoji started by
     * another device); there is no "verified" screen of the FTUE to confirm, so continue the FTUE.
     */
    fun psstOnVerifiedByIncomingRequest() = Unit
}

/** The state of the FTUE. */
sealed interface FtueState {
    /** The FTUE state is unknown, nothing to do for now. */
    data object Unknown : FtueState

    /** The FTUE state is incomplete. The FTUE flow should be displayed. */
    data object Incomplete : FtueState

    /** The FTUE state is complete. The FTUE flow should not be displayed anymore. */
    data object Complete : FtueState
}
