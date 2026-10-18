package dev.kyralabs.pingogram.core.ui.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * A text for the UI as data: what a ViewModel, which has no `Context`, puts in its state, and composition turns into a
 * string with [resolve] (`docs/ARCHITECTURE.md`, ViewModels). Strings come from resources with their arguments, so
 * they follow the app's language; no sentence is put together from pieces.
 *
 * Arguments are [String]s, numbers, or other [UiText]s, which are resolved first. Equal texts are equal, so UI state
 * holding them skips unchanged emissions.
 */
@Immutable
sealed interface UiText {
    /** Text that is shown as it is: a name, a message. `toString()` leaves it out. */
    data class Plain(
        val text: String,
    ) : UiText {
        override fun toString() = "Plain(…)"
    }

    /** The string [id], formatted with [args]. */
    data class Resource(
        @param:StringRes val id: Int,
        val args: ImmutableList<Any> = persistentListOf(),
    ) : UiText {
        override fun toString() = "Resource(id=$id, ${args.size} args)"
    }

    /** The plural string [id] for [count], formatted with [args] (which usually include [count]). */
    data class Plural(
        @param:PluralsRes val id: Int,
        val count: Int,
        val args: ImmutableList<Any> = persistentListOf(),
    ) : UiText {
        override fun toString() = "Plural(id=$id, count=$count, ${args.size} args)"
    }

    companion object {
        /** The string [id] formatted with [args]. */
        fun resource(
            @StringRes id: Int,
            vararg args: Any,
        ): UiText = Resource(id, args.toList().toImmutableList())

        /** The plural string [id] for [count], formatted with [args]. */
        fun plural(
            @PluralsRes id: Int,
            count: Int,
            vararg args: Any,
        ): UiText = Plural(id, count, args.toList().toImmutableList())
    }
}

/** This text as a string in the current configuration's language. */
@Composable
@ReadOnlyComposable
fun UiText.resolve(): String = resolve(LocalResources.current)

/**
 * [resolve] outside composition, e.g. for a notification. `SpreadOperator` is suppressed: `Resources` takes Java
 * varargs, and the array spread into them is the one built here for them.
 */
@Suppress("SpreadOperator")
fun UiText.resolve(resources: Resources): String =
    when (this) {
        is UiText.Plain -> {
            text
        }

        is UiText.Resource -> {
            if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args.resolved(resources))
        }

        is UiText.Plural -> {
            resources.getQuantityString(id, count, *args.resolved(resources))
        }
    }

private fun List<Any>.resolved(resources: Resources): Array<Any> =
    Array(size) { index ->
        when (val arg = get(index)) {
            is UiText -> arg.resolve(resources)
            else -> arg
        }
    }
