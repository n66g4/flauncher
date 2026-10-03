package me.efesser.flauncher.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import me.efesser.flauncher.domain.model.LauncherApp
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPlatformService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val packageManager = context.packageManager

    suspend fun loadInstalledApps(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val tvAppsDeferred = async { queryActivities(sideloaded = false) }
        val nonTvAppsDeferred = async { queryActivities(sideloaded = true) }
        val tvApps = tvAppsDeferred.await()
        val nonTvApps = nonTvAppsDeferred.await()

        val tvPackages = tvApps.map { it.activityInfo.packageName }.toSet()
        val merged = tvApps.map { buildLauncherApp(it, sideloaded = false) }.toMutableList()

        nonTvApps
            .filter { it.activityInfo.packageName !in tvPackages }
            .map { buildLauncherApp(it, sideloaded = true) }
            .let { merged.addAll(it) }

        if (merged.none { it.packageName.contains("settings") }) {
            findSettingsApp()?.let { merged.add(it) }
        }

        merged.sortedBy { it.name.lowercase() }
    }

    fun loadIcon(packageName: String): Drawable? = runCatching {
        packageManager.getApplicationInfo(packageName, 0).loadIcon(packageManager)
    }.getOrNull()

    fun isSideloaded(packageName: String): Boolean {
        val leanbackIntent = leanbackLaunchIntent(packageName)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        return leanbackIntent == null && launchIntent != null
    }

    fun launchApp(app: LauncherApp): Boolean {
        val intent = app.action?.let { Intent(it) }
            ?: leanbackLaunchIntent(app.packageName)
            ?: packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return false

        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun getApplication(packageName: String): LauncherApp? {
        val leanbackIntent = leanbackLaunchIntent(packageName)
        val launchIntent = leanbackIntent ?: packageManager.getLaunchIntentForPackage(packageName)
        val activityInfo = launchIntent?.resolveActivityInfo(packageManager, 0) ?: return null
        val sideloaded = leanbackIntent == null && launchIntent != null
        return buildLauncherAppFromActivity(activityInfo, sideloaded)
    }

    fun openAppInfo(packageName: String): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openSystemSettings(): Boolean = launchActivityFromAction(Settings.ACTION_SETTINGS)

    fun startScreensaver(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun hasImagePicker(): Boolean {
        val intent = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        return packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }

    fun uninstallApp(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_DELETE)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun leanbackLaunchIntent(packageName: String): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null
        }
        return packageManager.getLeanbackLaunchIntentForPackage(packageName)
    }

    private fun queryActivities(sideloaded: Boolean): List<ResolveInfo> {
        val category = when {
            sideloaded -> Intent.CATEGORY_LAUNCHER
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> Intent.CATEGORY_LEANBACK_LAUNCHER
            else -> Intent.CATEGORY_LAUNCHER
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL
        } else {
            @Suppress("DEPRECATION")
            0
        }
        return packageManager.queryIntentActivities(intent, flags)
    }

    private fun buildLauncherApp(info: ResolveInfo, sideloaded: Boolean): LauncherApp {
        return buildLauncherAppFromActivity(info.activityInfo, sideloaded)
    }

    private fun buildLauncherAppFromActivity(activityInfo: ActivityInfo, sideloaded: Boolean): LauncherApp {
        val version = runCatching {
            packageManager.getPackageInfo(activityInfo.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")

        return LauncherApp(
            packageName = activityInfo.packageName,
            name = activityInfo.loadLabel(packageManager).toString(),
            version = version,
            sideloaded = sideloaded,
        )
    }

    private fun launchActivityFromAction(action: String): Boolean {
        return runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    private fun findSettingsApp(): LauncherApp? {
        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
        val activityInfo: ActivityInfo = settingsIntent.resolveActivityInfo(packageManager, 0)
            ?: return null

        return LauncherApp(
            packageName = activityInfo.packageName,
            name = activityInfo.loadLabel(packageManager).toString(),
            version = "",
            sideloaded = false,
            action = Settings.ACTION_SETTINGS,
        )
    }
}
