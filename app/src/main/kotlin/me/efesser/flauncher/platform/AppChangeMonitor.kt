package me.efesser.flauncher.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.efesser.flauncher.domain.model.AppChangeEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppChangeMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPlatformService: AppPlatformService,
) {
    private val _events = MutableSharedFlow<AppChangeEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<AppChangeEvent> = _events.asSharedFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            _events.tryEmit(AppChangeEvent.Removed(packageName))
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) {
            emitAdded(packageName)
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            emitChanged(packageName)
        }

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            val apps = packageNames.mapNotNull { appPlatformService.getApplication(it) }
            if (apps.isNotEmpty()) {
                _events.tryEmit(AppChangeEvent.Available(apps))
            }
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = Unit
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val packageName = intent?.data?.schemeSpecificPart ?: return
            when (intent.action) {
                Intent.ACTION_PACKAGE_REMOVED -> {
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        _events.tryEmit(AppChangeEvent.Removed(packageName))
                    }
                }
                Intent.ACTION_PACKAGE_ADDED -> {
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        emitAdded(packageName)
                    }
                }
                Intent.ACTION_PACKAGE_CHANGED -> emitChanged(packageName)
            }
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            launcherApps.registerCallback(callback)
        } else {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            context.registerReceiver(packageReceiver, filter)
        }
    }

    private fun emitAdded(packageName: String) {
        appPlatformService.getApplication(packageName)?.let { app ->
            _events.tryEmit(AppChangeEvent.Added(app))
        }
    }

    private fun emitChanged(packageName: String) {
        appPlatformService.getApplication(packageName)?.let { app ->
            _events.tryEmit(AppChangeEvent.Changed(app))
        }
    }
}
