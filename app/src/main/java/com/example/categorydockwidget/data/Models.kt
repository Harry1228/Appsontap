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
    val icon: String? = "" // Holds symbol, text, or "gallery:<filename>"
) {
    val displayBadge: String
        get() = if (!icon.isNullOrBlank() && !icon.startsWith("gallery:")) icon else name.take(4).uppercase()
}

object WidgetKeys {
    val SELECTED_CATEGORY_ID = stringPreferencesKey("selected_category_id")
    val CATEGORIES_JSON = stringPreferencesKey("categories_json")
    val DEFAULT_CATEGORIES = emptyList<Category>()
}

object AppIconHelper {
    private val iconCache = LruCache<String, Bitmap>(100)
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

    private fun getIconPackMap(context: Context, iconPackPackage: String): Map<String, String> {
        if (iconPackMappingCache?.first == iconPackPackage) {
            return iconPackMappingCache!!.second
        }

        val map = mutableMapOf<String, String>()
        try {
            val pm = context.packageManager
            val packRes = pm.getResourcesForApplication(iconPackPackage)
            var parser: XmlPullParser? = null

            val resId = packRes.getIdentifier("appfilter", "xml", iconPackPackage)
            if (resId != 0) {
                parser = packRes.getXml(resId)
            }

            if (parser == null) {
                try {
                    val packContext = context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
                    val inputStream = packContext.assets.open("appfilter.xml")
                    val pullParser = android.util.Xml.newPullParser()
                    pullParser.setInput(inputStream, "utf-8")
                    parser = pullParser
                } catch (_: Exception) {}
            }

            if (parser != null) {
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        var component: String? = null
                        var drawable: String? = null

                        for (i in 0 until parser.attributeCount) {
                            val attrName = parser.getAttributeName(i)
                            val attrVal = parser.getAttributeValue(i)
                            if (attrName.equals("component", ignoreCase = true)) {
                                component = attrVal
                            } else if (attrName.equals("drawable", ignoreCase = true)) {
                                drawable = attrVal
                            }
                        }

                        if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                            val cleanComp = component.replace("ComponentInfo{", "").replace("}", "")
                            map[cleanComp] = drawable
                            map[cleanComp.lowercase()] = drawable

                            val slashIdx = cleanComp.indexOf('/')
                            if (slashIdx != -1) {
                                val pkgOnly = cleanComp.substring(0, slashIdx)
                                map.putIfAbsent(pkgOnly, drawable)
                                map.putIfAbsent(pkgOnly.lowercase(), drawable)
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
                drawableName = map[fullComp] ?: map[fullComp.lowercase()] ?: map[compInfo] ?: map[compInfo.lowercase()]
            }

            if (drawableName == null) {
                drawableName = map[appPackage] ?: map[appPackage.lowercase()]
            }
            if (drawableName == null) {
                drawableName = appPackage.replace('.', '_').lowercase()
            }

            val packRes = pm.getResourcesForApplication(iconPackPackage)
            var resId = packRes.getIdentifier(drawableName, "drawable", iconPackPackage)
            if (resId == 0) {
                val shortName = appPackage.substringAfterLast('.').lowercase()
                resId = packRes.getIdentifier(shortName, "drawable", iconPackPackage)
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

        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        var opaquePixels = 0
        for (p in pixels) {
            if (Color.alpha(p) > 30) opaquePixels++
        }

        val isGlyph = (opaquePixels < (size * size * 0.48))
        if (isGlyph) {
            val tintColor = if (isWhite) Color.WHITE else Color.parseColor("#18181B")
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
        } else {
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            val contrast = 1.4f
            val brightness = if (isWhite) 45f else -45f
            colorMatrix.postConcat(ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
        }
        return result
    }

    fun getAppBitmap(context: Context, packageName: String): Bitmap? {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        // Unified style key: "default", "white", "black", or "pack:<packPackageName>"
        val unifiedStyle = prefs.getString("unified_icon_style", "white") ?: "white"
        val cacheKey = "${packageName}_${unifiedStyle}"

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
            var baseBitmap: Bitmap? = null

            if (unifiedStyle.startsWith("pack:")) {
                val packPkg = unifiedStyle.removePrefix("pack:")
                baseBitmap = getIconFromPack(context, packPkg, packageName)
            }

            if (baseBitmap == null) {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)

                baseBitmap = if (unifiedStyle == "white" || unifiedStyle == "black") {
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

            val finalBitmap = when (unifiedStyle) {
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
