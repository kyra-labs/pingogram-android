package dev.kyralabs.pingogram.feature.chat.ui.privacy

import android.content.res.Resources
import android.os.Build
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.privacy.LocalPrivacyScreen
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem

/**
 * Whether the bubble around holds back its text and photos for the privacy screen (`REQUIREMENTS.md` FR-X25): its row
 * provides it, its text, link preview and media read it. Not hidden by default, as in previews and tests.
 */
internal val LocalPrivacyHidden: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/** Whether the bubble shows what the privacy screen hides: text, a link preview, a photo, a video, a GIF, an album. */
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
 * Whether a bubble's row hides it now ([hidden]): while the privacy screen is on (`REQUIREMENTS.md` FR-X25), until
 * [show] opens it, by a tap on the bubble ([privacyReveal], unless [tappable] is false: while messages are being
 * selected, a tap selects) or by screen readers' "Show" ([actions]), who hear [spoken] meanwhile.
 */
@Immutable
internal class PrivacyHiding(
    val hidden: Boolean,
    val tappable: Boolean,
    val show: () -> Unit,
) {
    /** What screen readers hear of a hidden [bubble]: its sender, never its text; null where it is not hidden. */
    fun spoken(
        resources: Resources,
        bubble: ChatItem.Bubble,
    ): String? {
        if (!hidden) return null
        return resources.getString(R.string.chat_privacy_hidden, bubble.sender.resolve(resources))
    }

    /** "Show", for screen readers, while hidden. */
    fun actions(resources: Resources): List<CustomAccessibilityAction> {
        if (!hidden) return emptyList()
        val label = resources.getString(R.string.chat_privacy_show)
        return listOf(
            CustomAccessibilityAction(label) {
                show()
                true
            },
        )
    }

    companion object {
        /** Nothing hidden: the lifted copy of a message, previews and tests. */
        val None = PrivacyHiding(hidden = false, tappable = false) {}
    }
}

/**
 * Whether [bubble]'s row hides it: shown once tapped, until the row scrolls away and leaves the composition, which
 * forgets it; never a bubble without text or photos.
 */
@Composable
internal fun rememberPrivacyHiding(
    bubble: ChatItem.Bubble,
    selecting: Boolean,
): PrivacyHiding {
    var revealed by remember(bubble.id) { mutableStateOf(false) }
    val hidden = LocalPrivacyScreen.current && !revealed && bubble.hasPrivateContent
    return remember(hidden, selecting) { PrivacyHiding(hidden, tappable = !selecting) { revealed = true } }
}

/** The bubble's tap that shows it ([PrivacyHiding.show]), before anything inside takes it; none where not tappable. */
internal fun Modifier.privacyReveal(privacy: PrivacyHiding): Modifier =
    if (privacy.hidden && privacy.tappable) revealOnTap(privacy.show) else this

/** How the privacy screen covers a bubble's text: blurred by [radius] (API 31+), else filled with [color]. */
@Immutable
internal class PrivacyCover(
    val radius: Dp,
    val color: Color,
)

/** The bubble's [PrivacyCover] while [LocalPrivacyHidden], from the theme; null while it shows. */
@Composable
internal fun currentPrivacyCover(): PrivacyCover? =
    if (LocalPrivacyHidden.current) {
        PrivacyCover(MaterialTheme.sizes.privacyScreenBlur, MaterialTheme.colorScheme.surfaceContainerHighest)
    } else {
        null
    }

/**
 * What the bubble's text, link preview and quoted words draw under [cover]: blurred on API 31+ (a `RenderEffect`, in
 * the draw phase), a `surfaceContainerHighest` fill below, which draws nothing of them. Media hide as spoilers do
 * instead, behind their blurred minithumbnail: no blur on media (`docs/PERFORMANCE.md`). Nothing while [cover] is null.
 */
internal fun Modifier.privacyCover(cover: PrivacyCover?): Modifier =
    when {
        cover == null -> {
            this
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            graphicsLayer {
                renderEffect = BlurEffect(cover.radius.toPx(), cover.radius.toPx(), TileMode.Decal)
                clip = true
            }
        }

        else -> {
            drawWithContent { drawRect(cover.color) }
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
