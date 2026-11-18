package dev.kyralabs.pingogram.feature.chat.ui.bot

import dev.kyralabs.pingogram.core.domain.repository.BotInfoRepository
import dev.kyralabs.pingogram.core.domain.repository.SnippetRepository
import javax.inject.Inject

/**
 * Where what "/" brings up in the composer comes from: the chat's bots and their commands ([bots]) and the account's
 * snippets (`REQUIREMENTS.md` FR-X2, [snippets]), which [ChatBotCommands] suggests together, the commands first.
 */
class SlashSources
    @Inject
    constructor(
        val bots: BotInfoRepository,
        val snippets: SnippetRepository,
    )
