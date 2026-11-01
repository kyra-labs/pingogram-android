package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi
import java.net.URI
import java.net.URLDecoder

/**
 * What kind of Telegram link a text is, as TDLib's `getInternalLinkType` tells it: TDLib parses links on the device,
 * so the demo, which has no TDLib, parses them itself (`docs/adr/0034-demo-mode-for-store-review.md`). It covers the
 * kinds the app opens (`docs/adr/0013-deep-links.md`) in their `tg://` and `t.me` forms; a `tg://` link it does not
 * know is an unknown deep link, and a web link that is not Telegram's is no internal link at all (null).
 *
 * One branch per kind of link: hence the suppressions.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
internal object DemoLinkParser {
    private val hosts = setOf("t.me", "telegram.me", "telegram.dog")
    private val username = Regex("[A-Za-z][A-Za-z0-9_]{3,31}")
    private val digits = Regex("[0-9]+")

    /** `c`, the channel's id, the post's number. */
    private const val PRIVATE_POST_SEGMENTS = 3

    fun parse(link: String): TdApi.InternalLinkType? {
        val trimmed = link.trim()
        val withScheme =
            if (hosts.any {
                    trimmed.startsWith(
                        "$it/",
                        ignoreCase = true,
                    )
                }
            ) {
                "https://$trimmed"
            } else {
                trimmed
            }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val query = queryOf(uri.rawQuery)
        return when (uri.scheme?.lowercase()) {
            "tg" -> tg(uri, query, trimmed)
            "http", "https" -> web(uri, query, trimmed)
            else -> null
        }
    }

    private fun tg(
        uri: URI,
        query: Map<String, String>,
        link: String,
    ): TdApi.InternalLinkType {
        val target =
            (
                uri.host ?: uri.schemeSpecificPart
                    .substringBefore('?')
                    .trimStart('/')
                    .substringBefore('/')
            ).lowercase()
        val path =
            uri.path
                ?.trim('/')
                ?.lowercase()
                .orEmpty()
        return when (target) {
            "resolve" -> {
                resolve(query["domain"].orEmpty(), query, link)
            }

            "join" -> {
                TdApi.InternalLinkTypeChatInvite("https://t.me/+${query["invite"].orEmpty()}")
            }

            "addlist" -> {
                TdApi.InternalLinkTypeChatFolderInvite("https://t.me/addlist/${query["slug"].orEmpty()}")
            }

            "addstickers" -> {
                TdApi.InternalLinkTypeStickerSet(query["set"].orEmpty(), false)
            }

            "addemoji" -> {
                TdApi.InternalLinkTypeStickerSet(query["set"].orEmpty(), true)
            }

            "proxy", "socks" -> {
                proxy(target, query) ?: TdApi.InternalLinkTypeUnknownDeepLink(link)
            }

            "login" -> {
                query["code"]?.let(TdApi::InternalLinkTypeAuthenticationCode)
                    ?: TdApi.InternalLinkTypeQrCodeAuthentication()
            }

            "msg_url" -> {
                draft(query)
            }

            "privatepost" -> {
                TdApi.InternalLinkTypeMessage(link)
            }

            "boost" -> {
                TdApi.InternalLinkTypeChatBoost(link)
            }

            "settings" -> {
                if (path == "saved-messages") TdApi.InternalLinkTypeSavedMessages() else settings(path)
            }

            "new" -> {
                TdApi.InternalLinkTypeNewPrivateChat()
            }

            "chats" -> {
                if (path == "search") TdApi.InternalLinkTypeSearch() else TdApi.InternalLinkTypeUnknownDeepLink(link)
            }

            "call" -> {
                TdApi.InternalLinkTypeGroupCall("https://t.me/call/${query["slug"].orEmpty()}")
            }

            else -> {
                TdApi.InternalLinkTypeUnknownDeepLink(link)
            }
        }
    }

    private fun web(
        uri: URI,
        query: Map<String, String>,
        link: String,
    ): TdApi.InternalLinkType? {
        val host = uri.host?.lowercase() ?: return null
        val base = hosts.firstOrNull { host == it || host.endsWith(".$it") } ?: return null
        if (host != base) {
            // username.t.me
            val name = host.removeSuffix(".$base")
            return if (username.matches(name)) resolve(name, query, link) else null
        }
        val segments =
            uri.path
                .orEmpty()
                .trim('/')
                .split('/')
                .filter { it.isNotEmpty() }
        val first = segments.firstOrNull() ?: return null
        val second = segments.getOrNull(1).orEmpty()
        return when {
            first.startsWith("+") && digits.matches(first.drop(1)) -> {
                TdApi.InternalLinkTypeUserPhoneNumber(first.drop(1), query["text"].orEmpty(), false)
            }

            first.startsWith("+") -> {
                TdApi.InternalLinkTypeChatInvite("https://t.me/$first")
            }

            first == "joinchat" -> {
                TdApi.InternalLinkTypeChatInvite("https://t.me/+$second")
            }

            first == "addlist" -> {
                TdApi.InternalLinkTypeChatFolderInvite("https://t.me/addlist/$second")
            }

            first == "addstickers" -> {
                TdApi.InternalLinkTypeStickerSet(second, false)
            }

            first == "addemoji" -> {
                TdApi.InternalLinkTypeStickerSet(second, true)
            }

            first == "proxy" || first == "socks" -> {
                proxy(first, query)
            }

            first == "share" -> {
                draft(query)
            }

            first == "c" && segments.size >= PRIVATE_POST_SEGMENTS -> {
                TdApi.InternalLinkTypeMessage(link)
            }

            (first == "c" && "boost" in query) || first == "boost" -> {
                TdApi.InternalLinkTypeChatBoost(link)
            }

            first == "call" -> {
                TdApi.InternalLinkTypeGroupCall("https://t.me/call/$second")
            }

            first == "login" && second.isNotEmpty() -> {
                TdApi.InternalLinkTypeAuthenticationCode(second)
            }

            username.matches(first) && digits.matches(second) -> {
                TdApi.InternalLinkTypeMessage(link)
            }

            username.matches(first) -> {
                resolve(first, query, link)
            }

            else -> {
                null
            }
        }
    }

    private fun resolve(
        name: String,
        query: Map<String, String>,
        link: String,
    ): TdApi.InternalLinkType =
        when {
            "start" in query -> TdApi.InternalLinkTypeBotStart(name, query["start"].orEmpty(), false)
            "post" in query -> TdApi.InternalLinkTypeMessage(link)
            "boost" in query -> TdApi.InternalLinkTypeChatBoost(link)
            else -> TdApi.InternalLinkTypePublicChat(name, query["text"].orEmpty(), "profile" in query)
        }

    private fun proxy(
        kind: String,
        query: Map<String, String>,
    ): TdApi.InternalLinkType? {
        val server = query["server"]?.takeIf { it.isNotBlank() } ?: return null
        val port = query["port"]?.toIntOrNull() ?: return null
        val type =
            if (kind == "socks") {
                TdApi.ProxyTypeSocks5(query["user"].orEmpty(), query["pass"].orEmpty())
            } else {
                TdApi.ProxyTypeMtproto(query["secret"].orEmpty())
            }
        return TdApi.InternalLinkTypeProxy(TdApi.Proxy(server, port, type))
    }

    private fun draft(query: Map<String, String>): TdApi.InternalLinkType {
        val url = query["url"].orEmpty()
        val text = listOf(url, query["text"].orEmpty()).filter { it.isNotEmpty() }.joinToString("\n")
        return TdApi.InternalLinkTypeMessageDraft(TdApi.FormattedText(text, emptyArray()), url.isNotEmpty())
    }

    private fun settings(path: String): TdApi.InternalLinkType {
        val section: TdApi.SettingsSection? =
            when (path) {
                "edit_profile" -> TdApi.SettingsSectionEditProfile("")
                "themes", "appearance" -> TdApi.SettingsSectionAppearance("")
                "folders" -> TdApi.SettingsSectionChatFolders("")
                "data" -> TdApi.SettingsSectionDataAndStorage("")
                "devices" -> TdApi.SettingsSectionDevices("")
                "language" -> TdApi.SettingsSectionLanguage("")
                "notifications" -> TdApi.SettingsSectionNotifications("")
                "privacy" -> TdApi.SettingsSectionPrivacyAndSecurity("")
                "qr_code" -> TdApi.SettingsSectionQrCode("")
                else -> null
            }
        return TdApi.InternalLinkTypeSettings(section)
    }

    private fun queryOf(raw: String?): Map<String, String> =
        raw
            .orEmpty()
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { pair ->
                val key = pair.substringBefore('=')
                val value = if ('=' in pair) pair.substringAfter('=') else ""
                decode(key).lowercase() to decode(value)
            }

    private fun decode(text: String): String = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
}
