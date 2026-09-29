package com.example.categorydockwidget.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import android.util.Xml
import androidx.datastore.preferences.core.stringPreferencesKey
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream

data class AppModel(
    val packageName: String,
    val appName: String,
    val activityName: String = ""
) {
    val id: String
        get() = if (activityName.isNotBlank()) "$packageName/$activityName" else packageName
}

data class Category(
    val id: String,
    val name: String,
    val packageNames: List<String> = emptyList(),
    val icon: String? = ""
) {
    val isGallery: Boolean
        get() = icon?.startsWith("gallery:") == true

    val galleryFileName: String?
        get() = if (isGallery) icon!!.removePrefix("gallery:") else null

    val displayBadge: String
        get() = if (!icon.isNullOrBlank() && !isGallery) icon else name.take(3).uppercase()
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

    fun getLaunchIntent(context: Context, appKey: String): Intent? {
        return intentCache.getOrPut(appKey) {
            val pm = context.packageManager
            if (appKey.contains("/")) {
                val parts = appKey.split("/")
                val pkg = parts[0]
                val act = parts[1]
                val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(pkg, act)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (explicitIntent.resolveActivity(pm) != null) {
                    return@getOrPut explicitIntent
                }
            }
            val plainPkg = appKey.substringBefore("/")
            pm.getLaunchIntentForPackage(plainPkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
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
                    val pullParser = Xml.newPullParser()
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

    private fun getIconFromPack(context: Context, iconPackPackage: String, appKey: String): Bitmap? {
        if (iconPackPackage.isBlank() || iconPackPackage == "none" || iconPackPackage == "default") return null
        return try {
            val map = getIconPackMap(context, iconPackPackage)
            val pm = context.packageManager
            val plainPkg = appKey.substringBefore("/")

            var drawableName: String? = null
            if (appKey.contains("/")) {
                val parts = appKey.split("/")
                val fullComp = "${parts[0]}/${parts[1]}"
                drawableName = map[fullComp] ?: map[fullComp.lowercase()]
            }

            if (drawableName == null) {
                val launchIntent = pm.getLaunchIntentForPackage(plainPkg)
                val comp = launchIntent?.component
                if (comp != null) {
                    val fullComp = comp.flattenToString()
                    drawableName = map[fullComp] ?: map[fullComp.lowercase()]
                }
            }

            if (drawableName == null) {
                drawableName = map[plainPkg] ?: map[plainPkg.lowercase()]
            }
            if (drawableName == null) {
                drawableName = plainPkg.replace('.', '_').lowercase()
            }

            val packRes = pm.getResourcesForApplication(iconPackPackage)
            var resId = packRes.getIdentifier(drawableName, "drawable", iconPackPackage)
            if (resId == 0) {
                val shortName = plainPkg.substringAfterLast('.').lowercase()
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

    fun getAppBitmap(context: Context, appKey: String): Bitmap? {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val unifiedStyle = prefs.getString("unified_icon_style", "default") ?: "default"
        val sanitizedKey = appKey.replace('/', '_')
        val cacheKey = "${sanitizedKey}_${unifiedStyle}"

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
            var finalBitmap: Bitmap? = null

            if (unifiedStyle.startsWith("pack:")) {
                val packPkg = unifiedStyle.removePrefix("pack:")
                finalBitmap = getIconFromPack(context, packPkg, appKey)
            }

            if (finalBitmap == null) {
                val pm = context.packageManager
                val drawable = if (appKey.contains("/")) {
                    val parts = appKey.split("/")
                    try {
                        pm.getActivityIcon(ComponentName(parts[0], parts[1]))
                    } catch (_: Exception) {
                        pm.getApplicationIcon(parts[0])
                    }
                } else {
                    pm.getApplicationIcon(appKey)
                }

                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                finalBitmap = bmp
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
