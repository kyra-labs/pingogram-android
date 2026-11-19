package dev.kyralabs.pingogram.feature.settings.ui.home

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.kyralabs.pingogram.core.ui.theme.CategoryHue
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.OtherAccountUiModel
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPage
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageSelection
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSubPage
import kotlinx.collections.immutable.ImmutableList
import java.text.Normalizer
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

// The one registry of Settings home's entries (docs/DESIGN_SYSTEM.md §13.9): its groups and
// what settings search finds.

/** Where an entry of Settings home leads: a settings page, or a page of another feature. */
internal sealed interface HomeDestination {
    data class Page(
        val page: SettingsPage,
    ) : HomeDestination

    /** The Bookmarks page (`:feature:chat`, `LocalBookmarksLauncher`), a tool of On this phone. */
    data object Bookmarks : HomeDestination

    /** A page above a page of Settings home, opened with that page below it (settings search only). */
    data class SubPage(
        val subPage: SettingsSubPage,
    ) : HomeDestination
}

/** An entry: where it leads ([destination]), its [title], [summary] and [icon]. */
internal class HomeEntry(
    val destination: HomeDestination,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
    @param:DrawableRes val icon: Int,
)

/**
 * A group of entries under its [title], its rows' icons in circles of its own [category] color
 * (`docs/DESIGN_SYSTEM.md` §13.9), with a [footer] where it explains what the group holds.
 */
internal class HomeGroup(
    @param:StringRes val title: Int,
    val entries: List<HomeEntry>,
    val category: CategoryHue,
    @param:StringRes val footer: Int? = null,
)

/**
 * The category color of [entry]'s group on Settings home, or of its parent page's for a page above one (settings
 * search); teal for Edit profile and the QR code, which are in no group.
 */
internal fun homeCategory(entry: HomeEntry): CategoryHue {
    val destination =
        (entry.destination as? HomeDestination.SubPage)?.let { HomeDestination.Page(it.subPage.parent) }
            ?: entry.destination
    return HomeGroups.firstOrNull { group -> group.entries.any { it.destination == destination } }?.category
        ?: CategoryHue.Teal
}

internal fun page(
    page: SettingsPage,
    @StringRes title: Int,
    @StringRes summary: Int,
    @DrawableRes icon: Int,
) = HomeEntry(HomeDestination.Page(page), title, summary, icon)

/**
 * "On this phone": the tools that keep what they hold on the device, never on Telegram's servers (Reminders,
 * Bookmarks, Focus, Keyword alerts), and Backup & restore, which puts them in a file the user keeps. Each adds its
 * row here as it lands; until then the group has none and is not shown. Owner decision 2026-11-15 (board 29g).
 */
internal val OnThisPhoneGroup: HomeGroup =
    HomeGroup(
        R.string.settings_home_on_this_phone,
        category = CategoryHue.Pink,
        entries =
            listOf(
                page(
                    SettingsPage.Reminders,
                    R.string.settings_reminders,
                    R.string.settings_reminders_summary,
                    UiR.drawable.ic_alarm_filled,
                ),
                HomeEntry(
                    HomeDestination.Bookmarks,
                    R.string.settings_bookmarks,
                    R.string.settings_bookmarks_summary,
                    UiR.drawable.ic_bookmark_filled,
                ),
                page(
                    SettingsPage.Focus,
                    R.string.settings_focus,
                    R.string.settings_focus_summary,
                    UiR.drawable.ic_notifications_off_filled,
                ),
                page(
                    SettingsPage.KeywordAlerts,
                    R.string.settings_keyword_alerts,
                    R.string.settings_keyword_alerts_summary,
                    UiR.drawable.ic_notifications_filled,
                ),
                page(
                    SettingsPage.Backup,
                    R.string.settings_backup,
                    R.string.settings_backup_summary,
                    UiR.drawable.ic_settings_backup_restore,
                ),
            ),
        footer = R.string.settings_home_on_this_phone_footer,
    )

