package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A library, font or icon set that Pingogram is built from, for the open-source licenses screen (`REQUIREMENTS.md`
 * FR-S1, About). The list is made when the app is built, from the dependencies of that very build
 * (`docs/adr/0002-open-source-licenses.md`).
 *
 * @property id the Maven `group:artifact` of a library, or a name of its own for what is not on Maven (a font, icons).
 * @property name the name its authors give it, e.g. `Compose Material3 Components`.
 * @property version the version in the app, e.g. `1.5.0-beta01`; for TDLib the first 10 characters of its commit, as
 *   its releases are named; null where there is none (icons drawn from a font).
 * @property description what it is, in its authors' words; null without one.
 * @property developers the people or organizations that make it.
 * @property website its home page, null without one.
 * @property licenses the licenses it is under, most often one.
 */
data class OpenSourceLibrary(
    val id: String,
    val name: String,
    val version: String?,
    val description: String?,
    val developers: ImmutableList<String>,
    val website: String?,
    val licenses: ImmutableList<OpenSourceLicense>,
)

/**
 * A license an [OpenSourceLibrary] is under.
 *
 * @property id its SPDX identifier where it has one, e.g. `Apache-2.0`.
 * @property name its name, e.g. `Apache License 2.0`.
 * @property url where it is published, null if unknown.
 * @property text its full text, as the app ships it; null where the build has none, and then [url] is the reference.
 */
data class OpenSourceLicense(
    val id: String,
    val name: String,
    val url: String?,
    val text: String?,
)
