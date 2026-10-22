package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

// What the view model tests of the group pages share: the main dispatcher, the view models' scopes, TDLib's answers
// for a supergroup, a fixed clock and fixed time patterns.

/** A test of view models on a [FakeTdlibClient], with the main dispatcher a [StandardTestDispatcher]. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class GroupViewModelTest {
    protected val client = FakeTdlibClient()

    /** The view models the test made, whose scopes end with it, before the main dispatcher is reset. */
    protected val created = mutableListOf<ViewModel>()

    @BeforeEach
    fun setMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun resetMainDispatcher() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    protected val clock =
        object : WallClock {
            override val minutes = MutableStateFlow(NOW)

            override fun now(): Instant = NOW

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    protected val patterns =
        object : TimePatterns {
            override fun is24HourClock() = false

            override fun bestPattern(
                locale: Locale,
                skeleton: String,
            ) = mapOf("hm" to "h:mm a", "Hm" to "HH:mm", "MMMd" to "MMM d", "yMd" to "M/d/y", "yMMMd" to "MMM d, y")
                .plus("yMMMMd" to "MMMM d, y")[skeleton] ?: error("No pattern for $skeleton")
        }

    /** Rights to change the info, restrict members and invite, without promoting. */
    protected val adminRights: TdApi.ChatAdministratorRights =
        TdApi.ChatAdministratorRights().also {
            it.canChangeInfo = true
            it.canRestrictMembers = true
            it.canInviteUsers = true
            it.canDeleteMessages = true
        }

    protected fun chat(isChannel: Boolean = false) =
        TdApiFixtures.chat(
            id = CHAT,
            type = TdApi.ChatTypeSupergroup(SUPERGROUP, isChannel),
            title = "Rust Berlin",
            canBeDeletedForAllUsers = true,
        )

    protected fun fullInfo(
        members: Int = 1_024,
        canGetMembers: Boolean = true,
        description: String = "Meetups",
    ) = TdApi.SupergroupFullInfo().also {
        it.description = description
        it.memberCount = members
        it.administratorCount = 3
        it.bannedCount = 2
        it.restrictedCount = 1
        it.canGetMembers = canGetMembers
        it.slowModeDelay = 30
        it.inviteLink =
            TdApi.ChatInviteLink().also { link ->
                link.inviteLink = "https://t.me/+primary"
                link.name = ""
                link.isPrimary = true
            }
    }

    /** Answers what reading supergroup [SUPERGROUP] of chat [CHAT] asks TDLib for. */
    protected fun answerSupergroup(
        status: TdApi.ChatMemberStatus = TdApi.ChatMemberStatusAdministrator(true, adminRights),
        isChannel: Boolean = false,
        usernames: List<String> = listOf("rustberlin"),
        full: TdResult<TdApi.SupergroupFullInfo> = TdResult.Success(fullInfo()),
        members: Array<TdApi.ChatMember> = arrayOf(member(ADA, TdApiFixtures.memberStatus())),
    ) {
        val chat = chat(isChannel)
        client.on(TdApi.GetChat::class, TdResult.Success(chat))
        client.on(
            TdApi.GetSupergroup::class,
            TdResult.Success(
                TdApiFixtures.supergroup(
                    id = SUPERGROUP,
                    usernames = usernames,
                    memberCount = 1_000,
                    status = status,
                    isChannel = isChannel,
                ),
            ),
        )
        client.on(TdApi.GetSupergroupFullInfo::class, full)
        client.on(TdApi.GetTextEntities::class, TdResult.Success(TdApi.TextEntities(emptyArray())))
        client.on(TdApi.GetSupergroupMembers::class, TdResult.Success(TdApi.ChatMembers(members.size, members)))
        client.on(TdApi.SetChatMemberStatus::class, TdResult.Success(TdApi.Ok()))
    }

    protected fun member(
        userId: Long,
        status: TdApi.ChatMemberStatus,
    ) = TdApi.ChatMember(TdApi.MessageSenderUser(userId), "", 0, 1_700_000_000, status)

    protected companion object {
        const val CHAT = -100L
        const val SUPERGROUP = 6L
        const val ME = 1L
        const val ADA = 42L
        val NOW: Instant = Instant.parse("2026-10-22T12:00:00Z")
    }
}
