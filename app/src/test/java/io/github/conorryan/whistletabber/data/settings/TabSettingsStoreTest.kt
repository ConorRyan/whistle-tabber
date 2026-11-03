package io.github.conorryan.whistletabber.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.conorryan.whistletabber.music.FingeringChoices
import io.github.conorryan.whistletabber.music.NoteNames
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TabSettingsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { tmp.newFile("tab.preferences_pb").also { it.delete() } })

    @Test
    fun `empty store emits defaults`() = runBlocking {
        assertEquals(TabSettings(), TabSettingsStore(newDataStore()).settings.first())
    }

    @Test
    fun `each setter persists its field`() = runBlocking {
        val store = TabSettingsStore(newDataStore())
        store.setWhistle(WhistleKey.LOW_D)
        store.setFingering(12, 1)
        store.setFoldOutOfRange(false)
        store.setMode(TransposeMode.SAME_FINGERINGS)
        store.setNoteNames(NoteNames.SOLFEGE)

        assertEquals(
            TabSettings(WhistleKey.LOW_D, FingeringChoices.DEFAULT.with(12, 1), foldOutOfRange = false, mode = TransposeMode.SAME_FINGERINGS, noteNames = NoteNames.SOLFEGE),
            store.settings.first(),
        )
    }

    @Test
    fun `values survive a new store on the same file`() = runBlocking {
        val dataStore = newDataStore()
        TabSettingsStore(dataStore).setWhistle(WhistleKey.B_FLAT)
        assertEquals(WhistleKey.B_FLAT, TabSettingsStore(dataStore).settings.first().whistle)
    }

    @Test
    fun `unknown enum names fall back to defaults`() = runBlocking {
        val dataStore = newDataStore()
        dataStore.edit {
            it[stringPreferencesKey("whistle")] = "KAZOO"
            it[stringPreferencesKey("cNatural")] = "XXX_XXX"
            it[stringPreferencesKey("transposeMode")] = ""
            it[stringPreferencesKey("noteNames")] = "NASHVILLE"
        }
        assertEquals(TabSettings(), TabSettingsStore(dataStore).settings.first())
    }

    @Test
    fun `fingering picks stack, and picking the default removes one`() = runBlocking {
        val store = TabSettingsStore(newDataStore())
        store.setFingering(12, 1)
        store.setFingering(22, 2)
        assertEquals(FingeringChoices.DEFAULT.with(12, 1).with(22, 2), store.settings.first().fingerings)
        store.setFingering(12, 0)
        assertEquals(FingeringChoices.DEFAULT.with(22, 2), store.settings.first().fingerings)
    }

    @Test
    fun `an old C natural setting carries over to both octaves`() = runBlocking {
        val dataStore = newDataStore()
        dataStore.edit { it[stringPreferencesKey("cNatural")] = "HALF_TOP" }
        val store = TabSettingsStore(dataStore)
        val f = store.settings.first().fingerings
        assertEquals("½OO OOO", f.fingeringAt(10)?.notation())
        assertEquals("½OO OOO+", f.fingeringAt(22)?.notation())
        // The first new pick keeps it.
        store.setFingering(12, 1)
        assertEquals("½OO OOO", store.settings.first().fingerings.fingeringAt(10)?.notation())
        assertEquals("OXX XXX+", store.settings.first().fingerings.fingeringAt(12)?.notation())
    }

    @Test
    fun `garbled fingering picks are dropped`() = runBlocking {
        val dataStore = newDataStore()
        dataStore.edit { it[stringPreferencesKey("fingerings")] = "12=OXXXXX+;x=1;10=QQQ;5=OOOOOO;;99=XXXXXX" }
        assertEquals(FingeringChoices.DEFAULT.with(12, 1), TabSettingsStore(dataStore).settings.first().fingerings)
    }
}
