package me.efesser.flauncher.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.efesser.flauncher.R
import me.efesser.flauncher.domain.model.LauncherSection
import me.efesser.flauncher.domain.model.NetworkType
import me.efesser.flauncher.ui.settings.SettingsActivity
import me.efesser.flauncher.ui.settings.SettingsPageFragment
import me.efesser.flauncher.ui.util.WallpaperBackgroundHelper

@AndroidEntryPoint
class LauncherFragment : Fragment() {

    private val viewModel: LauncherViewModel by activityViewModels()

    private lateinit var wallpaperGradient: View
    private lateinit var wallpaperImage: ImageView
    private lateinit var statusBar: View
    private lateinit var statusTime: TextView
    private lateinit var statusNetwork: TextView
    private lateinit var sectionsList: RecyclerView
    private lateinit var loadingText: TextView
    private lateinit var settingsOverlay: FrameLayout

    private lateinit var sectionsAdapter: LauncherSectionsAdapter
    private var adapterShowTitles = true
    private var settingsFragmentAttached = false
    private var appInfoShowing = false
    private var settingsActivityLaunched = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_launcher, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        wallpaperGradient = view.findViewById(R.id.wallpaper_gradient)
        wallpaperImage = view.findViewById(R.id.wallpaper_image)
        statusBar = view.findViewById(R.id.status_bar)
        statusTime = view.findViewById(R.id.status_time)
        statusNetwork = view.findViewById(R.id.status_network)
        sectionsList = view.findViewById(R.id.sections_list)
        loadingText = view.findViewById(R.id.loading_text)
        settingsOverlay = view.findViewById(R.id.settings_overlay)

        val favoritesName = getString(R.string.category_favorites)
        sectionsAdapter = createSectionsAdapter(favoritesName, showTitles = true)
        sectionsList.layoutManager = LinearLayoutManager(requireContext())
        sectionsList.adapter = sectionsAdapter

        view.findViewById<Button>(R.id.btn_settings).setOnClickListener { viewModel.openSettings() }

        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_MENU) {
                if (viewModel.uiState.value.showSettings) {
                    viewModel.closeSettings()
                } else {
                    viewModel.openSettings()
                }
                true
            } else {
                false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state, favoritesName) }
            }
        }
    }

    private fun createSectionsAdapter(
        favoritesName: String,
        showTitles: Boolean,
    ): LauncherSectionsAdapter =
        LauncherSectionsAdapter(
            favoritesName = favoritesName,
            showCategoryTitles = showTitles,
            onAppClick = { item, _ -> viewModel.launchApp(item.app) },
            onAppLongClick = { item, category -> viewModel.showAppInfo(item, category) },
        )

    private fun render(state: LauncherUiState, favoritesName: String) {
        WallpaperBackgroundHelper.applyGradient(wallpaperGradient, state.wallpaper.gradient)
        WallpaperBackgroundHelper.applyWallpaperImage(wallpaperImage, state.wallpaper)

        statusBar.visibility = if (state.settings.autoHideStatusBar) View.GONE else View.VISIBLE
        statusTime.text = buildStatusTime(state)
        statusNetwork.text = networkLabel(state)

        val categories = state.sections.filterIsInstance<LauncherSection.CategorySection>().map { it.category }
        val hasApps = categories.any { it.apps.isNotEmpty() }

        when {
            state.isLoading -> {
                loadingText.visibility = View.VISIBLE
                loadingText.setText(R.string.loading_apps)
                sectionsList.visibility = View.GONE
            }
            !hasApps -> {
                loadingText.visibility = View.VISIBLE
                loadingText.text = state.errorMessage ?: getString(R.string.empty_launcher)
                sectionsList.visibility = View.GONE
            }
            else -> {
                loadingText.visibility = View.GONE
                sectionsList.visibility = View.VISIBLE
                if (adapterShowTitles != state.settings.showCategoryTitles) {
                    adapterShowTitles = state.settings.showCategoryTitles
                    sectionsAdapter = createSectionsAdapter(favoritesName, adapterShowTitles)
                    sectionsList.adapter = sectionsAdapter
                }
                sectionsAdapter.submitSections(state.sections)
            }
        }

        updateSettingsOverlay(state)
        updateAppInfoDialog(state)
        maybeLaunchSettingsActivity(state)
    }

    private fun buildStatusTime(state: LauncherUiState): String =
        buildString {
            if (state.settings.showTimeInStatusBar) {
                append(state.dateTimeText.split("  ").lastOrNull().orEmpty())
            } else if (state.settings.showDateInStatusBar) {
                append(state.dateTimeText)
            }
        }.trim()

    private fun networkLabel(state: LauncherUiState): String {
        val type = when (state.networkState.networkType) {
            NetworkType.Wifi -> "WiFi"
            NetworkType.Cellular -> "4G"
            NetworkType.Wired -> "LAN"
            NetworkType.Vpn -> "VPN"
            NetworkType.Unknown -> "—"
        }
        return if (state.networkState.hasInternet) type else "$type ✕"
    }

    private fun updateSettingsOverlay(state: LauncherUiState) {
        val useOverlay = state.showSettings && !useSettingsActivity()
        settingsOverlay.visibility = if (useOverlay) View.VISIBLE else View.GONE
        if (useOverlay && !settingsFragmentAttached) {
            childFragmentManager.commit {
                replace(R.id.settings_container, SettingsPageFragment())
            }
            settingsFragmentAttached = true
        }
        if (!state.showSettings && settingsFragmentAttached) {
            childFragmentManager.findFragmentById(R.id.settings_container)?.let { fragment ->
                childFragmentManager.commit { remove(fragment) }
            }
            settingsFragmentAttached = false
        }
    }

    private fun maybeLaunchSettingsActivity(state: LauncherUiState) {
        if (!useSettingsActivity()) return
        if (state.showSettings && !settingsActivityLaunched) {
            settingsActivityLaunched = true
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        if (!state.showSettings) {
            settingsActivityLaunched = false
        }
    }

    private fun useSettingsActivity(): Boolean =
        arguments?.getBoolean(ARG_USE_SETTINGS_ACTIVITY, false) == true

    private fun updateAppInfoDialog(state: LauncherUiState) {
        val showing = state.selectedApp != null
        if (showing && !appInfoShowing) {
            appInfoShowing = true
            AppInfoDialogFragment().show(childFragmentManager, AppInfoDialogFragment.TAG)
        } else if (!showing && appInfoShowing) {
            appInfoShowing = false
            (childFragmentManager.findFragmentByTag(AppInfoDialogFragment.TAG) as? AppInfoDialogFragment)
                ?.dismissAllowingStateLoss()
        }
    }

    companion object {
        private const val ARG_USE_SETTINGS_ACTIVITY = "use_settings_activity"

        fun newInstance(useSettingsActivity: Boolean = false): LauncherFragment =
            LauncherFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_USE_SETTINGS_ACTIVITY, useSettingsActivity)
                }
            }
    }
}
