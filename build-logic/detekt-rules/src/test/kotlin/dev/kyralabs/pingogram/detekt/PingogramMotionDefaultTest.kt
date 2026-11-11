package dev.kyralabs.pingogram.detekt

import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class PingogramMotionDefaultTest {
    @TempDir
    lateinit var projectDir: Path

    private fun lint(
        code: String,
        path: String = FEATURE_SOURCE,
        rule: PingogramMotionDefault = PingogramMotionDefault(ALL_CHECKS),
    ): List<Finding> {
        val file = projectDir.resolve(path).createParentDirectories()
        file.writeText(code.trimIndent())
        return rule.lint(file)
    }

    @ParameterizedTest(name = "{0} reports {1}")
    @MethodSource("reported")
    fun `reports an animation left at Compose's default`(
        check: String,
        code: String,
    ) {
        val findings = lint(code)

        assertThat(findings).singleElement()
        assertThat(findings.single().message).startsWith("$check: ")
    }

    @ParameterizedTest(name = "{0} allows {1}")
    @MethodSource("allowed")
    fun `allows an animation on the theme's specs`(
        check: String,
        code: String,
    ) {
        assertThat(lint(code)).isEmpty()
    }

    @Test
    fun `runs the first ten checks without configuration`() {
        val rule = PingogramMotionDefault()
        val code =
            """
            val first = Modifier.animateContentSize()
            val strict = Modifier.animateItem()
            """

        val findings = lint(code, rule = rule)

        assertThat(findings).singleElement()
        assertThat(findings.single().message).startsWith("contentSize: ")
    }

    @Test
    fun `runs only the configured checks`() {
        val rule = PingogramMotionDefault(TestConfig(PingogramMotionDefault.CHECKS to listOf("items")))
        val code =
            """
            val first = Modifier.animateContentSize()
            val strict = Modifier.animateItem()
            """

        val findings = lint(code, rule = rule)

        assertThat(findings).singleElement()
        assertThat(findings.single().message).startsWith("items: ")
    }

    @Test
    fun `does not check test sources`() {
        val findings = lint("val size = Modifier.animateContentSize()", path = FEATURE_TEST_SOURCE)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not check core modules`() {
        val findings = lint("val size = Modifier.animateContentSize()", path = CORE_SOURCE)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not check imports`() {
        assertThat(lint("import android.animation.ValueAnimator")).isEmpty()
    }

    private companion object {
        const val FEATURE_SOURCE = "feature/chat/src/main/kotlin/dev/kyralabs/pingogram/feature/chat/ChatScreen.kt"
        const val FEATURE_TEST_SOURCE =
            "feature/chat/src/test/kotlin/dev/kyralabs/pingogram/feature/chat/ChatScreenTest.kt"
        const val CORE_SOURCE = "core/ui/src/main/kotlin/dev/kyralabs/pingogram/core/ui/component/Motion.kt"

        val ALL_CHECKS =
            TestConfig(
                PingogramMotionDefault.CHECKS to
                    PingogramMotionDefault.DEFAULT_CHECKS + PingogramMotionDefault.STRICT_CHECKS,
            )

        @JvmStatic
        fun reported() =
            listOf(
                arrayOf("visibility", "@Composable fun A() { AnimatedVisibility(visible) { Text(\"\") } }"),
                arrayOf("visibility", "@Composable fun A() { AnimatedVisibility(visible, enter = fadeIn(spec)) { } }"),
                arrayOf("content", "@Composable fun A() { AnimatedContent(state) { Text(it) } }"),
                arrayOf("content", "@Composable fun A() { transition.AnimatedContent(Modifier) { Text(it) } }"),
                arrayOf("crossfade", "@Composable fun A() { Crossfade(state) { Text(it) } }"),
                arrayOf("contentSize", "val size = Modifier.animateContentSize()"),
                arrayOf("asState", "@Composable fun A() { val alpha = animateFloatAsState(1f) }"),
                arrayOf("asState", "@Composable fun A() { val c = animateColorAsState(targetValue = color) }"),
                arrayOf("asState", "@Composable fun A() { val v = animateValueAsState(1, Int.VectorConverter) }"),
                arrayOf("transitions", "val enter = fadeIn()"),
                arrayOf("transitions", "val enter = expandVertically(expandFrom = Alignment.Top)"),
                arrayOf("transitions", "val exit = shrinkHorizontally()"),
                arrayOf("transitions", "val enter = slideIntoContainer(SlideDirection.Start)"),
                arrayOf("springDefault", "val spec = spring<Float>()"),
                arrayOf("springDefault", "val spec = spring<Float>(stiffness = Spring.StiffnessLow)"),
                arrayOf("springDefault", "val decay = exponentialDecay<Float>()"),
                arrayOf("springDefault", "val decay = splineBasedDecay<Float>(density)"),
                arrayOf("loops", "val spec = infiniteRepeatable(motionScheme.slowEffectsSpec<Float>())"),
                arrayOf("loops", "@Composable fun A() { val t = rememberInfiniteTransition() }"),
                arrayOf("schemeDirect", "val scheme = MotionScheme.expressive()"),
                arrayOf("schemeDirect", "val scheme = MotionScheme.standard()"),
                arrayOf("otherAnimators", "val animator = ValueAnimator.ofFloat(0f, 1f)"),
                arrayOf("items", "val row = Modifier.animateItem()"),
                arrayOf("items", "val row = Modifier.animateItem(fadeInSpec = spec)"),
                arrayOf("sizeTransform", "val size = SizeTransform(clip = false)"),
                arrayOf("pagerScroll", "suspend fun turn() { pager.animateScrollToPage(2) }"),
                arrayOf("panes", "@Composable fun A() { AnimatedPane(Modifier) { } }"),
                arrayOf("coil", "val request = ImageRequest.Builder(context).crossfade(true)"),
                arrayOf(
                    "compositionRead",
                    "@Composable fun A() { val alpha by animateFloatAsState(1f, spec); Box(Modifier.alpha(alpha)) }",
                ),
            )

        @JvmStatic
        fun allowed() =
            listOf(
                arrayOf("visibility", "@Composable fun A() { AnimatedVisibility(visible, enter = e, exit = x) { } }"),
                arrayOf("visibility", "@Composable fun A() { AnimatedVisibility(visible, Modifier, e, x) { } }"),
                arrayOf("content", "@Composable fun A() { AnimatedContent(state, transitionSpec = spec) { } }"),
                arrayOf("content", "@Composable fun A() { transition.AnimatedContent(Modifier, spec) { } }"),
                arrayOf("crossfade", "@Composable fun A() { Crossfade(state, animationSpec = spec) { } }"),
                arrayOf("contentSize", "val size = Modifier.animateContentSize(motionScheme.defaultSpatialSpec())"),
                arrayOf("asState", "@Composable fun A() { val a = animateFloatAsState(1f, motion.fastEffectsSpec()) }"),
                arrayOf("asState", "@Composable fun A() { val a = animateDpAsState(1.dp, animationSpec = spec) }"),
                arrayOf("transitions", "val enter = fadeIn(motionScheme.defaultEffectsSpec())"),
                arrayOf("transitions", "val enter = expandVertically(spec, expandFrom = Alignment.Top)"),
                arrayOf("transitions", "val enter = slideIntoContainer(SlideDirection.Start, animationSpec = spec)"),
                arrayOf("springDefault", "val spec = spring<Float>(dampingRatio = ratio, stiffness = stiffness)"),
                arrayOf("items", "val row = Modifier.animateItem(this, motion)"),
                arrayOf("items", "val row = Modifier.animateItem(scope, motion = itemMotion)"),
                arrayOf("items", "val row = Modifier.animateItem(fadeInSpec = a, placementSpec = b, fadeOutSpec = c)"),
                arrayOf("sizeTransform", "val size = SizeTransform(clip = false) { _, _ -> spec }"),
                arrayOf("pagerScroll", "suspend fun turn() { pager.animateScrollToPage(2, animationSpec = spec) }"),
                arrayOf("panes", "@Composable fun A() { AnimatedPane(Modifier, boundsAnimationSpec = spec) { } }"),
                arrayOf(
                    "compositionRead",
                    "@Composable fun A() { val a by animateFloatAsState(1f, spec); Box(Modifier.layer { alpha = a }) }",
                ),
                arrayOf(
                    "compositionRead",
                    "@Composable fun A() { val a = animateFloatAsState(1f, spec); B { a.value } }",
                ),
            )
    }
}
