package dev.kyralabs.pingogram.feature.chat.ui.inline

import dev.kyralabs.pingogram.core.domain.repository.DeviceLocationRepository
import dev.kyralabs.pingogram.core.domain.repository.InlineBotRepository
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotStartInbox
import javax.inject.Inject

/**
 * What the conversation's inline bots ([ChatInlineBots]) work with: the bots and their results ([bots]), the device's
 * location for bots that ask for it ([location]) with the user's answers ([consent]), and the start parameters of their
 * "Start" buttons on the way to a bot's chat ([starts]). One parameter of `ChatViewModel` for them, which already takes
 * one repository per kind of data it combines.
 */
class InlineBotRepositories
    @Inject
    constructor(
        val bots: InlineBotRepository,
        val location: DeviceLocationRepository,
        val consent: InlineLocationConsent,
        val starts: BotStartInbox,
    )
