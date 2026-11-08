/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.dfd.fractal.R

@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val weights = listOf(
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
)

/** Titles and the FRACTAL wordmark. */
val SpaceGrotesk = FontFamily(weights.map { variableFont(R.font.space_grotesk, it) })

/** Body text. */
val Inter = FontFamily(weights.map { variableFont(R.font.inter, it) })

/** Upper case, wide tracking wordmark style ("FRACTAL"). */
val WordmarkStyle = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    letterSpacing = 0.35.em,
)

internal fun fractalTypography(): Typography {
    val base = Typography()
    fun TextStyle.title() = copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold)
    fun TextStyle.body() = copy(fontFamily = Inter)
    return base.copy(
        displayLarge = base.displayLarge.title(),
        displayMedium = base.displayMedium.title(),
        displaySmall = base.displaySmall.title(),
        headlineLarge = base.headlineLarge.title(),
        headlineMedium = base.headlineMedium.title(),
        headlineSmall = base.headlineSmall.title(),
        titleLarge = base.titleLarge.title(),
        titleMedium = base.titleMedium.title(),
        titleSmall = base.titleSmall.title(),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.body().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.body().copy(fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.body().copy(fontWeight = FontWeight.Medium),
    )
}
