package me.efesser.flauncher.domain.model

sealed interface AppChangeEvent {
    data class Added(val app: LauncherApp) : AppChangeEvent
    data class Changed(val app: LauncherApp) : AppChangeEvent
    data class Removed(val packageName: String) : AppChangeEvent
    data class Available(val apps: List<LauncherApp>) : AppChangeEvent
}
