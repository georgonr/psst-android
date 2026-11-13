/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl

import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.login.api.LoginIntentResolver
import io.element.android.features.login.api.LoginParams

@ContributesBinding(AppScope::class)
class DefaultLoginIntentResolver : LoginIntentResolver {
    private companion object {
        // PSST
        const val PSST_LOGIN_LINKS_DISABLED = true
    }

    override fun parse(uriString: String): LoginParams? {
        // PSST: mobile.element.io login links are not supported, the app is locked to its own homeserver.
        @Suppress("ConstantConditionIf")
        if (PSST_LOGIN_LINKS_DISABLED) return null
        val uri = uriString.toUri()
        if (uri.host != "mobile.element.io") return null
        if (uri.path.orEmpty().startsWith("/element").not()) return null
        val accountProvider = uri.getQueryParameter("account_provider") ?: return null
        val loginHint = uri.getQueryParameter("login_hint")
        return LoginParams(
            accountProvider = accountProvider,
            loginHint = loginHint,
        )
    }
}
