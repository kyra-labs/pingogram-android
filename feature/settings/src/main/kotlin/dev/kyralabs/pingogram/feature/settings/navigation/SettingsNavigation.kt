package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.navigation.AboutRoute
import dev.kyralabs.pingogram.core.navigation.ActiveSessionsRoute
import dev.kyralabs.pingogram.core.navigation.AppearanceRoute
import dev.kyralabs.pingogram.core.navigation.AutoDownloadRoute
import dev.kyralabs.pingogram.core.navigation.BlockedUsersRoute
import dev.kyralabs.pingogram.core.navigation.ChatAppearanceRoute
import dev.kyralabs.pingogram.core.navigation.ChatFolderEditRoute
import dev.kyralabs.pingogram.core.navigation.ChatFoldersRoute
import dev.kyralabs.pingogram.core.navigation.ColorThemeRoute
import dev.kyralabs.pingogram.core.navigation.DataStorageRoute
import dev.kyralabs.pingogram.core.navigation.DeleteAccountRoute
import dev.kyralabs.pingogram.core.navigation.EditProfileRoute
import dev.kyralabs.pingogram.core.navigation.EditProxyRoute
import dev.kyralabs.pingogram.core.navigation.LanguageRoute
import dev.kyralabs.pingogram.core.navigation.MyQrCodeRoute
import dev.kyralabs.pingogram.core.navigation.NetworkUsageRoute
import dev.kyralabs.pingogram.core.navigation.NotificationExceptionsRoute
import dev.kyralabs.pingogram.core.navigation.NotificationSettingsRoute
import dev.kyralabs.pingogram.core.navigation.OpenSourceLibraryRoute
import dev.kyralabs.pingogram.core.navigation.OpenSourceLicensesRoute
import dev.kyralabs.pingogram.core.navigation.PasscodeLockRoute
import dev.kyralabs.pingogram.core.navigation.PrivacySecurityRoute
import dev.kyralabs.pingogram.core.navigation.PrivacySettingRoute
import dev.kyralabs.pingogram.core.navigation.ProxiesRoute
import dev.kyralabs.pingogram.core.navigation.ProxyLinkRoute
import dev.kyralabs.pingogram.core.navigation.QrLoginRoute
import dev.kyralabs.pingogram.core.navigation.QuickReactionRoute
import dev.kyralabs.pingogram.core.navigation.ReactionNotificationsRoute
import dev.kyralabs.pingogram.core.navigation.SetPasscodeRoute
import dev.kyralabs.pingogram.core.navigation.SettingsRoute
import dev.kyralabs.pingogram.core.navigation.StickerSetListRoute
import dev.kyralabs.pingogram.core.navigation.StickerSetRoute
import dev.kyralabs.pingogram.core.navigation.StickersRoute
import dev.kyralabs.pingogram.core.navigation.SwipeActionsRoute
import dev.kyralabs.pingogram.core.navigation.TwoStepVerificationFlowRoute
import dev.kyralabs.pingogram.core.navigation.TwoStepVerificationRoute
import dev.kyralabs.pingogram.core.ui.component.NoDialogWindowDim
import dev.kyralabs.pingogram.core.ui.component.SheetDialogProperties
import dev.kyralabs.pingogram.feature.settings.ui.LocalSettingsBackButton
import dev.kyralabs.pingogram.feature.settings.ui.NotificationSettingsPage
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPage
import dev.kyralabs.pingogram.feature.settings.ui.stickers.set.StickerSetSheetRoute
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlow
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationResult
import dev.kyralabs.pingogram.feature.settings.ui.AboutRoute as AboutScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.ActiveSessionsRoute as ActiveSessionsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.AppearanceRoute as AppearanceScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.AutoDownloadRoute as AutoDownloadScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.ChatAppearanceRoute as ChatAppearanceScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.ColorThemeRoute as ColorThemeScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.DataStorageRoute as DataStorageScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.LanguageRoute as LanguageScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.NotificationExceptionsRoute as NotificationExceptionsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.NotificationSettingsRoute as NotificationSettingsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.OpenSourceLibraryRoute as OpenSourceLibraryScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.OpenSourceLicensesRoute as OpenSourceLicensesScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.chatlist.SwipeActionsRoute as SwipeActionsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.folders.ChatFolderEditRoute as ChatFolderEditScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.folders.ChatFoldersRoute as ChatFoldersScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.notifications.ReactionNotificationsRoute as ReactionNotificationsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeLockRoute as PasscodeLockScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.passcode.SetPasscodeRoute as SetPasscodeScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.qr.MyQrCodeRoute as MyQrCodeScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.qr.QrLoginRoute as QrLoginScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.reactions.QuickReactionRoute as QuickReactionScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.stickers.StickerSetListRoute as StickerSetListScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.stickers.StickersRoute as StickersScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationFlowRoute as TwoStepVerificationFlowScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepVerificationRoute as TwoStepVerificationScreenRoute