/**
 * Settings home's groups. Every page Settings home opens is in one of them: "On this phone" (shown once a tool there
 * has a row), "Make it yours", "Alerts and privacy", and "App" for the rest. Owner decisions: "On this phone" first
 * (2026-11-15); no "More" group, Devices with the alerts and privacy, Language in App, and Recent calls only on the
 * Contacts tab (2026-11-19).
 */
internal val HomeGroups: List<HomeGroup> =
    listOf(
        OnThisPhoneGroup,
        HomeGroup(
            R.string.settings_home_make_it_yours,
            listOf(
                page(
                    SettingsPage.Appearance,
                    R.string.settings_appearance,
                    R.string.settings_appearance_summary,
                    UiR.drawable.ic_palette_filled,
                ),
                page(
                    SettingsPage.Chats,
                    R.string.settings_chats,
                    R.string.settings_chats_summary,
                    UiR.drawable.ic_chat_filled,
                ),
                page(
                    SettingsPage.ChatFolders,
                    R.string.settings_chat_folders,
                    R.string.settings_chat_folders_summary,
                    UiR.drawable.ic_folder_filled,
                ),
                page(
                    SettingsPage.Stickers,
                    R.string.settings_home_stickers_emoji,
                    R.string.settings_stickers_summary,
                    UiR.drawable.ic_mood_filled,
                ),
            ),
            category = CategoryHue.Green,
        ),
        HomeGroup(
            R.string.settings_home_alerts_privacy,
            listOf(
                page(
                    SettingsPage.Notifications,
                    R.string.settings_notifications,
                    R.string.settings_notifications_summary,
                    UiR.drawable.ic_notifications_filled,
                ),
                page(
                    SettingsPage.PrivacySecurity,
                    R.string.settings_privacy_security,
                    R.string.settings_privacy_security_summary,
                    R.drawable.ic_shield_filled,
                ),
                page(
                    SettingsPage.ActiveSessions,
                    R.string.settings_home_devices,
                    R.string.settings_home_devices_summary,
                    R.drawable.ic_devices_filled,
                ),
                page(
                    SettingsPage.DataStorage,
                    R.string.settings_data_storage,
                    R.string.settings_data_storage_summary,
                    R.drawable.ic_storage,
                ),
            ),
            category = CategoryHue.Orange,
        ),
        HomeGroup(
            R.string.settings_home_app,
            listOf(
                page(
                    SettingsPage.Language,
                    R.string.settings_language,
                    R.string.settings_language_summary,
                    R.drawable.ic_language,
                ),
                page(
                    SettingsPage.PowerSaving,
                    R.string.settings_power_saving,
                    R.string.settings_power_saving_summary,
                    R.drawable.ic_energy_savings_leaf_filled,
                ),
                page(
                    SettingsPage.About,
                    R.string.settings_about,
                    R.string.settings_about_summary,
                    R.drawable.ic_info_filled,
                ),
            ),
            category = CategoryHue.Purple,
        ),
    )

/**
 * The entries of [entries] whose title or summary holds every word of [query], ignoring case and accents, in their
 * order; none for a blank query. [available] leaves out what this window cannot open (Bookmarks without its page).
 */
internal fun searchEntries(
    entries: List<HomeEntry>,
    query: String,
    resources: Resources,
    locale: Locale,
    available: (HomeEntry) -> Boolean = { true },
): List<HomeEntry> {
    val words = normalized(query, locale).split(WHITESPACE).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return entries.filter { entry ->
        val text = resources.getString(entry.title) + " " + resources.getString(entry.summary)
        available(entry) && normalized(text, locale).let { normalizedText -> words.all { it in normalizedText } }
    }
}

private fun normalized(
    text: String,
    locale: Locale,
): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(locale)

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("\\s+")

/** What the account card's switch button offers: the device's other accounts, and "Add account" while [canAdd]. */
internal class AccountSwitcher(
    val accounts: ImmutableList<OtherAccountUiModel>,
    val canAdd: Boolean,
    val onSwitch: (slot: Int) -> Unit,
    val onAdd: () -> Unit,
)

/** What every row of the home shows besides its entry: the values beside rows, and the page open beside the list. */
internal class HomeRowState(
    val summaries: SettingsHomeSummaries,
    val selection: SettingsPageSelection?,
)
