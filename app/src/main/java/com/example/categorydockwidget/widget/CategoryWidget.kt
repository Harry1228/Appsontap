package com.example.categorydockwidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.categorydockwidget.data.AppIconHelper
import com.example.categorydockwidget.data.Category
import com.example.categorydockwidget.data.WidgetKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CategoryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val selectedId = prefs[WidgetKeys.SELECTED_CATEGORY_ID] ?: "work"
            val rawJson = prefs[WidgetKeys.CATEGORIES_JSON]

            val categories: List<Category> = if (rawJson != null) {
                val type = object : TypeToken<List<Category>>() {}.type
                Gson().fromJson(rawJson, type)
            } else {
                WidgetKeys.DEFAULT_CATEGORIES
            }

            val activeCategory = categories.firstOrNull { it.id == selectedId } ?: categories.firstOrNull()
            val apps = activeCategory?.packageNames ?: emptyList()

            val row1 = apps.take(4)
            val row2 = apps.drop(4).take(4)

            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xDD18181F)))
                    .cornerRadius(24.dp)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left pane: App grid
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (apps.isEmpty()) {
                        Box(
                            modifier = GlanceModifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Open Apps on Tap app to configure",
                                style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 12.sp)
                            )
                        }
                    } else {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.Start
                        ) {
                            row1.forEach { pkg ->
                                AppIconItem(context, pkg)
                            }
                        }

                        if (row2.isNotEmpty()) {
                            Row(
                                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalAlignment = Alignment.Start
                            ) {
                                row2.forEach { pkg ->
                                    AppIconItem(context, pkg)
                                }
                            }
                        }
                    }
                }

                // Vertical divider
                Spacer(
                    modifier = GlanceModifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                        .background(ColorProvider(Color(0x22FFFFFF)))
                )

                // Right pane: Clean Category Selector Dock
                Column(
                    modifier = GlanceModifier
                        .width(52.dp)
                        .fillMaxHeight()
                        .padding(start = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categories.forEach { category ->
                        val isSelected = category.id == activeCategory?.id
                        Box(
                            modifier = GlanceModifier
                                .size(38.dp)
                                .cornerRadius(12.dp)
                                .background(
                                    if (isSelected) ColorProvider(Color(0xFF3B82F6))
                                    else ColorProvider(Color(0xFF272730))
                                )
                                .padding(vertical = 4.dp)
                                .clickable(
                                    actionRunCallback<SwitchCategoryAction>(
                                        actionParametersOf(SwitchCategoryAction.CategoryId to category.id)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category.name.take(2).uppercase(),
                                style = TextStyle(
                                    color = ColorProvider(if (isSelected) Color.White else Color(0xFFB0B0C0)),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = GlanceModifier.height(8.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun AppIconItem(context: Context, pkg: String) {
        val launchIntent = AppIconHelper.getLaunchIntent(context, pkg)
        val iconBitmap = AppIconHelper.getAppBitmap(context, pkg)

        Box(
            modifier = GlanceModifier
                .size(54.dp)
                .padding(6.dp)
                .cornerRadius(12.dp)
                .clickable(
                    if (launchIntent != null) actionStartActivity(launchIntent)
                    else actionRunCallback<NoOpAction>()
                ),
            contentAlignment = Alignment.Center
        ) {
            if (iconBitmap != null) {
                Image(
                    provider = ImageProvider(iconBitmap),
                    contentDescription = pkg,
                    modifier = GlanceModifier.fillMaxSize()
                )
            } else {
                Text(
                    text = pkg.take(2).uppercase(),
                    style = TextStyle(color = ColorProvider(Color.White))
                )
            }
        }
    }
}

class SwitchCategoryAction : ActionCallback {
    companion object {
        val CategoryId = ActionParameters.Key<String>("category_id")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val categoryId = parameters[CategoryId] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WidgetKeys.SELECTED_CATEGORY_ID] = categoryId
        }
        CategoryWidget().update(context, glanceId)
    }
}

class NoOpAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {}
}

class CategoryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CategoryWidget()
}
