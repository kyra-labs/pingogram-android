package dev.kyralabs.pingogram.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * [NavigationLabelWidths.warmUp] on its background thread racing the first frame, whose [PingogramNavigationBar]
 * measures the labels itself where the warm-up did not measure what it asks for. Compose's emoji status makes its
 * snapshot state on the first paragraph that asks for it; made by the warm-up after the first composition took its
 * snapshot, it cannot be read in that composition. The status here behaves as Compose's own does while the emoji font
 * loads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class NavigationLabelWidthsRaceTest {
    @get:Rule(order = 0)
    val activityRegistration = ActivityRegistration(ComponentActivity::class.java)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var compose: Any? = null

    @Before
    fun loadingEmojiFont() {
        NavigationLabelWidths.clear()
        compose = EmojiStatus.replaceWithLoading()
    }

    @After
    fun restore() {
        EmojiStatus.restore(checkNotNull(compose))
        NavigationLabelWidths.clear()
    }

    @Test
    fun `the warm-up finishing during the bar's own measurement leaves it nothing it cannot read`() {
        val context = RuntimeEnvironment.getApplication()
        composeRule.setContent {
            val outer = Snapshot.current
            val base = LocalDensity.current
            val density =
                remember {
                    // The bar's labels are composed in a snapshot of their own (BoxWithConstraints), taken in the
                    // layout, which itself reads in the global snapshot.
                    val inBarComposition = {
                        val current = Snapshot.current.snapshotId
                        current != outer.snapshotId && current != Snapshot.global { Snapshot.current }.snapshotId
                    }
                    WarmingUpDensity(base, inBarComposition) { NavigationLabelWidths.warmUp(context) }
                }
            CompositionLocalProvider(LocalDensity provides density) {
                PingogramTheme(dynamicColor = false) {
                    PingogramNavigationBar(selected = TopLevelDestination.Chats, onSelect = {})
                }
            }
        }

        composeRule.onNode(hasText(context.getString(R.string.nav_chats)) and isSelectable()).assertIsSelected()
    }
}

/**
 * [base] at a font scale the warm-up does not measure for, so the bar measures the labels itself; read first in the
 * bar's composition ([inBarComposition]), before it measures, it runs [warmUp] on a thread of its own, to its end.
 */
private class WarmingUpDensity(
    private val base: Density,
    private val inBarComposition: () -> Boolean,
    private val warmUp: () -> Unit,
) : Density {
    private val warmedUp = AtomicBoolean(false)

    override val density: Float
        get() {
            if (inBarComposition() && warmedUp.compareAndSet(false, true)) thread { warmUp() }.join()
            return base.density
        }

    override val fontScale: Float = base.fontScale * FONT_SCALE_STEP

    private companion object {
        const val FONT_SCALE_STEP = 1.1f
    }
}

/**
 * Compose's emoji status (`EmojiCompatStatus`, internal to `ui-text`), replaced through its test hook by one that, as
 * Compose's own does while the emoji font loads, makes its snapshot state on the first call, on that caller's thread.
 */
private object EmojiStatus {
    private val status = Class.forName("androidx.compose.ui.text.platform.EmojiCompatStatus")
    private val delegateType = Class.forName("androidx.compose.ui.text.platform.EmojiCompatStatusDelegate")
    private val instance = status.getField("INSTANCE").get(null)
    private val setDelegate = status.getMethod("setDelegateForTesting\$ui_text", delegateType)
    private val delegate = status.getDeclaredField("delegate").apply { isAccessible = true }

    /** Installs the loading status; returns Compose's, for [restore]. */
    fun replaceWithLoading(): Any {
        val compose = checkNotNull(delegate.get(null))
        var fontLoaded: State<Boolean>? = null
        val loading =
            Proxy.newProxyInstance(delegateType.classLoader, arrayOf(delegateType)) { proxy, method, args ->
                when (method.name) {
                    "getFontLoaded" -> {
                        synchronized(
                            this,
                        ) { fontLoaded ?: mutableStateOf(false).also { fontLoaded = it } }
                    }

                    "equals" -> {
                        proxy === args?.firstOrNull()
                    }

                    "hashCode" -> {
                        System.identityHashCode(proxy)
                    }

                    else -> {
                        "LoadingEmojiStatus"
                    }
                }
            }
        setDelegate.invoke(instance, loading)
        return compose
    }

    fun restore(compose: Any) {
        setDelegate.invoke(instance, compose)
    }
}