/**
 * Adds the Settings tab, [SettingsRoute], to the graph: Settings home, and on expanded windows the page it opens beside
 * it ([SettingsListDetail]). With one pane its rows call [onOpen] with the route of the page they open, which the app
 * pushes above the tab: [EditProfileRoute], [AppearanceRoute], [NotificationSettingsRoute], [DataStorageRoute],
 * [LanguageRoute], [AboutRoute] or [PrivacySecurityRoute]. Once Edit profile saved the profile
 * ([popBackStackAfterProfileSaved], or beside Settings home), the tab says so.
 */
fun NavGraphBuilder.settingsScreen(
    onOpen: (route: Any) -> Unit,
    onScanQrCode: (forDeviceLogin: Boolean) -> Unit,
) {
    composable<SettingsRoute> { entry ->
        val results = entry.savedStateHandle
        val profileSaved by results.getStateFlow(PROFILE_SAVED_RESULT, false).collectAsStateWithLifecycle()
        SettingsListDetail(
            onOpen = onOpen,
            profileSaved = profileSaved,
            onProfileSavedShown = { results[PROFILE_SAVED_RESULT] = false },
            onProfileSaved = { results[PROFILE_SAVED_RESULT] = true },
            onScanQrCode = onScanQrCode,
        )
    }
}

/**
 * Appearance and the pages it opens with [onOpen]: the Theme page, Chat appearance, Double-tap to react and Swipe
 * actions.
 */
private fun NavGraphBuilder.appearancePages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
) {
    settingsPage<AppearanceRoute> {
        AppearanceScreenRoute(
            onOpenColorTheme = { onOpen(ColorThemeRoute) },
            onOpenChatAppearance = { onOpen(ChatAppearanceRoute) },
            onOpenQuickReaction = { onOpen(QuickReactionRoute) },
            onOpenSwipeActions = { onOpen(SwipeActionsRoute) },
            onBack = onBack,
        )
    }
    settingsPage<ColorThemeRoute> { ColorThemeScreenRoute(onBack = onBack) }
    settingsPage<ChatAppearanceRoute> { ChatAppearanceScreenRoute(onBack = onBack) }
    settingsPage<QuickReactionRoute> { QuickReactionScreenRoute(onBack = onBack) }
    settingsPage<SwipeActionsRoute> { SwipeActionsScreenRoute(onBack = onBack) }
}

/** Chat folders and the folder editor above it. */
private fun NavGraphBuilder.folderPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
) {
    settingsPage<ChatFoldersRoute> {
        ChatFoldersScreenRoute(onOpenFolder = { onOpen(ChatFolderEditRoute(it)) }, onBack = onBack)
    }
    settingsPage<ChatFolderEditRoute> { ChatFolderEditScreenRoute(onBack = onBack) }
}

/** Stickers and the Trending and Archived stickers pages above it. */
private fun NavGraphBuilder.stickerPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
) {
    settingsPage<StickersRoute> {
        StickersScreenRoute(
            onOpenList = { onOpen(StickerSetListRoute(archived = it)) },
            onOpenSet = { onOpen(StickerSetRoute(setId = it)) },
            onBack = onBack,
        )
    }
    settingsPage<StickerSetListRoute> { entry ->
        StickerSetListScreenRoute(
            archived = entry.toRoute<StickerSetListRoute>().archived,
            onOpenSet = { onOpen(StickerSetRoute(setId = it)) },
            onBack = onBack,
        )
    }
    // A dialog destination, so the page below stays visible under the sheet's scrim; links open it too.
    dialog<StickerSetRoute>(dialogProperties = SheetDialogProperties) {
        NoDialogWindowDim()
        StickerSetSheetRoute(onClose = onBack)
    }
}

