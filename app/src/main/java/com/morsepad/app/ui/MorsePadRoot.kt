package com.morsepad.app.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.morsepad.app.AppContainer
import com.morsepad.app.ui.editor.EditorScreen
import com.morsepad.app.ui.editor.NoteViewModel
import com.morsepad.app.ui.editor.findActivity
import com.morsepad.app.ui.notes.NoteListScreen
import com.morsepad.app.ui.notes.NoteListViewModel
import com.morsepad.app.ui.settings.SettingsScreen
import com.morsepad.app.ui.settings.SettingsViewModel
import com.morsepad.app.ui.theme.LocalPadColors
import com.morsepad.app.ui.theme.MorsePadTheme
import com.morsepad.core.settings.ThemeMode

/** Status/navigation bar icons follow the app theme, not the system theme. */
@Composable
private fun SystemBarIcons(theme: ThemeMode) {
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return
    DisposableEffect(activity, theme) {
        val style = if (theme == ThemeMode.DARK) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }
}

private object Routes {
    const val NOTES = "notes"
    const val EDITOR = "editor/{${NoteViewModel.ARG_NOTE_ID}}"
    const val SETTINGS = "settings"
    fun editor(noteId: Long) = "editor/$noteId"
}

private sealed interface Launch {
    data object Resolving : Launch
    data class Ready(val restoreNoteId: Long?) : Launch
}

/**
 * Navigation between the note list, the input screen and settings. Screen changes are
 * instant (no transition animations, spec 4.3). On a fresh launch the last unfinished note
 * is reopened directly (spec 8.1).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MorsePadRoot(container: AppContainer, restoreLastNote: Boolean) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()

    MorsePadTheme(settings.theme) {
        SystemBarIcons(settings.theme)
        var launch by remember { mutableStateOf<Launch>(if (restoreLastNote) Launch.Resolving else Launch.Ready(null)) }
        LaunchedEffect(Unit) {
            if (launch == Launch.Resolving) launch = Launch.Ready(container.noteToRestore())
        }
        val ready = launch as? Launch.Ready
        if (ready == null) {
            Box(Modifier.fillMaxSize().background(LocalPadColors.current.background))
            return@MorsePadTheme
        }

        val nav = rememberNavController()
        NavHost(
            navController = nav,
            startDestination = Routes.NOTES,
            modifier = Modifier
                .fillMaxSize()
                .background(LocalPadColors.current.background)
                // Lets UiAutomator-based tests find nodes by their test tags.
                .semantics { testTagsAsResourceId = true },
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.NOTES) {
                val vm: NoteListViewModel = viewModel(factory = viewModelFactory { initializer { NoteListViewModel(container.noteRepository) } })
                NoteListScreen(
                    viewModel = vm,
                    onOpen = { id -> nav.navigate(Routes.editor(id)) },
                    onCreate = { nav.navigate(Routes.editor(NoteViewModel.NEW_NOTE_ID)) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(navArgument(NoteViewModel.ARG_NOTE_ID) { type = NavType.LongType }),
            ) {
                val vm: NoteViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            NoteViewModel(
                                savedStateHandle = createSavedStateHandle(),
                                notes = container.noteRepository,
                                settingsRepository = container.settingsRepository,
                                latencyTracker = container.latencyTracker,
                                writeBarrier = container.writeBarrier,
                                appScope = container.applicationScope,
                            )
                        }
                    },
                )
                EditorScreen(
                    viewModel = vm,
                    latencyTracker = container.latencyTracker,
                    touchSignals = container.touchSignals,
                    haptics = container.haptics,
                    onExit = {
                        if (nav.currentDestination?.route == Routes.EDITOR &&
                            !nav.popBackStack(Routes.NOTES, inclusive = false)
                        ) {
                            nav.navigate(Routes.NOTES)
                        }
                    },
                )
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(
                    factory = viewModelFactory { initializer { SettingsViewModel(container.settingsRepository, container.latencyTracker, container.haptics) } },
                )
                SettingsScreen(vm, onBack = { nav.popBackStack() })
            }
        }

        LaunchedEffect(ready.restoreNoteId) {
            ready.restoreNoteId?.let { nav.navigate(Routes.editor(it)) }
        }
    }
}
