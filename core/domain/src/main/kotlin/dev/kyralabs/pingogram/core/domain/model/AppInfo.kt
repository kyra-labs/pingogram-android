package dev.kyralabs.pingogram.core.domain.model

/**
 * What the app knows about its own build, for About (`REQUIREMENTS.md` FR-S1) and the feedback report. `:app` provides
 * it from its `BuildConfig`, so features show it without depending on `:app`.
 *
 * @property versionName the version people see, e.g. `0.3.0` (`docs/RELEASE.md`: the tag without `v`).
 * @property versionCode the build's version code (`docs/RELEASE.md`), which tells the ABI split apart.
 * @property flavor `foss` or `gms` (`docs/ARCHITECTURE.md`, Build & modules).
 * @property isDebug a debug build.
 */
data class AppInfo(
    val versionName: String,
    val versionCode: Long = 0,
    val flavor: String = "",
    val isDebug: Boolean = false,
)
