/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.fractal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme

/**
 * Header with back button and title, then scrollable content over the theme background.
 *
 * With a gamepad the content gets the focus on entry, the legend shows A/B, then [extraHints],
 * then L/R to jump between the [SectionTitle]s.
 */
@Composable
fun FractalScreen(
    title: String,
    onBack: () -> Unit,
    extraHints: List<ButtonHint> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val focus = rememberScreenFocus()
    ButtonHints(screenHints(focus, extraHints))
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                // below the status bar when the window is edge to edge, like the older screens' app bar
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FractalIconButton(FractalIcons.Back, tr("Back"), onBack)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = FractalTheme.colors.text)
        }
        CompositionLocalProvider(LocalScreenFocus provides focus) {
            Column(
                Modifier
                    .fillMaxSize()
                    .screenFocusContainer(focus)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/** A Select, B Back, the screen's own hints, then L/R between sections when there are some. */
@Composable
fun screenHints(focus: ScreenFocus, extraHints: List<ButtonHint> = emptyList()): List<ButtonHint> = buildList {
    addAll(selectBackHints())
    addAll(extraHints)
    if (focus.sectionCount >= 2) {
        add(ButtonHint(listOf(PadButton.L1, PadButton.R1), tr("Section")) { focus.jumpSection(it == PadButton.R1) })
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = FractalTheme.colors.accent,
        modifier = Modifier.padding(top = 14.dp).sectionAnchor(),
    )
}
