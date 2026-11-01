package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * The demo's settings that Telegram keeps for the account (`docs/adr/0034-demo-mode-for-store-review.md`): 2-step
 * verification, the login email, changing the phone number, how long the account may stay away, auto-delete,
 * notification settings and exceptions, reaction notifications, storage and network usage, proxies (kept, never
 * connected through: the demo has no network), the archive's settings and the quick reaction.
 *
 * Codes: nothing is sent to an email or a phone in the demo, so any code of the asked length is taken. It keeps this
 * state itself (the password in [DemoWorld.password], which an ownership transfer asks for); every change goes into
 * the journal like the others.
 *
 * One branch per function: hence the suppressions.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "TooManyFunctions", "ReturnCount")
internal class DemoSettings : DemoArea {
    private var hint = ""
    private var recoveryEmail = ""
    private var pendingRecoveryEmail = ""
    private var loginEmail = SAMPLE_LOGIN_EMAIL
    private var pendingLoginEmail = ""
    private var pendingPhoneNumber = ""
    private var accountTtlDays = DEFAULT_ACCOUNT_TTL
    private var autoDeleteTime = 0
    private val scopes = mutableMapOf<String, TdApi.ScopeNotificationSettings>()
    private var reactionNotifications = defaultReactionNotifications()
    private var networkSince = 0
    private var networkReset = false
    private val proxies = mutableListOf<TdApi.AddedProxy>()
    private var nextProxyId = 1
    private var archive = TdApi.ArchiveChatListSettings(false, false, false)

    override fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer? =
        when (function) {
            is TdApi.GetPasswordState -> {
                ok(passwordState(world))
            }

            is TdApi.SetPassword -> {
                setPassword(world, function)
            }

            is TdApi.GetRecoveryEmailAddress -> {
                recoveryAddress(world, function.password)
            }

            is TdApi.SetRecoveryEmailAddress -> {
                setRecoveryEmail(world, function.password, function.newRecoveryEmailAddress)
            }

            is TdApi.CheckRecoveryEmailAddressCode -> {
                checkRecoveryCode(world, function.code)
            }

            is TdApi.ResendRecoveryEmailAddressCode -> {
                resendRecoveryCode(world)
            }

            is TdApi.CancelRecoveryEmailAddressVerification -> {
                cancelRecovery(world)
            }

            is TdApi.RequestPasswordRecovery -> {
                requestRecovery()
            }

            is TdApi.CheckPasswordRecoveryCode -> {
                code(function.recoveryCode)?.let { ok(TdApi.Ok()) }
                    ?: badRequest(CODE_INVALID)
            }

            is TdApi.RecoverPassword -> {
                recover(world, function)
            }

            is TdApi.ResetPassword -> {
                resetPassword(world)
            }

            is TdApi.CancelPasswordReset -> {
                ok(TdApi.Ok())
            }

            is TdApi.SetLoginEmailAddress -> {
                setLoginEmail(function.newLoginEmailAddress)
            }

            is TdApi.ResendLoginEmailAddressCode -> {
                codeInfo(pendingLoginEmail)?.let(::ok) ?: badRequest(EMAIL_INVALID)
            }

            is TdApi.CheckLoginEmailAddressCode -> {
                checkLoginEmailCode(function.code)
            }

            is TdApi.SendPhoneNumberCode -> {
                sendPhoneCode(world, function.phoneNumber)
            }

            is TdApi.ResendPhoneNumberCode -> {
                phoneCodeInfo(pendingPhoneNumber)?.let(::ok) ?: badRequest(PHONE_INVALID)
            }

            is TdApi.CheckPhoneNumberCode -> {
                checkPhoneCode(world, function.code)
            }

            is TdApi.GetAccountTtl -> {
                ok(TdApi.AccountTtl(accountTtlDays))
            }

            is TdApi.SetAccountTtl -> {
                okChanged(emptyList()).also { accountTtlDays = function.ttl.days }
            }

            is TdApi.GetDefaultMessageAutoDeleteTime -> {
                ok(TdApi.MessageAutoDeleteTime(autoDeleteTime))
            }

            is TdApi.SetDefaultMessageAutoDeleteTime -> {
                okChanged(emptyList()).also { autoDeleteTime = function.messageAutoDeleteTime.time }
            }

            is TdApi.GetScopeNotificationSettings -> {
                ok(scope(function.scope))
            }

            is TdApi.SetScopeNotificationSettings -> {
                setScope(function.scope, function.notificationSettings)
            }

            is TdApi.GetChatNotificationSettingsExceptions -> {
                exceptions(world, function.scope)
            }

            is TdApi.ResetAllNotificationSettings -> {
                resetNotifications(world)
            }

            is TdApi.SetReactionNotificationSettings -> {
                setReactionNotifications(function.notificationSettings)
            }

            is TdApi.GetStorageStatisticsFast -> {
                ok(storage(world))
            }

            is TdApi.OptimizeStorage -> {
                ok(TdApi.StorageStatistics(0, 0, emptyArray()))
            }

            is TdApi.GetNetworkStatistics -> {
                ok(network(world))
            }

            is TdApi.ResetNetworkStatistics -> {
                resetNetwork(world)
            }

            is TdApi.GetProxies -> {
                ok(TdApi.AddedProxies(proxies.toTypedArray()))
            }

            is TdApi.AddProxy -> {
                addProxy(world, function.proxy, function.enable, function.comment)
            }

            is TdApi.EditProxy -> {
                editProxy(world, function)
            }

            is TdApi.EnableProxy -> {
                enableProxy(world, function.proxyId)
            }

            is TdApi.DisableProxy -> {
                okChanged(emptyList()).also { proxies.forEach { it.isEnabled = false } }
            }

            is TdApi.RemoveProxy -> {
                okChanged(emptyList()).also { proxies.removeAll { it.id == function.proxyId } }
            }

            // A made-up answer time: the demo connects to nothing.
            is TdApi.PingProxy -> {
                ok(TdApi.Seconds(PING_SECONDS))
            }

            is TdApi.GetArchiveChatListSettings -> {
                ok(archive)
            }

            is TdApi.SetArchiveChatListSettings -> {
                okChanged(emptyList()).also { archive = function.settings }
            }

            is TdApi.ReadChatList -> {
                readList(world, function.chatList)
            }

            is TdApi.SetDefaultReactionType -> {
                okChanged(listOf(TdApi.UpdateDefaultReactionType(function.reactionType)))
            }

            else -> {
                null
            }
        }

