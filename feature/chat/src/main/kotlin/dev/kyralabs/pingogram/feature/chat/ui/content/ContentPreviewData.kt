package dev.kyralabs.pingogram.feature.chat.ui.content

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.Audio
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
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
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import dev.kyralabs.pingogram.core.domain.model.Venue
import dev.kyralabs.pingogram.core.domain.model.Video
import dev.kyralabs.pingogram.core.domain.model.VideoNote
import dev.kyralabs.pingogram.core.domain.model.VoiceNote
import dev.kyralabs.pingogram.core.domain.model.Waveform
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.io.ByteArrayOutputStream

/**
 * Sample content for the previews and screenshot tests ([ContentSamples]). File ids are distinct, so the preview
 * images, which Coil takes from `AsyncImagePreviewHandler` in inspection mode, differ per item.
 */
internal object ContentPreviewData {
    fun photo(
        minithumbnail: Minithumbnail?,
        width: Int = LANDSCAPE_WIDTH,
        height: Int = LANDSCAPE_HEIGHT,
        firstFileId: Int = 1,
        hasSpoiler: Boolean = false,
    ) = MessageContent.Photo(
        photo =
            Photo(
                sizes =
                    persistentListOf(
                        PhotoSize("m", width / SMALL_DIVISOR, height / SMALL_DIVISOR, FileRef(firstFileId, 0)),
                        PhotoSize("x", width / MEDIUM_DIVISOR, height / MEDIUM_DIVISOR, FileRef(firstFileId + 1, 0)),
                        PhotoSize("y", width, height, FileRef(firstFileId + 2, 0)),
                    ),
                minithumbnail = minithumbnail,
            ),
        caption = FormattedText.Empty,
        hasSpoiler = hasSpoiler,
        isSecret = false,
        showCaptionAboveMedia = false,
    )

    fun video(
        minithumbnail: Minithumbnail?,
        width: Int = LANDSCAPE_WIDTH,
        height: Int = LANDSCAPE_HEIGHT,
        fileId: Int = VIDEO_FILE_ID,
        duration: Int = VIDEO_SECONDS,
    ) = MessageContent.Video(
        video =
            Video(
                duration = duration,
                width = width,
                height = height,
                minithumbnail = minithumbnail,
                thumbnail =
                    Thumbnail(
                        ThumbnailFormat.Jpeg,
                        width / SMALL_DIVISOR,
                        height / SMALL_DIVISOR,
                        FileRef(fileId, 0),
                    ),
                supportsStreaming = true,
                file = FileRef(fileId + 1, VIDEO_BYTES),
            ),
        caption = FormattedText.Empty,
        hasSpoiler = false,
        isSecret = false,
        showCaptionAboveMedia = false,
    )

    fun animation(minithumbnail: Minithumbnail?) =
        MessageContent.Animation(
            animation =
                Animation(
                    duration = GIF_SECONDS,
                    width = GIF_SIDE,
                    height = GIF_SIDE,
                    mimeType = "video/mp4",
                    minithumbnail = minithumbnail,
                    thumbnail = Thumbnail(ThumbnailFormat.Jpeg, GIF_SIDE, GIF_SIDE, FileRef(GIF_FILE_ID, 0)),
                    file = FileRef(GIF_FILE_ID + 1, GIF_BYTES),
                ),
            caption = FormattedText.Empty,
            hasSpoiler = false,
            isSecret = false,
            showCaptionAboveMedia = false,
        )

    /** [count] photos and videos of different shapes, as an album holds them. */
    fun album(
        count: Int,
        minithumbnail: Minithumbnail?,
    ) = List(count) { index ->
        val (width, height) = AlbumShapes[index % AlbumShapes.size]
        val firstFileId = ALBUM_FILE_IDS + index * FILE_ID_STEP
        if (index % ALBUM_VIDEO_EVERY == ALBUM_VIDEO_EVERY - 1) {
            video(minithumbnail, width, height, firstFileId, duration = VIDEO_SECONDS + index)
        } else {
            photo(minithumbnail, width, height, firstFileId)
        }
    }.toImmutableList()

    /** A sticker of [format] for [emoji]; its file draws as a round shape in previews ([isPreviewSticker]). */
    fun sticker(
        format: StickerFormat,
        emoji: String = "🦆",
    ) = MessageContent.Sticker(
        Sticker(
            setId = 1,
            width = STICKER_SIDE,
            height = STICKER_SIDE,
            emoji = emoji,
            format = format,
            thumbnail = null,
            file = FileRef(STICKER_FILE_ID + format.ordinal, 0),
        ),
    )

