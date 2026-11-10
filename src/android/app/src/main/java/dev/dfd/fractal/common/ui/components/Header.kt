package dev.dfd.fractal.common.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.ui.theme.FractalTheme

/** Section title between settings rows, in the style of [dev.dfd.fractal.common.ui.fractal.SectionTitle]. */
@Composable
fun Header(text: String?, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 6.dp),
        text = (text ?: "").uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = FractalTheme.colors.accent,
    )
}
