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
    // PSST: the only homeserver (account provider) the app may connect to.
    const val PSST_HOMESERVER = "chat.macet.eu"

    // PSST: public source code of this AGPL fork, linked from the About screen.
    const val PSST_SOURCE_URL = "https://github.com/georgonr/psst-android"

    // PSST: private branding (applicationId / app name).
    const val APPLICATION_ID = "eu.macet.psst"
    const val APPLICATION_NAME = "psst"
    // PSST: OAuth redirect scheme = reversed sub-domain of URL_WEBSITE host (app.macet.eu).
    val METADATA_HOST_REVERSED: String? = "eu.macet.app"
    val OAUTH_CLIENT_URL_PATH: String? = null
    // PSST: no element.io URLs. Legal pages are expected on app.macet.eu (same host as the OAuth client URI).
    val URL_WEBSITE: String? = "https://app.macet.eu"
    // PSST: OAuth logo_uri must be an image on the client_uri host (Element Web ships vector-icons/512.png).
    val URL_LOGO: String? = "https://app.macet.eu/vector-icons/512.png"
    // PSST: Settings > About > Copyright -> psst terms page (bilingual SK/EN); the AGPL source link is separate.
    val URL_COPYRIGHT: String? = "https://app.macet.eu/terms.html"
    val URL_ACCEPTABLE_USE: String? = "https://app.macet.eu/terms.html"
    val URL_PRIVACY: String? = "https://app.macet.eu/privacy.html"
    val URL_POLICY: String? = "https://app.macet.eu/privacy.html"
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
    // PSST: Firebase (FCM via Element's Firebase project + matrix.org Sygnal) removed, UnifiedPush only.
    const val PUSH_CONFIG_INCLUDE_FIREBASE: Boolean = false
    const val PUSH_CONFIG_INCLUDE_UNIFIED_PUSH: Boolean = true
    val PUSHER_APP_ID_RELEASE: String? = "eu.macet.psst"
    val PUSHER_APP_ID_DEBUG: String? = "eu.macet.psst.debug"
    val PUSHER_APP_ID_NIGHTLY: String? = "eu.macet.psst.nightly"
}
