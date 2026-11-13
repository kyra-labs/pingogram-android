package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.AccountInfo
import dev.kyralabs.pingogram.core.domain.model.ChatActionBar
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** TDLib's chat action bars as the domain's ([toChatActionBar]). */
class ChatActionBarMappersTest {
    @Test
    fun `a chat someone new started carries what Telegram tells of the account`() {
        val bar = TdApi.ChatActionBarReportAddBlock(true, TdApi.AccountInfo(3, 2024, "DE", 1_760_000_000, 0))

        assertEquals(
            ChatActionBar.ReportAddBlock(
                canUnarchive = true,
                account = AccountInfo(3, 2024, "DE", lastNameChangeDate = 1_760_000_000L, lastPhotoChangeDate = 0L),
            ),
            bar.toChatActionBar(),
        )
    }

    @Test
    fun `an account Telegram tells nothing of has no details`() {
        assertEquals(
            ChatActionBar.ReportAddBlock(canUnarchive = false, account = null),
            TdApi.ChatActionBarReportAddBlock(false, null).toChatActionBar(),
        )
    }

    @Test
    fun `the other bars map one to one`() {
        assertEquals(
            ChatActionBar.ReportSpam(canUnarchive = true),
            TdApi.ChatActionBarReportSpam(true).toChatActionBar(),
        )
        assertEquals(ChatActionBar.AddContact, TdApi.ChatActionBarAddContact().toChatActionBar())
        assertEquals(ChatActionBar.SharePhoneNumber, TdApi.ChatActionBarSharePhoneNumber().toChatActionBar())
        assertEquals(
            ChatActionBar.JoinRequest("Rust Berlin", isChannel = false, requestDate = 1_760_000_000L),
            TdApi.ChatActionBarJoinRequest("Rust Berlin", false, 1_760_000_000).toChatActionBar(),
        )
    }

    @Test
    fun `a bar Pingogram does not show maps to none`() {
        assertNull(TdApi.ChatActionBarInviteMembers().toChatActionBar())
    }
}
