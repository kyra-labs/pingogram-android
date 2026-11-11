package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NewProfileTrack
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.audio
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/** How [TdlibProfileMusicRepository] pages a user's profile music and edits the user's own. */
class TdlibProfileMusicRepositoryTest {
    private val client =
        FakeTdlibClient().apply {
            on(TdApi.AddProfileAudio::class, TdResult.Success(TdApi.Ok()))
            on(TdApi.RemoveProfileAudio::class, TdResult.Success(TdApi.Ok()))
            on(TdApi.SetProfileAudioPosition::class, TdResult.Success(TdApi.Ok()))
        }
    private val repository = TdlibProfileMusicRepository(client)

    @Test
    fun `pages a user's profile music with its total`() =
        runTest {
            client.on(TdApi.GetUserProfileAudios::class, TdResult.Success(TdApi.Audios(4, arrayOf(audio(), audio()))))

            val page = (repository.tracks(USER_ID, offset = 2, limit = 20) as Outcome.Success).value

            val sent = client.sentOf<TdApi.GetUserProfileAudios>().single()
            assertEquals(USER_ID, sent.userId)
            assertEquals(listOf(2, 20), listOf(sent.offset, sent.limit))
            assertEquals(4, page.totalCount)
            assertEquals(2, page.tracks.size)
        }

    @Test
    fun `adds, removes and moves the user's own songs`() =
        runTest {
            repository.add(NewProfileTrack("/cache/uploads/a/song.mp3", 220, "Nachtfahrt", "Lena Fischer"))
            repository.remove(31)
            repository.move(31, afterFileId = 0)

            val added = client.sentOf<TdApi.AddProfileAudio>().single().audio
            assertEquals("/cache/uploads/a/song.mp3", (added.audio as TdApi.InputFileLocal).path)
            assertEquals(
                listOf("220", "Nachtfahrt", "Lena Fischer"),
                listOf("${added.duration}", added.title, added.performer),
            )
            assertEquals(31, client.sentOf<TdApi.RemoveProfileAudio>().single().fileId)
            val moved = client.sentOf<TdApi.SetProfileAudioPosition>().single()
            assertEquals(listOf(31, 0), listOf(moved.fileId, moved.afterFileId))
        }

    @Test
    fun `a refusal comes back as the domain's error`() =
        runTest {
            client.on(TdApi.GetUserProfileAudios::class, TdResult.Failure(TdError(400, "USER_ID_INVALID")))

            val failed = repository.tracks(USER_ID, 0, 20) as Outcome.Failure

            assertInstanceOf(DomainError::class.java, failed.error)
        }

    private companion object {
        const val USER_ID = 7L
    }
}
