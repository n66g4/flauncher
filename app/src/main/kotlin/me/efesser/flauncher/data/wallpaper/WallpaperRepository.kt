package me.efesser.flauncher.data.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import me.efesser.flauncher.data.settings.SettingsRepository
import me.efesser.flauncher.domain.model.LauncherGradient
import me.efesser.flauncher.domain.model.LauncherGradients
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class WallpaperState(
    val gradient: LauncherGradient = LauncherGradients.greatWhale,
    val wallpaperBitmap: Bitmap? = null,
)

@Singleton
class WallpaperRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val wallpaperFile = File(context.filesDir, "wallpaper")

    private val _state = MutableStateFlow(WallpaperState())
    val state: StateFlow<WallpaperState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val gradient = LauncherGradients.findByUuid(settingsRepository.settings.value.gradientUuid)
        val bitmap = if (wallpaperFile.exists()) {
            BitmapFactory.decodeFile(wallpaperFile.absolutePath)
        } else {
            null
        }
        _state.value = WallpaperState(gradient = gradient, wallpaperBitmap = bitmap)
    }

    suspend fun setGradient(gradient: LauncherGradient) = withContext(Dispatchers.IO) {
        if (wallpaperFile.exists()) {
            wallpaperFile.delete()
        }
        settingsRepository.update { it.copy(gradientUuid = gradient.uuid) }
        _state.value = WallpaperState(gradient = gradient, wallpaperBitmap = null)
    }

    suspend fun setWallpaperFromUri(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            wallpaperFile.outputStream().use { output -> input.copyTo(output) }
        }
        val bitmap = BitmapFactory.decodeFile(wallpaperFile.absolutePath)
        _state.value = _state.value.copy(wallpaperBitmap = bitmap)
    }
}
