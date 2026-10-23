package dev.kyralabs.pingogram.feature.settings.ui.folders

import android.icu.text.ListFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import dev.kyralabs.pingogram.core.domain.model.ChatFolderIncludedType
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsFooter
import dev.kyralabs.pingogram.feature.settings.ui.SettingsItemIcon
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListRowStyle
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSectionHeader
import dev.kyralabs.pingogram.feature.settings.ui.settingsListRow
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

// The list of the Chat folders page: "New folder", the user's folders in their order (dragged into another one by a
// handle or a long press), then the folders Telegram suggests.

/** The page's rows; [onOpenFolder] opens a folder's editor, or a new folder's with null. */
@Composable
internal fun ChatFoldersList(
    state: ChatFoldersUiState.Content,
    onEvent: (ChatFoldersEvent) -> Unit,
    onOpenFolder: (Int?) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    val spacing = MaterialTheme.spacing
    val style = SettingsListRowStyle(MaterialTheme.sizes.contentMaxWidth, windowMargin(), MaterialTheme.motionScheme)
    val reorder =
        rememberFolderReorderState(listState, state.folders.map { it.id }) { onEvent(ChatFoldersEvent.Reorder(it)) }
    val byId = state.folders.associateBy { it.id }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + spacing.small,
                bottom = contentPadding.calculateBottomPadding() + spacing.extraLarge,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = NEW, contentType = NEW) {
            SettingsRow(
                title = stringResource(R.string.settings_chat_folders_new),
                shapes = ListItemDefaults.segmentedShapes(0, 1),
                icon = UiR.drawable.ic_add,
                onClick = { onOpenFolder(null) },
                modifier = Modifier.settingsListRow(this, style),
            )
        }
        if (reorder.order.isEmpty()) {
            footer(NONE, R.string.settings_chat_folders_none, style)
        } else {
            folders(reorder, byId, style, onEvent, onOpenFolder)
        }
        recommended(state, style, onEvent)
    }
}

/** The user's folders under their heading, in the order the finger leaves them while one is dragged. */
private fun LazyListScope.folders(
    reorder: FolderReorderState,
    byId: Map<Int, FolderItem>,
    style: SettingsListRowStyle,
    onEvent: (ChatFoldersEvent) -> Unit,
    onOpenFolder: (Int?) -> Unit,
) {
    item(key = HEADER_YOURS, contentType = HEADER) {
        SettingsSectionHeader(
            stringResource(R.string.settings_chat_folders_section_yours),
            // A group's distance from the one above it, less the gap every item keeps.
            Modifier
                .settingsListRow(this, style)
                .padding(top = MaterialTheme.spacing.extraLarge - ListItemDefaults.SegmentedGap),
        )
    }
    val order = reorder.order
    itemsIndexed(order, key = { _, id -> folderRowKey(id) }, contentType = { _, _ -> ROW }) { index, id ->
        val folder = byId[id] ?: return@itemsIndexed
        val dragged = reorder.draggedId == id
        FolderRow(
            folder = folder,
            shapes = ListItemDefaults.segmentedShapes(index, order.size),
            dragged = dragged,
            onClick = { onOpenFolder(id) },
            onMove = { up -> onEvent(ChatFoldersEvent.Move(id, up)) },
            canMoveUp = index > 0,
            canMoveDown = index < order.lastIndex,
            modifier =
                Modifier
                    .settingsListRow(this, style, animatePlacement = !dragged)
                    .zIndex(if (dragged) 1f else 0f)
                    .graphicsLayer { translationY = if (dragged) reorder.offset else 0f }
                    .dragAfterLongPress(reorder, id),
            handleModifier = Modifier.dragHandle(reorder, id),
        )
    }
    footer(FOOTER_YOURS, R.string.settings_chat_folders_footer, style)
}

/** The folders Telegram suggests, each with "Add"; nothing while there are none. */
private fun LazyListScope.recommended(
    state: ChatFoldersUiState.Content,
    style: SettingsListRowStyle,
    onEvent: (ChatFoldersEvent) -> Unit,
) {
    if (state.recommended.isEmpty()) return
    item(key = HEADER_RECOMMENDED, contentType = HEADER) {
        SettingsSectionHeader(
            stringResource(R.string.settings_chat_folders_section_recommended),
            // A group's distance from the one above it, less the gap every item keeps.
            Modifier
                .settingsListRow(this, style)
                .padding(top = MaterialTheme.spacing.extraLarge - ListItemDefaults.SegmentedGap),
        )
    }
    itemsIndexed(
        state.recommended,
        key = { _, folder -> "recommended-${folder.key}" },
        contentType = { _, _ -> RECOMMENDED },
    ) { index, folder ->
        RecommendedFolderRow(
            folder = folder,
            shapes = ListItemDefaults.segmentedShapes(index, state.recommended.size),
            onAdd = { onEvent(ChatFoldersEvent.AddRecommended(folder.key)) },
            modifier = Modifier.settingsListRow(this, style),
        )
    }
}

private fun LazyListScope.footer(
    key: String,
    text: Int,
    style: SettingsListRowStyle,
) {
    item(key = key, contentType = FOOTER) {
        SettingsFooter(stringResource(text), Modifier.settingsListRow(this, style))
    }
}

