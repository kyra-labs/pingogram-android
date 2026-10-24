package dev.kyralabs.pingogram.feature.settings.ui

import android.icu.text.ListFormatter
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptions
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.toImmutableList
import java.text.NumberFormat

// The texts of the privacy settings (REQUIREMENTS.md FR-S1): their names, their choices and their exceptions.

/** The privacy settings Pingogram shows, in their order. */
internal val PrivacySettings = PrivacySetting.entries.toImmutableList()

/** The main choices, from the most to the least open. */
internal val PrivacyChoices = PrivacyChoice.entries.toImmutableList()

/** The setting's name: a row of Privacy and security, and the title of its page. */
@StringRes
internal fun PrivacySetting.titleRes(): Int =
    when (this) {
        PrivacySetting.PhoneNumber -> R.string.settings_privacy_phone_number
        PrivacySetting.LastSeen -> R.string.settings_privacy_last_seen
        PrivacySetting.ProfilePhoto -> R.string.settings_privacy_profile_photo
        PrivacySetting.Birthdate -> R.string.settings_privacy_birthdate
        PrivacySetting.ForwardedMessages -> R.string.settings_privacy_forwarded_messages
        PrivacySetting.Calls -> R.string.settings_privacy_calls
        PrivacySetting.ChatInvites -> R.string.settings_privacy_chat_invites
        PrivacySetting.VoiceMessages -> R.string.settings_privacy_voice_messages
    }

/** What the setting decides, as a question: the heading of its choices. */
@StringRes
internal fun PrivacySetting.questionRes(): Int =
    when (this) {
        PrivacySetting.PhoneNumber -> R.string.settings_privacy_phone_number_question
        PrivacySetting.LastSeen -> R.string.settings_privacy_last_seen_question
        PrivacySetting.ProfilePhoto -> R.string.settings_privacy_profile_photo_question
        PrivacySetting.Birthdate -> R.string.settings_privacy_birthdate_question
        PrivacySetting.ForwardedMessages -> R.string.settings_privacy_forwarded_messages_question
        PrivacySetting.Calls -> R.string.settings_privacy_calls_question
        PrivacySetting.ChatInvites -> R.string.settings_privacy_chat_invites_question
        PrivacySetting.VoiceMessages -> R.string.settings_privacy_voice_messages_question
    }

@StringRes
internal fun PrivacyChoice.labelRes(): Int =
    when (this) {
        PrivacyChoice.Everybody -> R.string.settings_privacy_everybody
        PrivacyChoice.Contacts -> R.string.settings_privacy_contacts
        PrivacyChoice.Nobody -> R.string.settings_privacy_nobody
    }

/** The choice of [rules], "My contacts", with "+ exceptions" when an exception changes what it allows. */
@Composable
@ReadOnlyComposable
internal fun privacySummary(rules: PrivacyRules): String {
    val choice = stringResource(rules.choice.labelRes())
    return if (rules.hasExceptions) stringResource(R.string.settings_privacy_with_exceptions, choice) else choice
}

/** Who is always allowed: "2 users and members of 1 group", in the language's way of listing. */
@Composable
@ReadOnlyComposable
internal fun alwaysAllowedText(exceptions: PrivacyExceptions): String =
    listText(
        users = exceptions.allowedUserCount,
        groups = exceptions.allowedChatCount,
        premiumUsers = exceptions.allowsPremiumUsers,
    )

/** Who is never allowed: "3 users", in the language's way of listing. */
@Composable
@ReadOnlyComposable
internal fun neverAllowedText(exceptions: PrivacyExceptions): String =
    listText(users = exceptions.restrictedUserCount, groups = exceptions.restrictedChatCount, premiumUsers = false)

@Composable
@ReadOnlyComposable
private fun listText(
    users: Int,
    groups: Int,
    premiumUsers: Boolean,
): String {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = NumberFormat.getIntegerInstance(locale)
    val parts =
        buildList {
            if (users >
                0
            ) {
                add(pluralStringResource(R.plurals.settings_privacy_user_count, users, numbers.format(users)))
            }
            if (groups > 0) {
                add(pluralStringResource(R.plurals.settings_privacy_group_count, groups, numbers.format(groups)))
            }
            if (premiumUsers) add(stringResource(R.string.settings_privacy_premium_users))
        }
    return ListFormatter.getInstance(locale).format(parts)
}
