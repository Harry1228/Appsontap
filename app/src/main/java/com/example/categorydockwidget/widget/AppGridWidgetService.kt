package com.example.categorydockwidget.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.categorydockwidget.R
import com.example.categorydockwidget.data.Category
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

const val EXTRA_PACKAGE_NAME = "com.example.categorydockwidget.EXTRA_PACKAGE_NAME"

class AppGridWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return AppGridViewsFactory(applicationContext, intent)
    }
}

class AppGridViewsFactory(private val context: Context, intent: Intent) : RemoteViewsService.RemoteViewsFactory {
    private var appsList: List<String> = emptyList()

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val rawJson = prefs.getString("categories_json", null)
        if (rawJson != null) {
            try {
                val type = object : TypeToken<List<Category>>() {}.type
                val categories: List<Category> = Gson().fromJson(rawJson, type) ?: emptyList()
                val activeCategoryIndex = prefs.getInt("active_category_index", 0)
                val category = categories.getOrNull(activeCategoryIndex) ?: categories.firstOrNull()
                appsList = category?.packageNames ?: emptyList()
            } catch (_: Exception) {
                appsList = emptyList()
            }
        } else {
            appsList = emptyList()
        }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = appsList.size

    override fun getViewAt(position: Int): RemoteViews {
        val packageName = appsList.getOrNull(position) ?: ""
        val views = RemoteViews(context.packageName, R.layout.widget_app_item)
        
        val fillInIntent = Intent().apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
        }
        views.setOnClickFillInIntent(R.id.appItemContainer, fillInIntent)
        
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
