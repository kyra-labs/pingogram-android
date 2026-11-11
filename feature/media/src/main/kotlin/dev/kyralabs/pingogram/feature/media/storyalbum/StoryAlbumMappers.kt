package dev.kyralabs.pingogram.feature.media.storyalbum

import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.Story
import dev.kyralabs.pingogram.core.domain.model.StoryAlbum
import dev.kyralabs.pingogram.core.domain.model.StoryAlbumCover
import dev.kyralabs.pingogram.core.domain.model.StoryContent
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The album's cover tile. */
internal fun StoryAlbum.toTile(): StoryAlbumTile =
    StoryAlbumTile(
        id = id,
        name = name,
        cover =
            when (val cover = cover) {
                is StoryAlbumCover.Photo -> cover.photo.toPicture()
                is StoryAlbumCover.Video -> AlbumPicture(cover.minithumbnail, cover.thumbnail.toFiles())
                null -> null
            },
    )

/** The story's tile, posted on [date]. */
internal fun Story.toAlbumStory(date: String): AlbumStory =
    when (val content = content) {
        is StoryContent.Photo -> {
            AlbumStory(id, date, content.photo.toPicture(), isVideo = false)
        }

        is StoryContent.Video -> {
            AlbumStory(id, date, AlbumPicture(content.minithumbnail, content.thumbnail.toFiles()), isVideo = true)
        }

        is StoryContent.Live, StoryContent.Unsupported -> {
            AlbumStory(id, date, picture = null, isVideo = false)
        }
    }

private fun Photo.toPicture(): AlbumPicture =
    AlbumPicture(minithumbnail, sizes.map { AlbumPictureFile(it.file.id, it.width, it.height) }.toImmutableList())

/** A video's still as files Coil decodes: not an MPEG-4 or WebM preview. */
private fun Thumbnail?.toFiles() =
    when (this?.format) {
        ThumbnailFormat.Jpeg, ThumbnailFormat.Png, ThumbnailFormat.Webp, ThumbnailFormat.Gif -> {
            persistentListOf(AlbumPictureFile(file.id, width, height))
        }

        else -> {
            persistentListOf()
        }
    }

/**
 * The days of an album's stories in [zone] and [locale], with the platform's patterns ([TimePatterns]): "Oct 3" this
 * year, "Oct 3, 2025" before. A pattern java.time cannot read falls back to its own medium date.
 */
internal class AlbumDateFormatter(
    private val zone: ZoneId,
    private val locale: Locale,
    patterns: TimePatterns,
) {
    private val thisYear = patterns.formatter("MMMd")
    private val otherYear = patterns.formatter("yMMMd")

    /** The day of [epochSeconds] (Unix time), with the year unless it is [now]'s. */
    fun format(
        epochSeconds: Long,
        now: Instant,
    ): String {
        val day = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        return (if (day.year == now.atZone(zone).year) thisYear else otherYear).format(day)
    }

    private fun TimePatterns.formatter(skeleton: String): DateTimeFormatter =
        try {
            DateTimeFormatter.ofPattern(bestPattern(locale, skeleton), locale)
        } catch (_: IllegalArgumentException) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        }
}
