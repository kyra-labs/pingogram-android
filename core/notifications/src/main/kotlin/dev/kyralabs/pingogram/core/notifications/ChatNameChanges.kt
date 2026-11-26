package dev.kyralabs.pingogram.core.notifications

/**
 * Which chats' names given on this phone (`REQUIREMENTS.md` FR-X20) changed between renders: a name given, changed or
 * taken off, or all of them when the setting is turned off. The notifications and shortcuts of those chats still show
 * the old name until they are published again.
 */
internal class ChatNameChanges {
    /** The names at the last [since]; null before the first, when nothing has changed yet. */
    private var last: Map<Long, String>? = null

    /** The chats whose name in [names] is not the one at the last call; none at the first. */
    fun since(names: Map<Long, String>): Set<Long> {
        val before = last
        last = names
        if (before == null || before == names) return emptySet()
        return (before.keys + names.keys).filterTo(HashSet()) { before[it] != names[it] }
    }
}
