package com.example.categorydockwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.categorydockwidget.R
import com.example.categorydockwidget.data.AppIconHelper
import com.example.categorydockwidget.data.Category
import com.example.categorydockwidget.data.WidgetKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CategoryWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SWITCH_CATEGORY = "com.example.categorydockwidget.ACTION_SWITCH_CATEGORY"
        const val EXTRA_CATEGORY_ID = "extra_category_id"

        private val ICON_VIEW_IDS = intArrayOf(
            R.id.app_icon_0, R.id.app_icon_1, R.id.app_icon_2, R.id.app_icon_3,
            R.id.app_icon_4, R.id.app_icon_5, R.id.app_icon_6, R.id.app_icon_7
        )

        private val CAT_CONTAINER_IDS = intArrayOf(
            R.id.cat_container_0, R.id.cat_container_1, R.id.cat_container_2, R.id.cat_container_3
        )
        private val CAT_BG_IDS = intArrayOf(
            R.id.cat_bg_0, R.id.cat_bg_1, R.id.cat_bg_2, R.id.cat_bg_3
        )
        private val CAT_TEXT_IDS = intArrayOf(
            R.id.cat_text_0, R.id.cat_text_1, R.id.cat_text_2, R.id.cat_text_3
        )

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val selectedId = prefs.getString("selected_category_$appWidgetId", "work") ?: "work"
            val rawJson = prefs.getString("categories_json", null)

            val categories: List<Category> = if (rawJson != null) {
                val type = object : TypeToken<List<Category>>() {}.type
                Gson().fromJson(rawJson, type)
            } else {
                WidgetKeys.DEFAULT_CATEGORIES
            }

            val activeCategory = categories.firstOrNull { it.id == selectedId } ?: categories.firstOrNull()
            val apps = activeCategory?.packageNames ?: emptyList()

            val views = RemoteViews(context.packageName, R.layout.widget_category_dock)

            for (i in ICON_VIEW_IDS.indices) {
                val viewId = ICON_VIEW_IDS[i]
                if (i < apps.size) {
                    val pkg = apps[i]
                    val bitmap = AppIconHelper.getAppBitmap(context, pkg)
                    val launchIntent = AppIconHelper.getLaunchIntent(context, pkg)

                    if (bitmap != null) {
                        views.setImageViewBitmap(viewId, bitmap)
                    }

                    if (launchIntent != null) {
                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            appWidgetId * 100 + i,
                            launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(viewId, pendingIntent)
                    }
                    views.setViewVisibility(viewId, View.VISIBLE)
                } else {
                    views.setViewVisibility(viewId, View.INVISIBLE)
                }
            }

            for (j in CAT_CONTAINER_IDS.indices) {
                val containerId = CAT_CONTAINER_IDS[j]
                val bgId = CAT_BG_IDS[j]
                val textId = CAT_TEXT_IDS[j]

                if (j < categories.size) {
                    val cat = categories[j]
                    val isSelected = cat.id == activeCategory?.id

                    views.setTextViewText(textId, cat.displayBadge)
                    views.setImageViewResource(
                        bgId,
                        if (isSelected) R.drawable.pill_active else R.drawable.pill_inactive
                    )

                    val switchIntent = Intent(context, CategoryWidgetProvider::class.java).apply {
                        action = ACTION_SWITCH_CATEGORY
                        putExtra(EXTRA_CATEGORY_ID, cat.id)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    val pendingSwitch = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 10 + j,
                        switchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(containerId, pendingSwitch)
                    views.setViewVisibility(containerId, View.VISIBLE)
                } else {
                    views.setViewVisibility(containerId, View.GONE)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, CategoryWidgetProvider::class.java))
            for (id in ids) {
                updateWidget(context, appWidgetManager, id)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SWITCH_CATEGORY) {
            val catId = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: return
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("selected_category_$appWidgetId", catId).apply()

                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}
