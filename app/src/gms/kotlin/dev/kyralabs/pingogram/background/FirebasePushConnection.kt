package dev.kyralabs.pingogram.background

import dev.kyralabs.pingogram.core.domain.repository.PushRepository
import javax.inject.Inject

/**
 * The `gms` flavor's [BackgroundConnection]: Firebase Cloud Messaging wakes the app for new messages
 * (`docs/adr/0004-push-delivery.md`), so nothing keeps running. [start] makes [PushRepository] follow the login: it
 * registers the push token with Telegram at every login and deletes it at logout.
 */
class FirebasePushConnection
    @Inject
    constructor(
        private val pushRepository: PushRepository,
    ) : BackgroundConnection {
        override fun start() = pushRepository.start()
    }
