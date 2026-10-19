package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toPrivacyRules
import dev.kyralabs.pingogram.core.data.mapper.toPrivacySetting
import dev.kyralabs.pingogram.core.data.mapper.toTdPrivacySetting
import dev.kyralabs.pingogram.core.data.mapper.withChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
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
import javax.inject.Singleton

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
@Singleton
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
    }
