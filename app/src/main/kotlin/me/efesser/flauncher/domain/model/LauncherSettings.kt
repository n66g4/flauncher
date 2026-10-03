package me.efesser.flauncher.domain.model

enum class BackButtonAction {
    Nothing,
    Clock,
    Screensaver,
}

data class LauncherSettings(
    val showCategoryTitles: Boolean = true,
    val showWatchNext: Boolean = false,
    val showDateInStatusBar: Boolean = true,
    val showTimeInStatusBar: Boolean = true,
    val autoHideStatusBar: Boolean = false,
    val dateFormat: String = "EEEE d",
    val timeFormat: String = "H:mm",
    val backButtonAction: BackButtonAction = BackButtonAction.Nothing,
    val gradientUuid: String? = null,
)

enum class SettingsPage {
    Main,
    Wallpaper,
    Gradients,
    StatusBar,
    DateTimeFormat,
    BackButtonAction,
    Sections,
    CategoryDetail,
}
