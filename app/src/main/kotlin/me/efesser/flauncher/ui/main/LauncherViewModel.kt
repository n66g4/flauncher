package me.efesser.flauncher.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.efesser.flauncher.data.repository.LauncherRepository
import me.efesser.flauncher.data.settings.SettingsRepository
import me.efesser.flauncher.data.wallpaper.WallpaperRepository
import me.efesser.flauncher.data.wallpaper.WallpaperState
import me.efesser.flauncher.domain.model.BackButtonAction
import me.efesser.flauncher.domain.model.CategoryLayoutType
import me.efesser.flauncher.domain.model.LauncherApp
import me.efesser.flauncher.domain.model.LauncherAppItem
import me.efesser.flauncher.domain.model.LauncherCategory
import me.efesser.flauncher.domain.model.LauncherGradient
import me.efesser.flauncher.domain.model.LauncherSection
import me.efesser.flauncher.domain.model.LauncherSettings
import me.efesser.flauncher.domain.model.NetworkState
import me.efesser.flauncher.domain.model.SettingsPage
import me.efesser.flauncher.platform.AppChangeMonitor
import me.efesser.flauncher.platform.AppPlatformService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SelectedAppContext(
    val item: LauncherAppItem,
    val categoryId: Int,
    val categoryName: String,
    val manualSort: Boolean,
    val appIndex: Int,
)

