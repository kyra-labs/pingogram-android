package dev.kyralabs.pingogram.feature.chats.ui.create

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.ChatCreationLimits
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// The steps of creating a group or a channel, each hoisting the flow's view model, which the nested graph's entry
// holds (ChatsNavigation.kt).

/** Picking the people of a new group, then [onNext]; [onBack] leaves the flow. */
@Composable
internal fun NewGroupMembersStep(
    viewModel: NewGroupViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.members.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    CreateChatEffects(viewModel.memberEffects, snackbarHostState, onNext = {}, onOpenChat = {})
    MemberPickerScreen(
        state = state,
        title = stringResource(R.string.new_chat_group),
        action = stringResource(R.string.create_next),
        onEvent = viewModel::onMembersEvent,
        onAction = onNext,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * The new group's details and creating it, which ends in [onOpenChat] with the group; [onBack] returns to the people.
 * Opens the system photo picker for its photo (no storage permission, `REQUIREMENTS.md` §7).
 */
@Composable
internal fun NewGroupDetailsStep(
    viewModel: NewGroupViewModel,
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.details.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    CreateChatEffects(viewModel.detailsEffects, snackbarHostState, onNext = {}, onOpenChat = onOpenChat)
    val pickPhoto = rememberPhotoPicker { viewModel.onEvent(ChatDetailsEvent.PhotoPicked(it)) }
    NewGroupDetailsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onNotAddedEvent = viewModel::onNotAddedEvent,
        onPickPhoto = pickPhoto,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** The new channel's details; creating it ends in [onNext]. [onBack] leaves the flow. */
@Composable
internal fun NewChannelDetailsStep(
    viewModel: NewChannelViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.details.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    CreateChatEffects(viewModel.detailsEffects, snackbarHostState, onNext = onNext, onOpenChat = {})
    val pickPhoto = rememberPhotoPicker { viewModel.onDetailsEvent(ChatDetailsEvent.PhotoPicked(it)) }
    NewChannelDetailsScreen(
        state = state,
        onEvent = viewModel::onDetailsEvent,
        onPickPhoto = pickPhoto,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Public or private for the new channel, then [onNext]; back opens the channel ([onOpenChat]), which exists by now.
 * The link is copied to the clipboard (Android 13+ confirms that itself, earlier versions get a snackbar) or shared.
 */
@Composable
internal fun NewChannelTypeStep(
    viewModel: NewChannelViewModel,
    onNext: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.type.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    CreateChatEffects(viewModel.typeEffects, snackbarHostState, onNext = onNext, onOpenChat = onOpenChat)
    BackHandler(onBack = viewModel::onFinish)
    NewChannelTypeScreen(
        state = state,
        onEvent = viewModel::onTypeEvent,
        onCopyLink = { link ->
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.create_link), link))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.create_link_copied)) }
            }
        },
        onShareLink = { shareText(context, it) },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** Adding subscribers to the new channel, or skipping that; both end in [onOpenChat], and so does back. */
@Composable
internal fun NewChannelMembersStep(
    viewModel: NewChannelViewModel,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.members.collectAsStateWithLifecycle()
    val adding by viewModel.memberState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    CreateChatEffects(viewModel.memberEffects, snackbarHostState, onNext = {}, onOpenChat = onOpenChat)
    BackHandler(onBack = viewModel::onFinish)
    MemberPickerScreen(
        state = state,
        title = stringResource(R.string.create_channel_members),
        action = stringResource(if (state.picked.isEmpty()) R.string.create_skip else R.string.create_add),
        onEvent = viewModel::onMembersEvent,
        onAction = viewModel::onAddMembers,
        onBack = null,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        isWorking = adding.isAdding,
        floodWait = adding.floodWait,
    )
    adding.notAdded?.let { NotAddedAlert(it, isChannel = true, onEvent = viewModel::onNotAddedEvent) }
}

/**
 * Handles a step's [effects] once each: on to the next step ([onNext]), the new chat ([onOpenChat]), the share sheet
 * for an invite link, and snackbars in [snackbarHostState], which queue up without holding later effects back.
 */
@Composable
private fun CreateChatEffects(
    effects: Flow<CreateChatEffect>,
    snackbarHostState: SnackbarHostState,
    onNext: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                CreateChatEffect.Next -> {
                    currentOnNext()
                }

                is CreateChatEffect.OpenChat -> {
                    currentOnOpenChat(effect.chatId)
                }

                is CreateChatEffect.ShareLink -> {
                    shareText(context, effect.link)
                }

                CreateChatEffect.TooManyMembers -> {
                    val max = ChatCreationLimits.MEMBERS_TO_ADD_MAX
                    val message = resources.getQuantityString(R.plurals.create_too_many_members, max, max)
                    launch { snackbarHostState.showSnackbar(message) }
                }

                is CreateChatEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
}

/** A launcher of the system photo picker for images, which hands the picked image's URI to [onPicked]. */
@Composable
private fun rememberPhotoPicker(onPicked: (uri: String) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher =
        rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let { currentOnPicked(it.toString()) } }
    return { launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
}
