package dev.kyralabs.pingogram.feature.chat.ui.privacy

import android.os.Build
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem

/**
 * Whether the bubble around holds back its text and photos for the privacy screen (`REQUIREMENTS.md` FR-X25): its row
 * provides it, its text, link preview and media read it. Not hidden by default, as in previews and tests.
 */
internal val LocalPrivacyHidden: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/** Whether the bubble shows what the privacy screen hides: text, a link preview, a photo, a video, a GIF or an album. */
internal val ChatItem.Bubble.hasPrivateContent: Boolean
    get() =
        text.text.isNotEmpty() ||
            linkPreview != null ||
            album != null ||
            content is MessageContent.Photo ||
            content is MessageContent.Video ||
            content is MessageContent.Animation ||
            content is MessageContent.PaidMedia

/**
 * What the bubble's text and link preview draw while [LocalPrivacyHidden]: blurred by `sizes.privacyScreenBlur` on
 * API 31+ (a `RenderEffect`, in the draw phase), a `surfaceContainerHighest` cover below, which draws nothing of it.
 * Media hide as spoilers do instead, behind their blurred minithumbnail: no blur on media (`docs/PERFORMANCE.md`).
 */
@Composable
internal fun privacyCover(): Modifier {
    if (!LocalPrivacyHidden.current) return Modifier
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radius = MaterialTheme.sizes.privacyScreenBlur
        Modifier.graphicsLayer {
            renderEffect = BlurEffect(radius.toPx(), radius.toPx(), TileMode.Decal)
            clip = true
        }
    } else {
        val cover = MaterialTheme.colorScheme.surfaceContainerHighest
        Modifier.drawWithContent { drawRect(cover) }
    }
}

/**
 * Calls [onReveal] on a tap, before anything inside sees it: a link, a mention or a photo under the tap is not opened
 * by it. A long press or a drag passes through untouched, to open the message's actions or scroll the list.
 */
internal fun Modifier.revealOnTap(onReveal: () -> Unit): Modifier =
    pointerInput(onReveal) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tap = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitTapUp(down) }
            if (tap != null) {
                tap.consume()
                onReveal()
            }
        }
    }

/** The release of [down] where it stays within the touch slop; null where it moves away or is lost. */
private suspend fun AwaitPointerEventScope.awaitTapUp(down: PointerInputChange): PointerInputChange? {
    var change: PointerInputChange? = down
    while (change != null && change.pressed) {
        change =
            awaitPointerEvent(PointerEventPass.Initial)
                .changes
                .firstOrNull { it.id == down.id }
                ?.takeIf { (it.position - down.position).getDistance() <= viewConfiguration.touchSlop }
    }
    return change
}
