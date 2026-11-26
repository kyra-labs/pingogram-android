package dev.kyralabs.pingogram.core.domain.keyword

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Keyword alerts' matching (board 30): whole words, any case, accents ignored, where in the text. */
class KeywordMatcherTest {
    @Test
    fun `finds a whole word in any letter case, where it is written`() {
        val text = "The public LAUNCH is moving."

        val match = KeywordMatcher(listOf("launch")).firstMatch(text)!!

        assertEquals("launch", match.word)
        assertEquals("LAUNCH", text.substring(match.start, match.end))
    }

    @Test
    fun `a word inside another word does not match`() {
        val matcher = KeywordMatcher(listOf("launch", "0.9.4"))

        assertNull(matcher.firstMatch("We relaunched it"))
        assertNull(matcher.firstMatch("launches tomorrow"))
        assertNull(matcher.firstMatch("version 0.9.45"))
        assertEquals("0.9.4", matcher.firstMatch("0.9.4 rollout is at 50%")!!.word)
    }

    @Test
    fun `accents are ignored either way, and the match keeps the text's own letters`() {
        val text = "Meet at the Café, then résumé review"

        val matches = KeywordMatcher(listOf("cafe", "resume")).matches(text)

        assertEquals(listOf("Café", "résumé"), matches.map { text.substring(it.start, it.end) })
        val naive = KeywordMatcher(listOf("NAIVE")).firstMatch("so naïve")!!
        assertEquals("naïve", "so naïve".substring(naive.start, naive.end))
    }

    @Test
    fun `greek accents fold, as latin ones do, typed as one letter or two`() {
        val greek = "Το άλφα"
        val alpha = KeywordMatcher(listOf("αλφα")).firstMatch(greek)!!
        assertEquals("άλφα", greek.substring(alpha.start, alpha.end))
        val typedApart = "a cafe\u0301"
        val cafe = KeywordMatcher(listOf("café")).firstMatch(typedApart)!!
        assertEquals("cafe\u0301", typedApart.substring(cafe.start, cafe.end))
    }

    @Test
    fun `marks that make another letter are kept, in Cyrillic, Japanese, Thai and Devanagari`() {
        // "й" is not "и".
        assertNull(KeywordMatcher(listOf("мои")).firstMatch("мой дом"))
        // "が" is not "か".
        assertNull(KeywordMatcher(listOf("か"), withinUnspacedScripts = true).firstMatch("ありがとう"))
        // "ไก่" (a chicken) is not "ไก", its tone mark gone.
        assertNull(KeywordMatcher(listOf("ไก"), withinUnspacedScripts = true).firstMatch("ไก่ทอด"))
        // "ज़" (za, with a nukta) is not "ज" (ja).
        assertNull(KeywordMatcher(listOf("जरा")).firstMatch("ज़रा सा"))
    }

    @Test
    fun `phrases, usernames and every match in order, the longer where two overlap`() {
        val text = "@ada, the Pingogram Pro deal: Pingogram is back"

        val matches = KeywordMatcher(listOf("Pingogram", "pingogram pro", "@ada")).matches(text)

        assertEquals(listOf("@ada", "pingogram pro", "Pingogram"), matches.map { it.word })
        assertNull(KeywordMatcher(listOf("@ada")).firstMatch("x@ada"))
    }

    @Test
    fun `within unspaced scripts a word matches inside the text, elsewhere still whole`() {
        val matcher = KeywordMatcher(listOf("剧透", "ネタバレ", "สปอยล์", "spoiler"), withinUnspacedScripts = true)

        assertEquals(listOf("剧透"), matcher.matches("这一集有剧透了").map { it.word })
        assertEquals(listOf("ネタバレ"), matcher.matches("これはネタバレです").map { it.word })
        assertEquals(listOf("สปอยล์"), matcher.matches("ระวังสปอยล์นะ").map { it.word })
        assertTrue(matcher.matches("spoilers ahead").isEmpty())
        assertEquals(listOf("spoiler"), matcher.matches("spoiler ahead").map { it.word })
    }

    @Test
    fun `keyword alerts keep whole words in unspaced scripts`() {
        assertTrue(KeywordMatcher(listOf("剧透")).matches("这一集有剧透了").isEmpty())
    }

    @Test
    fun `blank words look for nothing`() {
        val matcher = KeywordMatcher(listOf("  ", ""))

        assertTrue(matcher.isEmpty)
        assertTrue(matcher.matches("anything").isEmpty())
    }
}
