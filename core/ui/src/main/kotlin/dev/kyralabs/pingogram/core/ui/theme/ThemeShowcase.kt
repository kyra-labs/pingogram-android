package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

/**
 * Every color role, type style and shape of the active theme, for previews, screenshot tests and
 * design reviews. Each section fits a compact phone screen on its own.
 */
@Composable
fun ThemeShowcase(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
    ) {
        ColorRolesShowcase()
        TypeScaleShowcase()
        ShapesShowcase()
    }
}

@Composable
internal fun ColorRolesShowcase(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val swatches =
        listOf(
            Swatch("primary", colors.primary, colors.onPrimary),
            Swatch("primaryContainer", colors.primaryContainer, colors.onPrimaryContainer),
            Swatch("secondary", colors.secondary, colors.onSecondary),
            Swatch("secondaryContainer", colors.secondaryContainer, colors.onSecondaryContainer),
            Swatch("tertiary", colors.tertiary, colors.onTertiary),
            Swatch("tertiaryContainer", colors.tertiaryContainer, colors.onTertiaryContainer),
            Swatch("error", colors.error, colors.onError),
            Swatch("errorContainer", colors.errorContainer, colors.onErrorContainer),
            Swatch("primaryFixed", colors.primaryFixed, colors.onPrimaryFixed),
            Swatch("primaryFixedDim", colors.primaryFixedDim, colors.onPrimaryFixedVariant),
            Swatch("secondaryFixed", colors.secondaryFixed, colors.onSecondaryFixed),
            Swatch("secondaryFixedDim", colors.secondaryFixedDim, colors.onSecondaryFixedVariant),
            Swatch("tertiaryFixed", colors.tertiaryFixed, colors.onTertiaryFixed),
            Swatch("tertiaryFixedDim", colors.tertiaryFixedDim, colors.onTertiaryFixedVariant),
            Swatch("surfaceDim", colors.surfaceDim, colors.onSurface),
            Swatch("surfaceBright", colors.surfaceBright, colors.onSurface),
            Swatch("surfaceContainerLowest", colors.surfaceContainerLowest, colors.onSurface),
            Swatch("surfaceContainerLow", colors.surfaceContainerLow, colors.onSurface),
            Swatch("surfaceContainer", colors.surfaceContainer, colors.onSurface),
            Swatch("surfaceContainerHigh", colors.surfaceContainerHigh, colors.onSurface),
            Swatch("surfaceContainerHighest", colors.surfaceContainerHighest, colors.onSurface),
            Swatch("surfaceVariant", colors.surfaceVariant, colors.onSurfaceVariant),
            Swatch("surface", colors.surface, colors.onSurface),
            Swatch("background", colors.background, colors.onBackground),
            Swatch("inverseSurface", colors.inverseSurface, colors.inverseOnSurface),
            Swatch("inversePrimary", colors.inverseSurface, colors.inversePrimary),
            Swatch("outline", colors.outline, colors.surface),
            Swatch("outlineVariant", colors.outlineVariant, colors.onSurface),
            Swatch("scrim 32%", colors.scrim.copy(alpha = 0.32f), colors.onSurface),
            Swatch("online", MaterialTheme.pingogramColors.online, colors.surface),
        )
    Column(
        modifier = modifier.padding(MaterialTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        swatches.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
                row.forEach { swatch ->
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .background(swatch.container, MaterialTheme.shapes.small)
                                .padding(MaterialTheme.spacing.small),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(swatch.name, color = swatch.content, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TypeScaleShowcase(modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    val rows =
        listOf(
            TypeRow("displayLarge", type.displayLarge, type.displayLargeEmphasized, sample = "Ping"),
            TypeRow("displayMedium", type.displayMedium, type.displayMediumEmphasized, sample = "Ping"),
            TypeRow("displaySmall", type.displaySmall, type.displaySmallEmphasized, sample = "Ping"),
            TypeRow("headlineLarge", type.headlineLarge, type.headlineLargeEmphasized, sample = "Ping"),
            TypeRow("headlineMedium", type.headlineMedium, type.headlineMediumEmphasized, sample = "Ping"),
            TypeRow("headlineSmall", type.headlineSmall, type.headlineSmallEmphasized, sample = "Ping"),
            TypeRow("titleLarge", type.titleLarge, type.titleLargeEmphasized),
            TypeRow("titleMedium", type.titleMedium, type.titleMediumEmphasized),
            TypeRow("titleSmall", type.titleSmall, type.titleSmallEmphasized),
            TypeRow("bodyLarge", type.bodyLarge, type.bodyLargeEmphasized),
            TypeRow("bodyMedium", type.bodyMedium, type.bodyMediumEmphasized),
            TypeRow("bodySmall", type.bodySmall, type.bodySmallEmphasized),
            TypeRow("labelLarge", type.labelLarge, type.labelLargeEmphasized),
            TypeRow("labelMedium", type.labelMedium, type.labelMediumEmphasized),
            TypeRow("labelSmall", type.labelSmall, type.labelSmallEmphasized),
            TypeRow("bodyLarge", type.bodyLarge, type.bodyLargeEmphasized, italic = true),
        )
    Column(
        modifier = modifier.padding(MaterialTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)) {
                TypeSample(row, emphasized = false, Modifier.weight(1f))
                TypeSample(row, emphasized = true, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TypeSample(
    row: TypeRow,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    val style = if (emphasized) row.emphasized else row.base
    val name = (if (emphasized) "${row.name}Emphasized" else row.name) + (if (row.italic) " italic" else "")
    Column(modifier) {
        Text(
            text = row.sample,
            style = if (row.italic) style.copy(fontStyle = FontStyle.Italic) else style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private class TypeRow(
    val name: String,
    val base: TextStyle,
    val emphasized: TextStyle,
    val sample: String = "Ping 12:45",
    val italic: Boolean = false,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShapesShowcase(modifier: Modifier = Modifier) {
    val shapes = MaterialTheme.shapes
    val scale =
        listOf(
            "extraSmall" to shapes.extraSmall,
            "small" to shapes.small,
            "medium" to shapes.medium,
            "large" to shapes.large,
            "largeIncreased" to shapes.largeIncreased,
            "extraLarge" to shapes.extraLarge,
            "extraLargeIncreased" to shapes.extraLargeIncreased,
            "extraExtraLarge" to shapes.extraExtraLarge,
            "full" to CircleShape,
        )
    Column(
        modifier = modifier.padding(MaterialTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        scale.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)) {
                row.forEach { (name, shape) ->
                    ShapeSample(
                        name = name,
                        shape = shape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        square = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            repeat(7) { id ->
                val colors = MaterialTheme.colorScheme.avatarPlaceholderColors(id.toLong())
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .background(colors.container, avatarPlaceholderPolygon(id.toLong()).toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${'A' + id}", color = colors.content, style = MaterialTheme.typography.titleMediumEmphasized)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge)) {
            listOf(false, true).forEach { morphed ->
                ShapeSample(
                    name = if (morphed) "morphed" else "morph start",
                    shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, morphed),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    square = true,
                )
            }
        }
    }
}

/** A [shape] sample: a 96 dp square for polygons, a full-width bar for corner sizes. */
@Composable
private fun ShapeSample(
    name: String,
    shape: Shape,
    color: Color,
    square: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        Box(
            modifier =
                (if (square) Modifier.size(96.dp) else Modifier.fillMaxWidth().height(56.dp))
                    .background(color, shape),
        )
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private class Swatch(
    val name: String,
    val container: Color,
    val content: Color,
)

@PingogramPreviews
@Composable
private fun ThemeShowcasePreview() {
    PingogramPreview { ThemeShowcase() }
}
