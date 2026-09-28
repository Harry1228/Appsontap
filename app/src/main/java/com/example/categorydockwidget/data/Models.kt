package com.example.categorydockwidget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.datastore.preferences.core.stringPreferencesKey

data class AppModel(
    val packageName: String,
    val appName: String
)

data class Category(
    val id: String,
    val name: String,
    val packageNames: List<String> = emptyList()
)

object WidgetKeys {
    val SELECTED_CATEGORY_ID = stringPreferencesKey("selected_category_id")
    val CATEGORIES_JSON = stringPreferencesKey("categories_json")

    val DEFAULT_CATEGORIES = listOf(
        Category("work", "Work", listOf("com.google.android.gm", "com.android.chrome")),
        Category("social", "Social", listOf("com.whatsapp", "org.telegram.messenger")),
        Category("media", "Media", listOf("com.google.android.youtube", "com.spotify.music"))
    )
}

object AppIconHelper {
    fun getAppBitmap(context: Context, packageName: String): Bitmap? {
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val width = drawable.intrinsicWidth.coerceAtLeast(64)
            val height = drawable.intrinsicHeight.coerceAtLeast(64)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
