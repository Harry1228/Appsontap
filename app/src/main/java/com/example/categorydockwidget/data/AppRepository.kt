package com.example.categorydockwidget.data

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppRepository {
    private const val PREFS_NAME = "app_cache_prefs"
    private const val KEY_CACHED_APPS = "cached_apps_json"
    private val gson = Gson()

    private var memoryApps: List<AppModel>? = null

    fun getCachedApps(context: Context): List<AppModel> {
        memoryApps?.let { return it }

        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_CACHED_APPS, null)
            if (json != null) {
                val type = object : TypeToken<List<AppModel>>() {}.type
                val list: List<AppModel> = gson.fromJson(json, type) ?: emptyList()
                val distinct = list.distinctBy { it.id }
                memoryApps = distinct
                distinct
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun reloadApps(context: Context): List<AppModel> = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager

            // 1. Standard Launcher activities
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launcherActivities = pm.queryIntentActivities(launcherIntent, 0)

            // 2. Dialer & Phone activities
            val dialIntent = Intent(Intent.ACTION_DIAL)
            val dialActivities = pm.queryIntentActivities(dialIntent, 0)

            val combined = (launcherActivities + dialActivities)

            val apps = combined.mapNotNull { resolveInfo ->
                try {
                    val pkg = resolveInfo.activityInfo.packageName
                    val actName = resolveInfo.activityInfo.name
                    if (pkg != context.packageName) {
                        val name = resolveInfo.loadLabel(pm).toString().ifBlank {
                            resolveInfo.activityInfo.loadLabel(pm).toString()
                        }
                        AppModel(packageName = pkg, appName = name, activityName = actName)
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
            .distinctBy { "${it.packageName}/${it.appName}" }
            .sortedBy { it.appName.lowercase() }

            memoryApps = apps

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CACHED_APPS, gson.toJson(apps)).apply()

            apps
        } catch (_: Exception) {
            memoryApps ?: emptyList()
        }
    }
}
