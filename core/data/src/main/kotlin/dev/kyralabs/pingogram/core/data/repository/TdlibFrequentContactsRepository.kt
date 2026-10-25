package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.repository.FrequentContactsRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * [FrequentContactsRepository] on TDLib's option `disable_top_chats` (true: no suggestions, and TDLib clears the top
 * chats it counted), which `getTopChats` then answers with an error that search already takes as none. No cache, as
 * TDLib keeps the option.
 */
@AccountScoped
class TdlibFrequentContactsRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : FrequentContactsRepository {
        override val suggests: Flow<Boolean> = client.booleanOption(OPTION).map { disabled -> !disabled }

        override suspend fun setSuggests(suggests: Boolean): Outcome<Unit> = client.setBooleanOption(OPTION, !suggests)

        private companion object {
            const val OPTION = "disable_top_chats"
        }
    }
