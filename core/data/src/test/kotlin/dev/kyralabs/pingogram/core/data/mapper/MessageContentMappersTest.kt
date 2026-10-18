package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.Audio
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.domain.model.ExpiredMedia
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.LinkPreview
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollOption
import dev.kyralabs.pingogram.core.domain.model.PollType
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import dev.kyralabs.pingogram.core.domain.model.Venue
import dev.kyralabs.pingogram.core.domain.model.Video
import dev.kyralabs.pingogram.core.domain.model.VideoNote
import dev.kyralabs.pingogram.core.domain.model.VoiceNote
import dev.kyralabs.pingogram.core.domain.model.Waveform
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MessageContentMappersTest {
    private val minithumbnail = Minithumbnail(40, 30, byteArrayOf(1, 2, 3))
    private val jpegThumbnail = Thumbnail(ThumbnailFormat.Jpeg, 320, 180, FileRef(20, 1_024))
    private val photo =
        Photo(
            persistentListOf(
                PhotoSize("m", 320, 240, FileRef(10, 1_024)),
                PhotoSize("x", 800, 600, FileRef(11, 1_024)),
            ),
            minithumbnail,
        )

    @Test
    fun `a text keeps its entities`() {
        val content =
            TdApiFixtures.messageText(
                "Hello @bob",
                arrayOf(TdApiFixtures.textEntity(6, 4, TdApi.TextEntityTypeMention())),
            )

        assertEquals(
            MessageContent.Text(
                FormattedText("Hello @bob", persistentListOf(TextEntity(6, 4, TextEntityType.Mention))),
                linkPreview = null,
            ),
            content.map(),
        )
    }

    @Test
    fun `a link preview of an article carries its texts and photo`() {
        val content =
            TdApiFixtures.messageText(
                "https://example.org/article",
                linkPreview = TdApiFixtures.linkPreview(),
            )

        assertEquals(
            LinkPreview(
                url = "https://example.org/article",
                displayUrl = "example.org/article",
                siteName = "Example",
                title = "Example article",
                description = text("An example page"),
                photo = photo,
                showLargeMedia = true,
                showAboveText = false,
            ),
            (content.map() as MessageContent.Text).linkPreview,
        )
    }

    @Test
    fun `a link preview of a user shows the user's photo`() {
        val chatPhoto =
            TdApi.ChatPhoto(1, 0, TdApiFixtures.minithumbnail(), arrayOf(TdApiFixtures.photoSize()), null, null, null)
        val content =
            TdApiFixtures.messageText(
                linkPreview = TdApiFixtures.linkPreview(type = TdApi.LinkPreviewTypeUser(chatPhoto, false)),
            )

        assertEquals(
            Photo(persistentListOf(PhotoSize("x", 800, 600, FileRef(11, 1_024))), minithumbnail),
            (content.map() as MessageContent.Text).linkPreview?.photo,
        )
    }

    @Test
    fun `a link preview without a picture has no photo`() {
        val content =
            TdApiFixtures.messageText(
                linkPreview = TdApiFixtures.linkPreview(type = TdApi.LinkPreviewTypeMessage()),
            )

        assertNull((content.map() as MessageContent.Text).linkPreview?.photo)
    }

    @Test
    fun `a photo carries its sizes, minithumbnail, caption and spoiler`() {
        val content = TdApiFixtures.messagePhoto(caption = TdApiFixtures.formattedText("Sunset"), hasSpoiler = true)

        assertEquals(
            MessageContent.Photo(
                photo,
                text("Sunset"),
                hasSpoiler = true,
                isSecret = false,
                showCaptionAboveMedia = false,
            ),
            content.map(),
        )
    }

    @Test
    fun `a video carries its size, duration, thumbnails and file`() {
        val content = TdApiFixtures.messageVideo(caption = TdApiFixtures.formattedText("Clip"))

        assertEquals(
            MessageContent.Video(
                Video(
                    30,
                    1280,
                    720,
                    minithumbnail,
                    jpegThumbnail,
                    supportsStreaming = true,
                    file = FileRef(21, 5_000_000),
                ),
                text("Clip"),
                hasSpoiler = false,
                isSecret = false,
                showCaptionAboveMedia = false,
            ),
            content.map(),
        )
    }

    @Test
    fun `an animation keeps its MIME type`() {
        val content =
            TdApi.MessageAnimation(
                TdApiFixtures.animation(),
                TdApiFixtures.formattedText(""),
                false,
                false,
                false,
            )

        assertEquals(
            MessageContent.Animation(
                Animation(3, 480, 270, "video/mp4", null, jpegThumbnail, FileRef(22, 1_024)),
                text(""),
                hasSpoiler = false,
                isSecret = false,
                showCaptionAboveMedia = false,
            ),
            content.map(),
        )
    }

    @Test
    fun `stickers keep their format`() {
        val formats =
            listOf(TdApi.StickerFormatWebp(), TdApi.StickerFormatTgs(), TdApi.StickerFormatWebm()).map { format ->
                (TdApiFixtures.messageSticker(TdApiFixtures.sticker(format = format)).map() as MessageContent.Sticker)
                    .sticker
                    .format
            }

        assertEquals(listOf(StickerFormat.Webp, StickerFormat.Tgs, StickerFormat.Webm), formats)
    }

    @Test
    fun `a sticker carries its set, size, emoji, thumbnail and file`() {
        val content = TdApiFixtures.messageSticker(TdApiFixtures.sticker(thumbnail = TdApiFixtures.thumbnail()))

        assertEquals(
            MessageContent.Sticker(Sticker(7, 512, 512, "👍", StickerFormat.Webp, jpegThumbnail, FileRef(50, 1_024))),
            content.map(),
        )
    }

    @Test
    fun `thumbnails keep their format`() {
        val formats =
            listOf(
                TdApi.ThumbnailFormatJpeg(),
                TdApi.ThumbnailFormatPng(),
                TdApi.ThumbnailFormatWebp(),
                TdApi.ThumbnailFormatGif(),
                TdApi.ThumbnailFormatMpeg4(),
                TdApi.ThumbnailFormatWebm(),
                TdApi.ThumbnailFormatTgs(),
            ).map { TdApiFixtures.thumbnail(format = it).toThumbnail()?.format }

        assertEquals(ThumbnailFormat.entries, formats)
    }

    @Test
    fun `a voice note carries its waveform and whether it was played`() {
        val content = TdApiFixtures.messageVoiceNote(isListened = true)

        assertEquals(
            MessageContent.VoiceNote(
                VoiceNote(5, Waveform(byteArrayOf(0, 4, 8, 31)), FileRef(30, 1_024)),
                text(""),
                isListened = true,
            ),
            content.map(),
        )
    }

    @Test
    fun `a video note carries its length and thumbnails`() {
        val content = TdApi.MessageVideoNote(TdApiFixtures.videoNote(), true, false)

        assertEquals(
            MessageContent.VideoNote(
                VideoNote(10, 384, minithumbnail, jpegThumbnail, FileRef(31, 1_024)),
                isViewed = true,
                isSecret = false,
            ),
            content.map(),
        )
    }

    @Test
    fun `an audio file carries its title, performer and file name`() {
        val content = TdApi.MessageAudio(TdApiFixtures.audio(), TdApiFixtures.formattedText("Listen"))

        assertEquals(
            MessageContent.Audio(
                Audio(200, "Song", "Band", "song.mp3", null, null, FileRef(23, 1_024)),
                text("Listen"),
            ),
            content.map(),
        )
    }

    @Test
    fun `a document carries its name, MIME type and file`() {
        val content = TdApiFixtures.messageDocument(caption = TdApiFixtures.formattedText("Q3"))

        assertEquals(
            MessageContent.Document(
                Document("report.pdf", "application/pdf", null, null, FileRef(40, 1_024)),
                text("Q3"),
            ),
            content.map(),
        )
    }

    @Test
    fun `a location is static, a live location keeps its live period`() {
        val point = TdApi.Location(51.5, -0.12, 0.0)
        val contents =
            listOf(
                TdApi.MessageLocation(point),
                TdApi.MessageLiveLocation(TdApi.LiveLocation(point, 900, 0, 0), 600),
            ).map { it.map() }

        assertEquals(
            listOf(
                MessageContent.Location(Location(51.5, -0.12), livePeriod = 0),
                MessageContent.Location(Location(51.5, -0.12), livePeriod = 900),
            ),
            contents,
        )
    }

    @Test
    fun `a venue carries its place, name and address`() {
        val venue = TdApi.Venue(TdApi.Location(48.85, 2.29, 0.0), "Tower", "Champ de Mars", "foursquare", "id", "type")

        assertEquals(
            MessageContent.Venue(Venue(Location(48.85, 2.29), "Tower", "Champ de Mars")),
            TdApi.MessageVenue(venue).map(),
        )
    }

    @Test
    fun `a contact carries its names, number and account`() {
        val contact = TdApi.Contact(TdApiFixtures.PHONE_NUMBER, "Alice", "Smith", "", 42)

        assertEquals(
            MessageContent.Contact(Contact(TdApiFixtures.PHONE_NUMBER, "Alice", "Smith", 42)),
            TdApi.MessageContact(contact).map(),
        )
    }

    @Test
    fun `a poll carries its question, options, votes and flags`() {
        val poll =
            TdApiFixtures.poll(
                options =
                    arrayOf(
                        TdApiFixtures.pollOption("Pizza", 3, 75, isChosen = true),
                        TdApiFixtures.pollOption("Salad", 1, 25),
                    ),
                totalVoterCount = 4,
                canSeeResults = true,
                isAnonymous = false,
                allowsMultipleAnswers = true,
                isClosed = true,
            )

        assertEquals(
            MessageContent.Poll(
                Poll(
                    id = 1,
                    question = text("Lunch?"),
                    options =
                        persistentListOf(
                            PollOption(0, text("Pizza"), 3, 75, isChosen = true, isCorrect = false),
                            PollOption(1, text("Salad"), 1, 25, isChosen = false, isCorrect = false),
                        ),
                    totalVoterCount = 4,
                    isAnonymous = false,
                    allowsMultipleAnswers = true,
                    canSeeResults = true,
                    isClosed = true,
                    type = PollType.Regular,
                ),
                description = text("Vote by noon"),
            ),
            TdApiFixtures.messagePoll(poll, TdApiFixtures.formattedText("Vote by noon")).map(),
        )
    }

    @Test
    fun `a quiz marks its correct options and carries the explanation`() {
        val quiz =
            TdApiFixtures.poll(
                options =
                    arrayOf(
                        TdApiFixtures.pollOption("3"),
                        TdApiFixtures.pollOption("4"),
                        TdApiFixtures.pollOption("5"),
                    ),
                type = TdApi.PollTypeQuiz(intArrayOf(1), TdApiFixtures.formattedText("2 + 2 = 4"), null),
            )

        val poll = (TdApiFixtures.messagePoll(quiz).map() as MessageContent.Poll).poll

        assertEquals(
            listOf(false, true, false) to PollType.Quiz(text("2 + 2 = 4")),
            poll.options.map { it.isCorrect } to poll.type,
        )
    }

    @Test
    fun `poll options follow TDLib's display order and keep their index`() {
        val shuffled = TdApiFixtures.poll(optionOrder = intArrayOf(1, 0))

        val options = (TdApiFixtures.messagePoll(shuffled).map() as MessageContent.Poll).poll.options

        assertEquals(listOf(1 to "Salad", 0 to "Pizza"), options.map { it.index to it.text.text })
    }

    @Test
    fun `an invalid display order leaves the poll options as they are`() {
        val invalid = TdApiFixtures.poll(optionOrder = intArrayOf(0, 0))

        val options = (TdApiFixtures.messagePoll(invalid).map() as MessageContent.Poll).poll.options

        assertEquals(listOf(0, 1), options.map { it.index })
    }

    @Test
    fun `a die keeps its emoji and value`() {
        assertEquals(MessageContent.Dice("🎲", 4), TdApi.MessageDice(null, null, "🎲", 4, 0).map())
    }

    @Test
    fun `an animated emoji keeps the emoji and its sticker`() {
        val animated = TdApi.AnimatedEmoji(TdApiFixtures.sticker(emoji = "❤️"), 512, 512, 0, null, true)

        assertEquals(
            MessageContent.AnimatedEmoji(
                "❤️",
                Sticker(7, 512, 512, "❤️", StickerFormat.Webp, null, FileRef(50, 1_024)),
            ),
            TdApi.MessageAnimatedEmoji(animated, "❤️").map(),
        )
    }

    @Test
    fun `a game keeps its title`() {
        val game = TdApi.Game().apply { title = "Lumberjack" }

        assertEquals(MessageContent.Game("Lumberjack"), TdApi.MessageGame(game).map())
    }

    @Test
    fun `an invoice keeps the title of its product`() {
        val content = TdApi.MessageInvoice().apply { productInfo = TdApi.ProductInfo("Coffee beans", null, null) }

        assertEquals(MessageContent.Invoice("Coffee beans"), content.map())
    }

    @Test
    fun `a forwarded story keeps its poster and id`() {
        assertEquals(MessageContent.Story(-1003, 12), TdApi.MessageStory(-1003, 12, false).map())
    }

    @Test
    fun `expired self-destructing media keeps its kind`() {
        val contents =
            listOf(
                TdApi.MessageExpiredPhoto(),
                TdApi.MessageExpiredVideo(),
                TdApi.MessageExpiredVideoNote(),
                TdApi.MessageExpiredVoiceNote(),
            ).map { it.map() }

        assertEquals(ExpiredMedia.entries.map { MessageContent.Expired(it) }, contents)
    }

    @Test
    fun `regular content without a model of its own is unsupported`() {
        val contents = UNSUPPORTED_CONTENTS.map { it.getDeclaredConstructor().newInstance().map() }

        assertEquals(List(UNSUPPORTED_CONTENTS.size) { MessageContent.Unsupported }, contents)
    }

    private fun TdApi.MessageContent.map() = TdApiFixtures.message(content = this).toMessage().content

    private fun text(text: String) = FormattedText(text, persistentListOf())

    companion object {
        /** Regular (not service) contents of the pinned TDLib version that Pingogram does not show yet. */
        val UNSUPPORTED_CONTENTS: List<Class<out TdApi.MessageContent>> =
            listOf(
                TdApi.MessageRichMessage::class.java,
                TdApi.MessagePaidMedia::class.java,
                TdApi.MessageStakeDice::class.java,
                TdApi.MessageChecklist::class.java,
                TdApi.MessageGiveaway::class.java,
                TdApi.MessageGiveawayWinners::class.java,
                TdApi.MessageUnsupported::class.java,
            )
    }
}
