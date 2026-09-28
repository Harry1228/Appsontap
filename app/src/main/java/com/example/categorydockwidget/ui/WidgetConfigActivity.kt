package com.example.categorydockwidget.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.example.categorydockwidget.data.AppModel
import com.example.categorydockwidget.data.Category
import com.example.categorydockwidget.data.WidgetKeys
import com.example.categorydockwidget.widget.CategoryWidget
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            ConfigScreen(onSave = { updatedCategories -> saveAndFinish(updatedCategories) })
        }
    }

    private fun saveAndFinish(categories: List<Category>) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { prefs ->
                prefs[WidgetKeys.CATEGORIES_JSON] = Gson().toJson(categories)
                prefs[WidgetKeys.SELECTED_CATEGORY_ID] = categories.firstOrNull()?.id ?: "work"
            }
            CategoryWidget().update(this@WidgetConfigActivity, glanceId)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(onSave: (List<Category>) -> Unit) {
    var categories by remember { mutableStateOf(WidgetKeys.DEFAULT_CATEGORIES) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var installedApps by remember { mutableStateOf<List<AppModel>>(emptyList()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val apps = context.packageManager.queryIntentActivities(intent, 0).map { resolveInfo ->
                AppModel(
                    packageName = resolveInfo.activityInfo.packageName,
                    appName = resolveInfo.loadLabel(context.packageManager).toString()
                )
            }.sortedBy { it.appName }
            installedApps = apps
        }
    }

    val currentCategory = categories[selectedCategoryIndex]

    Scaffold(
        topBar = { TopAppBar(title = { Text("Configure Widget") }) },
        bottomBar = {
            Button(
                onClick = { onSave(categories) },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("Save and Apply")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = selectedCategoryIndex) {
                categories.forEachIndexed { index, cat ->
                    Tab(
                        selected = selectedCategoryIndex == index,
                        onClick = { selectedCategoryIndex = index },
                        text = { Text(cat.name) }
                    )
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(installedApps) { app ->
                    val isChecked = currentCategory.packageNames.contains(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val updatedPackages = if (isChecked) {
                                    currentCategory.packageNames - app.packageName
                                } else {
                                    currentCategory.packageNames + app.packageName
                                }
                                val updatedList = categories.toMutableList()
                                updatedList[selectedCategoryIndex] = currentCategory.copy(packageNames = updatedPackages)
                                categories = updatedList
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(app.appName, modifier = Modifier.weight(1f))
                        Checkbox(checked = isChecked, onCheckedChange = null)
                    }
                }
            }
        }
    }
}
