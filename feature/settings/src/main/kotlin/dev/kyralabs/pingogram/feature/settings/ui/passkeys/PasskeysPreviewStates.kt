package dev.kyralabs.pingogram.feature.settings.ui.passkeys

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** The states of the Passkeys page, shared by previews and screenshot tests. */
internal object PasskeysPreviewStates {
    private val passkeys =
        persistentListOf(
            PasskeyItem(
                id = "p1",
                name = "Google Password Manager",
                details = UiText.Plain("Added Oct 14 · used 9:41 AM"),
            ),
            PasskeyItem(
                id = "p2",
                name = "Bitwarden",
                details = UiText.Plain("Added Mar 17 · never used"),
            ),
        )

    private val protection = AccountProtection(twoStepVerification = true, sessionCount = 20)

    val content = PasskeysUiState(passkeys = PasskeysState.Loaded(passkeys), protection = protection)

    val removing = content.copy(removingIds = persistentSetOf("p2"))

    val adding = content.copy(isAdding = true)

    val dialog = content.copy(dialog = RemovePasskeyDialog("p1", "Google Password Manager"))

    val empty =
        PasskeysUiState(
            passkeys = PasskeysState.Loaded(persistentListOf()),
            protection = AccountProtection(twoStepVerification = false, sessionCount = 1),
        )

    val loading = PasskeysUiState()

    val error = PasskeysUiState(passkeys = PasskeysState.Failed(DomainError.Network))
}
