package dev.kyralabs.pingogram.core.domain.model

/**
 * What the app knows about its own build, for About (`REQUIREMENTS.md` FR-S1). `:app` provides it from its
 * `BuildConfig`, so features show it without depending on `:app`.
 *
 * @property versionName the version people see, e.g. `0.3.0` (`docs/RELEASE.md`: the tag without `v`).
 */
data class AppInfo(
    val versionName: String,
)
