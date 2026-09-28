package com.example.categorydockwidget.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
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
    private val iconCache = LruCache<String, Bitmap>(40)
    private val intentCache = HashMap<String, Intent?>()

    fun getLaunchIntent(context: Context, packageName: String): Intent? {
        return intentCache.getOrPut(packageName) {
            context.packageManager.getLaunchIntentForPackage(packageName)
        }
    }

    fun getAppBitmap(context: Context, packageName: String): Bitmap? {
        iconCache.get(packageName)?.let { return it }

        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val size = 96
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            iconCache.put(packageName, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
