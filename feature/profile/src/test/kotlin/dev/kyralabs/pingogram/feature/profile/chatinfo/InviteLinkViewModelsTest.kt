package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibInviteLinkRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * [InviteLinksViewModel] (the user's links by kind, revoking and deleting after a confirmation, errors) and
 * [EditInviteLinkViewModel] (a new link's settings, an edited link's own, process death) on the real repositories.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InviteLinkViewModelsTest : GroupViewModelTest() {
    private fun <T> TestScope.collected(flow: Flow<T>): List<T> {
        val items = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(items) }
        return items
    }

    private fun link(
        url: String,
        primary: Boolean = false,
        revoked: Boolean = false,
        expiration: Int = 0,
        limit: Int = 0,
        joined: Int = 3,
        name: String = "",
    ) = TdApi.ChatInviteLink().also {
        it.inviteLink = url
        it.name = name
        it.creatorUserId = ME
        it.date = 1_700_000_000
        it.expirationDate = expiration
        it.memberLimit = limit
        it.memberCount = joined
        it.isPrimary = primary
        it.isRevoked = revoked
    }

    private fun answerLinks(
        active: Array<TdApi.ChatInviteLink> =
            arrayOf(
                link("https://t.me/+primary", primary = true),
                link("https://t.me/+flyer", name = "Flyer", expiration = (NOW.epochSecond + 86_400).toInt()),
            ),
        revoked: Array<TdApi.ChatInviteLink> = arrayOf(link("https://t.me/+old", revoked = true)),
    ) {
        client.on(TdApi.GetChatInviteLinks::class) { request ->
            val links = if (request.isRevoked) revoked else active
            TdResult.Success(TdApi.ChatInviteLinks(links.size, links))
        }
    }

    private suspend fun TestScope.links(): InviteLinksViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        runCurrent()
        client.emit(TdApiFixtures.myIdOption(ME))
        runCurrent()
        val viewModel =
            InviteLinksViewModel(CHAT, TdlibInviteLinkRepository(client), users, clock, patterns) { Locale.US }
        created += viewModel
        runCurrent()
        return viewModel
    }

    private val InviteLinksViewModel.content get() = uiState.value as InviteLinksUiState.Content

    @Test
    fun `the primary link, the user's other links and the revoked ones show apart`() =
        runTest {
            answerLinks()
            val viewModel = links()

            with(viewModel.content) {
                assertEquals("https://t.me/+primary", primary?.link)
                assertFalse(checkNotNull(primary).canEdit)
                val flyer = links.single()
                assertEquals("Flyer", flyer.title)
                assertTrue(flyer.canEdit && flyer.isActive)
                val detail = flyer.detail as UiText.Resource
                assertEquals(R.string.chat_info_link_detail, detail.id)
                assertEquals(UiText.resource(R.string.chat_info_link_expires, "Oct 23"), detail.args[1])
                assertEquals("t.me/+old", revoked.single().title)
            }
            assertTrue(client.sentOf<TdApi.GetChatInviteLinks>().all { it.creatorUserId == ME })
        }

    @Test
    fun `revoking asks first, then revokes the link and reads the links again`() =
        runTest {
            answerLinks()
            client.on(TdApi.RevokeChatInviteLink::class, TdResult.Success(TdApi.ChatInviteLinks(0, emptyArray())))
            val viewModel = links()
            val effects = collected(viewModel.effects)
            val reads = client.sentOf<TdApi.GetChatInviteLinks>().size

            viewModel.onEvent(InviteLinksEvent.Revoke("https://t.me/+flyer"))
            runCurrent()
            assertEquals(InviteLinksDialog.Revoke("https://t.me/+flyer", isPrimary = false), viewModel.content.dialog)
            viewModel.onEvent(InviteLinksEvent.Confirm)
            runCurrent()

            assertEquals("https://t.me/+flyer", client.sentOf<TdApi.RevokeChatInviteLink>().single().inviteLink)
            assertEquals(reads + 2, client.sentOf<TdApi.GetChatInviteLinks>().size)
            assertEquals(listOf(InviteLinksEffect.Done(UiText.resource(R.string.chat_info_link_revoked))), effects)
        }

    @Test
    fun `deleting every revoked link names the user, and a refusal is a snackbar`() =
        runTest {
            answerLinks()
            client.on(
                TdApi.DeleteAllRevokedChatInviteLinks::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 3")),
            )
            val viewModel = links()
            val effects = collected(viewModel.effects)

            viewModel.onEvent(InviteLinksEvent.DeleteAllRevoked)
            viewModel.onEvent(InviteLinksEvent.Confirm)
            runCurrent()

            assertEquals(ME, client.sentOf<TdApi.DeleteAllRevokedChatInviteLinks>().single().creatorUserId)
            assertEquals(listOf(InviteLinksEffect.ShowError(DomainError.FloodWait(3))), effects)
        }

    @Test
    fun `links that cannot be read are an error, and retrying reads them`() =
        runTest {
            client.on(TdApi.GetChatInviteLinks::class, TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED")))
            val viewModel = links()
            assertEquals(InviteLinksUiState.Error(DomainError.AdminRightsRequired), viewModel.uiState.value)

            answerLinks()
            viewModel.onEvent(InviteLinksEvent.Retry)
            runCurrent()

            assertTrue(viewModel.uiState.value is InviteLinksUiState.Content)
        }

    @Test
    fun `copying, sharing, editing and creating are effects, the QR code a sheet`() =
        runTest {
            answerLinks()
            val viewModel = links()
            val effects = collected(viewModel.effects)

            viewModel.onEvent(InviteLinksEvent.Copy("https://t.me/+flyer"))
            viewModel.onEvent(InviteLinksEvent.Edit("https://t.me/+flyer"))
            viewModel.onEvent(InviteLinksEvent.Create)
            viewModel.onEvent(InviteLinksEvent.ShowQrCode("https://t.me/+primary"))
            runCurrent()

            assertEquals(
                listOf(
                    InviteLinksEffect.Copy("https://t.me/+flyer"),
                    InviteLinksEffect.OpenEditor("https://t.me/+flyer"),
                    InviteLinksEffect.OpenEditor(null),
                ),
                effects,
            )
            assertEquals(InviteLinksDialog.QrCode("https://t.me/+primary"), viewModel.content.dialog)
        }

    // The editor

    private fun TestScope.editor(
        link: String? = null,
        handle: SavedStateHandle = SavedStateHandle(),
    ): EditInviteLinkViewModel {
        val viewModel = EditInviteLinkViewModel(CHAT, link, handle, TdlibInviteLinkRepository(client), clock)
        created += viewModel
        runCurrent()
        return viewModel
    }

    private val EditInviteLinkViewModel.content get() = uiState.value as EditInviteLinkUiState.Content

    @Test
    fun `a new link is created with its expiry from now, and join requests allow no limit`() =
        runTest {
            client.on(TdApi.CreateChatInviteLink::class, TdResult.Success(link("https://t.me/+new")))
            val viewModel = editor()
            val effects = collected(viewModel.effects)

            viewModel.onEvent(EditInviteLinkEvent.NameChanged("Flyer"))
            viewModel.onEvent(EditInviteLinkEvent.SelectLimit(10))
            viewModel.onEvent(EditInviteLinkEvent.SelectExpiry(ExpiryChoice.After(86_400)))
            viewModel.onEvent(EditInviteLinkEvent.SetJoinRequests(on = true))
            assertEquals(0, viewModel.content.memberLimit)
            viewModel.onEvent(EditInviteLinkEvent.Save)
            runCurrent()

            val sent = client.sentOf<TdApi.CreateChatInviteLink>().single()
            assertEquals("Flyer", sent.name)
            assertEquals((NOW.epochSecond + 86_400).toInt(), sent.expirationDate)
            assertTrue(sent.createsJoinRequest)
            assertEquals(0, sent.memberLimit)
            assertEquals(listOf(EditInviteLinkEffect.Saved), effects)
        }

    @Test
    fun `an edited link keeps its own expiry and limit among the choices`() =
        runTest {
            client.on(
                TdApi.GetChatInviteLink::class,
                TdResult.Success(link("https://t.me/+flyer", name = "Flyer", expiration = 1_900_000_000, limit = 25)),
            )
            client.on(TdApi.EditChatInviteLink::class, TdResult.Success(link("https://t.me/+flyer")))
            val viewModel = editor(link = "https://t.me/+flyer")

            with(viewModel.content) {
                assertFalse(isNew)
                assertEquals(ExpiryChoice.At(1_900_000_000), expiry)
                assertEquals(25, memberLimit)
                assertTrue(25 in limitChoices)
            }
            viewModel.onEvent(EditInviteLinkEvent.Save)
            runCurrent()

            val sent = client.sentOf<TdApi.EditChatInviteLink>().single()
            assertEquals(1_900_000_000, sent.expirationDate)
            assertEquals(25, sent.memberLimit)
        }

    @Test
    fun `what was chosen survives process death, and a refusal is a snackbar`() =
        runTest {
            client.on(TdApi.CreateChatInviteLink::class, TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED")))
            val handle = SavedStateHandle()
            editor(handle = handle).onEvent(EditInviteLinkEvent.SelectExpiry(ExpiryChoice.After(3_600)))

            val restored = editor(handle = handle)
            val effects = collected(restored.effects)
            assertEquals(ExpiryChoice.After(3_600), restored.content.expiry)
            restored.onEvent(EditInviteLinkEvent.Save)
            runCurrent()

            assertEquals(listOf(EditInviteLinkEffect.ShowError(DomainError.AdminRightsRequired)), effects)
            assertFalse(restored.content.isSaving)
        }
}
