package com.example.categorydockwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.example.categorydockwidget.R
import com.example.categorydockwidget.data.AppIconHelper
import com.example.categorydockwidget.data.Category
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CategoryWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SWITCH_CATEGORY = "com.example.categorydockwidget.ACTION_SWITCH_CATEGORY"
        const val ACTION_LAUNCH_APP = "com.example.categorydockwidget.ACTION_LAUNCH_APP"
        const val EXTRA_CATEGORY_ID = "extra_category_id"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

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
            val selectedId = prefs.getString("selected_category_$appWidgetId", null)
            val rawJson = prefs.getString("categories_json", null)
            val sidebarPosition = prefs.getString("sidebar_position", "right") ?: "right"
            val sidebarAlignment = prefs.getString("sidebar_alignment", "bottom") ?: "bottom"
            val sidebarDisplay = prefs.getString("sidebar_display_type", "heading") ?: "heading"
            val sidebarSizeSp = prefs.getInt("sidebar_icon_size_sp", 14)
            val sidebarFont = prefs.getString("sidebar_font_family", "sans-serif") ?: "sans-serif"

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

            val layoutRes = if (sidebarPosition == "left") {
                R.layout.widget_category_dock_left
            } else {
                R.layout.widget_category_dock
            }

            val views = RemoteViews(context.packageName, layoutRes)
            val activeCategory = categories.firstOrNull { it.id == selectedId } ?: categories.firstOrNull()

            // 1. Heading with selected typeface
            val titleText = if (activeCategory != null) activeCategory.name.uppercase() else "APPS WIDGET"
            val titleSpan = SpannableString(titleText).apply {
                setSpan(TypefaceSpan(sidebarFont), 0, titleText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            views.setTextViewText(R.id.widget_category_title, titleSpan)
            views.setViewVisibility(R.id.widget_category_title, View.VISIBLE)
            views.setViewVisibility(R.id.category_title_divider, View.VISIBLE)

            // 2. Vertical Alignment of Sidebar
            val gravityValue = when (sidebarAlignment) {
                "top" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                "bottom" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                else -> Gravity.CENTER
            }
            views.setInt(R.id.sidebar_container, "setGravity", gravityValue)

            // 3. Category Dock Pills
            for (j in CAT_CONTAINER_IDS.indices) {
                val containerId = CAT_CONTAINER_IDS[j]
                val bgId = CAT_BG_IDS[j]
                val textId = CAT_TEXT_IDS[j]

                if (j < categories.size) {
                    val cat = categories[j]
                    val isSelected = cat.id == activeCategory?.id

                    val displayText = if (sidebarDisplay == "heading" || sidebarDisplay == "text") {
                        cat.name.take(3).uppercase()
                    } else {
                        cat.displayBadge
                    }

                    val pillSpan = SpannableString(displayText).apply {
                        setSpan(TypefaceSpan(sidebarFont), 0, displayText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }

                    views.setTextViewText(textId, pillSpan)
                    views.setTextViewTextSize(textId, TypedValue.COMPLEX_UNIT_SP, sidebarSizeSp.toFloat())
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

            // 4. Scrollable GridView
            val serviceIntent = Intent(context, AppGridWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.app_grid_view, serviceIntent)

            // 5. Fill-in intent template
            val launchIntent = Intent(context, CategoryWidgetProvider::class.java).apply {
                action = ACTION_LAUNCH_APP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val flagMutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingTemplate = PendingIntent.getBroadcast(context, appWidgetId, launchIntent, flagMutable)
            views.setPendingIntentTemplate(R.id.app_grid_view, pendingTemplate)

            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.app_grid_view)
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
        when (intent.action) {
            ACTION_SWITCH_CATEGORY -> {
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
            ACTION_LAUNCH_APP -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
                val launchIntent = AppIconHelper.getLaunchIntent(context, pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
            }
        }
    }
}