/**
 * A folder: its icon, its name (user content) over what it includes, and a drag handle at the end
 * ([handleModifier] starts the drag, 48 dp wide), in the list item's dragged shape while [dragged]. A tap opens its
 * editor; screen readers move it with "Move up" and "Move down" ([onMove]), where [canMoveUp] and [canMoveDown].
 */
@Suppress("LongParameterList") // A row's content, its states and the two ways to move it.
@Composable
private fun FolderRow(
    folder: FolderItem,
    shapes: ListItemShapes,
    dragged: Boolean,
    onClick: () -> Unit,
    onMove: (up: Boolean) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    val moveUp = stringResource(R.string.settings_chat_folders_move_up)
    val moveDown = stringResource(R.string.settings_chat_folders_move_down)
    val rowShapes = if (dragged) shapes.copy(shape = shapes.draggedShape) else shapes
    SegmentedListItem(
        onClick = onClick,
        shapes = rowShapes,
        modifier =
            modifier.semantics {
                customActions =
                    buildList {
                        if (canMoveUp) {
                            add(
                                CustomAccessibilityAction(moveUp) {
                                    onMove(true)
                                    true
                                },
                            )
                        }
                        if (canMoveDown) {
                            add(
                                CustomAccessibilityAction(moveDown) {
                                    onMove(false)
                                    true
                                },
                            )
                        }
                    }
            },
        leadingContent = { SettingsItemIcon(folderIconDrawable(folder.iconName)) },
        supportingContent = { Text(folderSummary(folder)) },
        trailingContent = {
            // Screen readers move the folder with the row's custom actions instead.
            Box(modifier = handleModifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_drag_indicator), contentDescription = null)
            }
        },
        colors = settingsRowColors(),
    ) {
        Text(text = folder.title, style = LocalTextStyle.current.userContent)
    }
}

/**
 * A suggested folder: its icon, its name over Telegram's description, and "Add" ([onAdd]), which screen readers hear
 * with the name; progress while it is created.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecommendedFolderRow(
    folder: RecommendedFolderItem,
    shapes: ListItemShapes,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        shapes = shapes,
        modifier = modifier,
        leadingContent = { SettingsItemIcon(folderIconDrawable(folder.iconName)) },
        supportingContent = { Text(folder.description) },
        trailingContent = {
            if (folder.isAdding) {
                val adding = stringResource(R.string.settings_chat_folders_adding, folder.title)
                LoadingIndicator(Modifier.semantics { contentDescription = adding })
            } else {
                val add = stringResource(R.string.settings_chat_folders_add_named, folder.title)
                TextButton(
                    onClick = onAdd,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.semantics { contentDescription = add },
                ) {
                    Text(stringResource(R.string.settings_chat_folders_add))
                }
            }
        },
        colors = settingsRowColors(),
    ) {
        Text(text = folder.title, style = LocalTextStyle.current.userContent)
    }
}

/** What [folder] includes: its kinds of chats, then how many chats were chosen; "Shared · …" for a shared folder. */
@Composable
private fun folderSummary(folder: FolderItem): String {
    val locale = LocalConfiguration.current.locales[0]
    val parts =
        buildList {
            ChatFolderIncludedType.entries.filter { it in folder.includedTypes }.forEach {
                add(stringResource(it.label))
            }
            if (folder.chatCount > 0) {
                add(
                    pluralStringResource(
                        R.plurals.settings_chat_folders_chat_count,
                        folder.chatCount,
                        NumberFormat.getIntegerInstance(locale).format(folder.chatCount),
                    ),
                )
            }
        }
    val includes = ListFormatter.getInstance(locale).format(parts)
    return if (folder.isShared) stringResource(R.string.settings_chat_folders_shared, includes) else includes
}

/** How a kind of chats is named in summaries and the editor. */
internal val ChatFolderIncludedType.label: Int
    get() =
        when (this) {
            ChatFolderIncludedType.Contacts -> R.string.settings_chat_folders_type_contacts
            ChatFolderIncludedType.NonContacts -> R.string.settings_chat_folders_type_non_contacts
            ChatFolderIncludedType.Groups -> R.string.settings_chat_folders_type_groups
            ChatFolderIncludedType.Channels -> R.string.settings_chat_folders_type_channels
            ChatFolderIncludedType.Bots -> R.string.settings_chat_folders_type_bots
        }

/** A kind of chats, as its row shows it. */
internal val ChatFolderIncludedType.icon: Int
    get() =
        when (this) {
            ChatFolderIncludedType.Contacts -> UiR.drawable.ic_contacts
            ChatFolderIncludedType.NonContacts -> UiR.drawable.ic_person_filled
            ChatFolderIncludedType.Groups -> UiR.drawable.ic_chat
            ChatFolderIncludedType.Channels -> R.drawable.ic_public
            ChatFolderIncludedType.Bots -> UiR.drawable.ic_tag
        }

private const val NEW = "new"
private const val NONE = "none"
private const val HEADER = "header"
private const val HEADER_YOURS = "header-yours"
private const val HEADER_RECOMMENDED = "header-recommended"
private const val FOOTER = "footer"
private const val FOOTER_YOURS = "footer-yours"
private const val ROW = "row"
private const val RECOMMENDED = "recommended"
