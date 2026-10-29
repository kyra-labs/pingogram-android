package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toExceptionLists
import dev.kyralabs.pingogram.core.data.mapper.toPrivacyRules
import dev.kyralabs.pingogram.core.data.mapper.toPrivacySetting
import dev.kyralabs.pingogram.core.data.mapper.toTdPrivacySetting
import dev.kyralabs.pingogram.core.data.mapper.withChoice
import dev.kyralabs.pingogram.core.data.mapper.withExceptions
import dev.kyralabs.pingogram.core.domain.model.NewChatPrivacy
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptionLists
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacyRulesChange
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.domain.repository.PrivacyRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [PrivacyRepository] on TDLib's `getUserPrivacySettingRules`, `setUserPrivacySettingRules` and
 * `updateUserPrivacySettingRules`. TDLib asks Telegram for a setting's rules once and answers from memory after,
 * following Telegram's updates (td/telegram/PrivacyManager.cpp), so nothing is cached here and this is no
 * `TdlibUpdateCache`.
 *
 * Changing the main choice reads the rules first and sends them back with only the main rule replaced, so the users
 * and groups always or never allowed stay exactly as they are (`withChoice`). TDLib sends changes of one setting in
 * order, each after the one before.
 */
@AccountScoped
class TdlibPrivacyRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : PrivacyRepository {
        override suspend fun rules(setting: PrivacySetting): Outcome<PrivacyRules> =
            client.request(TdApi.GetUserPrivacySettingRules(setting.toTdPrivacySetting())).map { it.toPrivacyRules() }

        override val changes: Flow<PrivacyRulesChange> =
            client.updates
                .filterIsInstance<TdApi.UpdateUserPrivacySettingRules>()
                .mapNotNull { update ->
                    update.setting.toPrivacySetting()?.let { PrivacyRulesChange(it, update.rules.toPrivacyRules()) }
                }

        override suspend fun setChoice(
            setting: PrivacySetting,
            choice: PrivacyChoice,
        ): Outcome<PrivacyRules> {
            val current =
                when (val rules = client.request(TdApi.GetUserPrivacySettingRules(setting.toTdPrivacySetting()))) {
                    is Outcome.Success -> rules.value
                    is Outcome.Failure -> return rules
                }
            val rules = current.withChoice(choice)
            // toPrivacyRules first: TDLib may change the rules it was sent.
            val changed = rules.toPrivacyRules()
            return client.request(TdApi.SetUserPrivacySettingRules(setting.toTdPrivacySetting(), rules)).map { changed }
        }

        override suspend fun exceptions(setting: PrivacySetting): Outcome<PrivacyExceptionLists> =
            client
                .request(TdApi.GetUserPrivacySettingRules(setting.toTdPrivacySetting()))
                .map { it.toExceptionLists() }

        override suspend fun setExceptions(
            setting: PrivacySetting,
            lists: PrivacyExceptionLists,
        ): Outcome<PrivacyRules> {
            val current =
                when (val rules = client.request(TdApi.GetUserPrivacySettingRules(setting.toTdPrivacySetting()))) {
                    is Outcome.Success -> rules.value
                    is Outcome.Failure -> return rules
                }
            val rules = current.withExceptions(lists)
            val changed = rules.toPrivacyRules()
            return client.request(TdApi.SetUserPrivacySettingRules(setting.toTdPrivacySetting(), rules)).map { changed }
        }

        override suspend fun showsReadDate(): Outcome<Boolean> =
            client.request(TdApi.GetReadDatePrivacySettings()).map { it.showReadDate }

        override suspend fun setShowsReadDate(shows: Boolean): Outcome<Unit> =
            client.request(TdApi.SetReadDatePrivacySettings(TdApi.ReadDatePrivacySettings(shows))).map {}

        override suspend fun newChatPrivacy(): Outcome<NewChatPrivacy> {
            val settings =
                when (val result = client.request(TdApi.GetNewChatPrivacySettings())) {
                    is Outcome.Success -> result.value
                    is Outcome.Failure -> return result
                }
            // TDLib answers options from memory; an option it does not know is empty, which means no.
            val canRestrict =
                when (val option = client.request(TdApi.GetOption(CAN_SET_NEW_CHAT_PRIVACY))) {
                    is Outcome.Success -> (option.value as? TdApi.OptionValueBoolean)?.value == true
                    is Outcome.Failure -> false
                }
            return Outcome.Success(
                NewChatPrivacy(
                    allowsNonContacts = settings.allowNewChatsFromUnknownUsers,
                    paidMessageStars = settings.incomingPaidMessageStarCount,
                    canRestrict = canRestrict,
                ),
            )
        }

        override suspend fun setAllowsNonContacts(allows: Boolean): Outcome<Unit> {
            val current =
                when (val result = client.request(TdApi.GetNewChatPrivacySettings())) {
                    is Outcome.Success -> result.value
                    is Outcome.Failure -> return result
                }
            val stars = if (allows) current.incomingPaidMessageStarCount else 0
            return client.request(TdApi.SetNewChatPrivacySettings(TdApi.NewChatPrivacySettings(allows, stars))).map {}
        }

        private companion object {
            const val CAN_SET_NEW_CHAT_PRIVACY = "can_set_new_chat_privacy_settings"
        }
    }
