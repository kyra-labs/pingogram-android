package dev.kyralabs.pingogram.core.domain.model

/** When power saving is on (Settings → Power saving): never, while Android's Battery Saver is on, or always. */
enum class PowerSavingMode {
    Off,
    WithBatterySaver,
    Always,
}

/** What power saving turns off while it is on; the user keeps each part on or off. */
enum class PowerSavingPart {
    /** Animated stickers and custom emoji show their first frame. */
    AnimatedStickers,

    /** Videos and GIFs in the media viewer wait for a tap instead of playing by themselves. */
    Autoplay,

    /** The call screens' moving avatar and its speaking rings stand still. */
    CallAnimations,
}

/**
 * The power saving settings, kept on this device across logouts like the Appearance settings: the [mode], and the
 * [parts] it turns off while on. By default it follows Battery Saver and turns every part off.
 */
data class PowerSavingSettings(
    val mode: PowerSavingMode = PowerSavingMode.WithBatterySaver,
    val parts: Set<PowerSavingPart> = PowerSavingPart.entries.toSet(),
) {
    /** What is turned off now, while Battery Saver is [batterySaver] on or off. */
    fun inEffect(batterySaver: Boolean): PowerSaving {
        val on =
            when (mode) {
                PowerSavingMode.Off -> false
                PowerSavingMode.WithBatterySaver -> batterySaver
                PowerSavingMode.Always -> true
            }
        return if (on) PowerSaving(parts) else PowerSaving.None
    }
}

/** What power saving turns off now ([PowerSavingSettings.inEffect]); [None] while it is off. */
data class PowerSaving(
    val off: Set<PowerSavingPart> = emptySet(),
) {
    val stillStickers: Boolean get() = PowerSavingPart.AnimatedStickers in off
    val noAutoplay: Boolean get() = PowerSavingPart.Autoplay in off
    val stillCalls: Boolean get() = PowerSavingPart.CallAnimations in off

    companion object {
        val None = PowerSaving()
    }
}