/**
 * Adds the settings pages pushed above the Settings tab: [EditProfileRoute], [AppearanceRoute], [ChatFoldersRoute],
 * [StickersRoute], [NotificationSettingsRoute], [DataStorageRoute], [LanguageRoute], [AboutRoute] and
 * [PrivacySecurityRoute], and the ones these push above themselves, which they open with [onOpen]:
 * [ChangePhoneNumberRoute] above Edit profile, [ColorThemeRoute], [ChatAppearanceRoute], [QuickReactionRoute] and
 * [SwipeActionsRoute]
 * above Appearance, [ChatFolderEditRoute] above Chat folders, [StickerSetListRoute] and the sheet of a sticker set ([StickerSetRoute], a dialog destination) above Stickers, [NotificationExceptionsRoute]
 * above Notifications, [AutoDownloadRoute], [NetworkUsageRoute] and [ProxiesRoute] above Data and storage,
 * [EditProxyRoute] above the proxies, the sheet of a proxy link ([ProxyLinkRoute], a dialog destination),
 * [PrivacySettingRoute], [BlockedUsersRoute], [ActiveSessionsRoute], [TwoStepVerificationRoute], [PasscodeLockRoute]
 * and [DeleteAccountRoute] above Privacy and security, [TwoStepVerificationFlowRoute] above 2-step verification,
 * [SetPasscodeRoute] above Passcode lock, [OpenSourceLicensesRoute] above About and [OpenSourceLibraryRoute] above it.
 * [onBack] leaves each of them; Edit profile leaves with [onProfileSaved] once it saved, which the app implements with
 * [popBackStackAfterProfileSaved], and a flow of 2-step verification with [onTwoStepVerificationDone] once it is done,
 * which the app implements with [popBackStackAfterTwoStepVerification]. Active sessions' "Link desktop device" and the
 * user's own QR code ([MyQrCodeRoute]) open the QR code scanner, full screen, with [onScanQrCode] (for a device's login
 * code only, or any Telegram link); [QrLoginRoute], which a scanned or opened login code leads to, asks before it logs
 * the device in. The detail pane of [SettingsListDetail] adds them to a `NavHost` of its own, with these callbacks on
 * its own back stack.
 */

@Suppress("LongParameterList") // Where the settings pages lead.
fun NavGraphBuilder.settingsPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
    onProfileSaved: () -> Unit,
    onTwoStepVerificationDone: (TwoStepVerificationResult) -> Unit,
    onScanQrCode: (forDeviceLogin: Boolean) -> Unit,
) {
    accountPages(onOpen, onBack, onProfileSaved)
    appearancePages(onOpen, onBack)
    folderPages(onOpen, onBack)
    stickerPages(onOpen, onBack)
    settingsPage<NotificationSettingsRoute> {
        NotificationSettingsScreenRoute(onOpen = { onOpen(it.route) }, onBack = onBack)
    }
    settingsPage<ReactionNotificationsRoute> { ReactionNotificationsScreenRoute(onBack = onBack) }
    settingsPage<NotificationExceptionsRoute> { entry ->
        val scope =
            NotificationScope.entries.firstOrNull {
                it.name ==
                    entry.toRoute<NotificationExceptionsRoute>().scope
            }
        if (scope != null) {
            NotificationExceptionsScreenRoute(scope = scope, onBack = onBack)
        } else {
            // A kind of chats this version does not know, e.g. from a back stack restored after an update.
            LaunchedEffect(Unit) { onBack() }
        }
    }
    settingsPage<DataStorageRoute> {
        DataStorageScreenRoute(
            onOpenAutoDownload = { onOpen(AutoDownloadRoute(it.name)) },
            onOpenProxies = { onOpen(ProxiesRoute) },
            onOpenNetworkUsage = { onOpen(NetworkUsageRoute) },
            onBack = onBack,
        )
    }
    settingsPage<AutoDownloadRoute> { entry ->
        val network = AutoDownloadNetwork.entries.firstOrNull { it.name == entry.toRoute<AutoDownloadRoute>().network }
        if (network != null) {
            AutoDownloadScreenRoute(network = network, onBack = onBack)
        } else {
            // A network this version does not know, e.g. from a back stack restored after an update.
            LaunchedEffect(Unit) { onBack() }
        }
    }
    networkPages(onOpen, onBack)
    settingsPage<LanguageRoute> { LanguageScreenRoute(onBack = onBack) }
    settingsPage<AboutRoute> { AboutScreenRoute(onOpenLicenses = { onOpen(OpenSourceLicensesRoute) }, onBack = onBack) }
    settingsPage<OpenSourceLicensesRoute> {
        OpenSourceLicensesScreenRoute(onOpenLibrary = { onOpen(OpenSourceLibraryRoute(it)) }, onBack = onBack)
    }
    settingsPage<OpenSourceLibraryRoute> { entry ->
        OpenSourceLibraryScreenRoute(libraryId = entry.toRoute<OpenSourceLibraryRoute>().libraryId, onBack = onBack)
    }
    privacyPages(onOpen, onBack)
    devicePages(onOpen, onBack, onScanQrCode)
    settingsPage<PasscodeLockRoute> {
        PasscodeLockScreenRoute(onSetPasscode = { onOpen(SetPasscodeRoute) }, onBack = onBack)
    }
    settingsPage<SetPasscodeRoute> { SetPasscodeScreenRoute(onSaved = onBack, onBack = onBack) }
    twoStepVerificationPages(onOpen, onBack, onTwoStepVerificationDone)
}

