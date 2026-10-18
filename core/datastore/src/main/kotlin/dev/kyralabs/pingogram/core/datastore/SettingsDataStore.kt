package dev.kyralabs.pingogram.core.datastore

import javax.inject.Qualifier

/** Qualifies the `DataStore<Preferences>` of the app's settings file, which the settings repositories share. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SettingsDataStore
