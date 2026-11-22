package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.service
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The people a service message names besides its sender ([memberIdsOf]): a wallpaper the user set for both people of
 * a private chat names the other one, the chat's own user, whether or not a message of theirs is loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiceMessageNamesTest : ChatViewModelHarness() {
    @Test
    fun `a wallpaper the user set for both names the private chat's other person`() =
        runTest {
            users.setUsers(user(ME, firstName = "Me"), user(ADA, firstName = "Ada"))
            val viewModel =
                showing(
                    chat(),
                    history(
                        service(
                            1,
                            ServiceAction.WallpaperSet(onlyForSelf = false),
                            chatId = PRIVATE_CHAT,
                            sender = MessageSender.User(ME),
                        ),
                    ),
                )

            val service = viewModel.items.filterIsInstance<ChatItem.Service>().single()
            assertEquals(
                res(UiR.string.preview_you_set_wallpaper_for_both, UiText.Plain("\u2068Ada\u2069")),
                service.text,
            )
        }
}
