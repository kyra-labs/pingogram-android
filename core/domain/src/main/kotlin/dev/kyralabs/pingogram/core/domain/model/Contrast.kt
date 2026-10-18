package dev.kyralabs.pingogram.core.domain.model

/**
 * Material 3 contrast level from Appearance settings (`REQUIREMENTS.md` FR-S2). On Android 14+ the
 * system contrast setting takes precedence over this choice.
 */
enum class Contrast {
    Standard,
    Medium,
    High,
}
