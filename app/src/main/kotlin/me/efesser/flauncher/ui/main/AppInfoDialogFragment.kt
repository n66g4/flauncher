package me.efesser.flauncher.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import me.efesser.flauncher.R
import me.efesser.flauncher.domain.model.LauncherSection

class AppInfoDialogFragment : DialogFragment() {

    interface Host {
        fun appInfoViewModel(): LauncherViewModel
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val widthPx = (380 * resources.displayMetrics.density).toInt()
        return Dialog(requireContext(), R.style.Theme_FLauncher).apply {
            window?.apply {
                setGravity(Gravity.END)
                setLayout(widthPx, ViewGroup.LayoutParams.MATCH_PARENT)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.6f)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_app_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewModel = (requireActivity() as Host).appInfoViewModel()
        val icon = view.findViewById<ImageView>(R.id.app_info_icon)
        val name = view.findViewById<TextView>(R.id.app_info_name)
        val pkg = view.findViewById<TextView>(R.id.app_info_package)
        val version = view.findViewById<TextView>(R.id.app_info_version)
        val actions = view.findViewById<LinearLayout>(R.id.app_info_actions)
        val addCategoryContainer = view.findViewById<LinearLayout>(R.id.add_category_container)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val selected = state.selectedApp ?: run {
                        dismissAllowingStateLoss()
                        return@collect
                    }
                    val category = state.sections
                        .filterIsInstance<LauncherSection.CategorySection>()
                        .map { it.category }
                        .find { it.id == selected.categoryId }

                    icon.setImageDrawable(selected.item.icon)
                    name.text = selected.item.app.name
                    pkg.text = selected.item.app.packageName
                    version.text = "v${selected.item.app.version}"

                    actions.removeAllViews()
                    addCategoryContainer.removeAllViews()

                    if (state.appInfoShowAddToCategory) {
                        addCategoryContainer.visibility = View.VISIBLE
                        addCategoryContainer.addView(
                            sectionTitle(getString(R.string.action_add_to_category)),
                        )
                        if (state.addToCategoryTargets.isEmpty()) {
                            addCategoryContainer.addView(
                                textLabel(getString(R.string.no_categories_available)),
                            )
                        } else {
                            state.addToCategoryTargets.forEach { cat ->
                                addCategoryContainer.addView(
                                    actionButton(cat.name) { viewModel.addSelectedAppToCategory(cat.id) },
                                )
                            }
                        }
                        addCategoryContainer.addView(
                            actionButton(getString(R.string.action_back)) {
                                viewModel.cancelAddToCategoryPicker()
                            },
                        )
                        return@collect
                    }

                    addCategoryContainer.visibility = View.GONE
                    actions.addView(actionButton(getString(R.string.action_open)) { viewModel.openSelectedApp() })
                    if (selected.manualSort) {
                        val canLeft = selected.appIndex > 0
                        val canRight = category != null && selected.appIndex < category.apps.lastIndex
                        actions.addView(
                            actionButton(getString(R.string.action_move_left), enabled = canLeft) {
                                viewModel.moveSelectedAppInCategory(-1)
                            },
                        )
                        actions.addView(
                            actionButton(getString(R.string.action_move_right), enabled = canRight) {
                                viewModel.moveSelectedAppInCategory(1)
                            },
                        )
                    }
                    val hideLabel = if (selected.item.app.hidden) {
                        getString(R.string.action_show)
                    } else {
                        getString(R.string.action_hide)
                    }
                    actions.addView(actionButton(hideLabel) { viewModel.toggleSelectedAppHidden() })
                    actions.addView(
                        actionButton(getString(R.string.action_add_to_category)) {
                            viewModel.showAddToCategoryPicker()
                        },
                    )
                    actions.addView(
                        actionButton(
                            getString(R.string.action_remove_from_category, selected.categoryName),
                        ) { viewModel.removeSelectedAppFromCategory() },
                    )
                    actions.addView(
                        actionButton(getString(R.string.action_app_info)) { viewModel.openSelectedAppInfo() },
                    )
                    actions.addView(
                        actionButton(getString(R.string.action_uninstall)) { viewModel.uninstallSelectedApp() },
                    )
                    actions.addView(actionButton(getString(R.string.action_close)) { viewModel.dismissAppInfo() })
                }
            }
        }
    }

    private fun actionButton(label: String, enabled: Boolean = true, onClick: () -> Unit): Button {
        val margin = (8 * resources.displayMetrics.density).toInt()
        return Button(requireContext()).apply {
            text = label
            isEnabled = enabled
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = margin }
            setOnClickListener { onClick() }
        }
    }

    private fun sectionTitle(text: String): TextView =
        TextView(requireContext()).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        }

    private fun textLabel(text: String): TextView =
        TextView(requireContext()).apply {
            this.text = text
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 13f
        }

    companion object {
        const val TAG = "AppInfoDialog"
    }
}
