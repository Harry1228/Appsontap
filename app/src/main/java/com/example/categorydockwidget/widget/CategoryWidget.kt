package com.example.categorydockwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
import java.io.File

class CategoryWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SWITCH_CATEGORY = "com.example.categorydockwidget.ACTION_SWITCH_CATEGORY"
        const val ACTION_LAUNCH_APP = "com.example.categorydockwidget.ACTION_LAUNCH_APP"
        const val ACTION_TOGGLE_APPS = "com.example.categorydockwidget.ACTION_TOGGLE_APPS"
        const val EXTRA_CATEGORY_ID = "extra_category_id"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        private val CAT_CONTAINER_IDS = intArrayOf(
            R.id.cat_container_0, R.id.cat_container_1, R.id.cat_container_2,
            R.id.cat_container_3, R.id.cat_container_4, R.id.cat_container_5
        )
        private val CAT_BG_IDS = intArrayOf(
            R.id.cat_bg_0, R.id.cat_bg_1, R.id.cat_bg_2,
            R.id.cat_bg_3, R.id.cat_bg_4, R.id.cat_bg_5
        )
        private val CAT_ICON_IDS = intArrayOf(
            R.id.cat_icon_0, R.id.cat_icon_1, R.id.cat_icon_2,
            R.id.cat_icon_3, R.id.cat_icon_4, R.id.cat_icon_5
        )

        private fun resolveTypeface(fontKey: String): Typeface {
            return try {
                when (fontKey) {
                    "serif" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    "monospace" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    "casual" -> Typeface.create("casual", Typeface.BOLD)
                    "cursive" -> Typeface.create("cursive", Typeface.BOLD)
                    "sans-serif-condensed" -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
            } catch (_: Exception) {
                Typeface.DEFAULT_BOLD
            }
        }

        private fun createTextBadgeBitmap(
            text: String,
            fontKey: String,
            sizeSp: Float,
            isSelected: Boolean
        ): Bitmap {
            val width = 160
            val height = 120
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isSelected) Color.WHITE else Color.parseColor("#B0B0B8")
                typeface = resolveTypeface(fontKey)
                textAlign = Paint.Align.CENTER
            }

            var computedSize = sizeSp * 2.1f
            paint.textSize = computedSize

            val textWidth = paint.measureText(text)
            val maxAvailableWidth = width * 0.85f
            if (textWidth > maxAvailableWidth && textWidth > 0f) {
                computedSize *= (maxAvailableWidth / textWidth)
                paint.textSize = computedSize
            }

            val yPos = (height / 2f) - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText(text, width / 2f, yPos, paint)
            return bitmap
        }

        private fun createGalleryIconBitmap(
            context: Context,
            fileName: String,
            sizeSp: Float
        ): Bitmap? {
            return try {
                val file = File(context.filesDir, "category_icons/$fileName")
                if (!file.exists()) return null
                val source = BitmapFactory.decodeFile(file.absolutePath) ?: return null

                val targetDim = (sizeSp * 2.2f).toInt().coerceIn(24, 110)
                val output = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val scaled = Bitmap.createScaledBitmap(source, targetDim, targetDim, true)

                val left = (160 - targetDim) / 2f
                val top = (120 - targetDim) / 2f
                canvas.drawBitmap(scaled, left, top, null)
                output
            } catch (_: Exception) {
                null
            }
        }

        fun getLayoutRes(context: Context, sidebarPosition: String, animStyle: String): Int {
            val isRight = sidebarPosition == "right"
            val layoutName = when (animStyle) {
                "slide_h" -> if (isRight) "widget_category_dock_slide_h" else "widget_category_dock_left_slide_h"
                "slide_v" -> if (isRight) "widget_category_dock_slide_v" else "widget_category_dock_left_slide_v"
                "zoom" -> if (isRight) "widget_category_dock_zoom" else "widget_category_dock_left_zoom"
                "none" -> if (isRight) "widget_category_dock_none" else "widget_category_dock_left_none"
                else -> if (isRight) "widget_category_dock" else "widget_category_dock_left"
            }
            val id = context.resources.getIdentifier(layoutName, "layout", context.packageName)
            return if (id != 0) id else {
                if (isRight) R.layout.widget_category_dock else R.layout.widget_category_dock_left
            }
        }

        fun switchCategorySeamless(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            newCatId: String
        ) {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val currentSlot = prefs.getInt("active_flipper_slot_$appWidgetId", 0)
            val nextSlot = if (currentSlot == 0) 1 else 0

            prefs.edit()
                .putString("selected_category_$appWidgetId", newCatId)
                .putInt("active_flipper_slot_$appWidgetId", nextSlot)
                .putBoolean("hide_apps_$appWidgetId", false) // Force show apps on category click
                .apply()

            val nextGridId = if (nextSlot == 0) R.id.app_grid_view_0 else R.id.app_grid_view_1
            val rawJson = prefs.getString("categories_json", null)
            val categories: List<Category> = if (rawJson != null) {
                try {
                    val type = object : TypeToken<List<Category>>() {}.type
                    Gson().fromJson(rawJson, type) ?: emptyList()
                } catch (_: Exception) { emptyList() }
            } else emptyList()

            val sidebarPosition = prefs.getString("sidebar_position", "left") ?: "left"
            val animStyle = prefs.getString("animation_style", "fade") ?: "fade"
            val layoutRes = getLayoutRes(context, sidebarPosition, animStyle)

            val partialViews = RemoteViews(context.packageName, layoutRes)

            // Keep Flipper visible
            partialViews.setViewVisibility(R.id.app_view_flipper, View.VISIBLE)

            for (j in CAT_CONTAINER_IDS.indices) {
                if (j < categories.size) {
                    val cat = categories[j]
                    partialViews.setImageViewResource(
                        CAT_BG_IDS[j],
                        if (cat.id == newCatId) R.drawable.pill_active else R.drawable.pill_inactive
                    )
                }
            }

            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, nextGridId)
            partialViews.setDisplayedChild(R.id.app_view_flipper, nextSlot)
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, partialViews)
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                val selectedId = prefs.getString("selected_category_$appWidgetId", null)
                val rawJson = prefs.getString("categories_json", null)
                val sidebarPosition = prefs.getString("sidebar_position", "left") ?: "left"
                val sidebarAlignment = prefs.getString("sidebar_alignment", "bottom") ?: "bottom"
                val sidebarDisplay = prefs.getString("sidebar_display_type", "icons") ?: "icons"
                val sidebarFont = prefs.getString("sidebar_font_family", "sans-serif") ?: "sans-serif"
                val animStyle = prefs.getString("animation_style", "fade") ?: "fade"

                val sidebarTextSizeSp = prefs.getInt("sidebar_text_size_sp", 14)
                val sidebarIconSizeSp = prefs.getInt("sidebar_icon_size_sp", 28)

                val clockEnabled = prefs.getBoolean("clock_enabled", true)
                val clockFont = prefs.getString("clock_font", "sans-serif") ?: "sans-serif"
                val clockSizeSp = prefs.getInt("clock_size_sp", 26)
                val hideApps = prefs.getBoolean("hide_apps_$appWidgetId", false)

                val categories: List<Category> = if (rawJson != null) {
                    try {
                        val type = object : TypeToken<List<Category>>() {}.type
                        Gson().fromJson(rawJson, type) ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                } else emptyList()

                val layoutRes = getLayoutRes(context, sidebarPosition, animStyle)
                val views = RemoteViews(context.packageName, layoutRes)
                val activeCategory = categories.firstOrNull { it.id == selectedId } ?: categories.firstOrNull()

                // Hide apps logic
                views.setViewVisibility(R.id.app_view_flipper, if (hideApps) View.INVISIBLE else View.VISIBLE)

                // 1. Clock Configuration & Toggle Intent
                if (clockEnabled) {
                    views.setViewVisibility(R.id.clock_container, View.VISIBLE)
                    views.setTextViewTextSize(R.id.clock_hours, TypedValue.COMPLEX_UNIT_SP, clockSizeSp.toFloat())
                    views.setTextViewTextSize(R.id.clock_minutes, TypedValue.COMPLEX_UNIT_SP, clockSizeSp.toFloat())

                    val formatH = SpannableString("hh").apply {
                        setSpan(TypefaceSpan(clockFont), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    val formatM = SpannableString("mm").apply {
                        setSpan(TypefaceSpan(clockFont), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    views.setCharSequence(R.id.clock_hours, "setFormat12Hour", formatH)
                    views.setCharSequence(R.id.clock_hours, "setFormat24Hour", SpannableString("HH").apply {
                        setSpan(TypefaceSpan(clockFont), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    })
                    views.setCharSequence(R.id.clock_minutes, "setFormat12Hour", formatM)
                    views.setCharSequence(R.id.clock_minutes, "setFormat24Hour", formatM)

                    val toggleIntent = Intent(context, CategoryWidgetProvider::class.java).apply {
                        action = ACTION_TOGGLE_APPS
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    val pendingToggle = PendingIntent.getBroadcast(
                        context, appWidgetId * 100, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.clock_container, pendingToggle)
                } else {
                    views.setViewVisibility(R.id.clock_container, View.GONE)
                }

                // 2. Sidebar Alignment
                val gravityValue = when (sidebarAlignment) {
                    "top" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    "center" -> Gravity.CENTER
                    else -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                views.setInt(R.id.sidebar_container, "setGravity", gravityValue)

                // 3. Category Dock Pills
                for (j in CAT_CONTAINER_IDS.indices) {
                    val containerId = CAT_CONTAINER_IDS[j]
                    val bgId = CAT_BG_IDS[j]
                    val iconId = CAT_ICON_IDS[j]

                    if (j < categories.size) {
                        val cat = categories[j]
                        val isSelected = cat.id == activeCategory?.id

                        if (cat.isGallery && cat.galleryFileName != null) {
                            val galleryBmp = createGalleryIconBitmap(context, cat.galleryFileName!!, sidebarIconSizeSp.toFloat())
                            if (galleryBmp != null) {
                                views.setImageViewBitmap(iconId, galleryBmp)
                            } else {
                                val fallbackBmp = createTextBadgeBitmap(cat.name.take(3).uppercase(), sidebarFont, sidebarTextSizeSp.toFloat(), isSelected)
                                views.setImageViewBitmap(iconId, fallbackBmp)
                            }
                        } else {
                            val isTextLabel = sidebarDisplay == "heading" || sidebarDisplay == "text" || cat.icon.isNullOrBlank()
                            val displayText = if (isTextLabel) cat.name.take(4).uppercase() else cat.displayBadge
                            val sizeSp = if (isTextLabel) sidebarTextSizeSp else sidebarIconSizeSp

                            val badgeBmp = createTextBadgeBitmap(displayText, sidebarFont, sizeSp.toFloat(), isSelected)
                            views.setImageViewBitmap(iconId, badgeBmp)
                        }

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

                // 4. Bind Flipper Grid Buffers
                val currentSlot = prefs.getInt("active_flipper_slot_$appWidgetId", 0)
                views.setDisplayedChild(R.id.app_view_flipper, currentSlot)

                val serviceIntent0 = Intent(context, AppGridWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("widget://categorydock/grid/$appWidgetId/0")
                }
                views.setRemoteAdapter(R.id.app_grid_view_0, serviceIntent0)

                val serviceIntent1 = Intent(context, AppGridWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("widget://categorydock/grid/$appWidgetId/1")
                }
                views.setRemoteAdapter(R.id.app_grid_view_1, serviceIntent1)

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
                views.setPendingIntentTemplate(R.id.app_grid_view_0, pendingTemplate)
                views.setPendingIntentTemplate(R.id.app_grid_view_1, pendingTemplate)

                val activeGridId = if (currentSlot == 0) R.id.app_grid_view_0 else R.id.app_grid_view_1
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, activeGridId)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (_: Exception) {}
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
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        when (intent.action) {
            ACTION_TOGGLE_APPS -> {
                val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                val isHidden = prefs.getBoolean("hide_apps_$appWidgetId", false)
                prefs.edit().putBoolean("hide_apps_$appWidgetId", !isHidden).apply()
                
                // Fast partial update to hide/show Flipper
                val sidebarPosition = prefs.getString("sidebar_position", "left") ?: "left"
                val animStyle = prefs.getString("animation_style", "fade") ?: "fade"
                val layoutRes = getLayoutRes(context, sidebarPosition, animStyle)
                val partialViews = RemoteViews(context.packageName, layoutRes)
                
                partialViews.setViewVisibility(R.id.app_view_flipper, if (!isHidden) View.INVISIBLE else View.VISIBLE)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, partialViews)
            }
            ACTION_SWITCH_CATEGORY -> {
                val catId = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: return
                switchCategorySeamless(context, appWidgetManager, appWidgetId, catId)
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
