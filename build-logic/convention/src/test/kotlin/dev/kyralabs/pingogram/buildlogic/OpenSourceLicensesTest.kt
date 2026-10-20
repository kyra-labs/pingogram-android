package dev.kyralabs.pingogram.buildlogic

import groovy.json.JsonSlurper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class OpenSourceLicensesTest {
    private val apache =
        PomLicense("The Apache Software License, Version 2.0", "https://www.apache.org/licenses/LICENSE-2.0.txt")
    private val texts = mapOf("Apache-2.0" to "Apache text", "BSL-1.0" to "Boost text", "OFL-1.1" to "OFL text")

    private fun maven(
        coordinates: String,
        vararg licenses: PomLicense,
        name: String? = null,
        problem: String? = null,
    ): MavenLibrary {
        val (group, artifact, version) = coordinates.split(':')
        return MavenLibrary(
            MavenCoordinates(group, artifact, version),
            EffectivePom(name, "What it is", "https://example.com/$artifact", listOf("Someone"), licenses.toList()),
            problem,
        )
    }

    private val tdlib =
        MavenLibrary(
            MavenCoordinates("org.drinkless", "tdlib", "a94b83d67ab4c8397c6245e541b31a4131ea2f79"),
            EffectivePom(null, null, null, emptyList(), emptyList()),
        )

    private fun extra(
        id: String,
        vararg licenses: String,
        name: String? = null,
    ) = ExtraLibrary(id, name, null, null, null, emptyList(), licenses.toList())

    private fun generate(
        dependencies: List<MavenLibrary>,
        extras: List<ExtraLibrary> = emptyList(),
        texts: Map<String, String> = this.texts,
    ) = OpenSourceLicenses.generate("fossRelease", dependencies, extras, texts)

    private fun failure(block: () -> Unit): String = assertThrows<IllegalStateException>(block).message.orEmpty()

    @Test
    fun `lists each dependency with its POM values and SPDX licenses, sorted by id`() {
        val generated =
            generate(listOf(maven("b.group:b:2.0", apache, name = "B"), maven("a.group:a:1.0", apache, apache)))

        assertEquals(listOf("a.group:a", "b.group:b"), generated.libraries.map { it.id })
        assertEquals(
            OpenSourceLicenses.Library(
                id = "a.group:a",
                name = "a",
                version = "1.0",
                description = "What it is",
                website = "https://example.com/a",
                developers = listOf("Someone"),
                licenses = listOf("Apache-2.0"),
            ),
            generated.libraries.first(),
        )
        assertEquals(
            mapOf(
                "Apache-2.0" to
                    OpenSourceLicenses.License(
                        "Apache License 2.0",
                        "https://www.apache.org/licenses/LICENSE-2.0",
                        "Apache text",
                    ),
            ),
            generated.licenses,
        )
    }

    @Test
    fun `an extra entry completes its dependency and keeps the dependency's version`() {
        val generated = generate(listOf(tdlib), listOf(extra("org.drinkless:tdlib", "BSL-1.0", name = "TDLib")))

        val library = generated.libraries.single()
        assertEquals("TDLib", library.name)
        assertEquals("a94b83d67ab4c8397c6245e541b31a4131ea2f79", library.version)
        assertEquals(listOf("BSL-1.0"), library.licenses)
        assertEquals("Boost text", generated.licenses.getValue("BSL-1.0").text)
    }

    @Test
    fun `an extra entry without a colon is a library of its own`() {
        val generated = generate(emptyList(), listOf(extra("google-sans-flex", "OFL-1.1", name = "Google Sans Flex")))

        assertEquals(listOf("Google Sans Flex"), generated.libraries.map { it.name })
        assertEquals(setOf("OFL-1.1"), generated.licenses.keys)
    }

    @Test
    fun `a dependency without a license fails, naming it and why`() {
        val message =
            failure {
                generate(
                    listOf(
                        maven(
                            "com.example:quiet:1.0",
                            problem = "its parent POM com.example:parent:3 could not be resolved",
                        ),
                    ),
                )
            }

        assertTrue("fossRelease" in message, message)
        assertTrue(
            "com.example:quiet:1.0: its parent POM com.example:parent:3 could not be resolved" in message,
            message,
        )
        assertTrue(OpenSourceLicenses.EXTRA_FILE in message, message)
    }

    @Test
    fun `a license that is not in the SPDX table fails, naming the artifact and the license`() {
        val message =
            failure {
                generate(
                    listOf(maven("com.example:odd:1.0", PomLicense("Odd Public License", "https://odd.example"))),
                )
            }

        assertTrue("com.example:odd:1.0: the POM license 'Odd Public License' https://odd.example" in message, message)
        assertTrue("SpdxLicenses" in message, message)
    }

    @Test
    fun `a license without a text fails, naming the file to add`() {
        val message = failure { generate(listOf(maven("com.example:a:1.0", apache)), texts = emptyMap()) }

        assertTrue("com.example:a: no text for the license Apache-2.0" in message, message)
        assertTrue("config/licenses/texts/Apache-2.0.txt" in message, message)
    }

    @Test
    fun `a name and a link of different licenses fail`() {
        val conflicting = PomLicense("BSD-3-Clause", "https://www.apache.org/licenses/LICENSE-2.0")

        val message = failure { generate(listOf(maven("com.example:a:1.0", conflicting))) }

        assertTrue("names BSD-3-Clause but links to Apache-2.0" in message, message)
    }

    @Test
    fun `an extra entry for a dependency the variant does not have fails`() {
        val message = failure { generate(emptyList(), listOf(extra("com.example:gone", "Apache-2.0"))) }

        assertTrue("completes com.example:gone, which is not a dependency of fossRelease" in message, message)
    }

    @Test
    fun `lists every problem at once`() {
        val message =
            failure {
                generate(listOf(maven("com.example:a:1.0"), maven("com.example:b:1.0", PomLicense("Odd", null))))
            }

        assertTrue("com.example:a:1.0" in message && "com.example:b:1.0" in message, message)
    }

    @Test
    fun `writes the documented JSON format`() {
        val generated =
            generate(
                listOf(maven("a.group:a:1.0", apache, name = "A \"quoted\" café")),
                listOf(extra("material-symbols", "Apache-2.0")),
            )

        val json = JsonSlurper().parseText(OpenSourceLicenses.toJson(generated)) as Map<*, *>

        assertEquals(OpenSourceLicenses.FORMAT_VERSION, json["formatVersion"])
        val libraries = json["libraries"] as List<*>
        assertEquals(
            mapOf(
                "id" to "a.group:a",
                "name" to "A \"quoted\" café",
                "version" to "1.0",
                "description" to "What it is",
                "website" to "https://example.com/a",
                "developers" to listOf("Someone"),
                "licenses" to listOf("Apache-2.0"),
            ),
            libraries.first(),
        )
        // Unknown values are left out.
        assertEquals(
            mapOf(
                "id" to "material-symbols",
                "name" to "material-symbols",
                "developers" to emptyList<String>(),
                "licenses" to listOf("Apache-2.0"),
            ),
            libraries.last(),
        )
        assertEquals(
            mapOf(
                "name" to "Apache License 2.0",
                "url" to "https://www.apache.org/licenses/LICENSE-2.0",
                "text" to "Apache text",
            ),
            (json["licenses"] as Map<*, *>)["Apache-2.0"],
        )
    }

    @Test
    fun `reads extra json`() {
        val extras =
            OpenSourceLicenses.parseExtras(
                """
                {"comment": "ignored", "libraries": [
                  {"id": "org.drinkless:tdlib", "name": "TDLib", "developers": ["A", "B"], "licenses": ["BSL-1.0"]}
                ]}
                """,
            )

        assertEquals(
            listOf(ExtraLibrary("org.drinkless:tdlib", "TDLib", null, null, null, listOf("A", "B"), listOf("BSL-1.0"))),
            extras,
        )
    }

    @Test
    fun `an extra entry without licenses is refused`() {
        assertThrows<IllegalArgumentException> { OpenSourceLicenses.parseExtras("""{"libraries": [{"id": "x"}]}""") }
    }

    @Test
    fun `the committed extra json names only licenses of the SPDX table`() {
        val extras = OpenSourceLicenses.parseExtras(File("../../${OpenSourceLicenses.EXTRA_FILE}").readText())

        assertEquals(emptyList<String>(), extras.flatMap { it.licenses }.filter { SpdxLicenses[it] == null })
    }

    @Test
    fun `splits coordinates into rounds that hold each module once`() {
        val coordinates =
            listOf(
                MavenCoordinates("g", "parent", "1"),
                MavenCoordinates("g", "parent", "2"),
                MavenCoordinates("g", "other", "1"),
                MavenCoordinates("g", "parent", "1"),
            )

        assertEquals(
            listOf(
                listOf(MavenCoordinates("g", "parent", "1"), MavenCoordinates("g", "other", "1")),
                listOf(MavenCoordinates("g", "parent", "2")),
            ),
            versionRounds(coordinates),
        )
    }
}
