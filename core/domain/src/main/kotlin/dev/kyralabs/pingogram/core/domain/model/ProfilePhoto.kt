package dev.kyralabs.pingogram.core.domain.model

/**
 * A user's or chat's photo: [small] (160 × 160 px) for avatars, [big] (640 × 640 px) for profiles, and a
 * [minithumbnail] to show blurred while [small] downloads.
 */
data class ProfilePhoto(
    val small: FileRef,
    val big: FileRef,
    val minithumbnail: Minithumbnail?,
)
