package dev.kyralabs.pingogram.licenses

import android.app.Application
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.OpenSourceLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The list of libraries this very build generated (`docs/adr/0002-open-source-licenses.md`): the raw resource that
 * build-logic's `pingogram.android.licenses` wrote for the unit test's variant, with the entries of
 * `config/licenses/extra.json`, as the screens get it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GeneratedOpenSourceLicensesRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val repository = GeneratedOpenSourceLicensesRepository(context, Dispatchers.Unconfined)

    private val libraries: List<OpenSourceLibrary> by lazy {
        runBlocking { (repository.libraries() as Outcome.Success).value }
    }

    private fun library(id: String): OpenSourceLibrary = libraries.single { it.id == id }

    private fun asset(path: String): String = context.assets.open(path).use { it.readBytes().decodeToString() }

    @Test
    fun `lists the app's Maven dependencies with their versions and license texts`() {
        val material3 = library("androidx.compose.material3:material3")

        assertTrue(material3.version!!.isNotBlank())
        assertEquals(listOf("Apache-2.0"), material3.licenses.map { it.id })
        assertTrue(
            material3.licenses
                .single()
                .text!!
                .contains("Apache License"),
        )
    }

    @Test
    fun `lists TDLib under the Boost license, by its short commit`() {
        val tdlib = library("org.drinkless:tdlib")

        assertEquals("TDLib", tdlib.name)
        assertEquals("https://github.com/tdlib/td", tdlib.website)
        assertTrue(tdlib.version!!.matches(Regex("[0-9a-f]{10}(-local)?")))
        assertEquals(listOf("BSL-1.0"), tdlib.licenses.map { it.id })
        assertTrue(
            tdlib.licenses
                .single()
                .text!!
                .startsWith("Boost Software License - Version 1.0"),
        )
    }

    @Test
    fun `lists Google Sans Flex with the license the app ships beside the font`() {
        val font = library("google-sans-flex")

        assertEquals(listOf("OFL-1.1"), font.licenses.map { it.id })
        assertEquals(asset("licenses/google_sans_flex_OFL.txt"), font.licenses.single().text)
    }

    @Test
    fun `lists Material Symbols with the license the app ships for them`() {
        val icons = library("material-symbols")

        assertEquals(listOf("Apache-2.0"), icons.licenses.map { it.id })
        assertEquals(asset("licenses/material_symbols_LICENSE.txt"), icons.licenses.single().text)
    }

    @Test
    fun `every library has a license with its full text`() {
        // The build already fails for a dependency without a license text (config/licenses/texts); this reads the file.
        val incomplete =
            libraries.filter { library ->
                library.licenses.isEmpty() ||
                    library.licenses.any { it.text == null }
            }

        assertEquals(emptyList<String>(), incomplete.map { it.id })
    }

    @Test
    fun `leaves out BOMs, which put no code into the app`() {
        assertEquals(emptyList<String>(), libraries.map { it.id }.filter { it.endsWith("-bom") })
    }

    @Test
    fun `lists a Kotlin Multiplatform library once, by the module the app declares`() {
        val ids = libraries.map { it.id }.toSet()

        assertTrue("androidx.compose.ui:ui" in ids)
        assertEquals(emptyList<String>(), ids.filter { it.endsWith("-android") && it.removeSuffix("-android") in ids })
    }

    @Test
    fun `sorts the libraries by name, ignoring case`() {
        val names = libraries.map { it.name }

        assertEquals(names.sortedWith(String.CASE_INSENSITIVE_ORDER), names)
    }

    @Test
    fun `finds a library by its id, and none of an id the build does not have`() =
        runBlocking {
            assertEquals("TDLib", (repository.library("org.drinkless:tdlib") as Outcome.Success).value.name)
            assertEquals(Outcome.Failure(DomainError.NotFound), repository.library("com.example:gone"))
        }
}
