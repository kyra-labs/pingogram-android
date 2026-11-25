package dev.kyralabs.pingogram.detekt

import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

/** [PingogramGlobalScope], [PingogramRunBlocking], [PingogramComposedModifier] and [PingogramSubcomposeAsyncImage]. */
class PingogramBannedNameTest {
    @TempDir
    lateinit var projectDir: Path

    private fun lint(
        rule: PingogramBannedName,
        code: String,
        path: String = DATA_SOURCE,
    ): List<Finding> {
        val file = projectDir.resolve(path).createParentDirectories()
        file.writeText(code.trimIndent())
        return rule.lint(file)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("banned")
    fun `reports a banned name in production code`(
        id: String,
        rule: PingogramBannedName,
        code: String,
    ) {
        val findings = lint(rule, code)

        assertThat(findings).singleElement()
        assertEquals(id, findings.single().id)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("banned")
    fun `allows a banned name in tests`(
        id: String,
        rule: PingogramBannedName,
        code: String,
    ) {
        assertThat(lint(rule, code, path = "core/data/src/test/kotlin/dev/kyralabs/pingogram/RepositoryTest.kt"))
            .isEmpty()
        assertThat(lint(rule, code, path = "app/src/testFoss/kotlin/dev/kyralabs/pingogram/ServiceTest.kt")).isEmpty()
        assertThat(lint(rule, code, path = "app/src/androidTest/kotlin/dev/kyralabs/pingogram/FlowTest.kt")).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = [FEATURE_SOURCE, "app/src/gms/kotlin/dev/kyralabs/pingogram/push/Service.kt", DATA_SOURCE])
    fun `checks every production source set`(path: String) {
        assertThat(lint(PingogramRunBlocking(), "fun read() = runBlocking { 1 }", path)).singleElement()
    }

    @Test
    fun `reports a qualified call`() {
        val findings = lint(PingogramRunBlocking(), "fun read() = kotlinx.coroutines.runBlocking { 1 }")

        assertThat(findings).singleElement()
    }

    @Test
    fun `reports GlobalScope wherever it is named, not its import`() {
        val code =
            """
            import kotlinx.coroutines.GlobalScope

            val scope = GlobalScope
            fun start() = GlobalScope.launch { }
            """

        assertEquals(2, lint(PingogramGlobalScope(), code).size)
    }

    @Test
    fun `allows other names that only look alike`() {
        val code =
            """
            fun composedTitle(parts: List<String>) = parts.joinToString()
            val composed = composedTitle(listOf("a"))
            fun read(block: () -> Int) = block()
            val image = AsyncImage(model = url, contentDescription = null)
            """

        listOf(PingogramComposedModifier(), PingogramRunBlocking(), PingogramSubcomposeAsyncImage()).forEach {
            assertThat(lint(it, code)).isEmpty()
        }
    }

    @Test
    fun `a commented suppression keeps a call that must block`() {
        val code =
            """
            // The caller's thread must have the result before it returns.
            @Suppress("PingogramRunBlocking")
            fun onMessage() = runBlocking { 1 }
            """

        assertThat(lint(PingogramRunBlocking(), code)).isEmpty()
    }

    @Test
    fun `the message says what to do instead`() {
        val message = lint(PingogramComposedModifier(), "val m = Modifier.composed { this }").single().message

        assertTrue(message.startsWith("`composed` "), message)
        assertTrue(message.endsWith("write a Modifier.Node."), message)
    }

    private companion object {
        const val FEATURE_SOURCE = "feature/chat/src/main/kotlin/dev/kyralabs/pingogram/feature/chat/ChatScreen.kt"
        const val DATA_SOURCE = "core/data/src/main/kotlin/dev/kyralabs/pingogram/core/data/Repository.kt"

        @JvmStatic
        fun banned(): List<Arguments> =
            listOf(
                Arguments.of("PingogramGlobalScope", PingogramGlobalScope(), "fun start() = GlobalScope.launch { }"),
                Arguments.of("PingogramRunBlocking", PingogramRunBlocking(), "fun read() = runBlocking { 1 }"),
                Arguments.of(
                    "PingogramComposedModifier",
                    PingogramComposedModifier(),
                    "fun Modifier.pressed() = composed { this }",
                ),
                Arguments.of(
                    "PingogramSubcomposeAsyncImage",
                    PingogramSubcomposeAsyncImage(),
                    "@Composable fun Photo(url: String) = SubcomposeAsyncImage(model = url, contentDescription = null)",
                ),
            )
    }
}