    /** What TDLib announces at its start that Pingogram keeps: the reaction notification settings. */
    fun announce(): List<TdApi.Update> = listOf(TdApi.UpdateReactionNotificationSettings(reactionNotifications))

    private fun passwordState(world: DemoWorld) =
        TdApi.PasswordState(
            world.password != null,
            hint,
            recoveryEmail.isNotEmpty(),
            false,
            codeInfo(pendingRecoveryEmail),
            pattern(loginEmail),
            0,
        )

    private fun setPassword(
        world: DemoWorld,
        function: TdApi.SetPassword,
    ): DemoAnswer {
        val current = world.password
        if (current != null && function.oldPassword != current) return badRequest(PASSWORD_INVALID)
        world.password = function.newPassword.takeIf { it.isNotEmpty() }
        hint = if (world.password == null) "" else function.newHint.orEmpty()
        if (world.password == null) {
            recoveryEmail = ""
            pendingRecoveryEmail = ""
        } else if (function.setRecoveryEmailAddress) {
            pendingRecoveryEmail = function.newRecoveryEmailAddress.orEmpty()
        }
        return changed(passwordState(world), emptyList())
    }

    private fun recoveryAddress(
        world: DemoWorld,
        password: String,
    ): DemoAnswer =
        if (world.password != null && password == world.password) {
            ok(TdApi.RecoveryEmailAddress(recoveryEmail))
        } else {
            badRequest(PASSWORD_INVALID)
        }

    private fun setRecoveryEmail(
        world: DemoWorld,
        password: String,
        email: String,
    ): DemoAnswer {
        if (world.password == null || password != world.password) return badRequest(PASSWORD_INVALID)
        if (!email.contains('@')) return badRequest(EMAIL_INVALID)
        pendingRecoveryEmail = email
        return changed(passwordState(world), emptyList())
    }

