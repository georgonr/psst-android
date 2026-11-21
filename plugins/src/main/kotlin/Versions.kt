/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * Version codes are quite sensitive, because there is a mix between bundle and APKs.
 * Max versionCode allowed by the PlayStore (for information):
 * 2_100_000_000
 *
 * Also note that the versionCode is multiplied by 10 in app/build.gradle.kts:
 * ```
 * output.versionCode.set((output.versionCode.orNull ?: 0) * 10 + abiCode)
 * ```
 * We are using a CalVer-like approach to version the application. The version code is calculated as follows:
 * - 2 digits for the year
 * - 2 digits for the month
 * - 1 (or 2) digits for the release number
 * Note that the version codes need to be greater than the ones calculated for the previous releases, so we use
 * year on 4 digits for this internal value.
 * So for instance, the first release of Jan 2025 will have:
 * - the version name: 25.01.0
 * - the version code: 20250100a (202_501_00a) where `a` stands for the architecture code
 */

/**
 * Year of the version on 2 digits.
 * Do not update this value. it is updated by the release script.
 */
private const val versionYear = 26

/**
 * Month of the version on 2 digits. Value must be in [1,12].
 * Do not update this value. it is updated by the release script.
 */
private const val versionMonth = 11

/**
 * Release number in the month. Value must be in [0,99].
 * Do not update this value. it is updated by the release script.
 */
// PSST (D94..D97): psst 26.11.1 (disappearing messages, screen protection, location, no Element remnants).
private const val versionReleaseNumber = 1

object Versions {
    /**
     * Base version code that will be set in the Android Manifest.
     * The value will be modified at build time to add the ABI code when APK are build.
     * AAB will have a ABI code of 0.
     * See comment above for the calculation method.
     */
    // PSST: every build has its own number (CI: PSST_BUILD_NUMBER = GitHub run number) and date (PSST_BUILD_DATE,
    // YYYYMMDD). versionCode = (21_000_000 + build) * 10 + ABI, always above upstream's 202611xxx so it installs
    // over older psst APKs. Shown in Settings → About as "26.11.1-b42+2026-11-15". See BUILD.md / DECISIONS D54.
    val PSST_BUILD_NUMBER: Int = System.getenv("PSST_BUILD_NUMBER")?.toIntOrNull() ?: 0
    val PSST_BUILD_DATE: String = System.getenv("PSST_BUILD_DATE")
        ?: java.time.LocalDate.now(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
    val VERSION_CODE = 21_000_000 + PSST_BUILD_NUMBER
    private val UPSTREAM_VERSION_NAME = "$versionYear.${versionMonth.toString().padStart(2, '0')}.$versionReleaseNumber"
    // No spaces/parentheses: versionName is part of the User-Agent ("psst/<versionName> (<device>; …)") and
    // servers take the device name from the first parentheses (D60).
    val VERSION_NAME = UPSTREAM_VERSION_NAME + "-b$PSST_BUILD_NUMBER+" +
        "${PSST_BUILD_DATE.substring(0, 4)}-${PSST_BUILD_DATE.substring(4, 6)}-${PSST_BUILD_DATE.substring(6, 8)}"

    /**
     * Compile SDK version. Must be updated when a new Android version is released.
     * When updating COMPILE_SDK, please also update BUILD_TOOLS_VERSION.
     */
    const val COMPILE_SDK = 37

    /**
     * Build tools version. Must be kept in sync with COMPILE_SDK.
     * The value is used by the release script.
     */
    @Suppress("unused")
    private const val BUILD_TOOLS_VERSION = "37.0.0"

    /**
     * Target SDK version. Should be kept up to date with COMPILE_SDK.
     */
    const val TARGET_SDK = 37

    /**
     * Minimum SDK version for FOSS builds.
     */
    private const val MIN_SDK_FOSS = 24

    /**
     * Minimum SDK version for Enterprise builds.
     */
    private const val MIN_SDK_ENTERPRISE = 33

    /**
     * minSdkVersion that will be set in the Android Manifest.
     */
    val minSdk = if (isEnterpriseBuild) MIN_SDK_ENTERPRISE else MIN_SDK_FOSS

    /**
     * Java version used for compilation.
     * Update this value when you want to use a newer Java version.
     */
    private const val JAVA_VERSION = 21

    val javaVersion: JavaVersion = JavaVersion.toVersion(JAVA_VERSION)
    val javaLanguageVersion: JavaLanguageVersion = JavaLanguageVersion.of(JAVA_VERSION)

    // Perform some checks on the values to avoid releasing with bad values
    init {
        require(versionMonth in 1..12) { "versionMonth must be in [1,12]" }
        require(versionReleaseNumber in 0..99) { "versionReleaseNumber must be in [0,99]" }
        require(BUILD_TOOLS_VERSION.startsWith(COMPILE_SDK.toString())) { "When updating COMPILE_SDK, please also update BUILD_TOOLS_VERSION" }
    }
}
