package me.efesser.flauncher

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import me.efesser.flauncher.ui.main.AppInfoDialogFragment
import me.efesser.flauncher.ui.main.LauncherFragment
import me.efesser.flauncher.ui.main.LauncherViewModel
import androidx.activity.viewModels

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), AppInfoDialogFragment.Host {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.main_container, LauncherFragment.newInstance())
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.handleLauncherBack()
                }
            },
        )
    }

    override fun appInfoViewModel(): LauncherViewModel = viewModel
}