    private fun checkRecoveryCode(
        world: DemoWorld,
        code: String,
    ): DemoAnswer {
        if (pendingRecoveryEmail.isEmpty()) return badRequest(EMAIL_INVALID)
        code(code) ?: return badRequest(CODE_INVALID)
        recoveryEmail = pendingRecoveryEmail
        pendingRecoveryEmail = ""
        return changed(passwordState(world), emptyList())
    }

    private fun resendRecoveryCode(world: DemoWorld): DemoAnswer =
        if (pendingRecoveryEmail.isEmpty()) badRequest(EMAIL_INVALID) else ok(passwordState(world))

    private fun cancelRecovery(world: DemoWorld): DemoAnswer {
        pendingRecoveryEmail = ""
        return changed(passwordState(world), emptyList())
    }

    private fun requestRecovery(): DemoAnswer = codeInfo(recoveryEmail)?.let(::ok) ?: badRequest("PASSWORD_RECOVERY_NA")

    private fun recover(
        world: DemoWorld,
        function: TdApi.RecoverPassword,
    ): DemoAnswer {
        code(function.recoveryCode) ?: return badRequest(CODE_INVALID)
        world.password = function.newPassword.takeIf { it.isNotEmpty() }
        hint = function.newHint.orEmpty()
        return changed(passwordState(world), emptyList())
    }

    /** Telegram resets a forgotten password after a waiting period; the demo resets it at once. */
    private fun resetPassword(world: DemoWorld): DemoAnswer {
        world.password = null
        hint = ""
        recoveryEmail = ""
        return changed(TdApi.ResetPasswordResultOk(), emptyList())
    }

    private fun setLoginEmail(email: String): DemoAnswer {
        if (!email.contains('@')) return badRequest(EMAIL_INVALID)
        pendingLoginEmail = email
        return changed(codeInfo(email)!!, emptyList())
    }

    private fun checkLoginEmailCode(code: TdApi.EmailAddressAuthentication): DemoAnswer {
        val typed = (code as? TdApi.EmailAddressAuthenticationCode)?.code ?: return needsAccount()
        if (pendingLoginEmail.isEmpty()) return badRequest(EMAIL_INVALID)
        code(typed) ?: return badRequest(CODE_INVALID)
        loginEmail = pendingLoginEmail
        pendingLoginEmail = ""
        return okChanged(emptyList())
    }

    private fun sendPhoneCode(
        world: DemoWorld,
        phone: String,
    ): DemoAnswer {
        val digits = phone.filter(Char::isDigit)
        if (digits.length < MIN_PHONE_DIGITS) return badRequest(PHONE_INVALID)
        if (world.users.values.any {
                it.id != DemoData.ME && it.phoneNumber == digits
            }
        ) {
            return badRequest("PHONE_NUMBER_OCCUPIED")
        }
        pendingPhoneNumber = digits
        return changed(phoneCodeInfo(digits)!!, emptyList())
    }

    private fun checkPhoneCode(
        world: DemoWorld,
        code: String,
    ): DemoAnswer {
        if (pendingPhoneNumber.isEmpty()) return badRequest(PHONE_INVALID)
        code(code) ?: return badRequest("PHONE_CODE_INVALID")
        val me = world.users.getValue(DemoData.ME)
        me.phoneNumber = pendingPhoneNumber
        pendingPhoneNumber = ""
        return okChanged(listOf(TdApi.UpdateUser(me)))
    }

    private fun scope(scope: TdApi.NotificationSettingsScope): TdApi.ScopeNotificationSettings =
        scopes.getOrPut(scope.javaClass.simpleName) { DemoNotifications.scopeSettings() }

    private fun setScope(
        scope: TdApi.NotificationSettingsScope,
        settings: TdApi.ScopeNotificationSettings,
    ): DemoAnswer {
        scopes[scope.javaClass.simpleName] = settings
        return okChanged(listOf(TdApi.UpdateScopeNotificationSettings(scope, settings)))
    }