    fun voiceNote(isListened: Boolean) =
        MessageContent.VoiceNote(
            voiceNote =
                VoiceNote(
                    duration = VOICE_SECONDS,
                    waveform = Waveform(encodeWaveform(SampleLevels)),
                    file = FileRef(VOICE_FILE_ID, VOICE_BYTES),
                ),
            caption = FormattedText.Empty,
            isListened = isListened,
        )

    fun videoNote(
        minithumbnail: Minithumbnail?,
        isViewed: Boolean,
    ) = MessageContent.VideoNote(
        videoNote =
            VideoNote(
                duration = VIDEO_NOTE_SECONDS,
                length = VIDEO_NOTE_SIDE,
                minithumbnail = minithumbnail,
                thumbnail =
                    Thumbnail(
                        ThumbnailFormat.Jpeg,
                        VIDEO_NOTE_SIDE,
                        VIDEO_NOTE_SIDE,
                        FileRef(VIDEO_NOTE_FILE_ID, 0),
                    ),
                file = FileRef(VIDEO_NOTE_FILE_ID + 1, VIDEO_BYTES),
            ),
        isViewed = isViewed,
        isSecret = false,
    )

    val audio =
        MessageContent.Audio(
            audio =
                Audio(
                    duration = AUDIO_SECONDS,
                    title = "Clair de lune",
                    performer = "Claude Debussy",
                    fileName = "clair_de_lune.mp3",
                    albumCoverMinithumbnail = null,
                    albumCoverThumbnail = null,
                    file = FileRef(AUDIO_FILE_ID, AUDIO_BYTES),
                ),
            caption = FormattedText.Empty,
        )

    fun document(
        fileName: String,
        mimeType: String,
    ) = MessageContent.Document(
        document =
            Document(
                fileName = fileName,
                mimeType = mimeType,
                minithumbnail = null,
                thumbnail = null,
                file = FileRef(DOCUMENT_FILE_ID, DOCUMENT_BYTES),
            ),
        caption = FormattedText.Empty,
    )

    fun location(livePeriod: Int) = MessageContent.Location(Location(BERLIN_LATITUDE, BERLIN_LONGITUDE), livePeriod)

    val venue =
        MessageContent.Venue(
            Venue(
                location = Location(BERLIN_LATITUDE, BERLIN_LONGITUDE),
                title = "Brandenburger Tor",
                address = "Pariser Platz, 10117 Berlin",
            ),
        )

    val contact =
        MessageContent.Contact(
            Contact(phoneNumber = "15551234567", firstName = "Ada", lastName = "Lovelace", userId = ADA_USER_ID),
        )

    /**
     * A poll on the best season. [voted] shows the results with the user's answer; [quiz] makes it a quiz whose
     * correct answer is autumn, answered with [chosenIndex].
     */
    fun poll(
        voted: Boolean,
        quiz: Boolean = false,
        closed: Boolean = false,
        chosenIndex: Int = 2,
        multipleAnswers: Boolean = false,
        anonymous: Boolean = true,
    ): MessageContent.Poll {
        val seeResults = voted || closed
        val options =
            SeasonVotes.mapIndexed { index, (season, votes) ->
                PollOption(
                    index = index,
                    text = FormattedText(season, persistentListOf()),
                    voterCount = if (seeResults) votes else 0,
                    votePercentage = if (seeResults) SeasonPercentages[index] else 0,
                    isChosen = voted && index == chosenIndex,
                    isCorrect = quiz && seeResults && index == QUIZ_CORRECT_INDEX,
                )
            }
        return MessageContent.Poll(
            poll =
                Poll(
                    id = 1,
                    question =
                        FormattedText(
                            if (quiz) "Which season has the equinox in September?" else "Best season?",
                            persistentListOf(),
                        ),
                    options = options.toImmutableList(),
                    totalVoterCount = if (voted || closed) SeasonVotes.sumOf { it.second } else POLL_VOTERS_BEFORE,
                    isAnonymous = anonymous,
                    allowsMultipleAnswers = multipleAnswers,
                    canSeeResults = seeResults,
                    isClosed = closed,
                    type = if (quiz) PollType.Quiz(FormattedText.Empty) else PollType.Regular,
                ),
            description = FormattedText.Empty,
        )
    }

