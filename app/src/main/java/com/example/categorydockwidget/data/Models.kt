package com.example.categorydockwidget.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.FileOutputStream

data class AppModel(
    val packageName: String,
    val appName: String
)

data class Category(
    val id: String,
    val name: String,
    val packageNames: List<String> = emptyList(),
    val icon: String? = ""
) {
    val displayBadge: String
        get() = if (!icon.isNullOrBlank()) icon else name.take(2).uppercase()
}

object WidgetKeys {
    val SELECTED_CATEGORY_ID = stringPreferencesKey("selected_category_id")
    val CATEGORIES_JSON = stringPreferencesKey("categories_json")

    // Empty list by default — user creates all categories
    val DEFAULT_CATEGORIES = emptyList<Category>()
}

object AppIconHelper {
    private val iconCache = LruCache<String, Bitmap>(50)
    private val intentCache = HashMap<String, Intent?>()

    fun getLaunchIntent(context: Context, packageName: String): Intent? {
        return intentCache.getOrPut(packageName) {
            context.packageManager.getLaunchIntentForPackage(packageName)
        }
    }

    fun getAppBitmap(context: Context, packageName: String): Bitmap? {
        iconCache.get(packageName)?.let { return it }

        val iconDir = File(context.filesDir, "cached_icons")
        val iconFile = File(iconDir, "$packageName.png")

        if (iconFile.exists()) {
            try {
                val diskBitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (diskBitmap != null) {
                    iconCache.put(packageName, diskBitmap)
                    return diskBitmap
                }
            } catch (_: Exception) {}
        }

        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val size = 96
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)

            if (!iconDir.exists()) iconDir.mkdirs()
            FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            iconCache.put(packageName, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun prewarmIcons(context: Context, categories: List<Category>) {
        Thread {
            for (cat in categories) {
                for (pkg in cat.packageNames) {
                    getAppBitmap(context, pkg)
                }
            }
        }.start()
    }
}
