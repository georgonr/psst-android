/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.enterprise.impl

import androidx.compose.ui.graphics.Color
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.features.enterprise.api.BugReportUrl
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.mapCatchingExceptions
import io.element.android.libraries.core.uri.ensureProtocol
import io.element.android.libraries.matrix.api.ClientUrlContentFetcher
import io.element.android.libraries.matrix.api.TemporaryMatrixClientFactory
import io.element.android.libraries.matrix.api.accountprovider.AccountProvider
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

@ContributesBinding(AppScope::class)
class DefaultEnterpriseService(
    private val temporaryMatrixClientFactory: TemporaryMatrixClientFactory,
    private val jsonProvider: JsonProvider,
    private val dispatchers: CoroutineDispatchers,
) : EnterpriseService {
    override suspend fun isEnterpriseUser(sessionId: SessionId) = false
    override suspend fun tweakMasUrl(url: String, urlContentFetcher: ClientUrlContentFetcher) = url
    // PSST: the app is locked to a single homeserver (chat.macet.eu). With a singleton allow list and
    // canConnectToAnyAccountProvider() == false, the onboarding only offers "Sign in to chat.macet.eu" (+ QR code and
    // "Create account" on chat.macet.eu via its MAS), never the "change account provider" / free text server input,
    // and every other entry point (login links, QR code login, Element Classic import) is rejected by the access control.
    override fun accountProviderAllowList(): List<AccountProvider> = listOf(psstAccountProvider)
    override fun canConnectToAnyAccountProvider(): Boolean = false
    override suspend fun isAllowedToConnectToAccountProvider(accountProvider: AccountProvider) =
        accountProviderAllowList().any { it.matches(accountProvider) }

    // PSST: the upstream implementation fetches /.well-known/element/element.json to detect homeservers that require
    // Element Pro. psst only ever talks to its own homeserver, so this check (and the extra request) is not needed.
    override suspend fun isElementProEnforced(serverName: String): Boolean = false

    @Suppress("unused")
    private suspend fun upstreamIsElementProEnforced(serverName: String): Boolean = withContext(dispatchers.io) {
        val temporaryMatrixClient = temporaryMatrixClientFactory.create(serverName).getOrElse { return@withContext false }
        temporaryMatrixClient.use { client ->
            val baseUrl = serverName.ensureProtocol().removeSuffix("/")
            // We'll always perform a network request here since we're not interested in any cached value.
            client.getUrl("$baseUrl/.well-known/element/element.json")
                .mapCatchingExceptions { response ->
                    val remoteConfig = jsonProvider().decodeFromString<MinimalEnterpriseConfig>(String(response))
                    remoteConfig.enforceElementPro ?: false
                }
                .onFailure {
                    Timber.e(it, "Failed to fetch enterprise config for checking if Element Pro is enforced for $serverName")
                }
                .getOrElse { false }
        }
    }

    override suspend fun overrideBrandColor(sessionId: SessionId?, brandColor: String?) = Unit

    // PSST: brand (accent) colour #38BDF8, see PsstColors.
    override fun brandColorsFlow(sessionId: SessionId?): Flow<Color?> {
        return flowOf(PsstColors.accent)
    }

    override fun semanticColorsFlow(sessionId: SessionId?): Flow<SemanticColorsLightDark> {
        return flowOf(PsstColors.semanticColors)
    }

    override fun firebasePushGateway(): String? = null
    override fun unifiedPushDefaultPushGateway(): String? = null

    // PSST: bug reports (rageshakes) are disabled, nothing is sent to Element's rageshake server.
    override fun bugReportUrlFlow(sessionId: SessionId?): Flow<BugReportUrl> {
        return flowOf(BugReportUrl.Disabled)
    }

    override fun getNoisyNotificationChannelId(sessionId: SessionId): String? = null
}

// PSST: the only allowed account provider. Server discovery (/.well-known/matrix/client) happens on this host.
internal val psstAccountProvider = AccountProvider.Generic(
    serverName = BuildConfig.PSST_HOMESERVER,
)

/**
 * A minimal version of the enterprise config that only contains the fields we need to check if Element Pro is enforced.
 */
@Serializable
private data class MinimalEnterpriseConfig(
    @SerialName("enforce_element_pro") val enforceElementPro: Boolean? = null,
)
