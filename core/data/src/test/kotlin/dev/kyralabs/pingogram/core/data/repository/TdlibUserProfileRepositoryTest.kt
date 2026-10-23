package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.domain.model.UserFullInfo
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.formattedText
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.photoSize
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * [TdlibUserProfileRepository] on a [FakeTdlibClient]: the full info and its changes, and the profile photos' pages.
 */
class TdlibUserProfileRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibUserProfileRepository(client)

    private fun fullInfo(
        bio: String? = "Mathematician",
        birthdate: TdApi.Birthdate? = null,
        blockList: TdApi.BlockList? = null,
        botInfo: TdApi.BotInfo? = null,
        groupsInCommon: Int = 0,
    ) = TdApi.UserFullInfo().also {
        it.bio = bio?.let(::formattedText)
        it.birthdate = birthdate
        it.blockList = blockList
        it.botInfo = botInfo
        it.groupInCommonCount = groupsInCommon
    }

    @Test
    fun `the full info holds the bio, the birthday, the block state and the groups in common`() =
        runTest {
            client.on(
                TdApi.GetUserFullInfo::class,
                TdResult.Success(
                    fullInfo(
                        birthdate = TdApi.Birthdate(10, 12, 1815),
                        blockList = TdApi.BlockListMain(),
                        groupsInCommon = 3,
                    ),
                ),
            )

            repository.fullInfo(42).test {
                val info = (awaitItem() as Outcome.Success).value
                assertEquals("Mathematician", info.bio.text)
                assertEquals(Birthdate(10, 12, 1815), info.birthdate)
                assertEquals(true, info.isBlocked)
                assertEquals(3, info.groupsInCommon)
                assertNull(info.botDescription)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(42L, client.sentOf<TdApi.GetUserFullInfo>().single().userId)
        }

    @Test
    fun `a birthday without a year and the stories block list read as such`() =
        runTest {
            client.on(
                TdApi.GetUserFullInfo::class,
                TdResult.Success(fullInfo(birthdate = TdApi.Birthdate(1, 4, 0), blockList = TdApi.BlockListStories())),
            )

            repository.fullInfo(42).test {
                val info = (awaitItem() as Outcome.Success).value
                assertEquals(Birthdate(1, 4, null), info.birthdate)
                assertEquals(false, info.isBlocked)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `whether the user can be called, and with video, comes from the full info`() =
        runTest {
            val info =
                fullInfo().also {
                    it.canBeCalled = true
                    it.supportsVideoCalls = true
                }
            client.on(TdApi.GetUserFullInfo::class, TdResult.Success(info))

            repository.fullInfo(42).test {
                val mapped = (awaitItem() as Outcome.Success).value
                assertEquals(true, mapped.canBeCalled)
                assertEquals(true, mapped.supportsVideoCalls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a bot's description comes from its bot info`() =
        runTest {
            val bot = TdApi.BotInfo().also { it.description = "Finds stickers" }
            client.on(TdApi.GetUserFullInfo::class, TdResult.Success(fullInfo(bio = null, botInfo = bot)))

            repository.fullInfo(7).test {
                val info = (awaitItem() as Outcome.Success).value
                assertEquals("Finds stickers", info.botDescription)
                assertEquals("", info.bio.text)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `changes of the user's full info follow, others' are left out`() =
        runTest {
            client.on(TdApi.GetUserFullInfo::class, TdResult.Success(fullInfo()))

            repository.fullInfo(42).test {
                awaitItem()
                client.emit(TdApi.UpdateUserFullInfo(43, fullInfo(bio = "Someone else")))
                client.emit(TdApi.UpdateUserFullInfo(42, fullInfo(bio = "Poet", blockList = TdApi.BlockListMain())))

                val changed = (awaitItem() as Outcome.Success<UserFullInfo>).value
                assertEquals("Poet", changed.bio.text)
                assertEquals(true, changed.isBlocked)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed request is the first value`() =
        runTest {
            client.on(TdApi.GetUserFullInfo::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 8")))

            repository.fullInfo(42).test {
                assertEquals(Outcome.Failure(DomainError.FloodWait(8)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `profile photos come a page at a time, newest first, with the total count`() =
        runTest {
            val photo =
                TdApi.ChatPhoto(5, 1_700_000_000, null, arrayOf(photoSize("a", 160, 160)), null, null, null)
            client.on(TdApi.GetUserProfilePhotos::class, TdResult.Success(TdApi.ChatPhotos(12, arrayOf(photo))))

            val page = (repository.profilePhotos(42, offset = 20, limit = 10) as Outcome.Success).value

            assertEquals(12, page.totalCount)
            assertEquals(5L, page.photos.single().id)
            assertEquals(1_700_000_000L, page.photos.single().addedDate)
            assertEquals(
                160,
                page.photos
                    .single()
                    .photo.sizes
                    .single()
                    .width,
            )
            val request = client.sentOf<TdApi.GetUserProfilePhotos>().single()
            assertEquals(42L, request.userId)
            assertEquals(20, request.offset)
            assertEquals(10, request.limit)
        }
}
