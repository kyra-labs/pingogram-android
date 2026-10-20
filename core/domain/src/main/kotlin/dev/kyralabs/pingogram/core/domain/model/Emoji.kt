package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * The emoji of one [category] of the emoji panel (`REQUIREMENTS.md` FR-C5), in Unicode's order: each one a complete
 * emoji sequence, skin tones folded into the base emoji.
 */
data class EmojiGroup(
    val category: EmojiCategory,
    val emoji: ImmutableList<String>,
)

/** Unicode's emoji groups (emoji-test.txt), in its order; its "Component" group (bare skin tones) is not offered. */
enum class EmojiCategory {
    SmileysAndEmotion,
    PeopleAndBody,
    AnimalsAndNature,
    FoodAndDrink,
    TravelAndPlaces,
    Activities,
    Objects,
    Symbols,
    Flags,
}
