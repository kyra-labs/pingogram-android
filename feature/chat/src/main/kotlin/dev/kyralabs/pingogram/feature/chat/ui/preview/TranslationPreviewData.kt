package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.MessageAction
import dev.kyralabs.pingogram.feature.chat.ui.MessageActionsSheet
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.search.ChatSearchUiState
import dev.kyralabs.pingogram.feature.chat.ui.search.SearchHighlight
import dev.kyralabs.pingogram.feature.chat.ui.search.SearchResults
import dev.kyralabs.pingogram.feature.chat.ui.search.withSearchMatch
import dev.kyralabs.pingogram.feature.chat.ui.translation.BubbleTranslation
import dev.kyralabs.pingogram.feature.chat.ui.translation.TranslateBar
import dev.kyralabs.pingogram.feature.chat.ui.translation.withTranslations
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Message translation for previews and screenshot tests (`REQUIREMENTS.md` FR-C3): a chat in Spanish with the
 * translate bar offering English, the same chat translated (the newest message still on its way), a message translated
 * alone from Hebrew beside one left in Hebrew, the actions of a Spanish message with "Translate", and the translated
 * chat searched, its result's translation marking the word found.
 */
internal object TranslationPreviewData {
    val barOff = TranslateBar(toLanguage = "en-US", fromLanguage = "es", isOn = false)

    val barOn = barOff.copy(isOn = true)

    /** A private chat in Spanish, the bar offering to translate it into English. */
    val offered: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header = header(LUCIA, "Lucía Fernández"),
                content =
                    content(
                        lucia(NEWEST, "¿Quedamos mañana para tomar un café?", "9:48 AM", GroupPosition.Last),
                        lucia(NEWEST - 1, "Te mando la dirección del sitio nuevo.", "9:47 AM", GroupPosition.First),
                        bubble(
                            NEWEST - 2,
                            "Sure! What time works for you?",
                            time = "9:45 AM",
                            isOutgoing = true,
                            status = OutgoingStatus.Read,
                        ),
                        lucia(NEWEST - 3, "¡Hola! ¿Qué tal el viaje? Ya tengo ganas de verte.", "9:44 AM"),
                    ),
                translateBar = barOff,
            )

    /** The chat translated: its older messages in English, the newest on its way. */
    val translated: ChatUiState
        get() =
            offered.copy(translateBar = barOn).withTranslations(
                persistentMapOf(
                    NEWEST to BubbleTranslation.Translating,
                    NEWEST - 1 to shown("I'll send you the address of the new place."),
                    NEWEST - 3 to shown("Hi! How was the trip? I can't wait to see you."),
                ),
            )

    /** The word the search in [searched] finds. */
    const val SEARCH_QUERY = "address"

    /** The translated chat searched for [SEARCH_QUERY]: its one result shown, its translation marking the word. */
    val searched: ChatUiState
        get() = translated.withSearchMatch(SearchHighlight(NEWEST - 1, MatchHighlight.of(SEARCH_QUERY)))

    /** The search's bar for [searched]. */
    val searchFound =
        ChatSearchUiState(
            SearchResults.Found(1, 1, isTotalApproximate = false, hasOlder = false, hasNewer = false),
        )

    /** A message translated alone from Hebrew, the next one left in Hebrew; Telegram suggests no translation here. */
    val rightToLeft: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header = header(NOA, "נועה כהן"),
                content =
                    content(
                        bubble(HEBREW + 2, "Got it, thanks!", time = "6:03 PM", isOutgoing = true),
                        noa(HEBREW + 1, "תודה רבה!", "6:02 PM", GroupPosition.Last),
                        noa(HEBREW, "הפגישה נדחתה ליום חמישי בשבע בערב.", "6:01 PM", GroupPosition.First),
                    ),
            ).withTranslations(
                persistentMapOf(
                    HEBREW to
                        BubbleTranslation.Shown(
                            FormattedText(
                                "The meeting was moved to Thursday at seven in the evening.",
                                persistentListOf(),
                            ),
                            fromLanguage = "he",
                        ),
                ),
            )

    /** The actions of a Spanish message: "Translate" after copying. */
    val actions =
        MessageActionsSheet(
            messageId = NEWEST,
            reactionsMessageId = NEWEST,
            reactions = persistentListOf(),
            actions =
                persistentListOf(
                    MessageAction.Reply,
                    MessageAction.Copy,
                    MessageAction.Translate,
                    MessageAction.Forward,
                    MessageAction.Select,
                    MessageAction.Report,
                    MessageAction.Delete,
                ),
        )

    private fun shown(text: String) = BubbleTranslation.Shown(FormattedText(text, persistentListOf()), "es")

    private fun header(
        chatId: Long,
        title: String,
    ) = ChatHeader(
        chatId = chatId,
        title = UiText.Plain(title),
        photo = null,
        placeholderIcon = null,
        subtitle = ChatSubtitle.Status(UiText.resource(UiR.string.user_status_online)),
    )

    private fun lucia(
        id: Long,
        text: String,
        time: String,
        position: GroupPosition = GroupPosition.Single,
    ): ChatItem.Bubble = bubble(id, text, time, sender = UiText.Plain("Lucía"), senderId = LUCIA, position = position)

    private fun noa(
        id: Long,
        text: String,
        time: String,
        position: GroupPosition,
    ): ChatItem.Bubble = bubble(id, text, time, sender = UiText.Plain("נועה"), senderId = NOA, position = position)

    private const val LUCIA = 201L
    private const val NOA = 202L
    private const val NEWEST = 64L
    private const val HEBREW = 80L
}