data class LauncherUiState(
    val isLoading: Boolean = true,
    val sections: List<LauncherSection> = emptyList(),
    val errorMessage: String? = null,
    val networkState: NetworkState = NetworkState(),
    val dateTimeText: String = "",
    val selectedApp: SelectedAppContext? = null,
    val appInfoShowAddToCategory: Boolean = false,
    val addToCategoryTargets: List<LauncherCategory> = emptyList(),
    val settings: LauncherSettings = LauncherSettings(),
    val wallpaper: WallpaperState = WallpaperState(),
    val showSettings: Boolean = false,
    val settingsPage: SettingsPage = SettingsPage.Main,
    val settingsCategoryId: Int? = null,
    val settingsMessage: String? = null,
)

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val repository: LauncherRepository,
    private val settingsRepository: SettingsRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val appPlatformService: AppPlatformService,
    appChangeMonitor: AppChangeMonitor,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    init {
        refresh()
        observeNetwork()
        observeAppChanges(appChangeMonitor)
        observeSettingsAndWallpaper()
        startClock()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                repository.refreshInstalledApps()
                repository.loadLauncherSections()
            }.onSuccess { sections ->
                _uiState.update { it.copy(isLoading = false, sections = sections) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
        }
    }

    private fun reloadSections() {
        viewModelScope.launch {
            runCatching { reloadSectionsNow() }
        }
    }

    private suspend fun reloadSectionsNow(): List<LauncherSection> {
        val sections = repository.loadLauncherSections()
        _uiState.update { it.copy(sections = sections, isLoading = false) }
        return sections
    }

    private fun categories(): List<LauncherCategory> =
        _uiState.value.sections.filterIsInstance<LauncherSection.CategorySection>().map { it.category }

    private fun observeNetwork() {
        viewModelScope.launch {
            repository.networkState.collect { networkState ->
                _uiState.update { it.copy(networkState = networkState) }
            }
        }
    }

    private fun observeSettingsAndWallpaper() {
        viewModelScope.launch {
            combine(settingsRepository.settings, wallpaperRepository.state) { settings, wallpaper ->
                settings to wallpaper
            }.collect { (settings, wallpaper) ->
                _uiState.update { it.copy(settings = settings, wallpaper = wallpaper) }
                updateDateTimeText()
            }
        }
    }

    private fun observeAppChanges(appChangeMonitor: AppChangeMonitor) {
        viewModelScope.launch {
            appChangeMonitor.events.collect { event ->
                repository.handleAppChange(event)
                reloadSections()
            }
        }
    }

    private fun startClock() {
        viewModelScope.launch {
            while (isActive) {
                updateDateTimeText()
                delay(30_000)
            }
        }
    }

    private fun updateDateTimeText() {
        val settings = _uiState.value.settings
        val parts = buildList {
            if (settings.showDateInStatusBar) {
                add(formatDate(settings.dateFormat))
            }
            if (settings.showTimeInStatusBar) {
                add(formatTime(settings.timeFormat))
            }
        }
        _uiState.update { it.copy(dateTimeText = parts.joinToString("  ")) }
    }

    private fun formatDate(pattern: String): String =
        runCatching { SimpleDateFormat(pattern, Locale.CHINA).format(Date()) }.getOrDefault("")

    private fun formatTime(pattern: String): String =
        runCatching { SimpleDateFormat(pattern, Locale.CHINA).format(Date()) }.getOrDefault("")

    fun launchApp(app: LauncherApp) {
        repository.launchApp(app)
    }

    fun showAppInfo(item: LauncherAppItem, category: LauncherCategory) {
        val index = category.apps.indexOfFirst { it.app.packageName == item.app.packageName }
        _uiState.update {
            it.copy(
                selectedApp = SelectedAppContext(
                    item = item,
                    categoryId = category.id,
                    categoryName = category.name,
                    manualSort = category.manualSort,
                    appIndex = index.coerceAtLeast(0),
                ),
                appInfoShowAddToCategory = false,
                addToCategoryTargets = emptyList(),
            )
        }
    }

    fun dismissAppInfo() {
        _uiState.update {
            it.copy(
                selectedApp = null,
                appInfoShowAddToCategory = false,
                addToCategoryTargets = emptyList(),
            )
        }
    }

    fun openSelectedApp() {
        val app = _uiState.value.selectedApp?.item?.app ?: return
        launchApp(app)
        dismissAppInfo()
    }

    fun openSelectedAppInfo() {
        val packageName = _uiState.value.selectedApp?.item?.app?.packageName ?: return
        repository.openAppInfo(packageName)
    }

    fun uninstallSelectedApp() {
        val packageName = _uiState.value.selectedApp?.item?.app?.packageName ?: return
        repository.uninstallApp(packageName)
        dismissAppInfo()
    }

    fun toggleSelectedAppHidden() {
        val selected = _uiState.value.selectedApp ?: return
        viewModelScope.launch {
            repository.setAppHidden(selected.item.app.packageName, hidden = !selected.item.app.hidden)
            reloadSections()
            dismissAppInfo()
        }
    }

    fun removeSelectedAppFromCategory() {
        val selected = _uiState.value.selectedApp ?: return
        viewModelScope.launch {
            repository.removeAppFromCategory(selected.categoryId, selected.item.app.packageName)
            reloadSections()
            dismissAppInfo()
        }
    }

    fun showAddToCategoryPicker() {
        val selected = _uiState.value.selectedApp ?: return
        viewModelScope.launch {
            val targets = repository.getCategoriesWithoutApp(selected.item.app.packageName)
            _uiState.update {
                it.copy(
                    appInfoShowAddToCategory = true,
                    addToCategoryTargets = targets,
                )
            }
        }
    }

    fun cancelAddToCategoryPicker() {
        _uiState.update { it.copy(appInfoShowAddToCategory = false, addToCategoryTargets = emptyList()) }
    }

    fun addSelectedAppToCategory(categoryId: Int) {
        val selected = _uiState.value.selectedApp ?: return
        viewModelScope.launch {
            repository.addAppToCategory(categoryId, selected.item.app.packageName)
            reloadSections()
            dismissAppInfo()
        }
    }

    fun moveSelectedAppInCategory(direction: Int) {
        val selected = _uiState.value.selectedApp ?: return
        if (!selected.manualSort) return
        val category = categories().find { it.id == selected.categoryId } ?: return
        val newIndex = selected.appIndex + direction
        if (newIndex !in category.apps.indices) return
        viewModelScope.launch {
            repository.reorderAppInCategory(selected.categoryId, selected.appIndex, newIndex)
            val sections = reloadSectionsNow()
            val updated = sections
                .filterIsInstance<LauncherSection.CategorySection>()
                .map { it.category }
                .find { it.id == selected.categoryId }
            val movedItem = updated?.apps?.getOrNull(newIndex) ?: return@launch
            showAppInfo(movedItem, updated)
        }
    }

    fun addCategory(name: String = "新分类") {
        viewModelScope.launch {
            repository.addCategory(name)
            reloadSections()
        }
    }

    fun addSpacer() {
        viewModelScope.launch {
            repository.addSpacer()
            reloadSections()
        }
    }

    fun deleteSection(section: LauncherSection) {
        viewModelScope.launch {
            when (section) {
                is LauncherSection.CategorySection -> repository.deleteCategory(section.category.id)
                is LauncherSection.SpacerSection -> repository.deleteSpacer(section.id)
            }
            reloadSections()
        }
    }

    fun moveSectionUp(index: Int) {
        if (index <= 0) return
        viewModelScope.launch {
            repository.moveSection(index, index - 1)
            reloadSections()
        }
    }

    fun moveSectionDown(index: Int) {
        val lastIndex = _uiState.value.sections.lastIndex
        if (index < 0 || index >= lastIndex) return
        viewModelScope.launch {
            repository.moveSection(index, index + 1)
            reloadSections()
        }
    }

    fun openCategoryDetail(categoryId: Int) {
        _uiState.update {
            it.copy(settingsPage = SettingsPage.CategoryDetail, settingsCategoryId = categoryId)
        }
    }

    fun renameCategory(categoryId: Int, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renameCategory(categoryId, name.trim())
            reloadSections()
        }
    }

    fun setCategorySort(categoryId: Int, manual: Boolean) {
        viewModelScope.launch {
            repository.setCategorySort(categoryId, manual)
            reloadSections()
        }
    }

    fun openSettings() {
        _uiState.update {
            it.copy(
                showSettings = true,
                settingsPage = SettingsPage.Main,
                settingsCategoryId = null,
                settingsMessage = null,
                selectedApp = null,
                appInfoShowAddToCategory = false,
            )
        }
    }

    fun closeSettings() {
        _uiState.update {
            it.copy(
                showSettings = false,
                settingsPage = SettingsPage.Main,
                settingsCategoryId = null,
                settingsMessage = null,
            )
        }
    }

    fun navigateSettings(page: SettingsPage) {
        _uiState.update { it.copy(settingsPage = page, settingsMessage = null) }
    }

    fun settingsBack(): Boolean {
        val state = _uiState.value
        return when (state.settingsPage) {
            SettingsPage.CategoryDetail -> {
                _uiState.update {
                    it.copy(settingsPage = SettingsPage.Sections, settingsCategoryId = null)
                }
                true
            }
            SettingsPage.Main -> {
                if (state.showSettings) {
                    closeSettings()
                    true
                } else {
                    false
                }
            }
            else -> {
                navigateSettings(SettingsPage.Main)
                true
            }
        }
    }

    fun handleLauncherBack(): Boolean {
        if (_uiState.value.appInfoShowAddToCategory) {
            cancelAddToCategoryPicker()
            return true
        }
        if (_uiState.value.selectedApp != null) {
            dismissAppInfo()
            return true
        }
        if (settingsBack()) return true
        return when (_uiState.value.settings.backButtonAction) {
            BackButtonAction.Nothing -> true
            BackButtonAction.Clock -> {
                appPlatformService.startScreensaver()
                true
            }
            BackButtonAction.Screensaver -> {
                appPlatformService.startScreensaver()
                true
            }
        }
    }

    fun openSystemSettings() {
        appPlatformService.openSystemSettings()
    }

    fun setShowCategoryTitles(enabled: Boolean) {
        settingsRepository.update { it.copy(showCategoryTitles = enabled) }
    }

    fun setShowWatchNext(enabled: Boolean) {
        settingsRepository.update { it.copy(showWatchNext = enabled) }
    }

    fun setCategoryLayoutType(categoryId: Int, layoutType: CategoryLayoutType) {
        viewModelScope.launch {
            repository.setCategoryLayoutType(categoryId, layoutType)
            reloadSections()
        }
    }

    fun setCategoryColumnsCount(categoryId: Int, columnsCount: Int) {
        viewModelScope.launch {
            repository.setCategoryColumnsCount(categoryId, columnsCount)
            reloadSections()
        }
    }

    fun setShowDateInStatusBar(enabled: Boolean) {
        settingsRepository.update { it.copy(showDateInStatusBar = enabled) }
    }

    fun setShowTimeInStatusBar(enabled: Boolean) {
        settingsRepository.update { it.copy(showTimeInStatusBar = enabled) }
    }

    fun setAutoHideStatusBar(enabled: Boolean) {
        settingsRepository.update { it.copy(autoHideStatusBar = enabled) }
    }

    fun setDateTimeFormat(dateFormat: String, timeFormat: String) {
        settingsRepository.update { it.copy(dateFormat = dateFormat, timeFormat = timeFormat) }
    }

    fun setBackButtonAction(action: BackButtonAction) {
        settingsRepository.update { it.copy(backButtonAction = action) }
    }

    fun setGradient(gradient: LauncherGradient) {
        viewModelScope.launch { wallpaperRepository.setGradient(gradient) }
    }

    fun pickWallpaper(onReady: () -> Unit) {
        if (!appPlatformService.hasImagePicker()) {
            _uiState.update { it.copy(settingsMessage = "no_file_explorer") }
            return
        }
        onReady()
    }

    fun importWallpaper(uri: Uri) {
        viewModelScope.launch {
            runCatching { wallpaperRepository.setWallpaperFromUri(uri) }
                .onFailure {
                    _uiState.update { state -> state.copy(settingsMessage = "import_failed") }
                }
        }
    }

    fun clearSettingsMessage() {
        _uiState.update { it.copy(settingsMessage = null) }
    }
}
