package dev.kyralabs.pingogram.detekt

import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class PingogramHardcodedTokenTest {
    @TempDir
    lateinit var projectDir: Path

    private fun lint(
        code: String,
        path: String = "feature/chat/src/main/kotlin/dev/kyralabs/pingogram/feature/chat/ChatScreen.kt",
        rule: PingogramHardcodedToken = PingogramHardcodedToken(),
    ): List<Finding> {
        val file = projectDir.resolve(path).createParentDirectories()
        file.writeText(code.trimIndent())
        return rule.lint(file)
    }

    @Test
    fun `reports a Color constructor call with the colorScheme hint`() {
        val findings = lint("val accent = Color(0xFF000000)")

        assertThat(findings).singleElement().hasMessage(
            "Hard-coded color `Color(0xFF000000)` in feature code; " +
                "use a MaterialTheme.colorScheme role or LocalPingogramColors instead.",
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["Color.Red", "Color.White", "androidx.compose.ui.graphics.Color.Black", "Color.hsv(1f, 1f, 1f)"],
    )
    fun `reports named colors and color factories`(color: String) {
        assertThat(lint("val accent = $color")).singleElement().hasMessage(
            "Hard-coded color `$color` in feature code; " +
                "use a MaterialTheme.colorScheme role or LocalPingogramColors instead.",
        )
    }

    @Test
    fun `allows transparent, unspecified and theme colors`() {
        val findings =
            lint(
                """
            val none = Color.Transparent
            val unset = Color.Unspecified
            val container = MaterialTheme.colorScheme.primaryContainer
            val dimmed = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            """,
            )

        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports sp literals with the typography hint`() {
        val findings = lint("val style = TextStyle(fontSize = 16.sp, lineHeight = (24f).sp)")

        assertThat(findings).hasSize(2)
        assertThat(findings.first()).hasMessage(
            "Hard-coded text size `16.sp` in feature code; use a MaterialTheme.typography style instead.",
        )
    }

    @Test
    fun `reports dp literals except zero with the spacing hint`() {
        val findings =
            lint(
                """
            val padded = Modifier.padding(16.dp).offset(x = (-4).dp, y = 1.5f.dp)
            val flat = Modifier.padding(0.dp).height(0f.dp)
            """,
            )

        assertThat(findings).hasSize(3)
        assertThat(findings.first()).hasMessage(
            "Hard-coded size `16.dp` in feature code; use a MaterialTheme.spacing token instead.",
        )
    }

    @Test
    fun `allows sizes and text styles from theme tokens`() {
        val findings =
            lint(
                """
            val padded = Modifier.padding(MaterialTheme.spacing.medium)
            val title = MaterialTheme.typography.titleLargeEmphasized
            val doubled = MaterialTheme.spacing.small * 2
            """,
            )

        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports a literal corner shape once with the shapes hint`() {
        val findings =
            lint(
                """
            val card = RoundedCornerShape(16.dp)
            val pill = RoundedCornerShape(percent = 50)
            val cut = CutCornerShape(topStart = CornerSize(8.dp))
            """,
            )

        assertThat(findings).hasSize(3)
        assertThat(findings.first()).hasMessage(
            "Hard-coded shape `RoundedCornerShape(16.dp)` in feature code; " +
                "use MaterialTheme.shapes or MaterialShapes instead.",
        )
    }

    @Test
    fun `allows shapes built from theme tokens`() {
        val findings =
            lint(
                """
            val bubble = MaterialTheme.shapes.large
            val tail = RoundedCornerShape(MaterialTheme.shapes.small.topStart)
            val polygon = MaterialShapes.Cookie9Sided.toShape()
            """,
            )

        assertThat(findings).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "tween(300)",
            "tween(durationMillis = 300, easing = LinearEasing)",
            "keyframes { durationMillis = 300 }",
            "TweenSpec<Float>(durationMillis = 150)",
            "snap(delayMillis = 50)",
            "spring<Float>(dampingRatio = 0.8f)",
            "CubicBezierEasing(0.2f, 0f, 0f, 1f)",
        ],
    )
    fun `reports literal animation specs once with the motionScheme hint`(spec: String) {
        assertThat(lint("val spec = $spec")).singleElement().hasMessage(
            "Hard-coded animation spec `$spec` in feature code; use a MaterialTheme.motionScheme spec instead.",
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "FastOutSlowInEasing",
            "LinearOutSlowInEasing",
            "FastOutLinearInEasing",
            "EaseInOutCubic",
            "Spring.StiffnessLow",
        ],
    )
    fun `reports named easings and spring constants`(value: String) {
        assertThat(lint("val motion = animate(value = $value)")).singleElement().hasMessage(
            "Hard-coded animation spec `$value` in feature code; use a MaterialTheme.motionScheme spec instead.",
        )
    }

    @Test
    fun `reports literal durations and delays outside of specs`() {
        val findings =
            lint(
                """
            val fade = crossfadeFor(initialAlpha = 0.3f, durationMillis = 300)
            val builder = customKeyframes { delayMillis = 50 }
            """,
            )

        assertThat(findings).hasSize(2)
    }

    @Test
    fun `allows motion scheme specs, plain snap and easing imports`() {
        val findings =
            lint(
                """
            import androidx.compose.animation.core.LinearEasing

            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            val reducedMotion = snap<Float>()
            """,
            )

        assertThat(findings).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "feature/chats/src/debug/kotlin/Preview.kt",
            "feature/chat/src/main/java/Screen.kt",
            "feature/settings/src/foss/kotlin/Screen.kt",
        ],
    )
    fun `checks every production source set of feature modules`(path: String) {
        assertThat(lint("val size = 16.sp", path)).hasSize(1)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "core/ui/src/main/kotlin/dev/kyralabs/pingogram/core/ui/theme/Color.kt",
            "app/src/main/kotlin/dev/kyralabs/pingogram/MainActivity.kt",
            "feature/chat/src/test/kotlin/ChatViewModelTest.kt",
            "feature/chat/src/androidTest/kotlin/ChatScreenTest.kt",
            "feature/chat/src/testFixtures/kotlin/Fixtures.kt",
            "feature/chat/build.gradle.kts",
        ],
    )
    fun `ignores core, app, test and build files`(path: String) {
        assertThat(lint("val accent = Color(0xFF000000)\nval size = 16.sp", path)).isEmpty()
    }

    @Test
    fun `can be switched off in the detekt config`() {
        val rule = PingogramHardcodedToken(TestConfig("active" to false))

        assertThat(lint("val accent = Color(0xFF000000)", rule = rule)).isEmpty()
    }
}