/**
 * Active sessions, the confirmation of a device's login ([QrLoginRoute]) and the user's QR code ([MyQrCodeRoute]),
 * whose scanner opens with [onScanQrCode].
 */
private fun NavGraphBuilder.devicePages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
    onScanQrCode: (forDeviceLogin: Boolean) -> Unit,
) {
    settingsPage<ActiveSessionsRoute> {
        ActiveSessionsScreenRoute(onLinkDevice = { onScanQrCode(true) }, onBack = onBack)
    }
    settingsPage<QrLoginRoute> { QrLoginScreenRoute(onBack = onBack) }
    settingsPage<MyQrCodeRoute> {
        MyQrCodeScreenRoute(
            onScan = { onScanQrCode(false) },
            onEditProfile = { onOpen(EditProfileRoute) },
            onBack = onBack,
        )
    }
}

/** [TwoStepVerificationRoute] and the flows it pushes above itself, [TwoStepVerificationFlowRoute]. */
private fun NavGraphBuilder.twoStepVerificationPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
    onDone: (TwoStepVerificationResult) -> Unit,
) {
    settingsPage<TwoStepVerificationRoute> { entry ->
        val results = entry.savedStateHandle
        val result by results.getStateFlow<String?>(TWO_STEP_VERIFICATION_RESULT, null).collectAsStateWithLifecycle()
        TwoStepVerificationScreenRoute(
            result = TwoStepVerificationResult.entries.firstOrNull { it.name == result },
            onResultShown = { results[TWO_STEP_VERIFICATION_RESULT] = null },
            onOpen = { onOpen(TwoStepVerificationFlowRoute(it.name)) },
            onBack = onBack,
        )
    }
    settingsPage<TwoStepVerificationFlowRoute> { entry ->
        val name = entry.toRoute<TwoStepVerificationFlowRoute>().flow
        val flow = TwoStepVerificationFlow.entries.firstOrNull { it.name == name }
        if (flow != null) {
            TwoStepVerificationFlowScreenRoute(flow = flow, onFinished = onDone, onBack = onBack)
        } else {
            // A flow this version does not know, e.g. from a back stack restored after an update.
            LaunchedEffect(Unit) { onBack() }
        }
    }
}

/**
 * A settings page of route [T]: [content], with its back button unless it is the page the detail pane of
 * [SettingsListDetail] shows beside Settings home ([LocalSettingsPageWithoutBack]).
 */
internal inline fun <reified T : Any> NavGraphBuilder.settingsPage(
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        CompositionLocalProvider(
            LocalSettingsBackButton provides (LocalSettingsPageWithoutBack.current != T::class),
        ) { content(entry) }
    }
}

/**
 * Leaves Edit profile after it saved the profile, back to the Settings tab below it, which then says so in a
 * snackbar: a result in the tab's saved state, as Navigation returns results.
 */
fun NavController.popBackStackAfterProfileSaved() {
    previousBackStackEntry?.savedStateHandle?.set(PROFILE_SAVED_RESULT, true)
    popBackStack()
}

/** The result Edit profile leaves for the Settings tab: true once it saved the profile. */
private const val PROFILE_SAVED_RESULT = "settings.profileSaved"

/**
 * Leaves a flow of 2-step verification once it is done, back to the 2-step verification page below it, which then says
 * what it did ([result]) in a snackbar: a result in the page's saved state, as Navigation returns results.
 */
fun NavController.popBackStackAfterTwoStepVerification(result: TwoStepVerificationResult) {
    previousBackStackEntry?.savedStateHandle?.set(TWO_STEP_VERIFICATION_RESULT, result.name)
    popBackStack()
}

/** The result a flow of 2-step verification leaves for its page: the name of its [TwoStepVerificationResult]. */
private const val TWO_STEP_VERIFICATION_RESULT = "settings.twoStepVerificationResult"

internal val SettingsPage.route: Any
    get() =
        when (this) {
            SettingsPage.EditProfile -> EditProfileRoute
            SettingsPage.Appearance -> AppearanceRoute
            SettingsPage.ChatFolders -> ChatFoldersRoute
            SettingsPage.Stickers -> StickersRoute
            SettingsPage.Notifications -> NotificationSettingsRoute
            SettingsPage.DataStorage -> DataStorageRoute
            SettingsPage.Language -> LanguageRoute
            SettingsPage.About -> AboutRoute
            SettingsPage.PrivacySecurity -> PrivacySecurityRoute
        }

private val NotificationSettingsPage.route: Any
    get() =
        when (this) {
            is NotificationSettingsPage.Exceptions -> NotificationExceptionsRoute(scope.name)
            NotificationSettingsPage.Reactions -> ReactionNotificationsRoute
        }
