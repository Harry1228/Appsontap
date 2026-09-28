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

    // RAM Cache for 0ms in-memory access
    private var memoryApps: List<AppModel>? = null

    fun getCachedApps(context: Context): List<AppModel> {
        memoryApps?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CACHED_APPS, null)
        if (json != null) {
            val type = object : TypeToken<List<AppModel>>() {}.type
            val list: List<AppModel> = gson.fromJson(json, type) ?: emptyList()
            memoryApps = list
            return list
        }
        return emptyList()
    }

    suspend fun reloadApps(context: Context): List<AppModel> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0).mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg != context.packageName) {
                val name = resolveInfo.loadLabel(pm).toString()
                AppModel(packageName = pkg, appName = name)
            } else null
        }.sortedBy { it.appName.lowercase() }

        memoryApps = apps

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CACHED_APPS, gson.toJson(apps)).apply()

        apps
    }
}
