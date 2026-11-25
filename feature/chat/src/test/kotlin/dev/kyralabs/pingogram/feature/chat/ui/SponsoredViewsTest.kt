package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.SponsoredPreviewData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * [ReportSponsoredViews] reports exactly the views it reported before a frame of a scroll stopped walking every item
 * (Telegram's terms, CLAUDE.md rule 3): it runs beside [reportedBefore], the reporter as it was, on the same list,
 * through scrolls both ways, a card taller than the viewport, and cards that come, go and move, and both report the
 * same views in the same order. Then that a frame of a scroll reads none of the items.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SponsoredViewsTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val reported = mutableListOf<ChatEvent>()
    private val reportedBefore = mutableListOf<ChatEvent>()
    private lateinit var listState: LazyListState
    private var items by mutableStateOf(CountingList(listOf<ChatItem>()))

    private val ad = sponsored(SponsoredPreviewData.text)
    private val recommended = sponsored(SponsoredPreviewData.recommended)
    private val tall = sponsored(SponsoredPreviewData.photo)
    private val bubbles = (60L downTo 1L).map { ChatPreviewData.bubble(it, "Message $it", time = "9:41 AM") }

    private fun setList(entries: List<ChatItem>) {
        composeRule.setContent {
            listState = rememberLazyListState()
            Box(Modifier.height(VIEWPORT.dp)) {
                LazyColumn(state = listState) {
                    items(items, key = { it.key }) { item ->
                        Box(Modifier.fillMaxWidth().height(if (item === tall) TALL.dp else ROW.dp))
                    }
                }
            }
            ReportSponsoredViews(items, listState) { reported += it }
            ReportedBefore(items, listState) { reportedBefore += it }
        }
        show(entries)
    }

    private fun show(entries: List<ChatItem>) {
        composeRule.runOnIdle { items = CountingList(entries) }
        composeRule.waitForIdle()
    }

    /** Scrolls by [pixels] in steps of [STEP] pixels, a frame each, as a fling's frames do. */
    private fun scroll(pixels: Int) {
        repeat(abs(pixels) / STEP) {
            composeRule.runOnIdle { runBlocking { listState.scrollBy(if (pixels > 0) STEP_F else -STEP_F) } }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `reports the same views as before, in the same order`() {
        setList(bubbles.take(10) + ad + bubbles.subList(10, 30) + tall + bubbles.drop(30))
        scroll(FAR)
        scroll(-FAR)
        scroll(FAR / 2)
        // A card comes, one goes, one moves to the newest end, and the list scrolls over them again.
        show(listOf(recommended) + bubbles.take(10) + ad + bubbles.drop(10))
        scroll(-FAR)
        show(bubbles.take(5) + tall + bubbles.drop(5))
        scroll(FAR)
        scroll(-FAR)
        show(bubbles)
        scroll(FAR)

        val viewed = reported.filterIsInstance<SponsoredEvent.Viewed>()
        assertEquals(reportedBefore, reported)
        assertTrue(
            viewed.map { it.messageId }.toSet().containsAll(listOf(ad.card.id, tall.card.id, recommended.card.id)),
        )
    }

    @Test
    fun `a frame of a scroll reads none of the items`() {
        setList(bubbles.take(10) + ad + bubbles.drop(10))
        val walked = items.iterations
        reportedBeforeReads = 0

        scroll(FAR)

        // The reporter as it was walked the items in every frame of the scroll; this one walks them in none.
        assertEquals(walked, items.iterations)
        assertTrue("$reportedBeforeReads", reportedBeforeReads > FAR / STEP / 2)
    }

    /** How often [ReportedBefore] walked the items, counted apart from the reporter tested. */
    private var reportedBeforeReads = 0

    /**
     * The reporter as it was, verbatim but for its counter of reads ([reportedBeforeReads]): the views the reporter
     * tested must equal.
     */
    @Composable
    private fun ReportedBefore(
        items: ImmutableList<ChatItem>,
        listState: LazyListState,
        onEvent: (ChatEvent) -> Unit,
    ) {
        val currentItems by rememberUpdatedState(items)
        val currentOnEvent by rememberUpdatedState(onEvent)
        LaunchedEffect(listState) {
            snapshotFlow {
                val all = currentItems
                val cards = (all as? CountingList)?.uncounted()?.also { reportedBeforeReads++ } ?: all
                val sponsored = cards.filterIsInstance<ChatItem.Sponsored>()
                if (sponsored.isEmpty()) return@snapshotFlow emptySet<Long>()
                val layout = listState.layoutInfo
                val viewport = layout.viewport
                val byKey = sponsored.associateBy { it.key }
                layout.visibleItemsInfo.mapNotNullTo(HashSet<Long>()) { info ->
                    val card = byKey[info.key] ?: return@mapNotNullTo null
                    val end = info.offset + info.size
                    val whole = info.offset >= 0 && end <= viewport
                    val fills = info.size > viewport && info.offset <= 0 && end >= viewport
                    card.card.id.takeIf { whole || fills }
                }
            }.distinctUntilChanged()
                .collect { ids -> ids.forEach { currentOnEvent(SponsoredEvent.Viewed(it)) } }
        }
    }

    private fun sponsored(card: SponsoredCard) = ChatItem.Sponsored(card, UiText.resource(R.string.chat_date_today))

    /** The entries, counting how often they are walked through ([iterations]); [uncounted] walks them uncounted. */
    private class CountingList(
        private val entries: List<ChatItem>,
    ) : ImmutableList<ChatItem>,
        List<ChatItem> by entries {
        var iterations = 0
            private set

        override fun iterator(): Iterator<ChatItem> {
            iterations++
            return entries.iterator()
        }

        override fun subList(
            fromIndex: Int,
            toIndex: Int,
        ): ImmutableList<ChatItem> = entries.subList(fromIndex, toIndex).toImmutableList()

        fun uncounted(): List<ChatItem> = entries
    }

    private companion object {
        const val VIEWPORT = 400
        const val ROW = 60
        const val TALL = 600
        const val STEP = 53
        const val STEP_F = STEP.toFloat()

        /** Far enough to pass every entry: 60 rows of 60 dp, at most 3 px a dp. */
        const val FAR = 12_000
    }
}
