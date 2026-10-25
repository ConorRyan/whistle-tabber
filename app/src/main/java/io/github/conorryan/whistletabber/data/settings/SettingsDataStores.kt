package io.github.conorryan.whistletabber.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The process-wide DataStores backing the settings stores. The stores receive
 * their DataStore through the constructor, so tests can substitute their own
 * file-backed store instead of the process-wide one.
 */
internal object SettingsDataStores {

    private val Context.tabSettings: DataStore<Preferences> by preferencesDataStore("whistletabber.tab")

    fun tab(context: Context): DataStore<Preferences> = context.applicationContext.tabSettings
}
