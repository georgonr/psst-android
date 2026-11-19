/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

// PSST: build-time configuration of the psst client (homeserver lock lives in DefaultEnterpriseService).
object BuildTimeConfig {
    // PSST (D85): the only account provider the app may connect to — Matrix server_name psst.pro (user IDs
    // @name:psst.pro), homeserver (client API + MAS) at chat.psst.pro.
    const val PSST_SERVER_NAME = "psst.pro"
    const val PSST_HOMESERVER_URL = "https://chat.psst.pro"

    // PSST: public source code of this AGPL fork, linked from the About screen.
    const val PSST_SOURCE_URL = "https://github.com/georgonr/psst-android"

    // PSST: private branding (applicationId / app name).
    const val APPLICATION_ID = "eu.macet.psst"
    const val APPLICATION_NAME = "psst"
    // PSST: OAuth redirect scheme = reversed sub-domain of URL_WEBSITE host (app.psst.pro).
    val METADATA_HOST_REVERSED: String? = "pro.psst.app"
    val OAUTH_CLIENT_URL_PATH: String? = null
    // PSST: no element.io URLs. Legal pages are expected on app.psst.pro (same host as the OAuth client URI).
    val URL_WEBSITE: String? = "https://app.psst.pro"
    // PSST: OAuth logo_uri must be an image on the client_uri host (Element Web ships vector-icons/512.png).
    val URL_LOGO: String? = "https://app.psst.pro/vector-icons/512.png"
    // PSST: Settings > About > Copyright -> psst terms page (bilingual SK/EN); the AGPL source link is separate.
    val URL_COPYRIGHT: String? = "https://app.psst.pro/terms.html"
    val URL_ACCEPTABLE_USE: String? = "https://app.psst.pro/terms.html"
    val URL_PRIVACY: String? = "https://app.psst.pro/privacy.html"
    val URL_POLICY: String? = "https://app.psst.pro/privacy.html"
    val SERVICES_MAPTILER_BASE_URL: String? = null
    val SERVICES_MAPTILER_APIKEY: String? = null
    val SERVICES_MAPTILER_LIGHT_MAPID: String? = null
    val SERVICES_MAPTILER_DARK_MAPID: String? = null
    val SERVICES_POSTHOG_HOST: String? = null
    val SERVICES_POSTHOG_APIKEY: String? = null
    val SERVICES_SENTRY_DSN: String? = null
    val SERVICES_SENTRY_DSN_RUST: String? = null
    // PSST: no rageshake server (Element's rageshakes.element.io is not used).
    val BUG_REPORT_URL: String? = null
    val BUG_REPORT_APP_NAME: String? = null
    // PSST: Firebase Cloud Messaging is the only push provider (our own Firebase project "psst-macet" and our own
    // Sygnal at chat.psst.pro, event_id_only). UnifiedPush is removed so the app never asks for a second app.
    const val PUSH_CONFIG_INCLUDE_FIREBASE: Boolean = true
    const val PUSH_CONFIG_INCLUDE_UNIFIED_PUSH: Boolean = false
    val PUSHER_APP_ID_RELEASE: String? = "eu.macet.psst"
    val PUSHER_APP_ID_DEBUG: String? = "eu.macet.psst.debug"
    val PUSHER_APP_ID_NIGHTLY: String? = "eu.macet.psst.nightly"
}
