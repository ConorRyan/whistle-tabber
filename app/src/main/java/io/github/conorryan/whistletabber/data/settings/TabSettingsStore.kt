package io.github.conorryan.whistletabber.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.conorryan.whistletabber.data.enumByName
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val WHISTLE_KEY = stringPreferencesKey("whistle")
private val C_NATURAL_KEY = stringPreferencesKey("cNatural")
private val FOLD_KEY = booleanPreferencesKey("foldOutOfRange")
private val MODE_KEY = stringPreferencesKey("transposeMode")

/**
 * How tabs are drawn: chosen whistle, C natural fingering, octave folding and
 * transpose mode. Enums are stored by name; an unknown name (from a newer or
 * older version) falls back to the default.
 */
class TabSettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<TabSettings> = dataStore.data.map { prefs ->
        val defaults = TabSettings()
        TabSettings(
            whistle = enumByName<WhistleKey>(prefs[WHISTLE_KEY]) ?: defaults.whistle,
            cNatural = enumByName<CNatural>(prefs[C_NATURAL_KEY]) ?: defaults.cNatural,
            foldOutOfRange = prefs[FOLD_KEY] ?: defaults.foldOutOfRange,
            mode = enumByName<TransposeMode>(prefs[MODE_KEY]) ?: defaults.mode,
        )
    }

    suspend fun setWhistle(whistle: WhistleKey) {
        dataStore.edit { it[WHISTLE_KEY] = whistle.name }
    }

    suspend fun setCNatural(cNatural: CNatural) {
        dataStore.edit { it[C_NATURAL_KEY] = cNatural.name }
    }

    suspend fun setFoldOutOfRange(fold: Boolean) {
        dataStore.edit { it[FOLD_KEY] = fold }
    }

    suspend fun setMode(mode: TransposeMode) {
        dataStore.edit { it[MODE_KEY] = mode.name }
    }

    companion object {
        fun create(context: Context): TabSettingsStore = TabSettingsStore(SettingsDataStores.tab(context))
    }
}
