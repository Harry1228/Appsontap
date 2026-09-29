package com.example.categorydockwidget.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.categorydockwidget.data.AppIconHelper
import com.example.categorydockwidget.data.AppModel
import com.example.categorydockwidget.data.AppRepository
import com.example.categorydockwidget.data.Category
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
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF7C3AED),
                    background = Color(0xFFF8F7FC),
                    surface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF8F7FC)
                ) {
                    MainScreen(
                        onSave = { updatedCategories, pos, align, display, sidebarSp, appDp, font, colorStyle, iconPack ->
                            saveAndSync(updatedCategories, pos, align, display, sidebarSp, appDp, font, colorStyle, iconPack, appWidgetId)
                        }
                    )
                }
            }
        }
    }

    private fun saveAndSync(
        categories: List<Category>,
        sidebarPosition: String,
        sidebarAlignment: String,
        sidebarDisplay: String,
        sidebarSizeSp: Int,
        categoryIconSizeDp: Int,
        sidebarFont: String,
        iconColorStyle: String,
        iconPack: String,
        appWidgetId: Int
    ) {
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val oldStyle = prefs.getString("icon_color_style", "default")
        val oldPack = prefs.getString("selected_icon_pack", "none")

        if (oldStyle != iconColorStyle || oldPack != iconPack) {
            AppIconHelper.clearCache(this)
        }

        prefs.edit()
            .putString("categories_json", Gson().toJson(categories))
            .putString("sidebar_position", sidebarPosition)
            .putString("sidebar_alignment", sidebarAlignment)
            .putString("sidebar_display_type", sidebarDisplay)
            .putInt("sidebar_icon_size_sp", sidebarSizeSp)
            .putInt("category_icon_size_dp", categoryIconSizeDp)
            .putString("sidebar_font_family", sidebarFont)
            .putString("icon_color_style", iconColorStyle)
            .putString("selected_icon_pack", iconPack)
            .apply()

        AppIconHelper.prewarmIcons(this, categories)
        CategoryWidgetProvider.updateAllWidgets(this)

        Toast.makeText(this, "Settings Applied & Widget Updated!", Toast.LENGTH_SHORT).show()
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
        }
        // Removed finish() to stay in the app after saving
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSave: (List<Category>, String, String, String, Int, Int, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE) }

    var selectedMainTab by remember { mutableIntStateOf(1) }
    var editingCategoryId by remember { mutableStateOf<String?>(null) }

    // Dialogs
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var showIconDialog by remember { mutableStateOf(false) }
    var iconInputCustom by remember { mutableStateOf("") }
    var showFontDialog by remember { mutableStateOf(false) }

    var categories by remember {
        val rawJson = prefs.getString("categories_json", null)
        val initial = if (rawJson != null) {
            try {
                val type = object : TypeToken<List<Category>>() {}.type
                Gson().fromJson<List<Category>>(rawJson, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        mutableStateOf(initial)
    }

    var sidebarPosition by remember {
        mutableStateOf(prefs.getString("sidebar_position", "right") ?: "right")
    }
    var sidebarAlignment by remember {
        mutableStateOf(prefs.getString("sidebar_alignment", "bottom") ?: "bottom")
    }
    var sidebarDisplayType by remember {
        mutableStateOf(prefs.getString("sidebar_display_type", "heading") ?: "heading")
    }
    var sidebarSizeSp by remember {
        mutableFloatStateOf(prefs.getInt("sidebar_icon_size_sp", 14).toFloat())
    }
    var categoryIconSizeDp by remember {
        mutableFloatStateOf(prefs.getInt("category_icon_size_dp", 46).toFloat())
    }
    var sidebarFont by remember {
        mutableStateOf(prefs.getString("sidebar_font_family", "sans-serif") ?: "sans-serif")
    }

    var iconColorStyle by remember {
        mutableStateOf(prefs.getString("icon_color_style", "default") ?: "default")
    }
    var selectedIconPack by remember {
        mutableStateOf(prefs.getString("selected_icon_pack", "none") ?: "none")
    }
    var installedIconPacks by remember {
        mutableStateOf<List<AppModel>>(emptyList())
    }

    val cachedList = remember { AppRepository.getCachedApps(context) }
    var installedApps by remember { mutableStateOf(cachedList) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(cachedList.isEmpty()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val packs = AppIconHelper.getInstalledIconPacks(context)
            val freshApps = AppRepository.reloadApps(context)
            withContext(Dispatchers.Main) {
                installedIconPacks = packs
                installedApps = freshApps
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, installedApps) {
        val list = if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
        list.distinctBy { it.packageName }
    }

    val fontOptions = listOf(
        "sans-serif" to "Modern Sans (System Default)",
        "sans-serif-medium" to "Medium Sans",
        "sans-serif-black" to "Heavy Bold Black",
        "sans-serif-light" to "Light Clean Sans",
        "sans-serif-thin" to "Ultra Thin Sans",
        "sans-serif-condensed" to "Condensed Clean",
        "sans-serif-condensed-medium" to "Condensed Medium",
        "sans-serif-condensed-light" to "Condensed Light",
        "serif" to "Classic Elegant Serif",
        "serif-monospace" to "Serif Monospace",
        "monospace" to "Tech Monospace",
        "casual" to "Casual Handwritten",
        "cursive" to "Cursive Script"
    )

    fun getFontLabel(fontKey: String): String {
        return fontOptions.firstOrNull { it.first == fontKey }?.second ?: "Modern Sans"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Apps Widget",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color(0xFF1E1B2E)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp
            ) {
                Button(
                    onClick = {
                        onSave(
                            categories,
                            sidebarPosition,
                            sidebarAlignment,
                            sidebarDisplayType,
                            sidebarSizeSp.toInt(),
                            categoryIconSizeDp.toInt(),
                            sidebarFont,
                            iconColorStyle,
                            selectedIconPack
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Text("Save & Apply Changes", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        },
        containerColor = Color(0xFFF8F7FC)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedMainTab,
                containerColor = Color.White,
                contentColor = Color(0xFF7C3AED)
            ) {
                listOf("Categories", "Side Bar", "General").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedMainTab == index,
                        onClick = {
                            selectedMainTab = index
                            editingCategoryId = null
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedMainTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 15.sp,
                                color = if (selectedMainTab == index) Color(0xFF7C3AED) else Color(0xFF6B7280)
                            )
                        }
                    )
                }
            }

            // TAB 0: CATEGORIES
            if (selectedMainTab == 0) {
                val currentCategory = categories.firstOrNull { it.id == editingCategoryId }

                if (currentCategory != null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { editingCategoryId = null }) {
                                Text("← Back", color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${currentCategory.packageNames.size} apps selected",
                                color = Color(0xFF6B7280),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFFEDE9FE))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(Color(0xFFF3E8FF), CircleShape)
                                        .clickable {
                                            iconInputCustom = ""
                                            showIconDialog = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentCategory.displayBadge,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7C3AED)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentCategory.name,
                                        color = Color(0xFF1E1B2E),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Rename",
                                            color = Color(0xFF7C3AED),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable {
                                                renameValue = currentCategory.name
                                                showRenameDialog = true
                                            }
                                        )
                                        Text("•", color = Color.Gray, fontSize = 12.sp)
                                        Text(
                                            text = "Change Icon",
                                            color = Color(0xFF7C3AED),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable {
                                                iconInputCustom = ""
                                                showIconDialog = true
                                            }
                                        )
                                        Text("•", color = Color.Gray, fontSize = 12.sp)
                                        Text(
                                            text = "Delete",
                                            color = Color(0xFFEF4444),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable {
                                                categories = categories.filter { it.id != currentCategory.id }
                                                editingCategoryId = null
                                            }
                                        )
                                    }
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
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color(0xFF7C3AED),
                                unfocusedBorderColor = Color(0xFFEDE9FE)
                            )
                        )

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF7C3AED))
                            }
                        } else {
                            val selectedSet = remember(currentCategory.packageNames) {
                                currentCategory.packageNames.toSet()
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                items(
                                    items = filteredApps,
                                    key = { it.packageName }
                                ) { app ->
                                    val isChecked = selectedSet.contains(app.packageName)
                                    AppItemRow(
                                        appName = app.appName,
                                        packageName = app.packageName,
                                        isChecked = isChecked,
                                        onToggle = {
                                            val updatedList = if (isChecked) {
                                                currentCategory.packageNames - app.packageName
                                            } else {
                                                currentCategory.packageNames + app.packageName
                                            }
                                            categories = categories.map {
                                                if (it.id == currentCategory.id) it.copy(packageNames = updatedList) else it
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Button(
                                onClick = {
                                    if (categories.size >= 4) {
                                        Toast.makeText(context, "Maximum 4 categories for widget dock", Toast.LENGTH_SHORT).show()
                                    } else {
                                        newCategoryName = ""
                                        showAddCategoryDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                            ) {
                                Text("+ Add Category", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        item {
                            SettingsCard(
                                title = "App Icon Size",
                                titleColor = Color(0xFF7C3AED),
                                subtitle = "Adjust how large app icons appear inside the widget grid."
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Icon Dimension", fontSize = 14.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
                                        Text("${categoryIconSizeDp.toInt()} dp", fontSize = 14.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = categoryIconSizeDp,
                                        onValueChange = { categoryIconSizeDp = it },
                                        valueRange = 32f..54f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF7C3AED),
                                            activeTrackColor = Color(0xFF7C3AED),
                                            inactiveTrackColor = Color(0xFFEDE9FE)
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Configured Categories (${categories.size}/4):",
                                color = Color(0xFF6B7280),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (categories.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No categories yet.\nTap \"+ Add Category\" to start!",
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(categories, key = { it.id }) { cat ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { editingCategoryId = cat.id },
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFFEDE9FE))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .background(Color(0xFFF3E8FF), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = cat.displayBadge,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF7C3AED)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cat.name,
                                                color = Color(0xFF1E1B2E),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${cat.packageNames.size} apps configured (scrollable)",
                                                color = Color(0xFF6B7280),
                                                fontSize = 12.sp
                                            )
                                        }

                                        Text(
                                            text = "Edit →",
                                            color = Color(0xFF7C3AED),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 1: SIDE BAR
            if (selectedMainTab == 1) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SettingsCard(
                            title = "Sidebar Placement",
                            titleColor = Color(0xFF2563EB),
                            subtitle = "Dock the category sidebar to the left or right side of your screen."
                        ) {
                            Column {
                                RadioOption(
                                    label = "Right Side (Default)",
                                    isSelected = sidebarPosition == "right",
                                    onClick = { sidebarPosition = "right" }
                                )
                                RadioOption(
                                    label = "Left Side",
                                    isSelected = sidebarPosition == "left",
                                    onClick = { sidebarPosition = "left" }
                                )
                            }
                        }
                    }

                    item {
                        SettingsCard(
                            title = "Vertical Alignment",
                            titleColor = Color(0xFF0284C7),
                            subtitle = "Position the sidebar tabs at the top, middle, or bottom of the widget."
                        ) {
                            Column {
                                RadioOption(
                                    label = "Top",
                                    isSelected = sidebarAlignment == "top",
                                    onClick = { sidebarAlignment = "top" }
                                )
                                RadioOption(
                                    label = "Middle (Center)",
                                    isSelected = sidebarAlignment == "center",
                                    onClick = { sidebarAlignment = "center" }
                                )
                                RadioOption(
                                    label = "Bottom",
                                    isSelected = sidebarAlignment == "bottom",
                                    onClick = { sidebarAlignment = "bottom" }
                                )
                            }
                        }
                    }

                    item {
                        SettingsCard(
                            title = "Tab Display Style",
                            titleColor = Color(0xFFDB2777),
                            subtitle = "Choose whether category tabs display heading text labels or icons."
                        ) {
                            Column {
                                RadioOption(
                                    label = "Heading (Text)",
                                    isSelected = sidebarDisplayType == "heading",
                                    onClick = { sidebarDisplayType = "heading" }
                                )
                                RadioOption(
                                    label = "Icons (Emoji / Badge)",
                                    isSelected = sidebarDisplayType == "icons",
                                    onClick = { sidebarDisplayType = "icons" }
                                )
                            }
                        }
                    }

                    // Compact Sidebar Heading Font Selector Card
                    item {
                        SettingsCard(
                            title = "Sidebar Heading Font",
                            titleColor = Color(0xFF059669),
                            subtitle = "Choose font style for sidebar labels & widget headings."
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFontDialog = true }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Current Font",
                                        fontSize = 12.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                    Text(
                                        text = getFontLabel(sidebarFont),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E1B2E)
                                    )
                                }
                                Text(
                                    text = "Change Font →",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF7C3AED)
                                )
                            }
                        }
                    }

                    item {
                        SettingsCard(
                            title = "Sidebar Item Size",
                            titleColor = Color(0xFF7C3AED),
                            subtitle = "Control text label size and badge scale on the sidebar dock."
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Text / Badge Scale", fontSize = 14.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
                                    Text("${sidebarSizeSp.toInt()} sp", fontSize = 14.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = sidebarSizeSp,
                                    onValueChange = { sidebarSizeSp = it },
                                    valueRange = 10f..22f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF7C3AED),
                                        activeTrackColor = Color(0xFF7C3AED),
                                        inactiveTrackColor = Color(0xFFEDE9FE)
                                    )
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            // TAB 2: GENERAL
            if (selectedMainTab == 2) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SettingsCard(
                            title = "App Icon Color Theme",
                            titleColor = Color(0xFF2563EB),
                            subtitle = "Switch between colorful default icons or clean minimalist monochrome icons."
                        ) {
                            Column {
                                RadioOption(
                                    label = "Default (Original App Colors)",
                                    isSelected = iconColorStyle == "default",
                                    onClick = { iconColorStyle = "default" }
                                )
                                RadioOption(
                                    label = "Monochrome White (Minimal)",
                                    isSelected = iconColorStyle == "white",
                                    onClick = { iconColorStyle = "white" }
                                )
                                RadioOption(
                                    label = "Monochrome Black (Stealth)",
                                    isSelected = iconColorStyle == "black",
                                    onClick = { iconColorStyle = "black" }
                                )
                            }
                        }
                    }

                    item {
                        SettingsCard(
                            title = "Icon Pack (Select Icon App)",
                            titleColor = Color(0xFF7C3AED),
                            subtitle = "Apply custom icons from installed launcher icon pack apps."
                        ) {
                            Column {
                                RadioOption(
                                    label = "None (System Default Icons)",
                                    isSelected = selectedIconPack == "none" || selectedIconPack.isBlank(),
                                    onClick = { selectedIconPack = "none" }
                                )

                                if (installedIconPacks.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "No third-party icon packs detected on device. You can install packs (e.g. Whicons, Delta, Viral) from Google Play Store.",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                } else {
                                    installedIconPacks.forEach { pack ->
                                        RadioOption(
                                            label = pack.appName,
                                            isSelected = selectedIconPack == pack.packageName,
                                            onClick = { selectedIconPack = pack.packageName }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SettingsCard(
                            title = "About Apps Widget",
                            titleColor = Color(0xFF6B7280),
                            subtitle = "High-performance Nova-grade native launcher widget."
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Zero-latency native RemoteViews engine with scrollable collections, icon theme masking, and in-memory synchronization.",
                                    color = Color(0xFF4B5563),
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        categories = emptyList()
                                        sidebarPosition = "right"
                                        sidebarAlignment = "bottom"
                                        sidebarDisplayType = "heading"
                                        sidebarSizeSp = 14f
                                        categoryIconSizeDp = 46f
                                        sidebarFont = "sans-serif"
                                        iconColorStyle = "default"
                                        selectedIconPack = "none"
                                        AppIconHelper.clearCache(context)
                                        Toast.makeText(context, "Reset completed. Tap Save to apply.", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Clear All Data & Reset", color = Color.White)
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Font Picker Dialog
    if (showFontDialog) {
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text("Select Font Style", fontWeight = FontWeight.Bold, color = Color(0xFF1E1B2E)) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(fontOptions) { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sidebarFont = key
                                    showFontDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .border(
                                        width = 2.dp,
                                        color = if (sidebarFont == key) Color(0xFF7C3AED) else Color(0xFF9CA3AF),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (sidebarFont == key) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .background(Color(0xFF7C3AED), CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (sidebarFont == key) FontWeight.Bold else FontWeight.Normal,
                                color = if (sidebarFont == key) Color(0xFF7C3AED) else Color(0xFF1E1B2E)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontDialog = false }) {
                    Text("Close", color = Color(0xFF7C3AED))
                }
            },
            containerColor = Color.White
        )
    }

    // Add Category Dialog
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("New Category", fontWeight = FontWeight.Bold, color = Color(0xFF1E1B2E)) },
            text = {
                Column {
                    Text("Enter category name (max 12 characters):", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { if (it.length <= 12) newCategoryName = it },
                        placeholder = { Text("e.g. Games, Finance, Work") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCategoryName.trim()
                        if (trimmed.isNotEmpty()) {
                            val newCat = Category(
                                id = "cat_" + System.currentTimeMillis(),
                                name = trimmed,
                                packageNames = emptyList(),
                                icon = "📁"
                            )
                            categories = categories + newCat
                            editingCategoryId = newCat.id
                            showAddCategoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Text("Create & Open", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color.White
        )
    }

    // Rename Category Dialog
    if (showRenameDialog) {
        val currentCategory = categories.firstOrNull { it.id == editingCategoryId }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Category", fontWeight = FontWeight.Bold, color = Color(0xFF1E1B2E)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { if (it.length <= 12) renameValue = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameValue.trim()
                        if (trimmed.isNotEmpty() && currentCategory != null) {
                            categories = categories.map {
                                if (it.id == currentCategory.id) it.copy(name = trimmed) else it
                            }
                            showRenameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Text("Rename", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color.White
        )
    }

    // Change Icon Dialog
    if (showIconDialog) {
        val currentCategory = categories.firstOrNull { it.id == editingCategoryId }
        val iconPresets = listOf("💼", "📱", "🎮", "🎵", "💬", "🛒", "📸", "🛠️", "🌐", "⭐", "📂", "🔥")

        AlertDialog(
            onDismissRequest = { showIconDialog = false },
            title = { Text("Select Dock Icon", fontWeight = FontWeight.Bold, color = Color(0xFF1E1B2E)) },
            text = {
                Column {
                    Text("Tap an emoji or type a custom character/letters:", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        iconPresets.take(6).forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFFF3E8FF), CircleShape)
                                    .clickable {
                                        if (currentCategory != null) {
                                            categories = categories.map {
                                                if (it.id == currentCategory.id) it.copy(icon = emoji) else it
                                            }
                                        }
                                        showIconDialog = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 18.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        iconPresets.drop(6).take(6).forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFFF3E8FF), CircleShape)
                                    .clickable {
                                        if (currentCategory != null) {
                                            categories = categories.map {
                                                if (it.id == currentCategory.id) it.copy(icon = emoji) else it
                                            }
                                        }
                                        showIconDialog = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 18.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = iconInputCustom,
                        onValueChange = { if (it.length <= 2) iconInputCustom = it },
                        placeholder = { Text("Or custom 2 letters / emoji") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = iconInputCustom.trim()
                        if (trimmed.isNotEmpty() && currentCategory != null) {
                            categories = categories.map {
                                if (it.id == currentCategory.id) it.copy(icon = trimmed) else it
                            }
                        }
                        showIconDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Text("Apply", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIconDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color.White
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    titleColor: Color,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFFEDE9FE))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = titleColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFF3E8FF))
            )
            content()
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun RadioOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    width = 2.dp,
                    color = if (isSelected) Color(0xFF7C3AED) else Color(0xFF9CA3AF),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF7C3AED), CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF1E1B2E) else Color(0xFF374151)
        )
    }
}

@Composable
private fun AppItemRow(
    appName: String,
    packageName: String,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFEDE9FE), RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appName,
                color = Color(0xFF1E1B2E),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = packageName,
                color = Color(0xFF6B7280),
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        Checkbox(
            checked = isChecked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF7C3AED),
                uncheckedColor = Color(0xFFD1D5DB)
            )
        )
    }
}
