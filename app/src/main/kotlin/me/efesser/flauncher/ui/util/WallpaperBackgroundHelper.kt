package me.efesser.flauncher.ui.util

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import me.efesser.flauncher.data.wallpaper.WallpaperState
import me.efesser.flauncher.domain.model.GradientStyle
import me.efesser.flauncher.domain.model.LauncherGradient

object WallpaperBackgroundHelper {
    fun applyGradient(view: View, gradient: LauncherGradient) {
        view.background = gradientDrawable(gradient)
    }

    fun applyWallpaperImage(imageView: ImageView, state: WallpaperState) {
        val bitmap = state.wallpaperBitmap
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
            imageView.visibility = View.VISIBLE
        } else {
            imageView.setImageDrawable(null)
            imageView.visibility = View.GONE
        }
    }

    fun gradientDrawable(gradient: LauncherGradient): GradientDrawable {
        val colors = intArrayOf(gradient.startColor, gradient.endColor)
        return when (gradient.style) {
            GradientStyle.LinearHorizontal -> GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                colors,
            )
            GradientStyle.Radial -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                colors,
            ).apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = 800f
            }
            GradientStyle.LinearVertical -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                colors,
            )
        }
    }
}
