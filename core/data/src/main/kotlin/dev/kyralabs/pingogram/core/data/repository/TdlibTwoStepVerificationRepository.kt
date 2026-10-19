package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toTwoStepVerificationState
import dev.kyralabs.pingogram.core.domain.model.TwoStepVerificationState
import dev.kyralabs.pingogram.core.domain.repository.TwoStepVerificationRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/** [TwoStepVerificationRepository] on TDLib's `getPasswordState`, which asks Telegram each time. */
@Singleton
class TdlibTwoStepVerificationRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : TwoStepVerificationRepository {
        override suspend fun state(): Outcome<TwoStepVerificationState> =
            client.request(TdApi.GetPasswordState()).map { it.toTwoStepVerificationState() }
    }
