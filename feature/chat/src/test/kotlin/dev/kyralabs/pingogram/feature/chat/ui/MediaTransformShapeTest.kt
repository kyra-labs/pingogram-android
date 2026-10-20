package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntRect
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.MessageBubbleShapes
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.transition.LocalSharedMediaShapes
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.core.ui.transition.ProvideSharedTransitionScope
import dev.kyralabs.pingogram.core.ui.transition.SharedMediaShapes
import dev.kyralabs.pingogram.feature.chat.ui.content.AlbumLayout
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.content.albumItemShape
import dev.kyralabs.pingogram.feature.chat.ui.preview.BubblePreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The corners the media viewer's container transform morphs from and back to (`docs/DESIGN_SYSTEM.md` §4, audit G08):
 * a photo in the conversation publishes exactly its clip in its bubble, the bubble's shape for its place in the group
 * ([MessageBubbleShapes]), square where a caption joins it; an album's items the album's corners where they meet them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MediaTransformShapeTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a photo alone takes the whole bubble's corners`() =
        assertPublished(GroupPosition.Single, isOutgoing = true) { it }

    @Test
    fun `the first photo of a group, at its top`() = assertPublished(GroupPosition.First, isOutgoing = true) { it }

    @Test
    fun `a photo in the middle of a group`() = assertPublished(GroupPosition.Middle, isOutgoing = false) { it }

    @Test
    fun `the last photo of a group, at its bottom`() = assertPublished(GroupPosition.Last, isOutgoing = false) { it }

    @Test
    fun `a captioned photo is square where its caption joins it`() =
        assertPublished(GroupPosition.Single, isOutgoing = false, caption = "The pier at dawn") { bubble ->
            bubble.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
        }

    @Test
    fun `an album's items take the album's corners where they meet them`() {
        val album = AlbumLayout(width = 200, height = 200, cells = emptyList())
        val albumShape = Shapes().extraLarge.copy(bottomStart = ZeroCornerSize)
        val cell = Shapes().extraSmall
        val topLeft = IntRect(0, 0, 99, 99)
        val bottomLeft = IntRect(0, 101, 99, 200)
        val bottomRight = IntRect(101, 101, 200, 200)

        assertEquals(cell.copy(topStart = albumShape.topStart), albumItemShape(topLeft, album, albumShape, cell, true))
        // Right to left, the top left cell is at the top end.
        assertEquals(cell.copy(topEnd = albumShape.topEnd), albumItemShape(topLeft, album, albumShape, cell, false))
        // Where the album is square (above a caption), the cell keeps its own corner.
        assertEquals(cell, albumItemShape(bottomLeft, album, albumShape, cell, true))
        assertEquals(
            cell.copy(bottomEnd = albumShape.bottomEnd),
            albumItemShape(bottomRight, album, albumShape, cell, true),
        )
    }

    /**
     * Shows a photo bubble at [position] on the [isOutgoing] side, with [caption], in the app's shared transition
     * scopes, and asserts that it publishes [expected] of its bubble's shape.
     */
    private fun assertPublished(
        position: GroupPosition,
        isOutgoing: Boolean,
        caption: String = "",
        expected: (bubble: CornerBasedShape) -> CornerBasedShape,
    ) {
        val bubble =
            ChatPreviewData.bubble(
                id = MESSAGE,
                text = caption,
                time = "6:11 PM",
                isOutgoing = isOutgoing,
                status = if (isOutgoing) OutgoingStatus.Read else null,
                position = position,
                content = ContentPreviewData.photo(minithumbnail = null),
                contentLabel = UiText.resource(UiR.string.preview_photo),
            )
        val state = BubblePreviewData.groupedBubbles.copy(content = ChatPreviewData.content(bubble))
        lateinit var shapes: SharedMediaShapes
        lateinit var bubbleShape: CornerBasedShape
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                bubbleShape =
                    MessageBubbleShapes(MaterialTheme.shapes).of(isOutgoing, position.joinsPrevious, position.joinsNext)
                SharedTransitionLayout {
                    ProvideSharedTransitionScope(this) {
                        shapes = LocalSharedMediaShapes.current!!
                        AnimatedVisibility(visible = true, label = "conversation") {
                            ProvideNavAnimatedVisibilityScope(this) {
                                ChatScreen(state = state, onEvent = {}, onBack = {})
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(expected(bubbleShape), shapes[MESSAGE])
    }

    private companion object {
        const val MESSAGE = 26L
    }
}