    /** The chats of a kind whose own mute setting differs from the kind's: the muted ones, in the demo. */
    private fun exceptions(
        world: DemoWorld,
        scope: TdApi.NotificationSettingsScope?,
    ): DemoAnswer {
        val muted = world.allChats().filter { it.positions.isNotEmpty() && !it.notificationSettings.useDefaultMuteFor }
        val chats =
            muted.filter { chat ->
                when (scope) {
                    is TdApi.NotificationSettingsScopePrivateChats -> {
                        chat.type is TdApi.ChatTypePrivate ||
                            chat.type is TdApi.ChatTypeSecret
                    }

                    is TdApi.NotificationSettingsScopeGroupChats -> {
                        chat.type is TdApi.ChatTypeBasicGroup ||
                            (chat.type as? TdApi.ChatTypeSupergroup)?.isChannel == false
                    }

                    is TdApi.NotificationSettingsScopeChannelChats -> {
                        (chat.type as? TdApi.ChatTypeSupergroup)?.isChannel ==
                            true
                    }

                    else -> {
                        true
                    }
                }
            }
        return ok(TdApi.Chats(chats.size, chats.map { it.id }.toLongArray()))
    }

    private fun resetNotifications(world: DemoWorld): DemoAnswer {
        val updates = mutableListOf<TdApi.Update>()
        listOf(
            TdApi.NotificationSettingsScopePrivateChats(),
            TdApi.NotificationSettingsScopeGroupChats(),
            TdApi.NotificationSettingsScopeChannelChats(),
        ).forEach {
            scopes[it.javaClass.simpleName] = DemoNotifications.scopeSettings()
            updates += TdApi.UpdateScopeNotificationSettings(it, DemoNotifications.scopeSettings())
        }
        world.allChats().filter { !it.notificationSettings.useDefaultMuteFor }.forEach {
            updates +=
                world.mute(
                    it.id,
                    0,
                )
        }
        return okChanged(updates)
    }

    private fun setReactionNotifications(settings: TdApi.ReactionNotificationSettings): DemoAnswer {
        reactionNotifications = settings
        return okChanged(listOf(TdApi.UpdateReactionNotificationSettings(settings)))
    }

    /** The demo's files on the phone: those the user sent, and the sample pictures and sound once drawn. */
    private fun storage(world: DemoWorld): TdApi.StorageStatisticsFast {
        val sent = world.sentFileSizes()
        return TdApi.StorageStatisticsFast(
            sent.sum() + SAMPLE_FILES_SIZE,
            sent.size + DemoFile.entries.size,
            DATABASE_SIZE,
            0,
            0,
        )
    }

    /** Made-up usage of the last week by network and kind; the demo itself sends and receives nothing. */
    private fun network(world: DemoWorld): TdApi.NetworkStatistics {
        if (networkSince == 0) networkSince = world.data.channel.date
        if (networkReset) return TdApi.NetworkStatistics(networkSince, emptyArray())
        val entries =
            listOf(
                file(TdApi.FileTypePhoto(), TdApi.NetworkTypeWiFi(), WIFI_PHOTOS_SENT, WIFI_PHOTOS_RECEIVED),
                file(TdApi.FileTypeVoiceNote(), TdApi.NetworkTypeWiFi(), WIFI_VOICE_SENT, WIFI_VOICE_RECEIVED),
                file(TdApi.FileTypePhoto(), TdApi.NetworkTypeMobile(), MOBILE_PHOTOS_SENT, MOBILE_PHOTOS_RECEIVED),
                file(TdApi.FileTypeNone(), TdApi.NetworkTypeMobile(), MOBILE_OTHER_SENT, MOBILE_OTHER_RECEIVED),
            )
        return TdApi.NetworkStatistics(networkSince, entries.toTypedArray())
    }

    private fun file(
        type: TdApi.FileType,
        network: TdApi.NetworkType,
        sent: Long,
        received: Long,
    ) = TdApi.NetworkStatisticsEntryFile(type, network, sent, received)

    private fun resetNetwork(world: DemoWorld): DemoAnswer {
        networkSince = world.now
        networkReset = true
        return okChanged(emptyList())
    }

