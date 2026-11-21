package dev.kyralabs.pingogram.feature.settings.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class CountSentenceTest {
    @Test
    fun `a count at the start leaves the words after it`() {
        assertEquals(
            CountSentence(before = "", count = "3", after = "things anyone on Telegram can see"),
            splitAtCount("3 things anyone on Telegram can see", "3"),
        )
    }

    @Test
    fun `21 in Russian, whose one form covers it, keeps its words`() {
        val count = String.format(Locale.forLanguageTag("ru"), "%d", 21)

        assertEquals(
            CountSentence(before = "", count = "21", after = "элемент, видимый всем в Telegram"),
            splitAtCount("$count элемент, видимый всем в Telegram", count),
        )
    }

    @Test
    fun `a count at the end leaves the words before it`() {
        assertEquals(
            CountSentence(before = "Visible to anyone on Telegram:", count = "4", after = ""),
            splitAtCount("Visible to anyone on Telegram: 4", "4"),
        )
    }

    @Test
    fun `a count in the middle splits the sentence around it`() {
        assertEquals(
            CountSentence(before = "Anyone sees", count = "2", after = "of your things"),
            splitAtCount("Anyone sees 2 of your things", "2"),
        )
    }

    @Test
    fun `digits formatted for Arabic are found as the sentence formats them`() {
        val locale = Locale.forLanguageTag("ar-EG")
        val count = String.format(locale, "%d", 3)
        val sentence = String.format(locale, "%1\$d عناصر يراها الجميع", 3)

        assertEquals(
            CountSentence(before = "", count = count, after = "عناصر يراها الجميع"),
            splitAtCount(sentence, count),
        )
    }

    @Test
    fun `a translation without the count keeps all its words after the count`() {
        assertEquals(
            CountSentence(before = "", count = "5", after = "things anyone on Telegram can see"),
            splitAtCount("things anyone on Telegram can see", "5"),
        )
    }
}
