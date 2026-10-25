package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import kotlinx.coroutines.flow.Flow

/** How the "Archived chats" row shows in the chat list, kept on this device and across logouts. */
interface ArchiveRowRepository {
    /** The style, then every change. Never fails: a style that cannot be read is [ArchiveRowStyle.Full]. */
    val style: Flow<ArchiveRowStyle>

    /** Saves [style]; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setStyle(style: ArchiveRowStyle): Outcome<Unit>
}
