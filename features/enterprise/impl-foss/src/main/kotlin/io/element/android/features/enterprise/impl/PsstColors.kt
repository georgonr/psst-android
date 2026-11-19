/*
 * Copyright 2026 PSST (psst.pro), derived from Element X Android.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.enterprise.impl

import androidx.compose.ui.graphics.Color
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.generated.compoundColorsDark
import io.element.android.compound.tokens.generated.compoundColorsLight

/**
 * PSST: brand colours. The accent colour is #38BDF8 (Tailwind sky-400).
 *
 * In the dark theme #38BDF8 is used as is. In the light theme #38BDF8 on white has a contrast ratio of ~2.1:1, which is
 * too low for text and icons, so darker shades of the same hue (sky-600 / sky-700) are used for text and foreground
 * elements, while #38BDF8 is kept for decorative fills (badges, selection).
 */
object PsstColors {
    val accent = Color(0xFF38BDF8)

    private val sky100 = Color(0xFFE0F2FE)
    private val sky200 = Color(0xFFBAE6FD)
    private val sky300 = Color(0xFF7DD3FC)
    private val sky500 = Color(0xFF0EA5E9)
    private val sky600 = Color(0xFF0284C7)
    private val sky700 = Color(0xFF0369A1)
    private val sky800 = Color(0xFF075985)
    private val sky900 = Color(0xFF0C4A6E)
    private val sky950 = Color(0xFF082F49)

    private fun SemanticColors.psstLight() = copy(
        bgAccentRest = sky600,
        bgAccentHovered = sky700,
        bgAccentPressed = sky800,
        bgAccentSelected = accent.copy(alpha = 0.16f),
        bgAccentSubtle = sky100,
        bgBadgeAccent = sky200,
        borderAccentPrimary = sky600,
        borderAccentSubtle = accent,
        iconAccentPrimary = sky600,
        iconAccentTertiary = sky500,
        textActionAccent = sky700,
        textBadgeAccent = sky900,
    )

    private fun SemanticColors.psstDark() = copy(
        bgAccentRest = accent,
        bgAccentHovered = sky300,
        bgAccentPressed = sky200,
        bgAccentSelected = accent.copy(alpha = 0.24f),
        bgAccentSubtle = sky950,
        bgBadgeAccent = sky800,
        borderAccentPrimary = accent,
        borderAccentSubtle = sky600,
        iconAccentPrimary = accent,
        iconAccentTertiary = sky500,
        textActionAccent = accent,
        textBadgeAccent = sky100,
    )

    val semanticColors = SemanticColorsLightDark(
        light = compoundColorsLight.psstLight(),
        dark = compoundColorsDark.psstDark(),
    )
}
