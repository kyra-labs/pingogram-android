package dev.kyralabs.pingogram.feature.media.profilemusic

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Audio
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.NewProfileTrack
import dev.kyralabs.pingogram.core.domain.model.ProfileTracks
import dev.kyralabs.pingogram.core.domain.repository.PickedSongRepository
import dev.kyralabs.pingogram.core.domain.repository.ProfileMusicRepository
import kotlinx.collections.immutable.toImmutableList

/** A user's profile songs held in [songs]; every change is recorded in [calls] and answered by [answer]. */
class FakeProfileMusicRepository : ProfileMusicRepository {
    val songs = mutableListOf<Audio>()
    var tracksFail: Outcome.Failure? = null
    var answer: Outcome<Unit> = Outcome.Success(Unit)

    /** `tracks:<user>`, `add:<title>`, `remove:<file>`, `move:<file>:<after>`, in order. */
    val calls = mutableListOf<String>()

    override suspend fun tracks(
        userId: Long,
        offset: Int,
        limit: Int,
    ): Outcome<ProfileTracks> {
        calls += "tracks:$userId"
        return tracksFail ?: Outcome.Success(ProfileTracks(songs.size, songs.toImmutableList()))
    }

    override suspend fun add(track: NewProfileTrack): Outcome<Unit> {
        calls += "add:${track.title}"
        if (answer is Outcome.Success) songs += song(songs.size + 100, track.title)
        return answer
    }

    override suspend fun remove(fileId: Int): Outcome<Unit> {
        calls += "remove:$fileId"
        return answer
    }

    override suspend fun move(
        fileId: Int,
        afterFileId: Int,
    ): Outcome<Unit> {
        calls += "move:$fileId:$afterFileId"
        return answer
    }
}

/** Prepares every picked song as [prepared]; [discarded] lists the copies thrown away. */
class FakePickedSongRepository : PickedSongRepository {
    var prepared: Outcome<NewProfileTrack> =
        Outcome.Success(
            NewProfileTrack("/uploads/song.mp3", 200, "Static Bloom", "Ida Ray"),
        )
    val discarded = mutableListOf<String>()

    override suspend fun prepareSong(uri: String): Outcome<NewProfileTrack> = prepared

    override fun discardSong(path: String) {
        discarded += path
    }
}

/** A song of file [fileId] titled [title], 3:40 long, by Lena Fischer, without a cover. */
fun song(
    fileId: Int,
    title: String,
): Audio =
    Audio(
        duration = 220,
        title = title,
        performer = "Lena Fischer",
        fileName = "$title.mp3",
        albumCoverMinithumbnail = null,
        albumCoverThumbnail = null,
        file = FileRef(fileId, 4_000_000),
    )
