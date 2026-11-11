package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A chat's story album (td_api.tl `storyAlbum`, board 11a): stories its owner grouped under [name], shown on the
 * profile with a [cover], a photo or the still of a video; null when it has neither. td_api.tl gives no story count
 * with an album: [StoryAlbumPage.totalCount] says it once the album is opened.
 */
data class StoryAlbum(
    val id: Int,
    val name: String,
    val cover: StoryAlbumCover?,
)

/** What an album's cover shows. */
sealed interface StoryAlbumCover {
    /** A photo of the album. */
    data class Photo(
        val photo: dev.kyralabs.pingogram.core.domain.model.Photo,
    ) : StoryAlbumCover

    /** The still of a video of the album, its [minithumbnail] blurred until the [thumbnail] shows. */
    data class Video(
        val minithumbnail: Minithumbnail?,
        val thumbnail: Thumbnail?,
    ) : StoryAlbumCover
}

/** A page of an album's stories, in the album's order, out of [totalCount] in all. */
data class StoryAlbumPage(
    val totalCount: Int,
    val stories: ImmutableList<Story>,
)
