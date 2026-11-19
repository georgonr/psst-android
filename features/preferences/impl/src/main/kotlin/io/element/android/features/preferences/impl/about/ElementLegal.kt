/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2021-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

import androidx.annotation.StringRes
import io.element.android.features.preferences.impl.BuildConfig
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// PSST (D87): the legal pages live on psst.pro. BuildTimeConfig keeps app.psst.pro/*.html (they redirect here) because
// MAS requires the OAuth client's tos/policy URIs on the client_uri host (app.psst.pro).
private const val COPYRIGHT_URL = "https://psst.pro/terms"
private const val USE_POLICY_URL = "https://psst.pro/terms"
private const val PRIVACY_URL = "https://psst.pro/privacy"

// PSST: link to the source code of this AGPL-3.0 fork.
private const val SOURCE_CODE_URL = BuildConfig.URL_SOURCE_CODE

sealed class ElementLegal(
    @StringRes val titleRes: Int,
    val url: String,
) {
    data object Copyright : ElementLegal(CommonStrings.common_copyright, COPYRIGHT_URL)
    data object AcceptableUsePolicy : ElementLegal(CommonStrings.common_acceptable_use_policy, USE_POLICY_URL)
    data object PrivacyPolicy : ElementLegal(CommonStrings.common_privacy_policy, PRIVACY_URL)

    // PSST
    data object SourceCode : ElementLegal(R.string.psst_about_source_code, SOURCE_CODE_URL)

    // PSST (D87): Google Play / App Store — how to delete the account, and the operator's contact
    data object DeleteAccount : ElementLegal(R.string.psst_about_delete_account, "https://psst.pro/delete-account")
    data object Contact : ElementLegal(R.string.psst_about_contact, "mailto:psst@psst.pro")
}

fun getAllLegals(): ImmutableList<ElementLegal> {
    return persistentListOf(
        ElementLegal.Copyright,
        ElementLegal.AcceptableUsePolicy,
        ElementLegal.PrivacyPolicy,
        // PSST
        ElementLegal.DeleteAccount,
        ElementLegal.Contact,
        ElementLegal.SourceCode,
    )
}
