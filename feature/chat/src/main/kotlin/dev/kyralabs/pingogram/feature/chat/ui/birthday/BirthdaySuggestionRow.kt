package dev.kyralabs.pingogram.feature.chat.ui.birthday

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.ui.component.BirthdaySuggestion
import dev.kyralabs.pingogram.core.ui.component.BirthdaySuggestionCard
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.topics.showError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A birthday suggestion in the conversation (`docs/DESIGN_SYSTEM.md` §6 "Birthday suggestions", the owner's board 16b):
 * `core:ui`'s [BirthdaySuggestionCard] centred like a service message, its answer and privacy from the conversation's
 * [LocalBirthdaySuggestions]. Only this item reads them, so an answer recomposes its card alone. Without a host
 * (previews, screenshots) it waits for an answer and its buttons do nothing.
 */
@Composable
internal fun BirthdaySuggestionRow(
    messageId: Long,
    birthday: ChatItem.ServiceBirthday,
    modifier: Modifier = Modifier,
) {
    val host = LocalBirthdaySuggestions.current
    val answers by (host?.answers ?: NoAnswers).collectAsStateWithLifecycle()
    val suggestion =
        BirthdaySuggestion(
            birthdate = birthday.birthdate,
            name = birthday.name.resolve(),
            outgoing = birthday.outgoing,
            privacy = answers.privacy?.let { stringResource(it.label) },
            status = answers.statusOf(messageId, birthday.birthdate),
        )
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BirthdaySuggestionCard(
            suggestion = suggestion,
            onAccept = { host?.accept(messageId, birthday.birthdate) },
            onDecline = { host?.decline(messageId) },
        )
    }
}

private val NoAnswers: StateFlow<BirthdayAnswers> = MutableStateFlow(BirthdayAnswers())

private val PrivacyChoice.label: Int
    get() =
        when (this) {
            PrivacyChoice.Everybody -> R.string.chat_birthday_privacy_everybody
            PrivacyChoice.Contacts -> R.string.chat_birthday_privacy_contacts
            PrivacyChoice.Nobody -> R.string.chat_birthday_privacy_nobody
        }

/** Shows Telegram's refusal of an answer in the conversation's snackbar. */
@Composable
internal fun BirthdayErrorsEffect(
    errors: Flow<DomainError>,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    LaunchedEffect(errors) {
        errors.collect { error -> snackbarHostState.showError(errorMessage(resources, error, showDetails), error) }
    }
}
