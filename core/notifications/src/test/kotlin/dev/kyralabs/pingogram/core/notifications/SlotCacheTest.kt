package dev.kyralabs.pingogram.core.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * [SlotCache]: a slot's values stay while its account does, and an account that takes a freed slot gets its own
 * (`docs/audit/performance-review-2026-11.md`, W4).
 */
class SlotCacheTest {
    private val cache = SlotCache<Any, Any>()

    @Test
    fun `the same account in its slot gets the same value, made once`() {
        val account = Any()
        var made = 0

        val first = cache.get(1, account) { Any().also { made++ } }
        val second = cache.get(1, account) { Any().also { made++ } }

        assertSame(first, second)
        assertEquals(1, made)
    }

    @Test
    fun `another account in a slot a removed one left gets a value of its own`() {
        val removed = Any()
        val added = Any()

        val old = cache.get(1, removed) { "removed's" }
        val new = cache.get(1, added) { "added's" }

        assertNotSame(old, new)
        assertEquals("added's", new)
    }
}
