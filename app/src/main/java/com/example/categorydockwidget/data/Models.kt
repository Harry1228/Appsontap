package com.example.categorydockwidget.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import android.util.Xml
import androidx.datastore.preferences.core.stringPreferencesKey
import org.xmlpull.v1.XmlPullParser
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
    private val iconCache = LruCache<String, Bitmap>(80)
    private val intentCache = HashMap<String, Intent?>()
    private var iconPackMappingCache: Pair<String, Map<String, String>>? = null

    fun clearCache(context: Context) {
        iconCache.evictAll()
        iconPackMappingCache = null
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

    // Parses appfilter.xml from the icon pack (assets or res/xml)
    private fun getIconPackMap(context: Context, iconPackPackage: String): Map<String, String> {
        if (iconPackMappingCache?.first == iconPackPackage) {
            return iconPackMappingCache!!.second
        }

        val map = mutableMapOf<String, String>()
        try {
            val packContext = context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
            val packRes = context.packageManager.getResourcesForApplication(iconPackPackage)

            var parser: XmlPullParser? = null
            try {
                val inputStream = packContext.assets.open("appfilter.xml")
                parser = Xml.newPullParser()
                parser.setInput(inputStream, "utf-8")
            } catch (_: Exception) {
                val resId = packRes.getIdentifier("appfilter", "xml", iconPackPackage)
                if (resId != 0) {
                    parser = packRes.getXml(resId)
                }
            }

            if (parser != null) {
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                            val cleaned = component.replace("ComponentInfo{", "").replace("}", "")
                            map[cleaned] = drawable
                            val slashIdx = cleaned.indexOf('/')
                            if (slashIdx != -1) {
                                val pkgOnly = cleaned.substring(0, slashIdx)
                                map.putIfAbsent(pkgOnly, drawable)
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (_: Exception) {}

        iconPackMappingCache = Pair(iconPackPackage, map)
        return map
    }

    private fun getIconFromPack(context: Context, iconPackPackage: String, appPackage: String): Bitmap? {
        if (iconPackPackage.isBlank() || iconPackPackage == "none") return null
        return try {
            val map = getIconPackMap(context, iconPackPackage)
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(appPackage)
            val comp = launchIntent?.component

            var drawableName: String? = null
            if (comp != null) {
                val fullComp = comp.flattenToString()
                val compInfo = "${comp.packageName}/${comp.className}"
                drawableName = map[fullComp] ?: map[compInfo] ?: map[appPackage]
            }
            if (drawableName == null) {
                drawableName = map[appPackage]
            }
            if (drawableName == null) {
                drawableName = appPackage.replace('.', '_').lowercase()
            }

            val packRes = pm.getResourcesForApplication(iconPackPackage)
            var resId = packRes.getIdentifier(drawableName, "drawable", iconPackPackage)
            if (resId == 0) {
                val fallback = appPackage.substringAfterLast('.').lowercase()
                resId = packRes.getIdentifier(fallback, "drawable", iconPackPackage)
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

    private fun convertToMonochrome(bitmap: Bitmap, isWhite: Boolean): Bitmap {
        val size = bitmap.width
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Check corner transparency
        val c1 = Color.alpha(bitmap.getPixel(2, 2))
        val c2 = Color.alpha(bitmap.getPixel(size - 3, 2))
        val c3 = Color.alpha(bitmap.getPixel(2, size - 3))
        val c4 = Color.alpha(bitmap.getPixel(size - 3, size - 3))
        val hasTransparentCorners = (c1 < 40 && c2 < 40 && c3 < 40 && c4 < 40)

        if (hasTransparentCorners) {
            // Cutout/glyph icon: tint non-transparent pixels directly
            val tintColor = if (isWhite) Color.WHITE else Color.parseColor("#151518")
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
        } else {
            // Opaque/full-bleed icon: use high-contrast grayscale to prevent solid white blocks
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            if (isWhite) {
                val contrast = 1.35f
                val brightness = 40f
                colorMatrix.postConcat(ColorMatrix(floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )))
            } else {
                val contrast = 1.35f
                val brightness = -40f
                colorMatrix.postConcat(ColorMatrix(floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
        }
        return result
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
                    var targetDrawable: Drawable? = null
                    if (drawable is AdaptiveIconDrawable) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            targetDrawable = drawable.monochrome
                        }
                        if (targetDrawable == null) {
                            targetDrawable = drawable.foreground
                        }
                    } else {
                        targetDrawable = drawable
                    }

                    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    targetDrawable?.setBounds(0, 0, size, size)
                    targetDrawable?.draw(canvas)
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
                "white" -> convertToMonochrome(baseBitmap, isWhite = true)
                "black" -> convertToMonochrome(baseBitmap, isWhite = false)
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
