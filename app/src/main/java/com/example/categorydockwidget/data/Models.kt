package com.example.categorydockwidget.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
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
    val DEFAULT_CATEGORIES = emptyList<Category>()
}

object AppIconHelper {
    private val iconCache = LruCache<String, Bitmap>(60)
    private val intentCache = HashMap<String, Intent?>()

    fun clearCache(context: Context) {
        iconCache.evictAll()
        val iconDir = File(context.filesDir, "cached_icons")
        if (iconDir.exists()) {
            iconDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun getLaunchIntent(context: Context, packageName: String): Intent? {
        return intentCache.getOrPut(packageName) {
            context.packageManager.getLaunchIntentForPackage(packageName)
        }
    }

    fun getInstalledIconPacks(context: Context): List<AppModel> {
        val pm = context.packageManager
        val themeActions = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.dlto.atom.launcher.THEME",
            "com.teslacoilsw.launcher.THEME"
        )
        val packages = mutableSetOf<String>()
        val result = mutableListOf<AppModel>()

        for (action in themeActions) {
            val infos = pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
            for (ri in infos) {
                val pkg = ri.activityInfo.packageName
                if (pkg != context.packageName && packages.add(pkg)) {
                    val label = ri.loadLabel(pm).toString()
                    result.add(AppModel(pkg, label))
                }
            }
        }
        return result.sortedBy { it.appName.lowercase() }
    }

    private fun getIconFromPack(context: Context, iconPackPackage: String, appPackage: String): Bitmap? {
        if (iconPackPackage.isBlank() || iconPackPackage == "none") return null
        return try {
            val pm = context.packageManager
            val packRes = pm.getResourcesForApplication(iconPackPackage)
            val formatted = appPackage.replace('.', '_').lowercase()
            var resId = packRes.getIdentifier(formatted, "drawable", iconPackPackage)

            if (resId == 0) {
                val launchIntent = pm.getLaunchIntentForPackage(appPackage)
                val comp = launchIntent?.component
                if (comp != null) {
                    val compName = (comp.packageName + "_" + comp.className).replace('.', '_').lowercase()
                    resId = packRes.getIdentifier(compName, "drawable", iconPackPackage)
                }
            }

            if (resId != 0) {
                val drawable = packRes.getDrawable(resId, null) ?: return null
                val size = 96
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                bitmap
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun getAppBitmap(context: Context, packageName: String): Bitmap? {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val iconStyle = prefs.getString("icon_color_style", "default") ?: "default"
        val iconPack = prefs.getString("selected_icon_pack", "none") ?: "none"
        val cacheKey = "${packageName}_${iconStyle}_${iconPack}"

        iconCache.get(cacheKey)?.let { return it }

        val iconDir = File(context.filesDir, "cached_icons")
        val iconFile = File(iconDir, "$cacheKey.png")

        if (iconFile.exists()) {
            try {
                val diskBitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (diskBitmap != null) {
                    iconCache.put(cacheKey, diskBitmap)
                    return diskBitmap
                }
            } catch (_: Exception) {}
        }

        return try {
            val size = 96
            var baseBitmap = getIconFromPack(context, iconPack, packageName)

            if (baseBitmap == null) {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)

                baseBitmap = if (iconStyle == "white" || iconStyle == "black") {
                    var targetDrawable: Drawable = drawable
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
                        drawable.monochrome?.let { targetDrawable = it }
                    } else if (drawable is AdaptiveIconDrawable) {
                        drawable.foreground?.let { targetDrawable = it }
                    }
                    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    targetDrawable.setBounds(0, 0, size, size)
                    targetDrawable.draw(canvas)
                    bmp
                } else {
                    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(canvas)
                    bmp
                }
            }

            val finalBitmap = when (iconStyle) {
                "white" -> {
                    val styled = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(styled)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                    }
                    canvas.drawBitmap(baseBitmap, 0f, 0f, paint)
                    styled
                }
                "black" -> {
                    val styled = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(styled)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        colorFilter = PorterDuffColorFilter(Color.parseColor("#151518"), PorterDuff.Mode.SRC_IN)
                    }
                    canvas.drawBitmap(baseBitmap, 0f, 0f, paint)
                    styled
                }
                else -> baseBitmap
            }

            if (!iconDir.exists()) iconDir.mkdirs()
            FileOutputStream(iconFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            iconCache.put(cacheKey, finalBitmap)
            finalBitmap
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
