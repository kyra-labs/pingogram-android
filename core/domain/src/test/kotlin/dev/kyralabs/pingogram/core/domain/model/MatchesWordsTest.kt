package dev.kyralabs.pingogram.core.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class MatchesWordsTest {
    @Test
    fun `a name matches when each word typed starts one of its words`() {
        assertTrue("Ada Lovelace".matchesWords("", Locale.US))
        assertTrue("Ada Lovelace".matchesWords("LOVE", Locale.US))
        assertTrue("Ada Lovelace".matchesWords("lov ad", Locale.US))
        assertFalse("Ada Lovelace".matchesWords("velace", Locale.US))
        assertFalse("Ada Lovelace".matchesWords("ada grace", Locale.US))
    }
}
