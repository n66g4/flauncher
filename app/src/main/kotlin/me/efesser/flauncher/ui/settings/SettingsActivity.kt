package me.efesser.flauncher.ui.settings

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.efesser.flauncher.R
import me.efesser.flauncher.ui.main.AppInfoDialogFragment
import me.efesser.flauncher.ui.main.LauncherViewModel

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity(), AppInfoDialogFragment.Host {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (!viewModel.uiState.value.showSettings) {
            viewModel.openSettings()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.settings_root, SettingsPageFragment())
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!viewModel.settingsBack()) {
                        finish()
                    }
                }
            },
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (!state.showSettings && !isFinishing) {
                        finish()
                    }
                }
            }
        }
    }

    override fun appInfoViewModel(): LauncherViewModel = viewModel
}
