package com.example.categorydockwidget.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
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
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val selectedId = prefs[WidgetKeys.SELECTED_CATEGORY_ID] ?: "work"
            val rawJson = prefs[WidgetKeys.CATEGORIES_JSON]

            val categories: List<Category> = if (rawJson != null) {
                val type = object : TypeToken<List<Category>>() {}.type
                Gson().fromJson(rawJson, type)
            } else {
                WidgetKeys.DEFAULT_CATEGORIES
            }

            val activeCategory = categories.firstOrNull { it.id == selectedId } ?: categories.firstOrNull()

            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFF1E1E24)))
                    .padding(8.dp)
            ) {
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (activeCategory != null && activeCategory.packageNames.isNotEmpty()) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            activeCategory.packageNames.forEach { pkg ->
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                                val iconBitmap = AppIconHelper.getAppBitmap(context, pkg)

                                Box(
                                    modifier = GlanceModifier
                                        .size(48.dp)
                                        .padding(horizontal = 4.dp)
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
                    } else {
                        Text(
                            text = "No apps assigned",
                            style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 12.sp)
                        )
                    }
                }

                Column(
                    modifier = GlanceModifier
                        .width(48.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categories.forEach { category ->
                        val isSelected = category.id == activeCategory?.id
                        Box(
                            modifier = GlanceModifier
                                .size(36.dp)
                                .padding(vertical = 2.dp)
                                .background(
                                    if (isSelected) ColorProvider(Color(0xFF4E73DF))
                                    else ColorProvider(Color(0xFF2C2C34))
                                )
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
                                    color = ColorProvider(Color.White),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
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
