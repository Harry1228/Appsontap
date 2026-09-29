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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.categorydockwidget.R
import com.example.categorydockwidget.data.AppIconHelper
import com.example.categorydockwidget.data.AppModel
import com.example.categorydockwidget.data.AppRepository
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
                    color = Color(0xFF111116)
                ) {
                    MainScreen(
                        onSave = { updatedCategories, accentColorHex, cornerRadius ->
                            saveAndSync(updatedCategories, accentColorHex, cornerRadius, appWidgetId)
                        }
                    )
                }
            }
        }
    }

    private fun saveAndSync(
        categories: List<Category>,
        accentColorHex: String,
        cornerRadius: Int,
        appWidgetId: Int
    ) {
        AppIconHelper.prewarmIcons(this, categories)

        val json = Gson().toJson(categories)
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("categories_json", json)
            .putString("accent_color", accentColorHex)
            .putInt("corner_radius", cornerRadius)
            .apply()

        CategoryWidgetProvider.updateAllWidgets(this)

        Toast.makeText(this, "Settings Applied!", Toast.LENGTH_SHORT).show()
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
        }
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSave: (List<Category>, String, Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE) }

    var selectedMainTab by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    var categories by remember {
        val rawJson = prefs.getString("categories_json", null)
        val initial = if (rawJson != null) {
            try {
                val type = object : TypeToken<List<Category>>() {}.type
                Gson().fromJson<List<Category>>(rawJson, type) ?: WidgetKeys.DEFAULT_CATEGORIES
            } catch (_: Exception) {
                WidgetKeys.DEFAULT_CATEGORIES
            }
        } else {
            WidgetKeys.DEFAULT_CATEGORIES
        }
        mutableStateOf(initial)
    }
    var activeCategoryIndex by remember { mutableIntStateOf(0) }

    var selectedAccentColor by remember {
        mutableStateOf(prefs.getString("accent_color", "#3B82F6") ?: "#3B82F6")
    }
    var selectedRadius by remember {
        mutableIntStateOf(prefs.getInt("corner_radius", 24))
    }

    val cachedList = remember { AppRepository.getCachedApps(context) }
    var installedApps by remember { mutableStateOf(cachedList) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(cachedList.isEmpty()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val freshApps = AppRepository.reloadApps(context)
            withContext(Dispatchers.Main) {
                installedApps = freshApps
                isLoading = false
            }
        }
    }

    val safeIndex = activeCategoryIndex.coerceIn(0, (categories.size - 1).coerceAtLeast(0))
    val activeCategory = categories.getOrNull(safeIndex) ?: WidgetKeys.DEFAULT_CATEGORIES.first()

    val selectedPackageSet = remember(activeCategory.packageNames) {
        activeCategory.packageNames.toSet()
    }

    val filteredApps = remember(searchQuery, installedApps) {
        val list = if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
        list.distinctBy { it.packageName }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Apps on Tap",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF181820)
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF181820),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Button(
                    onClick = { onSave(categories, selectedAccentColor, selectedRadius) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("Save & Apply Changes", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        containerColor = Color(0xFF111116)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedMainTab,
                containerColor = Color(0xFF181820),
                contentColor = Color.White
            ) {
                Tab(
                    selected = selectedMainTab == 0,
                    onClick = { selectedMainTab = 0 },
                    text = {
                        Text(
                            text = "Category",
                            fontWeight = if (selectedMainTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp
                        )
                    }
                )
                Tab(
                    selected = selectedMainTab == 1,
                    onClick = { selectedMainTab = 1 },
                    text = {
                        Text(
                            text = "Sidebar",
                            fontWeight = if (selectedMainTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp
                        )
                    }
                )
            }

            if (selectedMainTab == 0) {
                ScrollableTabRow(
                    selectedTabIndex = safeIndex,
                    containerColor = Color(0xFF15151C),
                    contentColor = Color.White,
                    edgePadding = 16.dp
                ) {
                    categories.forEachIndexed { index, cat ->
                        Tab(
                            selected = safeIndex == index,
                            onClick = { activeCategoryIndex = index },
                            text = { Text(cat.name) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${activeCategory.name} (${activeCategory.packageNames.size}/8 apps)",
                        color = Color(0xFFAAAAAF),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (categories.size > 1) {
                            TextButton(
                                onClick = {
                                    val updated = categories.toMutableList()
                                    updated.removeAt(safeIndex)
                                    categories = updated
                                    activeCategoryIndex = (safeIndex - 1).coerceAtLeast(0)
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                if (categories.size >= 4) {
                                    Toast.makeText(context, "Maximum 4 categories for widget dock", Toast.LENGTH_SHORT).show()
                                } else {
                                    newCategoryName = ""
                                    showAddCategoryDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282834)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("+ Add Category", color = Color(0xFF3B82F6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...", color = Color.Gray, fontSize = 14.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1B1B22),
                        unfocusedContainerColor = Color(0xFF1B1B22),
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF2C2C34),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF3B82F6))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        items(
                            items = filteredApps,
                            key = { it.packageName }
                        ) { app ->
                            val isChecked = selectedPackageSet.contains(app.packageName)
                            AppItemRow(
                                appName = app.appName,
                                packageName = app.packageName,
                                isChecked = isChecked,
                                onToggle = {
                                    val updatedList = if (isChecked) {
                                        activeCategory.packageNames - app.packageName
                                    } else {
                                        if (activeCategory.packageNames.size >= 8) {
                                            Toast.makeText(context, "Max 8 apps per category", Toast.LENGTH_SHORT).show()
                                            activeCategory.packageNames
                                        } else {
                                            activeCategory.packageNames + app.packageName
                                        }
                                    }
                                    val updated = categories.toMutableList()
                                    updated[safeIndex] = activeCategory.copy(packageNames = updatedList)
                                    categories = updated
                                }
                            )
                        }
                    }
                }
            }

            if (selectedMainTab == 1) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Active Pill Accent Color",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val colorOptions = listOf(
                        "#3B82F6" to "Blue",
                        "#8B5CF6" to "Purple",
                        "#10B981" to "Emerald",
                        "#EF4444" to "Red",
                        "#F59E0B" to "Amber",
                        "#EC4899" to "Pink"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        colorOptions.forEach { (hex, _) ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            val isSelected = selectedAccentColor.equals(hex, ignoreCase = true)

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(color, CircleShape)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedAccentColor = hex }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "Widget Corner Radius",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(16 to "Rounded", 24 to "Pill Soft", 32 to "Curved").forEach { (radius, label) ->
                            val isSelected = selectedRadius == radius
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) Color(0xFF3B82F6) else Color(0xFF1B1B22),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedRadius = radius }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = {
                Text(
                    text = "New Category",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter category name (max 12 characters):",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { if (it.length <= 12) newCategoryName = it },
                        placeholder = { Text("Category name...", color = Color.DarkGray) },
                        singleLine = true,
                