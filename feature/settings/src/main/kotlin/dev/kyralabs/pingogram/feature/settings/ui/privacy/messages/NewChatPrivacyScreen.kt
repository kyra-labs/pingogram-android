package dev.kyralabs.pingogram.feature.settings.ui.privacy.messages

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.NewChatPrivacy
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.RadioItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.launch
import java.text.NumberFormat

/** Who may message the user first: hoists [NewChatPrivacyViewModel] and shows its errors in snackbars. */
@Composable
internal fun NewChatPrivacyRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewChatPrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NewChatPrivacyEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    NewChatPrivacyScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * Who can message me (`docs/DESIGN_SYSTEM.md` §6, "Who can message me"): a settings page with "Everybody" and "My
 * contacts and Telegram Premium users" as radio rows, the second disabled with why where Telegram does not let the
 * account choose it (information only, never an offer), saved at once; what it means, and a price for non-contacts'
 * messages another app set, as the group's footer.
 */
@Composable
internal fun NewChatPrivacyScreen(
    state: NewChatPrivacyUiState,
    onEvent: (NewChatPrivacyEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_privacy_new_chats),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            NewChatPrivacyUiState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_privacy_new_chats_loading))
            }

            is NewChatPrivacyUiState.Error -> {
                SettingsError(state.error, onRetry = { onEvent(NewChatPrivacyEvent.Retry) })
            }

            is NewChatPrivacyUiState.Content -> {
                NewChatChoices(state, onSelect = { onEvent(NewChatPrivacyEvent.Select(it)) })
            }
        }
    }
}

@Composable
private fun NewChatChoices(
    state: NewChatPrivacyUiState.Content,
    onSelect: (allows: Boolean) -> Unit,
) {
    val privacy = state.privacy
    SettingsGroup(
        title = stringResource(R.string.settings_privacy_new_chats_question),
        footer = newChatFooter(privacy),
        rowsModifier = Modifier.selectableGroup(),
    ) {
        item(key = true) { shapes ->
            RadioItem(
                label = stringResource(R.string.settings_privacy_everybody),
                selected = privacy.allowsNonContacts,
                enabled = !state.isSaving,
                shapes = shapes,
                onClick = { onSelect(true) },
            )
        }
        item(key = false) { shapes ->
            RadioItem(
                label = stringResource(R.string.settings_privacy_new_chats_contacts),
                selected = !privacy.allowsNonContacts,
                enabled = !state.isSaving && (privacy.canRestrict || !privacy.allowsNonContacts),
                shapes = shapes,
                onClick = { onSelect(false) },
                summary =
                    if (privacy.canRestrict) {
                        null
                    } else {
                        stringResource(
                            R.string.settings_privacy_new_chats_premium,
                        )
                    },
            )
        }
    }
}

/** What the choice means, and the price of non-contacts' messages where another app set one. */
@Composable
private fun newChatFooter(privacy: NewChatPrivacy): String {
    val note = stringResource(R.string.settings_privacy_new_chats_note)
    if (privacy.paidMessageStars <= 0 || !privacy.allowsNonContacts) return note
    val stars = privacy.paidMessageStars
    val count = NumberFormat.getIntegerInstance(LocalLocale.current.platformLocale).format(stars)
    val price = pluralStringResource(R.plurals.settings_privacy_new_chats_paid, stars.toInt().coerceAtLeast(1), count)
    return "$note\n\n$price"
}
