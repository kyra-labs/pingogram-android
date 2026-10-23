package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.graphics.shapes.RoundedPolygon
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The parts of the Add contact and Edit contact screens (docs/DESIGN_SYSTEM.md §6, "Add and edit contact"): a small top
// bar on surfaceContainer above a rounded sheet, Material's rounded tonal text fields on it, and the form's end.

/** The small top bar of a contact form: back ([onBack]) and [title] in `titleLargeEmphasized`. */
@Composable
internal fun ContactFormTopBar(
    title: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.contacts_back),
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
    )
}

/**
 * What a contact form's field holds and does: its [label], its [error] (null without one) or [supporting] text below
 * it, whether it can be changed ([enabled]), its keyboard and autofill hint ([autofill]), and a [prefix] such as `@`.
 */
internal class ContactFieldSpec(
    val label: String,
    val error: String? = null,
    val supporting: String? = null,
    val enabled: Boolean = true,
    val keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    val onImeAction: () -> Unit = {},
    val autofill: ContentType? = null,
    val prefix: String? = null,
    val textStyle: TextStyle? = null,
)

/**
 * A line of text in Material's rounded tonal style (`TextFieldDefaults.roundedShape`, `tonalColors()`), directly on the
 * sheet, as Edit profile's fields (`docs/DESIGN_SYSTEM.md` §6, "Text fields"), with what [spec] says below it; an error
 * is announced politely to screen readers.
 */
@Composable
internal fun ContactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    spec: ContactFieldSpec,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { spec.autofill?.let { contentType = it } },
        enabled = spec.enabled,
        textStyle = spec.textStyle ?: MaterialTheme.typography.bodyLarge,
        label = { Text(spec.label) },
        prefix = spec.prefix?.let { prefix -> { Text(prefix) } },
        supportingText =
            (spec.error ?: spec.supporting)?.let { text ->
                {
                    val announced = spec.error != null
                    Text(text, Modifier.semantics { if (announced) liveRegion = LiveRegionMode.Polite })
                }
            },
        isError = spec.error != null,
        keyboardOptions = spec.keyboardOptions,
        keyboardActions = KeyboardActions { spec.onImeAction() },
        singleLine = true,
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
}

/**
 * A single choice of a form, every label fitting: Material 3 Expressive's connected [ToggleButton]s, which morph on
 * press and to a full pill when checked, as Appearance's choices (`docs/DESIGN_SYSTEM.md` §6, "Settings rows").
 */
@Composable
internal fun ConnectedChoices(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    val typography = MaterialTheme.typography
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        labels.forEachIndexed { index, label ->
            val checked = index == selectedIndex
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(index) },
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                enabled = enabled,
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors = colors,
            ) {
                // At large font scales a label wraps rather than being cut (`REQUIREMENTS.md` §6.3).
                Text(
                    text = label,
                    maxLines = LABEL_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = if (checked) typography.labelLargeEmphasized else typography.labelLarge,
                )
            }
        }
    }
}

/**
 * While Telegram asks to wait (`FLOOD_WAIT`): the time left in the error role, and beside it a determinate
 * [CircularWavyProgressIndicator] that runs down from [totalSeconds] (`docs/DESIGN_SYSTEM.md` §5), gliding from one
 * second to the next on the effects spring. The indicator is hidden from screen readers; they hear the text as a polite
 * live region, under a minute in 15-second steps rather than every second.
 */
@Composable
internal fun FloodWaitNote(
    seconds: Long,
    totalSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val fraction by animateFloatAsState(
        targetValue = if (totalSeconds > 0) seconds.toFloat() / totalSeconds else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "FloodWait",
    )
    val text = stringResource(R.string.contacts_flood_wait, durationText(seconds))
    val announced = if (seconds in 1 until SECONDS_PER_MINUTE) (seconds + STEP - 1) / STEP * STEP else seconds
    val announcement = stringResource(R.string.contacts_flood_wait, durationText(announced))
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularWavyProgressIndicator(
            progress = { fraction },
            modifier = Modifier.clearAndSetSemantics {},
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = text,
            modifier =
                Modifier.clearAndSetSemantics {
                    contentDescription = announcement
                    liveRegion = LiveRegionMode.Polite
                },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** An [icon] on a [shape] of [containerColor], above a form's headline; decorative (`docs/DESIGN_SYSTEM.md` §4). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ContactBadge(
    shape: RoundedPolygon,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val badgeSize = MaterialTheme.sizes.headlineBadge
    Box(
        modifier = modifier.size(badgeSize).background(containerColor, shape.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(badgeSize / 2),
            tint = iconColor,
        )
    }
}

/** The text of [error] at [field] of a contact form; null for another field, or without an error. */
@Composable
internal fun ContactFieldError?.textFor(field: ContactField): String? {
    if (this == null || this.field != field) return null
    return when (val error = error) {
        null -> {
            when (field) {
                ContactField.FirstName, ContactField.LastName -> stringResource(R.string.contacts_error_first_name)
                ContactField.PhoneNumber -> stringResource(R.string.contacts_error_phone_number)
                ContactField.Username -> stringResource(R.string.contacts_error_username)
            }
        }

        DomainError.NotFound -> {
            stringResource(R.string.contacts_error_username)
        }

        else -> {
            errorText(error)
        }
    }
}

private const val SECONDS_PER_MINUTE = 60L

/** Rounding up never claims less time than is left. */
private const val STEP = 15L

private const val LABEL_MAX_LINES = 2
