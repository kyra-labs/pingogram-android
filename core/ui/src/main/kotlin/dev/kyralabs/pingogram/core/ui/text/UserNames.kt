package dev.kyralabs.pingogram.core.ui.text

import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.ui.R

/**
 * The short name of [this] user for previews, typing and service messages ("Alice is typing…"): the first name, else
 * the last name; "Deleted Account" for a deleted account, and for one TDLib knows nothing about.
 */
fun User.shortName(): UiText {
    val name = firstName.ifBlank { lastName }
    return if (type == UserType.Deleted || type == UserType.Unknown || name.isBlank()) {
        UiText.resource(R.string.user_deleted_account)
    } else {
        UiText.Plain(name)
    }
}

/** Whether [this] user is a deleted account, shown with a placeholder avatar and "Deleted Account". */
val User.isDeleted: Boolean
    get() = type == UserType.Deleted || type == UserType.Unknown
