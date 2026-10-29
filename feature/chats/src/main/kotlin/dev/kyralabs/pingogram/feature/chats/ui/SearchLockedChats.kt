package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.Chat
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList

/**
 * [this] without the chats of [hidden], locked chats not opened with the passcode (`docs/adr/0029-locked-chats.md`):
 * left out of the suggestions, every section of chats, and the messages found, as if search had not found them.
 */
internal fun SearchState.withoutChats(hidden: Set<Long>): SearchState =
    if (hidden.isEmpty()) {
        this
    } else {
        when (this) {
            is SearchState.Suggestions -> {
                copy(
                    topPeople = topPeople.filterNot { it.id in hidden },
                    recents = recents.filterNot { it.id in hidden },
                )
            }

            is SearchState.Results -> {
                copy(
                    chats = chats.without(hidden),
                    contacts = contacts.without(hidden),
                    global = global.without(hidden),
                    messages =
                        messages.copy(
                            found = messages.found.filterNot { it.chat.id in hidden }.toPersistentList(),
                        ),
                )
            }
        }
    }

private fun SearchSection<Chat>.without(hidden: Set<Long>): SearchSection<Chat> =
    if (this is SearchSection.Items) {
        SearchSection.Items(
            items.filterNot { it.id in hidden }.toImmutableList(),
        )
    } else {
        this
    }
