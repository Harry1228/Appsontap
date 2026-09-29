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
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import kotlin.math.ceil

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

        // Robust cross-OEM tactile vibration
        fun performHaptic(context: Context) {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("haptics_enabled", true)) return
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                } ?: return

                if (!vibrator.hasVibrator()) return

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val attrs = VibrationAttributes.Builder()
                            .setUsage(VibrationAttributes.USAGE_TOUCH)
                            .build()
                        vibrator.vibrate(effect, attrs)
                    } else {
                        val audioAttrs = AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .build()
                        vibrator.vibrate(effect, audioAttrs)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(35L)
                }
            } catch (_: Exception) {}
        }

        fun getGridId(cols: Int, slot: Int): Int {
            return when (cols) {
                3 -> if (slot == 0) R.id.app_grid_view_3_0 else R.id.app_grid_view_3_1
                4 -> if (slot == 0) R.id.app_grid_view_4_0 else R.id.app_grid_view_4_1
                6 -> if (slot == 0) R.id.app_grid_view_6_0 else R.id.app_grid_view_6_1
                else -> if (slot == 0) R.id.app_grid_view_5_0 else R.id.app_grid_view_5_1
            }
        }

        fun getFlipperChildIndex(cols: Int, slot: Int): Int {
            val base = when (cols) {
                3 -> 0
                4 -> 2
                6 -> 6
                else -> 4
            }
            return base + (if (slot == 0) 0 else 1)
        }

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

        fun getLayoutRes(sidebarPosition: String): Int {
            return if (sidebarPosition == "right") R.layout.widget_category_dock else R.layout.widget_category_dock_left
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
                .putBoolean("hide_apps_$appWidgetId", false)
                .apply()

            val gridCols = prefs.getInt("grid_columns", 5)
            val nextGridId = getGridId(gridCols, nextSlot)
            val flipperIndex = getFlipperChildIndex(gridCols, nextSlot)

            val rawJson = prefs.getString("categories_json", null)
            val categories: List<Category> = if (rawJson != null) {
                try {
                    val type = object : TypeToken<List<Category>>() {}.type
                    Gson().fromJson(rawJson, type) ?: emptyList()
                } catch (_: Exception) { emptyList() }
            } else emptyList()

            val sidebarPosition = prefs.getString("sidebar_position", "left") ?: "left"
            val layoutRes = getLayoutRes(sidebarPosition)
            val partialViews = RemoteViews(context.packageName, layoutRes)
            partialViews.setViewVisibility(R.id.apps_outer_container, View.VISIBLE)

            for (j in CAT_CONTAINER_IDS.indices) {
                if (j < categories.size) {
                    val cat = categories[j]
                    partialViews.setImageViewResource(
                        CAT_BG_IDS[j],
                        if (cat.id == newCatId) R.drawable.pill_active else R.drawable.pill_inactive
                    )
                }
            }

            // Apply dynamic top/middle/bottom alignment
            val activeCat = categories.firstOrNull { it.id == newCatId }
            val appsAlignment = prefs.getString("apps_alignment", "top") ?: "top"
            val rowSpacingDp = prefs.getInt("grid_row_spacing", 8)
            val iconSizeDp = prefs.getInt("category_icon_size_dp", 46)

            val density = context.resources.displayMetrics.density
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val widgetMinHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220) ?: 220

            val appCount = activeCat?.packageNames?.size ?: 0
            val numRows = ceil(appCount.toDouble() / gridCols).toInt().coerceAtLeast(1)
            val cellHeightDp = iconSizeDp + rowSpacingDp + 12
            val totalContentHeightDp = numRows * cellHeightDp
            val availableExtraDp = (widgetMinHeightDp - 96 - totalContentHeightDp).coerceAtLeast(0)

            val topOffsetDp = when (appsAlignment) {
                "center", "middle" -> availableExtraDp / 2
                "bottom" -> availableExtraDp
                else -> 0
            }
            val topOffsetPx = (topOffsetDp * density).toInt()
            partialViews.setViewPadding(R.id.apps_outer_container, 0, topOffsetPx, 0, 0)

            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, nextGridId)
            partialViews.setDisplayedChild(R.id.app_view_flipper, flipperIndex)
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

                val sidebarTextSizeSp = prefs.getInt("sidebar_text_size_sp", 14)
                val sidebarIconSizeSp = prefs.getInt("sidebar_icon_size_sp", 28)

                val clockEnabled = prefs.getBoolean("clock_enabled", true)
                val clockFont = prefs.getString("clock_font", "sans-serif") ?: "sans-serif"
                val clockSizeSp = prefs.getInt("clock_size_sp", 26)
                val hideApps = prefs.getBoolean("hide_apps_$appWidgetId", false)

                val gridCols = prefs.getInt("grid_columns", 5)
                val appsAlignment = prefs.getString("apps_alignment", "top") ?: "top"
                val rowSpacingDp = prefs.getInt("grid_row_spacing", 8)
                val iconSizeDp = prefs.getInt("category_icon_size_dp", 46)

                val categories: List<Category> = if (rawJson != null) {
                    try {
                        val type = object : TypeToken<List<Category>>() {}.type
                        Gson().fromJson(rawJson, type) ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                } else emptyList()

                val layoutRes = getLayoutRes(sidebarPosition)
                val views = RemoteViews(context.packageName, layoutRes)
                val activeCategory = categories.firstOrNull { it.id == selectedId } ?: categories.firstOrNull()

                views.setViewVisibility(R.id.apps_outer_container, if (hideApps) View.INVISIBLE else View.VISIBLE)

                // Dynamic vertical alignment calculation
                val density = context.resources.displayMetrics.density
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val widgetMinHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220) ?: 220

                val appCount = activeCategory?.packageNames?.size ?: 0
                val numRows = ceil(appCount.toDouble() / gridCols).toInt().coerceAtLeast(1)
                val cellHeightDp = iconSizeDp + rowSpacingDp + 12
                val totalContentHeightDp = numRows * cellHeightDp
                val availableExtraDp = (widgetMinHeightDp - 96 - totalContentHeightDp).coerceAtLeast(0)

                val topOffsetDp = when (appsAlignment) {
                    "center", "middle" -> availableExtraDp / 2
                    "bottom" -> availableExtraDp
                    else -> 0
                }
                val topOffsetPx = (topOffsetDp * density).toInt()
                views.setViewPadding(R.id.apps_outer_container, 0, topOffsetPx, 0, 0)

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

                // 4. Bind Flipper Grid Buffers for all supported column sets
                val currentSlot = prefs.getInt("active_flipper_slot_$appWidgetId", 0)
                val flipperIndex = getFlipperChildIndex(gridCols, currentSlot)
                views.setDisplayedChild(R.id.app_view_flipper, flipperIndex)

                val colOptions = intArrayOf(3, 4, 5, 6)
                for (col in colOptions) {
                    for (slot in 0..1) {
                        val gridId = getGridId(col, slot)
                        val serviceIntent = Intent(context, AppGridWidgetService::class.java).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            data = Uri.parse("widget://categorydock/grid/$appWidgetId/$col/$slot")
                        }
                        views.setRemoteAdapter(gridId, serviceIntent)

                        val launchIntent = Intent(context, CategoryWidgetProvider::class.java).apply {
                            action = ACTION_LAUNCH_APP
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        val flagMutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                        val pendingTemplate = PendingIntent.getBroadcast(context, appWidgetId * 1000 + col * 10 + slot, launchIntent, flagMutable)
                        views.setPendingIntentTemplate(gridId, pendingTemplate)
                    }
                }

                val activeGridId = getGridId(gridCols, currentSlot)
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
                performHaptic(context)
                val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                val isHidden = prefs.getBoolean("hide_apps_$appWidgetId", false)
                prefs.edit().putBoolean("hide_apps_$appWidgetId", !isHidden).apply()

                val sidebarPosition = prefs.getString("sidebar_position", "left") ?: "left"
                val layoutRes = getLayoutRes(sidebarPosition)
                val partialViews = RemoteViews(context.packageName, layoutRes)

                partialViews.setViewVisibility(R.id.apps_outer_container, if (!isHidden) View.INVISIBLE else View.VISIBLE)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, partialViews)
            }
            ACTION_SWITCH_CATEGORY -> {
                performHaptic(context)
                val catId = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: return
                switchCategorySeamless(context, appWidgetManager, appWidgetId, catId)
            }
            ACTION_LAUNCH_APP -> {
                performHaptic(context)
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
