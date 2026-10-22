package dev.kyralabs.pingogram.core.ui.text

import android.content.res.Resources
import dev.kyralabs.pingogram.core.ui.R

/**
 * [parts] as one description for screen readers ("Alice, online, Admin"), the blank ones left out, joined by the
 * language's list separator (`list_separator`: ", " in English, "، " in Arabic, "、" in Japanese), never a separator
 * hard-coded in code (`docs/TRANSLATING.md`).
 */
fun spokenList(
    resources: Resources,
    parts: List<String?>,
): String = parts.filterNot { it.isNullOrBlank() }.joinToString(resources.getString(R.string.list_separator))
