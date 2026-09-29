package com.example.categorydockwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.categorydockwidget.R
import com.example.categorydockwidget.data.AppIconHelper
import com.example.categorydockwidget.data.Category
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AppGridWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return AppGridFactory(applicationContext, intent)
    }
}

class AppGridFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    private var apps: List<String> = emptyList()
    private var iconSizeDp: Int = 46

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        try {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val rawJson = prefs.getString("categories_json", null)
            val selectedId = prefs.getString("selected_category_$appWidgetId", null)
            iconSizeDp = prefs.getInt("category_icon_size_dp", 46)

            val categories: List<Category> = if (rawJson != null) {
                try {
                    val type = object : TypeToken<List<Category>>() {}.type
                    Gson().fromJson(rawJson, type) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val active = categories.firstOrNull { it.id == selectedId } ?: categories.firstOrNull()
            apps = active?.packageNames ?: emptyList()
        } catch (_: Exception) {
            apps = emptyList()
        }
    }

    override fun onDestroy() {
        apps = emptyList()
    }

    override fun getCount(): Int = apps.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in apps.indices) return null
        return try {
            val pkg = apps[position]
            val views = RemoteViews(context.packageName, R.layout.widget_grid_item)

            val bitmap = AppIconHelper.getAppBitmap(context, pkg)
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.grid_app_icon, bitmap)
            }

            val maxDimDp = 56
            val padDp = ((maxDimDp - iconSizeDp).coerceAtLeast(0) / 2)
            val density = context.resources.displayMetrics.density
            val padPx = (padDp * density).toInt()
            views.setViewPadding(R.id.grid_app_icon, padPx, padPx, padPx, padPx)

            val fillInIntent = Intent().apply {
                putExtra(CategoryWidgetProvider.EXTRA_PACKAGE_NAME, pkg)
            }
            views.setOnClickFillInIntent(R.id.grid_item_container, fillInIntent)
            views
        } catch (_: Exception) {
            null
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
