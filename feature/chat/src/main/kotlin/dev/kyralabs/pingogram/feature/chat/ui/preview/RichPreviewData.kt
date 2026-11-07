package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.domain.model.RichCaption
import dev.kyralabs.pingogram.core.domain.model.RichMessage
import dev.kyralabs.pingogram.core.domain.model.TableCell
import dev.kyralabs.pingogram.core.domain.model.TableCellAlignment
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.today
import dev.kyralabs.pingogram.feature.chat.ui.rich.RichMessageUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Rich messages for previews and screenshot tests: a channel's post with a title, a paragraph with formatting and a
 * link, and two sections, one holding a list and a photo, the other a table and a quote; [collapsed] as Telegram
 * sends it (both closed), [expanded] with both opened, and [partial] with only its start sent, its rest loading.
 */
internal object RichPreviewData {
    private const val CHANNEL = -200L
    private const val POST = 40L

    // "New components" in bold, "Read the guide" a link.
    private const val BOLD_LENGTH = 14
    private const val LINK_START = 51
    private const val LINK_LENGTH = 14

    private val header =
        ChatHeader(
            chatId = CHANNEL,
            title = UiText.Plain("Material Design"),
            photo = null,
            placeholderIcon = null,
            subtitle = ChatSubtitle.Status(UiText.plural(R.plurals.chat_subtitle_subscribers, 12_480, 12_480)),
        )

    private fun text(
        value: String,
        vararg entities: TextEntity,
    ) = FormattedText(value, entities.toList().toImmutableList())

    private fun cell(
        value: String,
        isHeader: Boolean = false,
    ) = TableCell(text(value), isHeader, colspan = 1, rowspan = 1, align = TableCellAlignment.Start)

    private fun features(minithumbnail: Minithumbnail?) =
        RichBlock.Details(
            id = 0,
            header = text("What's new"),
            blocks =
                persistentListOf(
                    RichBlock.ListBlock(
                        persistentListOf(
                            item("•", "Shapes that morph as you press"),
                            item("•", "Springs for every motion"),
                            RichBlock.ListItem(
                                "",
                                persistentListOf(RichBlock.Paragraph(text("Variable type"))),
                                hasCheckbox = true,
                                isChecked = true,
                            ),
                        ),
                    ),
                    RichBlock.Photo(
                        index = 0,
                        photo = ContentPreviewData.photo(minithumbnail, firstFileId = 70).photo,
                        caption = RichCaption(text("The new button group"), FormattedText.Empty),
                        url = "",
                        hasSpoiler = false,
                    ),
                ),
            isOpen = false,
        )

    private val details =
        RichBlock.Details(
            id = 1,
            header = text("Sizes"),
            blocks =
                persistentListOf(
                    RichBlock.Table(
                        caption = FormattedText.Empty,
                        rows =
                            persistentListOf(
                                persistentListOf(cell("Size", isHeader = true), cell("Height", isHeader = true)),
                                persistentListOf(cell("Small"), cell("40 dp")),
                                persistentListOf(cell("Medium"), cell("56 dp")),
                            ),
                        isBordered = true,
                        isStriped = false,
                    ),
                    RichBlock.BlockQuote(
                        persistentListOf(RichBlock.Paragraph(text("Expressive design makes products feel alive."))),
                        credit = text("The Material team"),
                    ),
                ),
            isOpen = false,
        )

    private fun item(
        label: String,
        value: String,
    ) = RichBlock.ListItem(label, persistentListOf(RichBlock.Paragraph(text(value))), false, false)

    private fun message(
        isFull: Boolean,
        minithumbnail: Minithumbnail?,
    ) = RichMessage(
        blocks =
            persistentListOf(
                RichBlock.Kicker(text("RELEASE NOTES")),
                RichBlock.Title(text("Material 3 Expressive")),
                RichBlock.Paragraph(
                    text(
                        "New components, shapes and motion, now in Compose. Read the guide.",
                        TextEntity(0, BOLD_LENGTH, TextEntityType.Bold),
                        TextEntity(LINK_START, LINK_LENGTH, TextEntityType.TextUrl("https://m3.material.io")),
                    ),
                ),
                features(minithumbnail),
                details,
            ),
        isRtl = false,
        isFull = isFull,
    )

    private fun post(
        minithumbnail: Minithumbnail?,
        isFull: Boolean = true,
        ui: RichMessageUi? = null,
    ): ChatUiState =
        ChatUiState(
            header = header,
            content =
                content(
                    bubble(
                        POST,
                        "",
                        time = "8:00 AM",
                        sender = UiText.Plain("Material Design"),
                        senderId = CHANNEL,
                        content = MessageContent.RichMessage(message(isFull, minithumbnail)),
                    ).copy(rich = ui),
                    ChatItem.DateSeparator(epochDay = 2, date = today),
                ),
        )

    /** The photo shows [minithumbnail] (`rememberSampleMinithumbnail` in previews), as no file loads there. */
    fun collapsed(minithumbnail: Minithumbnail? = null): ChatUiState = post(minithumbnail)

    fun expanded(minithumbnail: Minithumbnail? = null): ChatUiState =
        post(minithumbnail, ui = RichMessageUi(toggledSections = persistentSetOf(0, 1)))

    fun partial(minithumbnail: Minithumbnail? = null): ChatUiState =
        post(minithumbnail, isFull = false, ui = RichMessageUi(isLoadingFull = true))
}
