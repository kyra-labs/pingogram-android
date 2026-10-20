package dev.kyralabs.pingogram.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PomTest {
    private val guavaParent =
        Pom.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <parent>
                <groupId>org.sonatype.oss</groupId>
                <artifactId>oss-parent</artifactId>
                <version>9</version>
              </parent>
              <groupId>com.google.guava</groupId>
              <artifactId>guava-parent</artifactId>
              <version>33.3.1-android</version>
              <name>Guava Maven Parent</name>
              <description>Parent for guava artifacts</description>
              <url>https://github.com/google/guava</url>
              <properties>
                <scm.path>google/guava</scm.path>
              </properties>
              <licenses>
                <license>
                  <name>Apache License, Version 2.0</name>
                  <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
                </license>
              </licenses>
              <scm>
                <url>https://github.com/${'$'}{scm.path}</url>
              </scm>
              <developers>
                <developer>
                  <id>kevinb9n</id>
                  <name>Kevin Bourrillion</name>
                </developer>
              </developers>
            </project>
            """.trimIndent(),
            "guava-parent",
        )

    private val listenableFuture =
        Pom.parse(
            """
            <project>
              <parent>
                <groupId>com.google.guava</groupId>
                <artifactId>guava-parent</artifactId>
                <version>33.3.1-android</version>
              </parent>
              <artifactId>listenablefuture</artifactId>
              <version>9999.0-empty-to-avoid-conflict-with-guava</version>
              <name>Guava ListenableFuture only</name>
              <description>
                An empty artifact that Guava depends on to signal that it is providing
                ListenableFuture.
              </description>
            </project>
            """.trimIndent(),
            "listenablefuture",
        )

    @Test
    fun `reads the coordinates, taking the group from the parent when the POM leaves it out`() {
        assertEquals(
            MavenCoordinates("com.google.guava", "listenablefuture", "9999.0-empty-to-avoid-conflict-with-guava"),
            listenableFuture.coordinates,
        )
        assertEquals(MavenCoordinates("com.google.guava", "guava-parent", "33.3.1-android"), listenableFuture.parent)
    }

    @Test
    fun `reads name, description, licenses and developers, with white space collapsed`() {
        assertEquals("Guava Maven Parent", guavaParent.name)
        assertEquals(
            "An empty artifact that Guava depends on to signal that it is providing ListenableFuture.",
            listenableFuture.description,
        )
        assertEquals(
            listOf(PomLicense("Apache License, Version 2.0", "http://www.apache.org/licenses/LICENSE-2.0.txt")),
            guavaParent.licenses,
        )
        assertEquals(listOf("Kevin Bourrillion"), guavaParent.developers)
    }

    @Test
    fun `inherits licenses, developers and links from the parent, but not its name or description`() {
        val effective = listenableFuture.effective(listOf(guavaParent))

        assertEquals("Guava ListenableFuture only", effective.name)
        assertTrue(effective.description!!.startsWith("An empty artifact"))
        assertEquals(guavaParent.licenses, effective.licenses)
        assertEquals(listOf("Kevin Bourrillion"), effective.developers)
        assertEquals("https://github.com/google/guava", effective.website)
    }

    @Test
    fun `its own licenses win over the parent's`() {
        val own = listenableFuture.copy(licenses = listOf(PomLicense("BSD-3-Clause", null)))

        assertEquals(listOf(PomLicense("BSD-3-Clause", null)), own.effective(listOf(guavaParent)).licenses)
    }

    @Test
    fun `a parent without the field leaves it to the grandparent`() {
        val grandparent = guavaParent.copy(developers = emptyList(), organization = "Google")
        val parent = guavaParent.copy(licenses = emptyList(), developers = emptyList(), url = null)

        val effective = listenableFuture.effective(listOf(parent, grandparent))

        assertEquals(guavaParent.licenses, effective.licenses)
        assertEquals(listOf("Google"), effective.developers)
        // No url anywhere above: the scm link, filled in from the parent's properties.
        assertEquals("https://github.com/google/guava", effective.website)
    }

    @Test
    fun `fills in placeholders from properties and project values, and drops what it cannot fill`() {
        val pom =
            listenableFuture.copy(
                name = "\${project.groupId}:\${project.artifactId}",
                url = "https://example.com/\${unknown}",
                scmUrl = "https://github.com/\${owner}/\${project.artifactId}/tree/v\${project.version}",
                properties = mapOf("owner" to "\${org}", "org" to "google"),
            )

        val effective = pom.effective(emptyList())

        assertEquals("com.google.guava:listenablefuture", effective.name)
        assertEquals(
            "https://github.com/google/listenablefuture/tree/v9999.0-empty-to-avoid-conflict-with-guava",
            effective.website,
        )
    }

    @Test
    fun `keeps only http links`() {
        val pom = listenableFuture.copy(url = "scm:git:git://github.com/google/guava.git", scmUrl = null)

        assertNull(pom.effective(emptyList()).website)
    }

    @Test
    fun `a file that is not a POM fails, naming it`() {
        val message =
            assertThrows<IllegalArgumentException> { Pom.parse("<metadata/>", "The POM of a:b:1") }.message.orEmpty()

        assertTrue("The POM of a:b:1" in message, message)
    }

    @Test
    fun `never loads a DTD or an external entity`() {
        val xml =
            """
            <?xml version="1.0"?>
            <!DOCTYPE project [ <!ENTITY secret SYSTEM "file:///etc/hostname"> ]>
            <project><groupId>a</groupId><artifactId>b</artifactId><version>1</version><name>&secret;</name></project>
            """.trimIndent()

        val name = runCatching { Pom.parse(xml, "evil").name }.getOrNull()

        assertTrue(name == null || "localhost" !in name, "$name")
    }
}
