package dev.kyralabs.pingogram.core.ui.theme.mcu

import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Checks dynamic colors against a table that material-color-utilities 0.4.0 printed (`src/test/resources/mcu/`):
 * its "roles" line names the roles, each row gives a seed, dark theme, a contrast level and each role's RGB. The scheme
 * is SchemeContent's, with or without [fidelity] ([ContentScheme.fidelity]).
 */
internal fun assertSchemeTable(
    resource: String,
    roles: Map<String, DynamicColor>,
    fidelity: Boolean = true,
) {
    val lines =
        requireNotNull(SchemeTableResource::class.java.getResource("/mcu/$resource")) { "No table $resource" }
            .readText()
            .lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
    val names = lines.first().split(" ").drop(1)
    for (row in lines.drop(1)) {
        val cells = row.split(" ")
        val scheme = ContentScheme(Hct.fromInt(rgb(cells[0])), cells[1].toBoolean(), cells[2].toDouble(), fidelity)
        names.forEachIndexed { index, name ->
            val role = requireNotNull(roles[name]) { "No role $name" }
            assertEquals(rgb(cells[index + 3]), role.getArgb(scheme), "$name of ${cells.take(3)}")
        }
    }
}

private fun rgb(hex: String): Int = (0xFF000000 or hex.toLong(16)).toInt()

private object SchemeTableResource
