package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MemberTagLimits
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.coroutines.flow.drop

/**
 * A member's tag after their name (`docs/DESIGN_SYSTEM.md` §6 "Member tags"): `labelMediumEmphasized` on a full pill,
 * an administrator's in the accent color (`primaryContainer` / `onPrimaryContainer`), a member's tonal
 * (`surfaceContainerHighest` / `onSurfaceVariant`), one line, as user content.
 */
@Composable
internal fun MemberTagPill(
    tag: String,
    isAdministrator: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Text(
        text = tag,
        modifier =
            modifier
                .background(
                    if (isAdministrator) colors.primaryContainer else colors.surfaceContainerHighest,
                    CircleShape,
                ).padding(horizontal = spacing.small, vertical = spacing.extraSmall),
        color = if (isAdministrator) colors.onPrimaryContainer else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMediumEmphasized.userContent,
    )
}

/**
 * The "Set tag" sheet of a member (the owner's board 18b), a `ModalBottomSheet` that opens expanded:
 * [MemberTagContent]. Back, dragging it away and the scrim close it as nothing saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemberTagSheet(
    editor: MemberTagEditor,
    onEvent: (ChatMembersEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(ChatMembersEvent.DismissTag) }, sheetState = sheetState) {
        MemberTagContent(editor, onEvent)
    }
}

/**
 * What the "Set tag" sheet shows, scrolling where it does not fit:
 * - the member's avatar (48 dp) beside "Set tag" ("Your tag" for the user's own) in `titleLargeEmphasized`, a heading,
 *   over their name and role in `bodyMedium` `onSurfaceVariant`;
 * - the tag in a filled [TextField] labelled "Tag" (user content, one line), with where it shows or why a change failed
 *   (a polite live region) at the start below it and the counter ("11 / 16", tabular figures, read as "11 of 16
 *   characters") at the end; the keyboard's Done saves;
 * - a preview on `surface` with `large` corners: "Preview" in `labelMediumEmphasized` `onSurfaceVariant`, then a
 *   sample incoming bubble (`surfaceContainerHigh`, the conversation's group shape) with the member's avatar, their
 *   name in `primary` and the tag as [MemberTagPill], and which tags show in the accent color in `bodySmall`;
 *   decorative for screen readers, who hear the field;
 * - a [LinearWavyProgressIndicator] while Telegram answers, then "Remove tag" (an `error` [TextButton], while they
 *   have one) and the default [Button] "Save" (`labelLargeEmphasized`), both with the press morph; "Save" stays
 *   disabled until the tag changed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MemberTagContent(
    editor: MemberTagEditor,
    onEvent: (ChatMembersEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        TagHeader(editor)
        TagField(editor, onEvent)
        TagPreview(editor)
        SavingIndicator(editor.isSaving)
        TagButtons(editor, onEvent)
    }
}

/** The member's avatar beside the title and who the tag is for. */
@Composable
private fun TagHeader(editor: MemberTagEditor) {
    val member = editor.member
    val name = member.name.resolve()
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            photo = member.photo,
            name = name,
            id = member.avatarId,
            size = MaterialTheme.sizes.avatarRow,
            placeholderIcon = member.placeholderIcon,
        )
        Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
            Text(
                text =
                    stringResource(
                        if (editor.isOwn) R.string.chat_info_tag_title_own else R.string.chat_info_tag_title,
                    ),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            val role = member.role?.resolve()
            Text(
                text =
                    if (role == null) {
                        name
                    } else {
                        stringResource(R.string.chat_info_tag_for, name.bidiIsolated(), role).bidiIsolated()
                    },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        }
    }
}

/** The tag's field, where it shows or its error, and its counter. */
@Composable
private fun TagField(
    editor: MemberTagEditor,
    onEvent: (ChatMembersEvent) -> Unit,
) {
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val error = editor.error?.let { errorMessage(resources, it, showDetails) }
    val text = rememberTextFieldState(editor.text)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.drop(1).collect { currentOnEvent(ChatMembersEvent.TagChanged(it)) }
    }
    TextField(
        state = text,
        modifier = Modifier.fillMaxWidth(),
        enabled = !editor.isSaving,
        textStyle = MaterialTheme.typography.bodyLarge.userContent,
        label = { Text(stringResource(R.string.chat_info_tag_label)) },
        supportingText = { TagSupport(error, editor) },
        isError = error != null,
        keyboardOptions =
            KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        onKeyboardAction = { if (editor.canSave) currentOnEvent(ChatMembersEvent.SaveTag) },
        inputTransformation = TagLength,
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

/** Keeps the tag within [MemberTagLimits.LENGTH_MAX] characters (code points, as Telegram counts them). */
private object TagLength : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val typed = asCharSequence()
        val max = MemberTagLimits.LENGTH_MAX
        if (Character.codePointCount(typed, 0, typed.length) > max) {
            replace(Character.offsetByCodePoints(typed, 0, max), length, "")
        }
    }
}

