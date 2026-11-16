// Plain Kotlin models and use cases; must never depend on Android (docs/ARCHITECTURE.md).
plugins {
    alias(libs.plugins.pingogram.jvm.library)
    // Kotlin warnings fail the build here (docs/audit/v1-readiness.md).
    alias(libs.plugins.pingogram.kotlin.warnings)
}

dependencies {
    api(projects.core.common)
    // Models hold ImmutableList/ImmutableMap, so repositories emit immutable snapshots (docs/PERFORMANCE.md §3–4).
    api(libs.kotlinx.collections.immutable)
}
