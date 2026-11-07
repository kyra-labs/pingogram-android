package dev.kyralabs.pingogram.core.domain.model

/**
 * The color themes of Settings → Appearance → Theme (`REQUIREMENTS.md` FR-S2, `docs/adr/0016-color-themes.md`): the
 * brand's and curated Material 3 themes, each generated from a seed color by `tools/theme` (`docs/DESIGN_SYSTEM.md`
 * §2.1). Used whenever the wallpaper's colors are off or not available. Stored by name, so reordering keeps choices.
 */
enum class ThemePalette {
    /** The brand blue of the app icon, #2156C4. */
    Pingogram,
    Blue,
    Teal,
    Green,
    Lime,
    Yellow,
    Orange,
    Red,

    /** The experimental look's sample accent (`docs/DESIGN_SYSTEM.md` §13): the owner's design's coral, #FF8C7A. */
    Coral,
    Pink,
    Purple,
    Indigo,
    Brown,

    /** Greys only: material-color-utilities' monochrome scheme. */
    Monochrome,
}
