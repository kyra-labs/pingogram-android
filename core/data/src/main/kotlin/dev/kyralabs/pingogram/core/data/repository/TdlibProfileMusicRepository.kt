package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toAudio
import dev.kyralabs.pingogram.core.domain.model.NewProfileTrack
import dev.kyralabs.pingogram.core.domain.model.ProfileTracks
import dev.kyralabs.pingogram.core.domain.repository.ProfileMusicRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ProfileMusicRepository] on TDLib's profile audio functions (`getUserProfileAudios`, `addProfileAudio`,
 * `removeProfileAudio`, `setProfileAudioPosition`), without a cache: td_api.tl has no update for the list.
 */
@AccountScoped
class TdlibProfileMusicRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ProfileMusicRepository {
        override suspend fun tracks(
            userId: Long,
            offset: Int,
            limit: Int,
        ): Outcome<ProfileTracks> =
            client.request(TdApi.GetUserProfileAudios(userId, offset, limit)).map { found ->
                ProfileTracks(
                    found.totalCount,
                    found.audios
                        .orEmpty()
                        .map { it.toAudio() }
                        .toImmutableList(),
                )
            }

        override suspend fun add(track: NewProfileTrack): Outcome<Unit> =
            client
                .request(
                    TdApi.AddProfileAudio(
                        TdApi.InputAudio(
                            TdApi.InputFileLocal(track.path),
                            null,
                            track.durationSeconds,
                            track.title,
                            track.performer,
                        ),
                    ),
                ).map {}

        override suspend fun remove(fileId: Int): Outcome<Unit> =
            client.request(TdApi.RemoveProfileAudio(fileId)).map {}

        override suspend fun move(
            fileId: Int,
            afterFileId: Int,
        ): Outcome<Unit> = client.request(TdApi.SetProfileAudioPosition(fileId, afterFileId)).map {}
    }
