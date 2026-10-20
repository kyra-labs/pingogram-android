package dev.kyralabs.pingogram.language

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

// The app's own language before Android 13, which keep no per-app language: MainActivity's context takes it.

/**
 * [base] in [language]'s locale, the system's languages for a null tag, and the process's default locale set to match,
 * so text the app formats itself (times, numbers) follows it too. Unknown before the language is read: then [base]
 * as it is, in the system's languages.
 */
internal fun contextInLanguage(
    base: Context,
    language: InAppLanguage?,
): Context {
    if (language == null) return base
    val locales =
        language.languageTag?.let(LocaleList::forLanguageTags)
            ?: Resources.getSystem().configuration.locales
    LocaleList.setDefault(locales)
    val configuration = Configuration(base.resources.configuration).apply { setLocales(locales) }
    return base.createConfigurationContext(configuration)
}

/** The language [MainActivity][dev.kyralabs.pingogram.MainActivity] takes before Hilt injects it. */
internal fun inAppLanguageOf(context: Context): InAppLanguage? =
    EntryPointAccessors
        .fromApplication<AppLanguageEntryPoint>(context)
        .appLanguageRepository()
        .inAppLanguage.value

/** Reaches the language repository from `attachBaseContext`, which runs before Hilt injects the activity. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AppLanguageEntryPoint {
    fun appLanguageRepository(): PlatformAppLanguageRepository
}
