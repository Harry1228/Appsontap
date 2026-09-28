package com.example.categorydockwidget.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.categorydockwidget.data.AppModel
import com.example.categorydockwidget.data.Category
import com.example.categorydockwidget.data.WidgetKeys
import com.example.categorydockwidget.widget.CategoryWidgetProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121217)
                ) {
                    ConfigScreen(
                        onSave = { updatedCategories ->
                            saveAndSync(updatedCategories, appWidgetId)
                        }
                    )
                }
            }
        }
    }

    private fun saveAndSync(categories: List<Category>, appWidgetId: Int) {
        val json = Gson().toJson(categories)
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("categories_json", json).apply()

        // Instantly notify all home screen widgets
        CategoryWidgetProvider.updateAllWidgets(this)

        Toast.makeText(this, "Widgets updated!", Toast.LENGTH_SHORT).show()
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
        }
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(onSave: (List<Category>) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var categories by remember {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val rawJson = prefs.getString("categories_json", null)
        val initialList = if (rawJson != null) {
            val type = object : TypeToken<List<Category>>() {}.type
            Gson().fromJson<List<Category>>(rawJson, type)
        } else {
            WidgetKeys.DEFAULT_CATEGORIES
        }
        mutableStateOf(initialList)
    }

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var installedApps by remember { mutableStateOf<List<AppModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
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

            installedApps = apps
            isLoading = false
        }
    }

    val activeCategory = categories.getOrNull(selectedCategoryIndex) ?: categories.first()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apps on Tap Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E26),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF1E1E26),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Button(
                    onClick = { onSave(categories) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("Save & Update Widgets", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        containerColor = Color(0xFF121217)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedCategoryIndex,
                containerColor = Color(0xFF181820),
                contentColor = Color.White,
                edgePadding = 16.dp
            ) {
                categories.forEachIndexed { index, category ->
                    Tab(
                        selected = selectedCategoryIndex == index,
                        onClick = { selectedCategoryIndex = index },
                        text = {
                            Text(
                                text = category.name,
                                fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select apps for \"${activeCategory.name}\" (${activeCategory.packageNames.size} selected):",
                color = Color(0xFFAAAAAF),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF3B82F6))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(installedApps) { app ->
                        val isChecked = activeCategory.packageNames.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFF1C1C24), RoundedCornerShape(12.dp))
                                .clickable {
                                    val updatedList = if (isChecked) {
                                        activeCategory.packageNames - app.packageName
                                    } else {
                                        activeCategory.packageNames + app.packageName
                                    }
                                    categories = categories.toMutableList().also { list ->
                                        list[selectedCategoryIndex] = activeCategory.copy(packageNames = updatedList)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = app.packageName,
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF3B82F6),
                                    uncheckedColor = Color.Gray
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
