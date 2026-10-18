package dev.kyralabs.pingogram.core.ui.time

import android.content.Context
import android.text.format.DateFormat
import java.util.Locale

/**
 * How the device formats times, for the formatters of ViewModels (the chat list's times, the conversation's message
 * times, date separators and "last seen"): the user's 12- or 24-hour setting, and the platform's best pattern for a
 * skeleton in a language. [AndroidTimePatterns] asks the platform; tests pass fixed patterns, so ViewModels stay on
 * the JVM. `:app` binds it.
 */
interface TimePatterns {
    /** Whether the user chose the 24-hour clock in the system settings (or the language uses it by default). */
    fun is24HourClock(): Boolean

    /**
     * The pattern that shows the fields of [skeleton] ("Hm", "hm", "MMMd", "yMd") in [locale]'s order and separators,
     * e.g. "MMM d" for "MMMd" in English, "d. MMM" in German.
     */
    fun bestPattern(
        locale: Locale,
        skeleton: String,
    ): String
}

/**
 * [TimePatterns] of the platform: `android.text.format.DateFormat`, with the application [context]. Both may be called
 * off the main thread.
 */
class AndroidTimePatterns(
    private val context: Context,
) : TimePatterns {
    override fun is24HourClock(): Boolean = DateFormat.is24HourFormat(context)

    override fun bestPattern(
        locale: Locale,
        skeleton: String,
    ): String = DateFormat.getBestDateTimePattern(locale, skeleton)
}
