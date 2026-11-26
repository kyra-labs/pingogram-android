package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import dev.kyralabs.pingogram.core.domain.model.DeleteChatAction
import dev.kyralabs.pingogram.core.domain.model.DeleteChatOptions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** When Cleanup suggestions offer Leave (`REQUIREMENTS.md` FR-X24): a group or channel the user is in, not owns. */
class OffersLeaveTest {
    @Test
    fun `offered for a group or channel the user is in and does not own`() {
        assertTrue(offersLeave(DeleteChatOptions(DeleteChatAction.Leave, canDeleteForEveryone = false)))
    }

    @Test
    fun `never for one the user owns, a chat to delete, or when TDLib could not tell`() {
        assertFalse(offersLeave(DeleteChatOptions(DeleteChatAction.Leave, canDeleteForEveryone = true)))
        assertFalse(offersLeave(DeleteChatOptions(DeleteChatAction.Delete, canDeleteForEveryone = false)))
        assertFalse(offersLeave(null))
    }
}
