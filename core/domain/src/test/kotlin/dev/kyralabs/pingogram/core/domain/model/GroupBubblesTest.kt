package dev.kyralabs.pingogram.core.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** When the experimental look fills a group's bubbles with their senders' hues. */
class GroupBubblesTest {
    @Test
    fun `automatic is vivid up to 50 members and while the count is unknown, tinted above`() {
        assertTrue(GroupBubbles.Automatic.isVivid(50))
        assertTrue(GroupBubbles.Automatic.isVivid(null))
        assertFalse(GroupBubbles.Automatic.isVivid(51))
    }

    @Test
    fun `vivid and tinted hold whatever the group's size`() {
        assertTrue(GroupBubbles.Vivid.isVivid(1_000))
        assertFalse(GroupBubbles.Tinted.isVivid(3))
    }
}
