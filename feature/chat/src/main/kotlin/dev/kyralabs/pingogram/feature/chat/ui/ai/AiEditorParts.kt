package dev.kyralabs.pingogram.feature.chat.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import dev.kyralabs.pingogram.core.domain.model.DiffKind
import dev.kyralabs.pingogram.core.domain.model.DiffText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.ImmutableList
import java.util.Locale as JavaLocale

/**
 * Telegram's styles as [FilterChip]s wrapping in a [FlowRow], one radio group for screen readers, under "Style" in
 * `titleSmallEmphasized` `primary`; while translating "Keep its style" comes first ([canKeep]). A user's custom style
 * says so after its title.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StyleChips(
    styles: ImmutableList<AiStyleUi>,
    selected: String?,
    canKeep: Boolean,
    onSelect: (String?) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Text(
            text = stringResource(R.string.ai_editor_style),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        if (styles.isEmpty() && !canKeep) {
            Text(
                text = stringResource(R.string.ai_editor_no_styles),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            val chip: @Composable (String?, String) -> Unit = { name, label ->
                FilterChip(
                    selected = name == selected,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(name)
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge.userContent) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                    shapes = FilterChipDefaults.shapes(),
                )
            }
            if (canKeep) chip(null, stringResource(R.string.ai_editor_keep_style))
            for (style in styles) {
                val label =
                    if (style.isCustom) {
                        stringResource(
                            R.string.ai_editor_custom_style,
                            style.title,
                        )
                    } else {
                        style.title
                    }
                chip(style.name, label)
            }
        }
    }
}

/**
 * The language to translate into: a read-only filled [TextField] ("Translate into") with
 * [ExposedDropdownMenuDefaults.TrailingIcon], opening the languages by their names in the app's language, sorted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageField(
    language: String,
    languages: ImmutableList<String>,
    onSelect: (String) -> Unit,
) {
    val locale = Locale.current.platformLocale
    val named = remember(languages, locale) { languages.map { it to languageName(it, locale) }.sortedBy { it.second } }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val field = rememberTextFieldState()
    val shown = languageName(language, locale)
    LaunchedEffect(shown) { field.setTextAndPlaceCursorAtEnd(shown) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            state = field,
            readOnly = true,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text(stringResource(R.string.ai_editor_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((code, name) in named) {
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(code)
                    },
                )
            }
        }
    }
}

/** The name of language [code] ("de", "zh-TW") in [locale]'s language. */
internal fun languageName(
    code: String,
    locale: JavaLocale,
): String =
    JavaLocale
        .forLanguageTag(code)
        .getDisplayName(locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

/**
 * The result's text in `bodyLarge` as user content. A fix shows what changed: added and replacing parts in `primary`
 * and underlined, removed parts struck through in `onSurfaceVariant`, so the changes show by more than their color;
 * screen readers hear the fixed text and how many changes it has.
 */
@Composable
internal fun AiResultText(result: AiResult.Done) {
    val diff = result.diff
    val colors = MaterialTheme.colorScheme
    val changes = diff?.changes?.size ?: 0
    val description = stringResource(R.string.ai_editor_result_description, result.text.text)
    val changed =
        if (changes > 0) {
            pluralStringResource(R.plurals.ai_editor_changes, changes, changes)
        } else {
            null
        }
    val text =
        remember(diff, result.text, colors) {
            diff?.takeIf { it.changes.isNotEmpty() }?.let { diffText(it, colors.primary, colors.onSurfaceVariant) }
                ?: AnnotatedString(result.text.text)
        }
    Text(
        text = text,
        modifier =
            Modifier.clearAndSetSemantics {
                contentDescription = listOfNotNull(description, changed).joinToString(". ")
            },
        style = MaterialTheme.typography.bodyLarge.userContent,
    )
}

/** The fix's text with its changes marked: added and replacing parts in [accent], underlined; removed ones struck. */
internal fun diffText(
    diff: DiffText,
    accent: Color,
    removed: Color,
): AnnotatedString =
    buildAnnotatedString {
        var at = 0
        for (change in diff.changes.sortedBy { it.offset }) {
            val start = change.offset.coerceIn(at, diff.text.length)
            val end = (change.offset + change.length).coerceIn(start, diff.text.length)
            append(diff.text, at, start)
            val style =
                when (change.kind) {
                    DiffKind.Delete -> {
                        SpanStyle(color = removed, textDecoration = TextDecoration.LineThrough)
                    }

                    DiffKind.Insert, is DiffKind.Replace -> {
                        SpanStyle(
                            color = accent,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                }
            withStyle(style) { append(diff.text, start, end) }
            at = end
        }
        append(diff.text, at, diff.text.length)
    }
