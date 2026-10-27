package io.github.conorryan.whistletabber.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.conorryan.whistletabber.ui.editor.EditorScreen
import io.github.conorryan.whistletabber.ui.editor.EditorViewModel
import io.github.conorryan.whistletabber.ui.findtunes.FindTunesScreen
import io.github.conorryan.whistletabber.ui.library.LibraryScreen
import io.github.conorryan.whistletabber.ui.library.LibraryViewModel
import io.github.conorryan.whistletabber.ui.settings.SettingsScreen
import io.github.conorryan.whistletabber.ui.settings.SettingsViewModel
import io.github.conorryan.whistletabber.ui.tune.TuneScreen
import io.github.conorryan.whistletabber.ui.tune.TuneViewModel
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data class TuneRoute(val id: Long)

/** [id] null: a new tune. */
@Serializable
data class EditorRoute(val id: Long? = null)

@Serializable
data object SettingsRoute

@Serializable
data object FindTunesRoute

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
                onOpenTune = { newId ->
                    nav.navigate(TuneRoute(newId)) { popUpTo<TuneRoute> { inclusive = true } }
                },
            )
        }
        composable<EditorRoute> { entry ->
            val id = entry.toRoute<EditorRoute>().id
            val vm: EditorViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { EditorViewModel(id, createSavedStateHandle(), graph.tuneRepository) }
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
    }
}
