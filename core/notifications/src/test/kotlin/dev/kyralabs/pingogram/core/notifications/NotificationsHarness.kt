package dev.kyralabs.pingogram.core.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.SecretChatLinkPreviews
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibFileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibNotificationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibTransferRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.testing.FakeAppearanceRepository
import dev.kyralabs.pingogram.core.testing.FakeChatLockRepository
import dev.kyralabs.pingogram.core.testing.FakeLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The notifications of the app, from TDLib's updates to what Android shows: the real repositories of `core:data` on a
 * [FakeTdlibClient], and the renderer with everything it uses, on a test's scope. Robolectric's notification manager
 * records what is posted ([posted]); posting is allowed.
 */
internal class NotificationsHarness(
    scope: TestScope,
    val client: FakeTdlibClient = FakeTdlibClient(),
    val slot: Int = 0,
) {
    val context: Application = RuntimeEnvironment.getApplication()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val backgroundScope = scope.backgroundScope

    val openChats = OpenChats()
    val chats = TdlibChatRepository(client, backgroundScope, dispatcher, openChats)
    val users = TdlibUserRepository(client, backgroundScope)
    val groups = TdlibGroupRepository(client, backgroundScope)
    val files = TdlibFileRepository(client, TdlibTransferRepository(client, backgroundScope), backgroundScope)
    val notifications =
        TdlibNotificationRepository(
            client,
            backgroundScope,
            openChats,
            SecretChatLinkPreviews(client, FakeLinkPreviewSettingsRepository()),
        )
    val auth = TdlibAuthRepository(client, dispatcher)
    val chatLock = FakeChatLockRepository()

    val builder = ConversationNotificationBuilder(context)
    val account = NotificationAccount(slot, notifications, auth, chatLock, chats, users, groups, files)

    /** The device's accounts the renderer follows: this one; tests add others ([NotificationsHarness.account]). */
    val accounts = MutableStateFlow(listOf(account))
    val parts =
        AccountNotificationParts(
            builder = builder,
            shortcuts = ConversationShortcutsFactory(context, dispatcher),
            appearance = FakeAppearanceRepository(AppearanceSettings(dynamicColor = false)),
            ioDispatcher = dispatcher,
        )
    val renderer = NotificationRenderer(context, accounts, parts, NotificationChannels(context), backgroundScope)

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        context.registerLauncherActivity()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** The notification of group [groupId] of the account in [slot], if posted. */
    fun posted(
        groupId: Int,
        slot: Int = this.slot,
    ): Notification? = shadowOf(manager).getNotification(AccountNotificationRenderer.tagFor("group", slot), groupId)

    /** The locked chats' one notification, if posted. */
    fun lockedNotification(): Notification? =
        shadowOf(manager).getNotification(AccountNotificationRenderer.tagFor("locked", slot), 0)

    /** Every notification on screen. */
    fun all(): List<Notification> = shadowOf(manager).allNotifications

    /** The summary of the account in [slot], if posted. */
    fun summary(slot: Int = this.slot): Notification? =
        shadowOf(manager).getNotification(AccountNotificationRenderer.tagFor("summary", slot), 0)

    /** How many notifications are on screen. */
    fun count(): Int = shadowOf(manager).size()

    fun postStale(groupId: Int) {
        manager.notify("group", groupId, Notification.Builder(context, PingogramChannel.Other.id).build())
    }
}
