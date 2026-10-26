package dev.kyralabs.pingogram.background

import dev.kyralabs.pingogram.core.domain.repository.DevicePushRepository
import javax.inject.Inject

/**
 * The `gms` flavor's [BackgroundConnection]: Firebase Cloud Messaging wakes the app for new messages
 * (`docs/adr/0004-push-delivery.md`), so nothing keeps running. [start] makes every account's push follow its login
 * ([DevicePushRepository], `docs/adr/0031-multiple-accounts.md`): each registers the push token with Telegram at every
 * login, and the last one signed in deletes it at logout.
 */
class FirebasePushConnection
    @Inject
    constructor(
        private val pushRepository: DevicePushRepository,
    ) : BackgroundConnection {
        override fun start() = pushRepository.start()
    }
