package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.phone.CountryPickerSheet
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR
import java.util.Locale as JavaLocale

/**
 * "Who can vote", in a channel only (td_api.tl `inputMessagePoll.members_only`, `country_codes`): Everyone, Subscribers
 * only and Only in some countries as radio rows (`SegmentTick`); the last shows the countries picked as [InputChip]s
 * (the flag and the name, a close symbol that takes it out) and "Add country", an [AssistChip] that opens the phone
 * number's country picker ([CountryPickerSheet]) for one more, with a note while none is picked.
 */
internal fun LazyListScope.pollVotersItems(
    sheet: PollSheet,
    editor: PollEditor,
) {
    if (!sheet.isChannel) return
    item(key = VOTERS) {
        Column(Modifier.selectableGroup()) {
            FormSectionHeader(stringResource(R.string.poll_voters))
            PollVoters.entries.forEach { option -> VotersRow(option, editor) }
        }
    }
    if (editor.voters == PollVoters.Countries) item(key = COUNTRIES) { CountryChips(editor) }
}

@Composable
private fun VotersRow(
    option: PollVoters,
    editor: PollEditor,
) {
    val haptics = LocalHapticFeedback.current
    val selected = editor.voters == option
    ListItem(
        modifier =
            Modifier.selectable(selected = selected, role = Role.RadioButton) {
                if (!selected) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    editor.voters = option
                }
            },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(stringResource(option.label()))
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CountryChips(editor: PollEditor) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val locale = Locale.current.platformLocale
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            for (code in editor.countries) {
                val name = countryName(code, locale)
                val remove = stringResource(R.string.poll_country_remove, name)
                InputChip(
                    selected = false,
                    onClick = { editor.countries.remove(code) },
                    label = { Text("${flagOf(code)} $name") },
                    modifier = Modifier.semantics { contentDescription = remove },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_close),
                            contentDescription = null,
                            modifier = Modifier.padding(start = spacing.extraSmall),
                        )
                    },
                    shapes = InputChipDefaults.shapes(),
                )
            }
            AssistChip(
                onClick = { picking = true },
                label = { Text(stringResource(R.string.poll_country_add)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.padding(end = spacing.extraSmall),
                    )
                },
            )
        }
        if (editor.countries.isEmpty()) {
            Text(
                text = stringResource(R.string.poll_country_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (picking) PollCountryPicker(editor) { picking = false }
}

/** The phone number's country picker, for one more country of the poll's; it closes once one is picked. */
@Composable
private fun PollCountryPicker(
    editor: PollEditor,
    viewModel: PollCountriesViewModel = hiltViewModel(),
    onClose: () -> Unit,
) {
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.open(editor.countries) }
    CountryPickerSheet(
        state = picker,
        onQueryChange = viewModel::setQuery,
        onSelect = { code ->
            if (code !in editor.countries) editor.countries += code
            onClose()
        },
        onDismiss = onClose,
    )
}

/** Country [code]'s name in [locale]'s language. */
private fun countryName(
    code: String,
    locale: JavaLocale,
): String =
    JavaLocale
        .Builder()
        .setRegion(code)
        .build()
        .getDisplayCountry(locale)
        .ifEmpty { code }

/** The flag emoji of country [code] (two regional indicator symbols). */
internal fun flagOf(code: String): String =
    code
        .uppercase(JavaLocale.ROOT)
        .takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
        ?.let { letters ->
            letters.map { String(Character.toChars(REGIONAL_INDICATOR_A + (it - 'A'))) }.joinToString("")
        }.orEmpty()

private fun PollVoters.label(): Int =
    when (this) {
        PollVoters.Everyone -> R.string.poll_voters_everyone
        PollVoters.Subscribers -> R.string.poll_voters_subscribers
        PollVoters.Countries -> R.string.poll_voters_countries
    }

private const val REGIONAL_INDICATOR_A = 0x1F1E6
private const val VOTERS = "voters"
private const val COUNTRIES = "countries"
