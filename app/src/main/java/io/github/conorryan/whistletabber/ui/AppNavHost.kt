package io.github.conorryan.whistletabber.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.conorryan.whistletabber.AppGraph
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.ui.editor.EditorScreen
import io.github.conorryan.whistletabber.ui.editor.EditorViewModel
import io.github.conorryan.whistletabber.ui.findtunes.FindTunesScreen
import io.github.conorryan.whistletabber.ui.library.LibraryScreen
import io.github.conorryan.whistletabber.ui.library.LibraryViewModel
import io.github.conorryan.whistletabber.ui.scan.ScanTabScreen
import io.github.conorryan.whistletabber.ui.scan.ScanTabViewModel
import io.github.conorryan.whistletabber.ui.settings.SettingsScreen
import io.github.conorryan.whistletabber.ui.tap.TapEditorScreen
import io.github.conorryan.whistletabber.ui.tap.TapEditorViewModel
import io.github.conorryan.whistletabber.ui.settings.SettingsViewModel
import io.github.conorryan.whistletabber.ui.tune.TuneScreen
import io.github.conorryan.whistletabber.ui.tune.TuneViewModel
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data class TuneRoute(val id: Long)

/** [id] null: a new tune, starting from [draft] if given. */
@Serializable
data class EditorRoute(val id: Long? = null, val draft: String? = null)

/** The tap editor; [id] null: a new tune. */
@Serializable
data class TapEditorRoute(val id: Long? = null)

@Serializable
data object SettingsRoute

@Serializable
data object FindTunesRoute

@Serializable
data object ScanTabRoute

@Composable
fun AppNavHost(graph: AppGraph) {
    val nav = rememberNavController()
    LaunchedEffect(nav) {
        // Tunes shared from another app: go to the library, which reports the import.
        // Not from the editor, so a draft isn't lost; the report waits for the library.
        graph.incomingTunes.arrivals.collect {
            if (nav.currentDestination?.hasRoute<EditorRoute>() != true) {
                nav.popBackStack<LibraryRoute>(inclusive = false)
            }
        }
    }
    NavHost(nav, startDestination = LibraryRoute) {
        composable<LibraryRoute> {
            val vm: LibraryViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        LibraryViewModel(graph.tuneRepository, graph.tuneImporter, graph.abcFiles, graph.recentlyDeleted, graph.incomingTunes, graph.appScope)
                    }
                },
            )
            LibraryScreen(
                vm = vm,
                onOpenTune = { nav.navigate(TuneRoute(it)) },
                onNewTune = { nav.navigate(EditorRoute()) },
                onSettings = { nav.navigate(SettingsRoute) },
                onFindTunes = { nav.navigate(FindTunesRoute) },
                onScanTab = { nav.navigate(ScanTabRoute) },
                onWriteTaps = { nav.navigate(TapEditorRoute()) },
            )
        }
        composable<TuneRoute> { entry ->
            val id = entry.toRoute<TuneRoute>().id
            val vm: TuneViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        TuneViewModel(id, graph.tuneRepository, graph.tabSettingsEditor, graph.abcFiles, graph.recentlyDeleted)
                    }
                },
            )
            TuneScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(EditorRoute(id)) },
                onEditTaps = { nav.navigate(TapEditorRoute(id)) },
                onOpenTune = { newId ->
                    nav.navigate(TuneRoute(newId)) { popUpTo<TuneRoute> { inclusive = true } }
                },
            )
        }
        composable<EditorRoute> { entry ->
            val route = entry.toRoute<EditorRoute>()
            val id = route.id
            val vm: EditorViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { EditorViewModel(id, createSavedStateHandle(), graph.tuneRepository, route.draft) }
                },
            )
            EditorScreen(
                vm = vm,
                isNew = id == null,
                onClose = { nav.popBackStack() },
                onSaved = { savedId ->
                    if (id == null) {
                        nav.navigate(TuneRoute(savedId)) { popUpTo<EditorRoute> { inclusive = true } }
                    } else {
                        nav.popBackStack()
                    }
                },
            )
        }
        composable<TapEditorRoute> { entry ->
            val id = entry.toRoute<TapEditorRoute>().id
            val vm: TapEditorViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { TapEditorViewModel(id, createSavedStateHandle(), graph.tuneRepository, graph.tabSettingsStore.settings) }
                },
            )
            TapEditorScreen(
                vm = vm,
                isNew = id == null,
                onClose = { nav.popBackStack() },
                onSaved = { savedId ->
                    if (id == null) {
                        nav.navigate(TuneRoute(savedId)) { popUpTo<TapEditorRoute> { inclusive = true } }
                    } else {
                        nav.popBackStack()
                    }
                },
            )
        }
        composable<SettingsRoute> {
            val vm: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SettingsViewModel(graph.tabSettingsEditor, graph.tuneImporter, graph.appScope) }
                },
            )
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable<FindTunesRoute> {
            FindTunesScreen(onBack = { nav.popBackStack() })
        }
        composable<ScanTabRoute> {
            val defaultTitle = stringResource(R.string.scan_default_title)
            val vm: ScanTabViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ScanTabViewModel(graph.tabImageLoader::load, graph.tabSettingsStore.settings, defaultTitle) }
                },
            )
            ScanTabScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenInEditor = { abc ->
                    nav.navigate(EditorRoute(draft = abc)) { popUpTo<ScanTabRoute> { inclusive = true } }
                },
            )
        }
    }
}
