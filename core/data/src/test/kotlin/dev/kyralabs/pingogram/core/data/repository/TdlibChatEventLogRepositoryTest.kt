package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatEventAction
import dev.kyralabs.pingogram.core.domain.model.ChatEventFilter
import dev.kyralabs.pingogram.core.domain.model.ChatEventSetting
import dev.kyralabs.pingogram.core.domain.model.InviteLinkChange
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [TdlibChatEventLogRepository] on a [FakeTdlibClient]: what it asks for and how actions map. */
class TdlibChatEventLogRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibChatEventLogRepository(client)

    private fun event(action: TdApi.ChatEventAction) = TdApi.ChatEvent(1, 100, TdApi.MessageSenderUser(USER), action)

    private suspend fun mapped(action: TdApi.ChatEventAction): ChatEventAction {
        client.on(TdApi.GetChatEventLog::class, TdResult.Success(TdApi.ChatEvents(arrayOf(event(action)))))
        return (repository.events(CHAT, 0, emptySet()) as Outcome.Success).value.single().action
    }

    @Test
    fun `asks for every kind without filters, and only the kinds asked for with them`() =
        runTest {
            client.on(TdApi.GetChatEventLog::class, TdResult.Success(TdApi.ChatEvents(emptyArray())))

            repository.events(CHAT, fromEventId = 0, filters = emptySet())
            repository.events(CHAT, fromEventId = 42, filters = setOf(ChatEventFilter.MemberPromotions), limit = 10)

            val (all, some) = client.sentOf<TdApi.GetChatEventLog>()
            assertNull(all.filters)
            assertEquals(Triple(CHAT, 42L, 10), Triple(some.chatId, some.fromEventId, some.limit))
            assertTrue(some.filters.memberPromotions)
            assertFalse(some.filters.memberJoins)
        }

    @Test
    fun `maps who did what and when`() =
        runTest {
            client.on(
                TdApi.GetChatEventLog::class,
                TdResult.Success(TdApi.ChatEvents(arrayOf(event(TdApi.ChatEventTitleChanged("Old", "New"))))),
            )

            val event = (repository.events(CHAT, 0, emptySet()) as Outcome.Success).value.single()

            assertEquals(MessageSender.User(USER), event.actor)
            assertEquals(100L, event.date)
            assertEquals(ChatEventAction.TitleChanged("Old", "New"), event.action)
        }

    @Test
    fun `maps members, links and settings`() =
        runTest {
            assertEquals(
                ChatEventAction.MemberRoleChanged(MessageSender.User(7), MemberRole.Member, MemberRole.Banned(0)),
                mapped(
                    TdApi.ChatEventMemberRestricted(
                        TdApi.MessageSenderUser(7),
                        TdApi.ChatMemberStatusMember(0),
                        TdApi.ChatMemberStatusBanned(0),
                    ),
                ),
            )
            assertEquals(
                ChatEventAction.InviteLinkChanged("https://t.me/+abc", InviteLinkChange.Revoked),
                mapped(
                    TdApi.ChatEventInviteLinkRevoked(
                        TdApi.ChatInviteLink().also {
                            it.inviteLink =
                                "https://t.me/+abc"
                        },
                    ),
                ),
            )
            assertEquals(
                ChatEventAction.SettingToggled(ChatEventSetting.ProtectedContent, true),
                mapped(TdApi.ChatEventHasProtectedContentToggled(true)),
            )
            assertEquals(ChatEventAction.SlowModeChanged(30), mapped(TdApi.ChatEventSlowModeDelayChanged(0, 30)))
            assertEquals(ChatEventAction.Other, mapped(TdApi.ChatEventAccentColorChanged()))
        }

    @Test
    fun `maps the message an action concerns`() =
        runTest {
            val action = mapped(TdApi.ChatEventMessageDeleted(TdApiFixtures.message(id = 5, chatId = CHAT), false))

            assertEquals(5L, assertInstanceOf(ChatEventAction.MessageDeleted::class.java, action).message.id)
        }

    @Test
    fun `a refusal is a domain error`() =
        runTest {
            client.on(TdApi.GetChatEventLog::class, TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED")))

            assertEquals(Outcome.Failure(DomainError.AdminRightsRequired), repository.events(CHAT, 0, emptySet()))
        }

    private companion object {
        const val CHAT = -100L
        const val USER = 42L
    }
}
