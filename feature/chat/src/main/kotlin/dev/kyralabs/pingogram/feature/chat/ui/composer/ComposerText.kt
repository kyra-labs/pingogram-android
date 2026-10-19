package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import kotlinx.collections.immutable.toImmutableList

/**
 * The composer's text and its formatting (`REQUIREMENTS.md` FR-C5): [state] holds the text for the text field, and the
 * entities of the text (bold, links, …) are kept beside it, as plain [TextEntity]s, so they are sent, saved as a draft
 * and kept across process death as they are, and drawn by the field's output transformation in the theme's styles.
 *
 * - **Edits**: [inputTransformation], set on the text field, moves the entities with every edit the user makes (typing,
 *   pasting, the keyboard's corrections), from the exact changes of the edit ([afterReplace]). An edit it does not see
 *   (an undo, a text set from code) is reconciled when the text is next read ([afterChange]).
 * - **Formatting**: [toggle] applies or removes a type over a selection, [setLink] links it.
 * - **Reading**: [content] is the text with its entities, both snapshot state, so readers in composition or a
 *   `snapshotFlow` follow every change.
 *
 * Its owner, the conversation's view model, keeps it across rotations; it only touches the main thread, as the text
 * field does.
 */
@Stable
class ComposerText(
    initial: FormattedText = FormattedText.Empty,
) {
    /** The text of the field, with its selection and undo history. */
    val state: TextFieldState = TextFieldState(initial.text)

    /** The entities and the text they were last made for: the text can be ahead, by an edit not seen here. */
    private var formatting by mutableStateOf(Formatting(initial.text, initial.entities.toList()))

    /** Moves the entities with each of the user's edits, for the text field's `inputTransformation`. */
    val inputTransformation: InputTransformation = InputTransformation { followEdit() }

    /** The text with its entities, as the field shows them now. */
    val content: FormattedText
        get() {
            val text = state.text.toString()
            return FormattedText(text, entitiesFor(text).toImmutableList())
        }

    /** Whether there is nothing but white space to send. */
    val isBlank: Boolean get() = state.text.isBlank()

    /** Whether the field holds no text at all. */
    val isEmpty: Boolean get() = state.text.isEmpty()

    /** The entities of [text], which is the field's text now or right after an edit. */
    fun entitiesFor(text: String): List<TextEntity> {
        val current = formatting
        return if (current.text == text) current.entities else current.entities.afterChange(current.text, text)
    }

    /** Replaces the text and its formatting, the cursor at the end: a draft, an emptied field after sending. */
    fun setContent(content: FormattedText) {
        state.setTextAndPlaceCursorAtEnd(content.text)
        formatting = Formatting(content.text, content.entities.toList())
    }

    /** Applies [type] to the [selection], or removes it if the selection has it everywhere ([toggling]). */
    fun toggle(
        type: TextEntityType,
        selection: TextRange = state.selection,
    ) = changeEntities { it.toggling(type, selection.min, selection.max) }

    /** Links the [selection] to [url], or removes its links for a blank [url]. */
    fun setLink(
        url: String,
        selection: TextRange = state.selection,
    ) = changeEntities { entities ->
        val link = TextEntityType.TextUrl(url.trim())
        if (url.isBlank()) {
            entities.removing(link, selection.min, selection.max)
        } else {
            entities.toggling(link, selection.min, selection.max)
        }
    }

    /** Whether every character of the [selection] has [type]; false for an empty selection. */
    fun has(
        type: TextEntityType,
        selection: TextRange = state.selection,
    ): Boolean = entitiesFor(state.text.toString()).covers(type, selection.min, selection.max)

    /** The address the [selection] links to; null if it is not within one link. */
    fun linkAt(selection: TextRange = state.selection): String? =
        entitiesFor(state.text.toString()).linkAt(selection.min, selection.max)

    private inline fun changeEntities(change: (List<TextEntity>) -> List<TextEntity>) {
        val text = state.text.toString()
        formatting = Formatting(text, change(entitiesFor(text)))
    }

    /**
     * Applies the edit in this buffer to the entities. The changes do not overlap and are in order, each with its
     * range in the original text; applied from the last one back, the earlier ranges stay valid.
     */
    @OptIn(ExperimentalFoundationApi::class)
    private fun TextFieldBuffer.followEdit() {
        val changes = changes
        var entities = entitiesFor(originalText.toString())
        for (index in changes.changeCount - 1 downTo 0) {
            val original = changes.getOriginalRange(index)
            entities = entities.afterReplace(original.min, original.max, changes.getRange(index).length)
        }
        formatting = Formatting(asCharSequence().toString(), entities)
    }

    private data class Formatting(
        val text: String,
        val entities: List<TextEntity>,
    ) {
        override fun toString() = "Formatting(length=${text.length}, entities=${entities.size})"
    }
}