    // Landscape, portrait, square, wide and tall, so albums mix shapes.
    private val AlbumShapes =
        listOf(1280 to 960, 960 to 1280, 1080 to 1080, 1600 to 900, 900 to 1600, 1280 to 853, 1080 to 1350)

    // Votes for spring, summer, autumn and winter, and the percentages TDLib gives them (they add up to 100).
    private val SeasonVotes = listOf("Spring" to 12, "Summer" to 30, "Autumn" to 41, "Winter" to 17)
    private val SeasonPercentages = listOf(12, 30, 41, 17)

    // A voice message's 100 levels (0–31), one base-32 digit each.
    private val SampleLevels =
        "249ekpicmtvqha638fnsojd7425biqurlgb9ekpmf84237cjosvtnga69dhlic7326bhmqnie9538elrupjc74259dgc85324631"
            .map { it.digitToInt(radix = 32) }
            .toIntArray()
}

/**
 * A minithumbnail for previews: a small JPEG of a gradient with a sun, in the theme's colors, made once per theme.
 */
@Composable
internal fun rememberSampleMinithumbnail(): Minithumbnail {
    val colors = MaterialTheme.colorScheme
    val start = colors.tertiary.toArgb()
    val end = colors.primaryContainer.toArgb()
    val sun = colors.secondaryContainer.toArgb()
    return remember(start, end, sun) {
        val bitmap = createBitmap(MINITHUMBNAIL_WIDTH, MINITHUMBNAIL_HEIGHT)
        val width = MINITHUMBNAIL_WIDTH.toFloat()
        val height = MINITHUMBNAIL_HEIGHT.toFloat()
        Canvas(bitmap).apply {
            drawPaint(
                Paint().apply { shader = LinearGradient(0f, 0f, width, height, start, end, Shader.TileMode.CLAMP) },
            )
            drawCircle(
                width * SUN_X,
                height * SUN_Y,
                height * SUN_RADIUS,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color =
                        sun
                },
            )
        }
        val jpeg =
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                stream.toByteArray()
            }
        Minithumbnail(MINITHUMBNAIL_WIDTH, MINITHUMBNAIL_HEIGHT, jpeg)
    }
}

private const val LANDSCAPE_WIDTH = 1280
private const val LANDSCAPE_HEIGHT = 960
private const val SMALL_DIVISOR = 4
private const val MEDIUM_DIVISOR = 2

// Coprime with the five preview colors, so neighbouring album items differ.
private const val FILE_ID_STEP = 3
private const val ALBUM_FILE_IDS = 1000
private const val ALBUM_VIDEO_EVERY = 3
private const val VIDEO_FILE_ID = 200
private const val VIDEO_SECONDS = 65
private const val VIDEO_BYTES = 12_400_000L
private const val GIF_FILE_ID = 300
private const val GIF_SIDE = 480
private const val GIF_SECONDS = 3
private const val GIF_BYTES = 1_240_000L
private const val STICKER_FILE_ID = 400
private const val STICKER_SIDE = 512
private const val VOICE_FILE_ID = 500
private const val VOICE_SECONDS = 42
private const val VOICE_BYTES = 84_000L
private const val VIDEO_NOTE_FILE_ID = 600
private const val VIDEO_NOTE_SIDE = 384
private const val VIDEO_NOTE_SECONDS = 12
private const val AUDIO_FILE_ID = 700
private const val AUDIO_SECONDS = 302
private const val AUDIO_BYTES = 7_300_000L
private const val DOCUMENT_FILE_ID = 800
private const val DOCUMENT_BYTES = 4_520_000L
private const val BERLIN_LATITUDE = 52.516275
private const val BERLIN_LONGITUDE = 13.377704
private const val ADA_USER_ID = 9L
private const val QUIZ_CORRECT_INDEX = 2
private const val POLL_VOTERS_BEFORE = 87
private const val MINITHUMBNAIL_WIDTH = 40
private const val MINITHUMBNAIL_HEIGHT = 30
private const val SUN_X = 0.7f
private const val SUN_Y = 0.35f
private const val SUN_RADIUS = 0.22f
private const val JPEG_QUALITY = 80

/** Whether TDLib file [fileId] is the file of a [ContentPreviewData.sticker]. */
internal fun isPreviewSticker(fileId: Int): Boolean = fileId - STICKER_FILE_ID in StickerFormat.entries.indices
