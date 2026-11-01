package dev.kyralabs.pingogram.core.demo

import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DemoClientsTest {
    @TempDir
    lateinit var dir: File

    private val marker get() = dir.resolve("no_backup/demo")
    private val folder get() = dir.resolve("files/demo")

    private val clients =
        DemoClients(
            marker = { marker },
            folder = { folder },
            text = { "Text $it" },
            io = Dispatchers.Unconfined,
            fileWriter = { _, target -> target.writeText("file") },
        )

    @Test
    fun `the demo stays on until it is left, which deletes everything it made`() {
        assertFalse(clients.isOn())

        clients.turnOn()
        folder.resolve("journal.txt").apply { checkNotNull(parentFile).mkdirs() }.writeText("read\t1\t2\n")
        folder.resolve("files/photo.png").apply { checkNotNull(parentFile).mkdirs() }.writeText("photo")
        assertTrue(clients.isOn())

        clients.turnOffAndDelete()
        assertFalse(clients.isOn() || folder.exists())
    }
}
