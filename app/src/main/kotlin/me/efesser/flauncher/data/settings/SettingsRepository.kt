package me.efesser.flauncher.data.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.efesser.flauncher.domain.model.BackButtonAction
import me.efesser.flauncher.domain.model.LauncherSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val prefs: SharedPreferences,
) {
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        val updated = transform(_settings.value)
        persist(updated)
        _settings.value = updated
    }

    private fun prefKey(key: String): String = "flutter.$key"

    private fun readSettings(): LauncherSettings {
        val backAction = when (prefs.getString(prefKey(KEY_BACK_BUTTON), "") ?: "") {
            BACK_ACTION_CLOCK -> BackButtonAction.Clock
            BACK_ACTION_SCREENSAVER -> BackButtonAction.Screensaver
            else -> BackButtonAction.Nothing
        }
        return LauncherSettings(
            showCategoryTitles = prefs.getBoolean(prefKey(KEY_SHOW_CATEGORY_TITLES), true),
            showWatchNext = prefs.getBoolean(prefKey(KEY_SHOW_WATCH_NEXT), true),
            showDateInStatusBar = prefs.getBoolean(prefKey(KEY_SHOW_DATE), true),
            showTimeInStatusBar = prefs.getBoolean(prefKey(KEY_SHOW_TIME), true),
            autoHideStatusBar = prefs.getBoolean(prefKey(KEY_AUTO_HIDE_STATUS_BAR), false),
            dateFormat = prefs.getString(prefKey(KEY_DATE_FORMAT), DEFAULT_DATE_FORMAT) ?: DEFAULT_DATE_FORMAT,
            timeFormat = prefs.getString(prefKey(KEY_TIME_FORMAT), DEFAULT_TIME_FORMAT) ?: DEFAULT_TIME_FORMAT,
            backButtonAction = backAction,
            gradientUuid = prefs.getString(prefKey(KEY_GRADIENT_UUID), null),
        )
    }

    private fun persist(settings: LauncherSettings) {
        prefs.edit()
            .putBoolean(prefKey(KEY_SHOW_CATEGORY_TITLES), settings.showCategoryTitles)
            .putBoolean(prefKey(KEY_SHOW_WATCH_NEXT), settings.showWatchNext)
            .putBoolean(prefKey(KEY_SHOW_DATE), settings.showDateInStatusBar)
            .putBoolean(prefKey(KEY_SHOW_TIME), settings.showTimeInStatusBar)
            .putBoolean(prefKey(KEY_AUTO_HIDE_STATUS_BAR), settings.autoHideStatusBar)
            .putString(prefKey(KEY_DATE_FORMAT), settings.dateFormat)
            .putString(prefKey(KEY_TIME_FORMAT), settings.timeFormat)
            .putString(prefKey(KEY_BACK_BUTTON), settings.backButtonAction.toPrefValue())
            .putString(prefKey(KEY_GRADIENT_UUID), settings.gradientUuid)
            .apply()
    }

    private fun BackButtonAction.toPrefValue(): String = when (this) {
        BackButtonAction.Nothing -> ""
        BackButtonAction.Clock -> BACK_ACTION_CLOCK
        BackButtonAction.Screensaver -> BACK_ACTION_SCREENSAVER
    }

    companion object {
        const val DEFAULT_DATE_FORMAT = "EEEE d"
        const val DEFAULT_TIME_FORMAT = "H:mm"

        private const val KEY_SHOW_CATEGORY_TITLES = "show_category_titles"
        private const val KEY_SHOW_WATCH_NEXT = "show_watch_next"
        private const val KEY_SHOW_DATE = "show_date_in_status_bar"
        private const val KEY_SHOW_TIME = "show_time_in_status_bar"
        private const val KEY_AUTO_HIDE_STATUS_BAR = "auto_hide_app_bar"
        private const val KEY_DATE_FORMAT = "date_format"
        private const val KEY_TIME_FORMAT = "time_format"
        private const val KEY_BACK_BUTTON = "back_button_action"
        private const val KEY_GRADIENT_UUID = "gradient_uuid"

        private const val BACK_ACTION_CLOCK = "CLOCK"
        private const val BACK_ACTION_SCREENSAVER = "SCREENSAVER"
    }
}
