package me.efesser.flauncher.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.efesser.flauncher.R
import me.efesser.flauncher.domain.model.BackButtonAction
import me.efesser.flauncher.domain.model.CategoryLayoutType
import me.efesser.flauncher.domain.model.LauncherGradients
import me.efesser.flauncher.domain.model.LauncherSection
import me.efesser.flauncher.domain.model.SettingsPage
import me.efesser.flauncher.ui.main.LauncherViewModel
import me.efesser.flauncher.ui.util.WallpaperBackgroundHelper

@AndroidEntryPoint
class SettingsPageFragment : Fragment() {

    private val viewModel: LauncherViewModel by activityViewModels()

    private lateinit var pageTitle: TextView
    private lateinit var backButton: Button
    private lateinit var messageView: TextView
    private lateinit var content: LinearLayout

    private var categoryNameField: EditText? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { viewModel.importWallpaper(it) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        pageTitle = view.findViewById(R.id.settings_page_title)
        backButton = view.findViewById(R.id.settings_back)
        messageView = view.findViewById(R.id.settings_message)
        content = view.findViewById(R.id.settings_content)

        backButton.setOnClickListener { viewModel.settingsBack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state.settingsPage, state) }
            }
        }
    }

    private fun render(page: SettingsPage, state: me.efesser.flauncher.ui.main.LauncherUiState) {
        pageTitle.setText(pageTitleRes(page))
        backButton.visibility = if (page == SettingsPage.Main) View.GONE else View.VISIBLE

        when (state.settingsMessage) {
            "no_file_explorer" -> {
                messageView.visibility = View.VISIBLE
                messageView.setText(R.string.settings_no_file_explorer)
            }
            "import_failed" -> {
                messageView.visibility = View.VISIBLE
                messageView.text = getString(R.string.settings_no_file_explorer)
            }
            else -> messageView.visibility = View.GONE
        }

        content.removeAllViews()
        categoryNameField = null

        when (page) {
            SettingsPage.Main -> renderMain(state)
            SettingsPage.Wallpaper -> renderWallpaper()
            SettingsPage.Gradients -> renderGradients()
            SettingsPage.StatusBar -> renderStatusBar(state)
            SettingsPage.DateTimeFormat -> renderDateTime(state)
            SettingsPage.BackButtonAction -> renderBackButton(state)
            SettingsPage.Sections -> renderSections(state)
            SettingsPage.CategoryDetail -> renderCategoryDetail(state)
        }
    }

    private fun renderMain(state: me.efesser.flauncher.ui.main.LauncherUiState) {
        addNavButton(getString(R.string.settings_wallpaper)) { viewModel.navigateSettings(SettingsPage.Wallpaper) }
        addNavButton(getString(R.string.settings_status_bar)) { viewModel.navigateSettings(SettingsPage.StatusBar) }
        addNavButton(getString(R.string.settings_system)) { viewModel.openSystemSettings() }
        addNavButton(getString(R.string.settings_date_time_format)) {
            viewModel.navigateSettings(SettingsPage.DateTimeFormat)
        }
        addNavButton(getString(R.string.settings_back_button)) {
            viewModel.navigateSettings(SettingsPage.BackButtonAction)
        }
        addNavButton(getString(R.string.settings_sections)) { viewModel.navigateSettings(SettingsPage.Sections) }
        addSwitch(getString(R.string.settings_show_category_titles), state.settings.showCategoryTitles) {
            viewModel.setShowCategoryTitles(it)
        }
        addSwitch(getString(R.string.settings_show_watch_next), state.settings.showWatchNext) {
            viewModel.setShowWatchNext(it)
        }
        addNavButton(getString(R.string.action_close)) { viewModel.closeSettings() }
    }

    private fun renderWallpaper() {
        addNavButton(getString(R.string.settings_gradient)) { viewModel.navigateSettings(SettingsPage.Gradients) }
        addNavButton(getString(R.string.settings_picture)) {
            viewModel.pickWallpaper {
                pickImage.launch(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"))
            }
        }
    }

    private fun renderGradients() {
        LauncherGradients.all.forEach { gradient ->
            val preview = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(72),
                ).apply { bottomMargin = dp(4) }
                background = WallpaperBackgroundHelper.gradientDrawable(gradient)
            }
            content.addView(preview)
            addNavButton(gradient.name) { viewModel.setGradient(gradient) }
        }
    }

    private fun renderStatusBar(state: me.efesser.flauncher.ui.main.LauncherUiState) {
        addSwitch(getString(R.string.settings_auto_hide_status_bar), state.settings.autoHideStatusBar) {
            viewModel.setAutoHideStatusBar(it)
        }
        addSwitch(getString(R.string.settings_show_date), state.settings.showDateInStatusBar) {
            viewModel.setShowDateInStatusBar(it)
        }
        addSwitch(getString(R.string.settings_show_time), state.settings.showTimeInStatusBar) {
            viewModel.setShowTimeInStatusBar(it)
        }
    }

    private fun renderDateTime(state: me.efesser.flauncher.ui.main.LauncherUiState) {
        val presets = listOf(
            "EEEE d" to "H:mm",
            "yyyy-MM-dd" to "HH:mm",
            "MM/dd" to "h:mm a",
        )
        presets.forEach { (date, time) ->
            addNavButton("$date  ·  $time") { viewModel.setDateTimeFormat(date, time) }
        }
        content.addView(
            textLabel(
                "${getString(R.string.settings_current)}: ${state.settings.dateFormat} / ${state.settings.timeFormat}",
            ),
        )
    }

    private fun renderBackButton(state: me.efesser.flauncher.ui.main.LauncherUiState) {
        addNavButton(getString(R.string.settings_back_nothing)) {
            viewModel.setBackButtonAction(BackButtonAction.Nothing)
        }
        addNavButton(getString(R.string.settings_back_screensaver)) {
            viewModel.setBackButtonAction(BackButtonAction.Screensaver)
        }
        content.addView(
            textLabel("${getString(R.string.settings_current)}: ${state.settings.backButtonAction.name}"),
        )
    }

    private fun renderSections(state: me.efesser.flauncher.ui.main.LauncherUiState) {
        state.sections.forEachIndexed { index, section ->
            val title = when (section) {
                is LauncherSection.CategorySection -> section.category.name
                is LauncherSection.SpacerSection -> getString(R.string.settings_spacer, section.height)
            }
            content.addView(textLabel(title))
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            if (section is LauncherSection.CategorySection) {
                row.addView(smallButton(getString(R.string.settings_category_detail)) {
                    viewModel.openCategoryDetail(section.category.id)
                })
            }
            row.addView(smallButton("↑") { viewModel.moveSectionUp(index) })
            row.addView(smallButton("↓") { viewModel.moveSectionDown(index) })
            row.addView(smallButton("删除") { viewModel.deleteSection(section) })
            content.addView(row)
        }
        addNavButton(getString(R.string.settings_add_category)) { viewModel.addCategory() }
        addNavButton(getString(R.string.settings_add_spacer)) { viewModel.addSpacer() }
    }

    private fun renderCategoryDetail(state: me.efesser.flauncher.ui.main.LauncherUiState) {
        val category = state.sections
            .filterIsInstance<LauncherSection.CategorySection>()
            .map { it.category }
            .find { it.id == state.settingsCategoryId }
        if (category == null) {
            content.addView(textLabel(getString(R.string.category_not_found)))
            return
        }
        val nameField = EditText(requireContext()).apply {
            setText(category.name)
            hint = getString(R.string.settings_category_name)
            setTextColor(0xFFFFFFFF.toInt())
        }
        categoryNameField = nameField
        content.addView(nameField)
        addNavButton(getString(R.string.settings_save_name)) {
            viewModel.renameCategory(category.id, nameField.text.toString())
        }
        content.addView(textLabel(getString(R.string.settings_category_sort)))
        addNavButton(getString(R.string.settings_sort_manual)) {
            viewModel.setCategorySort(category.id, manual = true)
        }
        addNavButton(getString(R.string.settings_sort_alphabetical)) {
            viewModel.setCategorySort(category.id, manual = false)
        }
        val sortLabel = if (category.manualSort) {
            getString(R.string.settings_sort_manual)
        } else {
            getString(R.string.settings_sort_alphabetical)
        }
        content.addView(textLabel("${getString(R.string.settings_current)}: $sortLabel"))

        content.addView(textLabel(getString(R.string.settings_layout_type)))
        addNavButton(getString(R.string.settings_layout_row)) {
            viewModel.setCategoryLayoutType(category.id, CategoryLayoutType.Row)
        }
        addNavButton(getString(R.string.settings_layout_grid)) {
            viewModel.setCategoryLayoutType(category.id, CategoryLayoutType.Grid)
        }
        content.addView(
            textLabel(
                "${getString(R.string.settings_grid_columns)}: ${category.columnsCount}",
            ),
        )
        (5..7).forEach { columns ->
            addNavButton("$columns") { viewModel.setCategoryColumnsCount(category.id, columns) }
        }
    }

    private fun pageTitleRes(page: SettingsPage): Int = when (page) {
        SettingsPage.Main -> R.string.settings_title
        SettingsPage.Wallpaper -> R.string.settings_wallpaper
        SettingsPage.Gradients -> R.string.settings_gradient
        SettingsPage.StatusBar -> R.string.settings_status_bar
        SettingsPage.DateTimeFormat -> R.string.settings_date_time_format
        SettingsPage.BackButtonAction -> R.string.settings_back_button
        SettingsPage.Sections -> R.string.settings_sections
        SettingsPage.CategoryDetail -> R.string.settings_category_detail
    }

    private fun addNavButton(label: String, onClick: () -> Unit) {
        content.addView(
            Button(requireContext()).apply {
                text = label
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(8) }
                setOnClickListener { onClick() }
            },
        )
    }

    private fun addSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) }
        }
        val text = TextView(requireContext()).apply {
            this.text = label
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val toggle = Switch(requireContext()).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        row.addView(text)
        row.addView(toggle)
        content.addView(row)
    }

    private fun smallButton(label: String, onClick: () -> Unit): Button =
        Button(requireContext()).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    private fun textLabel(text: String): TextView =
        TextView(requireContext()).apply {
            this.text = text
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
