package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toActiveSession
import dev.kyralabs.pingogram.core.data.mapper.toActiveSessions
import dev.kyralabs.pingogram.core.domain.model.ActiveSession
import dev.kyralabs.pingogram.core.domain.model.ActiveSessions
import dev.kyralabs.pingogram.core.domain.repository.SessionRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [SessionRepository] on TDLib's `getActiveSessions`, `terminateSession`, `terminateAllOtherSessions`, `confirmSession`
 * and `confirmQrCodeAuthentication`, each a request to Telegram. TDLib sorts the sessions
 * (td/telegram/AccountManager.cpp), and keeps terminations it has not sent yet in its database, sending them once it
 * can.
 */
@AccountScoped
class TdlibSessionRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : SessionRepository {
        override suspend fun activeSessions(): Outcome<ActiveSessions> =
            client.request(TdApi.GetActiveSessions()).map { it.toActiveSessions() }

        override suspend fun terminate(sessionId: Long): Outcome<Unit> =
            client.request(TdApi.TerminateSession(sessionId)).map {}

        override suspend fun terminateAllOthers(): Outcome<Unit> =
            client.request(TdApi.TerminateAllOtherSessions()).map {}

        override suspend fun confirm(sessionId: Long): Outcome<Unit> =
            client.request(TdApi.ConfirmSession(sessionId)).map {}

        override suspend fun confirmQrCodeLogin(link: String): Outcome<ActiveSession> =
            client.request(TdApi.ConfirmQrCodeAuthentication(link)).map { it.toActiveSession() }
    }
