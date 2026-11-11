package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DiffChange
import dev.kyralabs.pingogram.core.domain.model.DiffKind
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Telegram's AI editor: its styles from `updateTextCompositionStyles`, and one request per tap. */
class TdlibAiTextRepositoryTest {
    private val client = FakeTdlibClient()

    private fun td(text: String) = TdApi.FormattedText(text, emptyArray())

    private val draft = FormattedText("helo world", persistentListOf())

    @Test
    fun `follows the styles TDLib announces`() =
        runTest {
            val repository = TdlibAiTextRepository(client, backgroundScope)
            assertTrue(repository.styles.value.isEmpty())

            client.emit(
                TdApi.UpdateTextCompositionStyles(
                    arrayOf(
                        TdApi.TextCompositionStyle("formal", 0, "Formal", false, false, 0, "", 0, null),
                        TdApi.TextCompositionStyle("pirate", 5, "Pirate", true, false, 1204, "", 7, null),
                    ),
                ),
            )
            testScheduler.runCurrent()

            assertEquals(listOf("formal", "pirate"), repository.styles.value.map { it.name })
            assertEquals(1204, repository.styles.value[1].installCount)
        }

    @Test
    fun `fixing returns the text and its changes`() =
        runTest {
            val repository = TdlibAiTextRepository(client, backgroundScope)
            val diff =
                TdApi.DiffText(
                    "hello world",
                    arrayOf(TdApi.DiffEntity(0, 5, TdApi.DiffEntityTypeReplace("helo"))),
                )
            client.on(TdApi.FixTextWithAi::class, TdResult.Success(TdApi.FixedText(td("hello world"), diff)))

            val fixed = (repository.fix(draft) as Outcome.Success).value

            assertEquals(
                "helo world",
                client
                    .sentOf<TdApi.FixTextWithAi>()
                    .single()
                    .text.text,
            )
            assertEquals("hello world", fixed.text.text)
            assertEquals(listOf(DiffChange(0, 5, DiffKind.Replace("helo"))), fixed.diff.changes)
        }

    @Test
    fun `composing sends the style, the language and emoji, empty for none`() =
        runTest {
            val repository = TdlibAiTextRepository(client, backgroundScope)
            client.on(TdApi.ComposeTextWithAi::class, TdResult.Success(td("Hallo Welt")))

            repository.compose(draft, styleName = null, translateTo = "de", addEmoji = true)
            repository.compose(draft, styleName = "formal", translateTo = null, addEmoji = false)

            val sent = client.sentOf<TdApi.ComposeTextWithAi>()
            assertEquals(listOf("" to "de", "formal" to ""), sent.map { it.styleName to it.translateToLanguageCode })
            assertEquals(listOf(true, false), sent.map { it.addEmojis })
        }

    @Test
    fun `Telegram's limits come back as their errors`() =
        runTest {
            val repository = TdlibAiTextRepository(client, backgroundScope)
            client.on(TdApi.FixTextWithAi::class, TdResult.Failure(TdError(400, "AICOMPOSE_FLOOD_PREMIUM")))
            client.on(TdApi.AddTextCompositionStyle::class, TdResult.Failure(TdError(400, "TONES_SAVED_TOO_MANY")))

            assertEquals(Outcome.Failure(DomainError.AiEditLimitReached), repository.fix(draft))
            assertEquals(Outcome.Failure(DomainError.TooManyTextStyles), repository.addStyle("pirate"))
        }

    @Test
    fun `reads the limit of custom styles from TDLib's option`() =
        runTest {
            val repository = TdlibAiTextRepository(client, backgroundScope)
            client.on(TdApi.GetOption::class, TdResult.Success(TdApi.OptionValueInteger(3)))

            assertEquals(3, repository.addedStyleLimit())
            assertEquals("added_text_composition_style_count_max", client.sentOf<TdApi.GetOption>().single().name)
        }

    @Test
    fun `translates into Telegram's languages, Chinese in its two scripts`() =
        runTest {
            val languages = TdlibAiTextRepository(client, backgroundScope).languages

            assertTrue("de" in languages && "zh-CN" in languages && "zh-TW" in languages)
            assertEquals(languages.sorted(), languages)
        }
}