    private fun addProxy(
        world: DemoWorld,
        proxy: TdApi.Proxy,
        enable: Boolean,
        comment: String?,
    ): DemoAnswer {
        if (enable) proxies.forEach { it.isEnabled = false }
        val added = TdApi.AddedProxy(nextProxyId++, if (enable) world.now else 0, enable, comment.orEmpty(), proxy)
        proxies += added
        return changed(added, emptyList())
    }

    private fun editProxy(
        world: DemoWorld,
        function: TdApi.EditProxy,
    ): DemoAnswer {
        val proxy = proxies.firstOrNull { it.id == function.proxyId } ?: return notFound()
        if (function.enable) proxies.forEach { it.isEnabled = false }
        proxy.proxy = function.proxy
        proxy.comment = function.comment.orEmpty()
        proxy.isEnabled = function.enable
        if (function.enable) proxy.lastUsedDate = world.now
        return changed(proxy, emptyList())
    }

    private fun enableProxy(
        world: DemoWorld,
        id: Int,
    ): DemoAnswer {
        val proxy = proxies.firstOrNull { it.id == id } ?: return notFound()
        proxies.forEach { it.isEnabled = false }
        proxy.isEnabled = true
        proxy.lastUsedDate = world.now
        return okChanged(emptyList())
    }

    private fun readList(
        world: DemoWorld,
        list: TdApi.ChatList,
    ): DemoAnswer {
        val updates =
            world.chatsIn(list).flatMap { chat ->
                world.read(chat.id, world.messagesOf(chat.id).lastOrNull()?.id ?: 0) +
                    if (chat.isMarkedAsUnread) world.markUnread(chat.id, false) else emptyList()
            }
        return okChanged(updates)
    }

    /** A typed code the demo takes: any digits of the length it asked for, as nothing was sent. */
    private fun code(typed: String): String? = typed.filter(Char::isDigit).takeIf { it.length == CODE_LENGTH }

    private fun codeInfo(email: String): TdApi.EmailAddressAuthenticationCodeInfo? =
        email.takeIf { it.isNotEmpty() }?.let { TdApi.EmailAddressAuthenticationCodeInfo(pattern(it), CODE_LENGTH) }

    private fun phoneCodeInfo(phone: String): TdApi.AuthenticationCodeInfo? =
        phone.takeIf { it.isNotEmpty() }?.let {
            TdApi.AuthenticationCodeInfo(it, TdApi.AuthenticationCodeTypeSms(CODE_LENGTH), null, 0)
        }

    /** An address as Telegram shows it back: its first letter and its domain, "s***@example.org". */
    private fun pattern(email: String): String {
        val at = email.indexOf('@')
        return if (at <= 0) email else email.first() + "***" + email.substring(at)
    }

    private fun defaultReactionNotifications() =
        TdApi.ReactionNotificationSettings(
            TdApi.ReactionNotificationSourceContacts(),
            TdApi.ReactionNotificationSourceContacts(),
            TdApi.ReactionNotificationSourceAll(),
            0,
            true,
        )

    private companion object {
        const val WIFI_PHOTOS_SENT = 1_200_000L
        const val WIFI_PHOTOS_RECEIVED = 34_000_000L
        const val WIFI_VOICE_SENT = 240_000L
        const val WIFI_VOICE_RECEIVED = 1_100_000L
        const val MOBILE_PHOTOS_SENT = 300_000L
        const val MOBILE_PHOTOS_RECEIVED = 8_400_000L
        const val MOBILE_OTHER_SENT = 90_000L
        const val MOBILE_OTHER_RECEIVED = 1_600_000L
        const val SAMPLE_LOGIN_EMAIL = "sam@example.org"
        const val CODE_LENGTH = 5
        const val MIN_PHONE_DIGITS = 7
        const val DEFAULT_ACCOUNT_TTL = 180
        const val PING_SECONDS = 0.084
        const val SAMPLE_FILES_SIZE = 3_400_000L
        const val DATABASE_SIZE = 2_100_000L
        const val PASSWORD_INVALID = "PASSWORD_HASH_INVALID"
        const val EMAIL_INVALID = "EMAIL_INVALID"
        const val PHONE_INVALID = "PHONE_NUMBER_INVALID"
        const val CODE_INVALID = "CODE_INVALID"
    }
}
