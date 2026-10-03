package me.efesser.flauncher.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.HorizontalGridView
import androidx.recyclerview.widget.RecyclerView
import me.efesser.flauncher.R
import me.efesser.flauncher.domain.model.CategoryLayoutType
import me.efesser.flauncher.domain.model.LauncherAppItem
import me.efesser.flauncher.domain.model.LauncherCategory
import me.efesser.flauncher.domain.model.LauncherSection

sealed class LauncherListItem {
    data class Spacer(val heightDp: Int) : LauncherListItem()
    data class CategoryTitle(val name: String) : LauncherListItem()
    data class AppsRow(val category: LauncherCategory, val apps: List<LauncherAppItem>) : LauncherListItem()
}

class LauncherSectionsAdapter(
    private val favoritesName: String,
    private val showCategoryTitles: Boolean,
    private val onAppClick: (LauncherAppItem, LauncherCategory) -> Unit,
    private val onAppLongClick: (LauncherAppItem, LauncherCategory) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<LauncherListItem>()

    fun submitSections(sections: List<LauncherSection>) {
        items.clear()
        sections.forEach { section ->
            when (section) {
                is LauncherSection.SpacerSection -> items.add(LauncherListItem.Spacer(section.height))
                is LauncherSection.CategorySection -> {
                    val category = section.category
                    if (category.apps.isEmpty()) return@forEach
                    if (showCategoryTitles) {
                        items.add(LauncherListItem.CategoryTitle(category.name))
                    }
                    val columns = category.columnsCount.coerceIn(5, 7)
                    val favoritesRow = category.name == favoritesName &&
                        category.layoutType == CategoryLayoutType.Row
                    if (category.layoutType == CategoryLayoutType.Row || favoritesRow) {
                        items.add(LauncherListItem.AppsRow(category, category.apps))
                    } else {
                        category.apps.chunked(columns).forEach { chunk ->
                            items.add(LauncherListItem.AppsRow(category, chunk))
                        }
                    }
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is LauncherListItem.Spacer -> VIEW_SPACER
        is LauncherListItem.CategoryTitle -> VIEW_TITLE
        is LauncherListItem.AppsRow -> VIEW_APPS_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_SPACER -> SpacerViewHolder(View(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            })
            VIEW_TITLE -> TitleViewHolder(
                inflater.inflate(R.layout.item_category_row, parent, false),
            )
            else -> AppsRowViewHolder(
                inflater.inflate(R.layout.item_category_row, parent, false),
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is LauncherListItem.Spacer -> (holder as SpacerViewHolder).bind(item.heightDp)
            is LauncherListItem.CategoryTitle -> (holder as TitleViewHolder).bind(item.name)
            is LauncherListItem.AppsRow -> (holder as AppsRowViewHolder).bind(
                category = item.category,
                apps = item.apps,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
            )
        }
    }

    override fun getItemCount(): Int = items.size

    private class SpacerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(heightDp: Int) {
            val density = itemView.resources.displayMetrics.density
            itemView.layoutParams.height = (heightDp * density).toInt()
        }
    }

    private class TitleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.category_title)
        private val grid: HorizontalGridView = view.findViewById(R.id.apps_row)

        init {
            grid.visibility = View.GONE
        }

        fun bind(name: String) {
            title.text = name
            title.visibility = View.VISIBLE
        }
    }

    private class AppsRowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.category_title)
        private val grid: HorizontalGridView = view.findViewById(R.id.apps_row)

        fun bind(
            category: LauncherCategory,
            apps: List<LauncherAppItem>,
            onAppClick: (LauncherAppItem, LauncherCategory) -> Unit,
            onAppLongClick: (LauncherAppItem, LauncherCategory) -> Unit,
        ) {
            title.visibility = View.GONE
            grid.visibility = View.VISIBLE
            grid.adapter = AppTileAdapter(
                apps,
                onAppClick = { onAppClick(it, category) },
                onAppLongClick = { onAppLongClick(it, category) },
            )
        }
    }

    private class AppTileAdapter(
        private val apps: List<LauncherAppItem>,
        private val onAppClick: (LauncherAppItem) -> Unit,
        private val onAppLongClick: (LauncherAppItem) -> Unit,
    ) : RecyclerView.Adapter<AppTileAdapter.TileHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_tile, parent, false)
            return TileHolder(view)
        }

        override fun onBindViewHolder(holder: TileHolder, position: Int) {
            holder.bind(apps[position], onAppClick, onAppLongClick)
        }

        override fun getItemCount(): Int = apps.size

        class TileHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon: ImageView = view.findViewById(R.id.app_icon)
            private val name: TextView = view.findViewById(R.id.app_name)

            fun bind(
                item: LauncherAppItem,
                onAppClick: (LauncherAppItem) -> Unit,
                onAppLongClick: (LauncherAppItem) -> Unit,
            ) {
                icon.setImageDrawable(item.icon)
                name.text = item.app.name
                itemView.setOnClickListener { onAppClick(item) }
                itemView.setOnLongClickListener {
                    onAppLongClick(item)
                    true
                }
            }
        }
    }

    companion object {
        private const val VIEW_SPACER = 0
        private const val VIEW_TITLE = 1
        private const val VIEW_APPS_ROW = 2
    }
}
