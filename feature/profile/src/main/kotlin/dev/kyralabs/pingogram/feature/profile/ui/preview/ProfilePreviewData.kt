package dev.kyralabs.pingogram.feature.profile.ui.preview

import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ContactAction
import dev.kyralabs.pingogram.feature.profile.ui.FileKind
import dev.kyralabs.pingogram.feature.profile.ui.MediaKind
import dev.kyralabs.pingogram.feature.profile.ui.MoreMedia
import dev.kyralabs.pingogram.feature.profile.ui.ProfileActions
import dev.kyralabs.pingogram.feature.profile.ui.ProfileHeader
import dev.kyralabs.pingogram.feature.profile.ui.ProfileInfo
import dev.kyralabs.pingogram.feature.profile.ui.ProfilePhotoItem
import dev.kyralabs.pingogram.feature.profile.ui.ProfilePhotos
import dev.kyralabs.pingogram.feature.profile.ui.ProfileUiState
import dev.kyralabs.pingogram.feature.profile.ui.ProfileWarning
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaContent
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaItem
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/** Profiles for previews and screenshot tests: a regular user, a bot, a deleted account, and their shared media. */
object ProfilePreviewData {
    private const val CHAT_ID = 42L

    private const val SMALL = 160
    private const val BIG = 640

    private fun photo(id: Int): Photo =
        Photo(
            persistentListOf(
                PhotoSize("a", SMALL, SMALL, FileRef(id, 0)),
                PhotoSize("c", BIG, BIG, FileRef(id + 1, 0)),
            ),
            null,
        )

    private const val BIO = "Mathematician and writer. Notes on the Analytical Engine at example.org"
    private const val BIO_LINK = "example.org"

    private val bio =
        FormattedText(
            BIO,
            persistentListOf(TextEntity(BIO.indexOf(BIO_LINK), BIO_LINK.length, TextEntityType.Url)),
        )

    val content =
        ProfileUiState.Content(
            header =
                ProfileHeader(
                    userId = 42,
                    name = UiText.Plain("Ada Lovelace"),
                    photo = null,
                    status = UiText.resource(UiR.string.user_status_online),
                    isOnline = true,
                    isVerified = true,
                    isPremium = true,
                ),
            info =
                ProfileInfo(
                    bio = bio,
                    usernames = persistentListOf("ada"),
                    phoneNumber = "+447700900123",
                    birthday = "December 10, 1815",
                ),
            actions =
                ProfileActions(
                    canMessage = true,
                    isMuted = false,
                    canStartSecretChat = true,
                    contact = ContactAction.Edit,
                    isBlocked = false,
                    canReport = true,
                ),
            photos =
                ProfilePhotos(
                    items = (1..4).map { ProfilePhotoItem(it.toLong(), photo(it * 10)) }.toImmutableList(),
                    totalCount = 4,
                ),
        )

    val bot =
        ProfileUiState.Content(
            header =
                ProfileHeader(
                    userId = 7,
                    name = UiText.Plain("Sticker Finder"),
                    photo = null,
                    status = UiText.resource(R.string.profile_status_bot),
                ),
            info =
                ProfileInfo(
                    bio = FormattedText("I find stickers for any emoji you send me.", persistentListOf()),
                    isBot = true,
                    usernames = persistentListOf("stickerfinderbot"),
                ),
            actions = ProfileActions(canMessage = true, isMuted = true, isBlocked = false, canReport = true),
        )

    val deleted =
        ProfileUiState.Content(
            header =
                ProfileHeader(
                    userId = 9,
                    name = UiText.resource(UiR.string.user_deleted_account),
                    photo = null,
                    placeholderIcon = UiR.drawable.ic_person_filled,
                ),
            info = ProfileInfo(),
            actions = ProfileActions(canMessage = true, isMuted = false),
        )

    val scam =
        content.copy(
            header =
                content.header.copy(
                    name = UiText.Plain("Free Crypto"),
                    isVerified = false,
                    isPremium = false,
                    isOnline = false,
                    status = UiText.resource(UiR.string.user_status_last_seen_recently),
                    warning = ProfileWarning.Scam,
                ),
            photos = ProfilePhotos(),
        )

    val media =
        SharedMediaUiState(
            selected = SharedMediaKind.PhotosAndVideos,
            chatId = CHAT_ID,
            content =
                SharedMediaContent.Items(
                    items =
                        (1..9)
                            .map { index ->
                                SharedMediaItem.Media(
                                    messageId = index.toLong(),
                                    kind = if (index % 4 == 0) MediaKind.Video else MediaKind.Photo,
                                    photo = photo(100 + index * 2),
                                    thumbnail = null,
                                    minithumbnail = null,
                                    duration = if (index % 4 == 0) "0:42" else null,
                                    date = "Mar 17",
                                )
                            }.toImmutableList(),
                    more = MoreMedia.Available,
                ),
        )

    val files =
        SharedMediaUiState(
            selected = SharedMediaKind.Files,
            chatId = CHAT_ID,
            content =
                SharedMediaContent.Items(
                    persistentListOf(
                        SharedMediaItem.File(
                            1,
                            FileKind.Document,
                            UiText.Plain("Notes on the Engine.pdf"),
                            null,
                            2_400_000,
                            "Mar 17",
                        ),
                        SharedMediaItem.File(
                            2,
                            FileKind.Document,
                            UiText.Plain("Table of Bernoulli numbers.xlsx"),
                            null,
                            48_000,
                            "Feb 2",
                        ),
                        SharedMediaItem.File(
                            3,
                            FileKind.Music,
                            UiText.Plain("Symphony No. 9"),
                            "Beethoven",
                            8_100_000,
                            "Jan 9",
                        ),
                    ),
                ),
        )

    val links =
        SharedMediaUiState(
            selected = SharedMediaKind.Links,
            chatId = CHAT_ID,
            content =
                SharedMediaContent.Items(
                    persistentListOf(
                        SharedMediaItem.Link(
                            1,
                            "https://example.org/engine",
                            "The Analytical Engine",
                            "example.org/engine",
                            "Mar 17",
                        ),
                    ),
                ),
        )

    val emptyMedia =
        SharedMediaUiState(selected = SharedMediaKind.Voice, chatId = CHAT_ID, content = SharedMediaContent.Empty)

    val loadingMedia = SharedMediaUiState(chatId = CHAT_ID, content = SharedMediaContent.Loading)
}