/** Where the tag shows, or why the change failed, at the start; the counter at the end. */
@Composable
private fun TagSupport(
    error: String?,
    editor: MemberTagEditor,
) {
    val max = MemberTagLimits.LENGTH_MAX
    val length = editor.length
    val description = pluralStringResource(R.plurals.chat_info_tag_counter_description, max, length, max)
    val where = stringResource(if (editor.isOwn) R.string.chat_info_tag_where_own else R.string.chat_info_tag_where)
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Text(
            text = error ?: where,
            modifier = Modifier.weight(1f).semantics { if (error != null) liveRegion = LiveRegionMode.Polite },
        )
        Text(
            text = stringResource(R.string.chat_info_tag_counter, length, max),
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
        )
    }
}

/** How the member's messages show the tag, as a sample bubble; decorative. */
@Composable
private fun TagPreview(editor: MemberTagEditor) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val member = editor.member
    val name = member.name.resolve()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {}
                .background(colors.surface, MaterialTheme.shapes.large)
                .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Text(
            text = stringResource(R.string.chat_info_tag_preview),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMediumEmphasized,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.Bottom,
        ) {
            Avatar(
                photo = member.photo,
                name = name,
                id = member.avatarId,
                size = MaterialTheme.sizes.avatarExtraSmall,
                placeholderIcon = member.placeholderIcon,
            )
            PreviewBubble(name, editor, Modifier.weight(1f, fill = false))
        }
        Text(
            text = stringResource(R.string.chat_info_tag_colors),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** A sample incoming bubble: the member's [name] and the tag as typed, over a sample message. */
@Composable
private fun PreviewBubble(
    name: String,
    editor: MemberTagEditor,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            modifier
                .background(colors.surfaceContainerHigh, MaterialTheme.shapes.extraLarge)
                .padding(horizontal = spacing.medium, vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLargeEmphasized.userContent,
            )
            editor.text
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { tag -> MemberTagPill(tag, editor.isAdministrator) }
        }
        Text(
            text = stringResource(R.string.chat_info_tag_preview_message),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** "Remove tag" at the start, while the member has one, and "Save" at the end. */
@Composable
private fun TagButtons(
    editor: MemberTagEditor,
    onEvent: (ChatMembersEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editor.canRemove) {
            TextButton(
                onClick = { onEvent(ChatMembersEvent.RemoveTag) },
                enabled = !editor.isSaving,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.chat_info_tag_remove), style = MaterialTheme.typography.labelLarge)
            }
        }
        Box(Modifier.weight(1f))
        Button(
            onClick = { onEvent(ChatMembersEvent.SaveTag) },
            enabled = editor.canSave,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.chat_info_tag_save), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** The sheet's states: typing a member's tag, an administrator's being changed, saving, a failure, the user's own. */
internal class MemberTagEditorStates : PreviewParameterProvider<MemberTagEditor> {
    override val values: Sequence<MemberTagEditor> =
        sequenceOf(
            MemberTagPreviewData.typing,
            MemberTagPreviewData.administrator,
            MemberTagPreviewData.typing.copy(isSaving = true),
            MemberTagPreviewData.typing.copy(error = DomainError.AdminRightsRequired),
            MemberTagPreviewData.own,
        )
}

/** Sample sheets for previews and goldens. */
internal object MemberTagPreviewData {
    private val owen =
        MemberItem(
            sender = MessageSender.User(3),
            name = UiText.Plain("Owen Park"),
            photo = null,
            avatarId = 3,
        )

    val typing =
        MemberTagEditor(member = owen, isAdministrator = false, isOwn = false, text = "Beta tester", saved = "")

    val administrator =
        MemberTagEditor(
            member =
                owen.copy(
                    sender = MessageSender.User(2),
                    name = UiText.Plain("Sahil Rao"),
                    avatarId = 2,
                    role = UiText.resource(R.string.chat_info_role_admin),
                    tag = "Moderator",
                    isAdministrator = true,
                ),
            isAdministrator = true,
            isOwn = false,
            text = "Moderator",
            saved = "Moderator",
        )

    val own = typing.copy(isOwn = true, text = "Night owl", saved = "Night owl")
}

@PingogramPreviews
@Composable
private fun MemberTagContentPreview(
    @PreviewParameter(MemberTagEditorStates::class) editor: MemberTagEditor,
) {
    PingogramPreview { MemberTagContent(editor, onEvent = {}) }
}
