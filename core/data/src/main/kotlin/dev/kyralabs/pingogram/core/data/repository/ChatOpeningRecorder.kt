package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.repository.ChatOpeningsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Records a chat as opened ([ChatOpeningsRepository.markOpened], `REQUIREMENTS.md` FR-X24) each time its conversation
 * comes on screen ([OpenChats]), from when the account's graph is made: a [TdlibUpdateCache] only to be made then.
 */
@AccountScoped
class ChatOpeningRecorder
    @Inject
    constructor(
        openChats: OpenChats,
        openings: ChatOpeningsRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : TdlibUpdateCache {
        init {
            scope.launch {
                var shown = emptySet<Long>()
                openChats.chatIds.collect { open ->
                    (open - shown).forEach { openings.markOpened(it) }
                    shown = open
                }
            }
        }
    }
