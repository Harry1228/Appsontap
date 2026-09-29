package com.example.categorydockwidget.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import java.io.File
import java.io.FileOutputStream

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
                        onSave = { updatedCategories, pos, align, display, sidebarSp, appDp, font, unifiedStyle, clockEnabled, clockFont, clockSize ->
                            saveAndSync(updatedCategories, pos, align, display, sidebarSp, appDp, font, unifiedStyle, clockEnabled, clockFont, clockSize, appWidgetId)
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
        unifiedIconStyle: String,
        clockEnabled: Boolean,
        clockFont: String,
        clockSizeSp: Int,
        appWidgetId: Int
    ) {
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val oldStyle = prefs.getString("unified_icon_style", "default")

        if (oldStyle != unifiedIconStyle) {
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
            .putString("unified_icon_style", unifiedIconStyle)
            .putBoolean("clock_enabled", clockEnabled)
            .putString("clock_font", clockFont)
            .putInt("clock_size_sp", clockSizeSp)
            .apply()

        AppIconHelper.prewarmIcons(this, categories)
        CategoryWidgetProvider.updateAllWidgets(this)

        Toast.makeText(this, "Settings Applied & Widget Updated!", Toast.LENGTH_SHORT).show()
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
        }
    }
}

@Composable
fun CategoryBadgeView(category: Category, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val galleryBitmap = remember(category.icon) {
        if (category.isGallery && category.galleryFileName != null) {
            val file = File(context.filesDir, "category_icons/${category.galleryFileName}")
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
        } else null
    }

    Box(
        modifier = modifier
            .size(size)
            .background(Color(0xFFF3E8FF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (galleryBitmap != null) {
            Image(
                bitmap = galleryBitmap,
                contentDescription = category.name,
                modifier = Modifier
                    .size(size * 0.68f)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = category.displayBadge,
                fontSize = if (category.displayBadge.length > 2) (size.value * 0.28f).sp else (size.value * 0.40f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7C3AED),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSave: (List<Category>, String, String, String, Int, Int, String, String, Boolean, String, Int) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE) }

    var selectedMainTab by remember { mutableIntStateOf(1) }
    var editingCategoryId by remember { mutableStateOf<String?>(null) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var showIconDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showClockFontDialog by remember { mutableStateOf(false) }

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
        mutableStateOf(prefs.getString("sidebar_position", "left") ?: "left")
    }
    var sidebarAlignment by remember {
        mutableStateOf(prefs.getString("sidebar_alignment", "bottom") ?: "bottom")
    }
    var sidebarDisplayType by remember {
        mutableStateOf(prefs.getString("sidebar_display_type", "icons") ?: "icons")
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

    // Only default or icon pack
    var unifiedIconStyle by remember {
        val saved = prefs.getString("unified_icon_style", "default") ?: "default"
        mutableStateOf(if (saved == "white" || saved == "black") "default" else saved)
    }
    var installedIconPacks by remember {
        mutableStateOf<List<AppModel>>(emptyList())
    }

    // Clock preferences
    var clockEnabled by remember {
        mutableStateOf(prefs.getBoolean("clock_enabled", true))
    }
    var clockFont by remember {
        mutableStateOf(prefs.getString("clock_font", "sans-serif") ?: "sans-serif")
    }
    var clockSizeSp by remember {
        mutableFloatStateOf(prefs.getInt("clock_size_sp", 26).toFloat())
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

    // Gallery Picker Contract with square center cropping
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && editingCategoryId != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val rawBitmap = BitmapFactory.decodeStream(inputStream)
                if (rawBitmap != null) {
                    val iconDir = File(context.filesDir, "category_icons")
                    if (!iconDir.exists()) iconDir.mkdirs()
                    val fileName = "cat_${editingCategoryId}_${System.currentTimeMillis()}.png"
                    val file = File(iconDir, fileName)

                    val minDim = minOf(rawBitmap.width, rawBitmap.height)
                    val squareBitmap = Bitmap.createBitmap(rawBitmap, (rawBitmap.width - minDim) / 2, (rawBitmap.height - minDim) / 2, minDim, minDim)
                    val scaled = Bitmap.createScaledBitmap(squareBitmap, 128, 128, true)
                    FileOutputStream(file).use { out ->
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }

                    categories = categories.map {
                        if (it.id == editingCategoryId) it.copy(icon = "gallery:$fileName") else it
                    }
                    showIconDialog = false
                    Toast.makeText(context, "Gallery Icon Applied!", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 6 visually distinctive, expressive typefaces
    val fontOptions = listOf(
        "sans-serif" to "Modern Sans (Clean Geometric)",
        "serif" to "Classic Serif (Literary & Editorial)",
        "monospace" to "Tech Monospace (Cyber & Terminal)",
        "casual" to "Casual Script (Hand-Drawn & Friendly)",
        "cursive" to "Cursive Handwriting (Calligraphy Style)",
        "sans-serif-condensed" to "Ultra Condensed (Tall & Compact)"
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
                            unifiedIconStyle,
                            clockEnabled,
                            clockFont,
                            clockSizeSp.toInt()
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
                                // Dedicated Badge View that renders gallery image or single-line text
                                CategoryBadgeView(
                                    category = currentCategory,
                                    size = 54.dp,
                                    modifier = Modifier.clickable { showIconDialog = true }
                                )

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
                                            modifier = Modifier.clickable { showIconDialog = true }
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
                                    if (categories.size >= 6) {
                                        Toast.makeText(context, "Maximum 6 categories for widget dock", Toast.LENGTH_SHORT).show()
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
                                text = "Configured Categories (${categories.size}/6):",
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
                                        CategoryBadgeView(category = cat, size = 46.dp)

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cat.name,
                                                color = Color(0xFF1E1B2E),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${cat.packageNames.size} apps configured",
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

            // TAB 1: SIDE BAR (Placement, Clock, Font, Size)
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
                                    label = "Left Side",
                                    isSelected = sidebarPosition == "left",
                                    onClick = { sidebarPosition = "left" }
                                )
                                RadioOption(
                                    label = "Right Side",
                                    isSelected = sidebarPosition == "right",
                                    onClick = { sidebarPosition = "right" }
                                )
                            }
                        }
                    }

                    item {
                        SettingsCard(
                            title = "Sidebar Clock",
                            titleColor = Color(0xFF7C3AED),
                            subtitle = "Enable or customize the stacked digital clock above the sidebar."
                        ) {
                            Column {
                                RadioOption(
                                    label = "Show Clock (On)",
                                    isSelected = clockEnabled,
                                    onClick = { clockEnabled = true }
                                )
                                RadioOption(
                                    label = "Hide Clock (Off)",
                                    isSelected = !clockEnabled,
                                    onClick = { clockEnabled = false }
                                )

                                if (clockEnabled) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showClockFontDialog = true }
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Clock Font", fontSize = 12.sp, color = Color(0xFF6B7280))
                                            Text(getFontLabel(clockFont), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B2E))
                                        }
                                        Text("Change Font →", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7C3AED))
                                    }

                                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Clock Size", fontSize = 13.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
                                            Text("${clockSizeSp.toInt()} sp", fontSize = 13.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = clockSizeSp,
                                            onValueChange = { clockSizeSp = it },
                                            valueRange = 18f..38f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color(0xFF7C3AED),
                                                activeTrackColor = Color(0xFF7C3AED),
                                                inactiveTrackColor = Color(0xFFEDE9FE)
                                            )
                                        )
                                    }
                                }
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
                                    label = "Bottom",
                                    isSelected = sidebarAlignment == "bottom",
                                    onClick = { sidebarAlignment = "bottom" }
                                )
                                RadioOption(
                                    label = "Middle (Center)",
                                    isSelected = sidebarAlignment == "center",
                                    onClick = { sidebarAlignment = "center" }
                                )
                                RadioOption(
                                    label = "Top",
                                    isSelected = sidebarAlignment == "top",
                                    onClick = { sidebarAlignment = "top" }
                                )
                            }
                        }
                    }

                    item {
                        SettingsCard(
                            title = "Tab Display Style",
                            titleColor = Color(0xFFDB2777),
                            subtitle = "Choose whether category tabs display icons or full text labels."
                        ) {
                            Column {
                                RadioOption(
                                    label = "Icons (Symbols / Emoji / Gallery)",
                                    isSelected = sidebarDisplayType == "icons",
                                    onClick = { sidebarDisplayType = "icons" }
                                )
                                RadioOption(
                                    label = "Heading (Full Text Labels)",
                                    isSelected = sidebarDisplayType == "heading",
                                    onClick = { sidebarDisplayType = "heading" }
                                )
                            }
                        }
                    }

                    item {
                        SettingsCard(
                            title = "Sidebar Heading Font",
                            titleColor = Color(0xFF059669),
                            subtitle = "Select font typeface for sidebar labels and headings."
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
                            subtitle = "Control text label size, symbol size, and gallery icon scale on the sidebar dock."
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Badge / Text / Icon Scale", fontSize = 14.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
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

            // TAB 2: GENERAL (Only Default & Icon Pack)
            if (selectedMainTab == 2) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SettingsCard(
                            title = "App Icon Style & Pack",
                            titleColor = Color(0xFF2563EB),
                            subtitle = "Select whether to use default system app icons or an installed third-party icon pack."
                        ) {
                            Column {
                                RadioOption(
                                    label = "Default (Original System Colors)",
                                    isSelected = unifiedIconStyle == "default",
                                    onClick = { unifiedIconStyle = "default" }
                                )

                                if (installedIconPacks.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "Installed Icon Packs:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF7C3AED)
                                        )
                                    }

                                    installedIconPacks.forEach { pack ->
                                        val packVal = "pack:${pack.packageName}"
                                        RadioOption(
                                            label = "${pack.appName} (Icon Pack)",
                                            isSelected = unifiedIconStyle == packVal,
                                            onClick = { unifiedIconStyle = packVal }
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "No icon pack apps detected on your device. You can install Whicons, Flight Lite, or Delta from the Play Store.",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
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
                                    text = "Zero-latency native RemoteViews engine with dynamic font rendering, scrollable collections, and gallery icon integration.",
                                    color = Color(0xFF4B5563),
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        categories = emptyList()
                                        sidebarPosition = "left"
                                        sidebarAlignment = "bottom"
                                        sidebarDisplayType = "icons"
                                        sidebarSizeSp = 14f
                                        categoryIconSizeDp = 46f
                                        sidebarFont = "sans-serif"
                                        unifiedIconStyle = "default"
                                        clockEnabled = true
                                        clockFont = "sans-serif"
                                        clockSizeSp = 26f
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

    // Sidebar Font Dialog
    if (showFontDialog) {
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text("Select Sidebar Font", fontWeight = FontWeight.Bold, color = Color(0xFF1E1B2E)) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                ) {
                    items(fontOptions) { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sidebarFont = key
                                    showFontDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
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
                                    Box(modifier = Modifier.size(9.dp).background(Color(0xFF7C3AED), CircleShape))
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

    // Clock Font Dialog
    if (showClockFontDialog) {
        AlertDialog(
            onDismissRequest = { showClockFontDialog = false },
            title = { Text("Select Clock Font", fontWeight = FontWeight.Bold, color = Color(0xFF1E1B2E)) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                ) {
                    items(fontOptions) { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clockFont = key
                                    showClockFontDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .border(
                                        width = 2.dp,
                                        color = if (clockFont == key) Color(0xFF7C3AED) else Color(0xFF9CA3AF),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (clockFont == key) {
                                    Box(modifier = Modifier.size(9.dp).background(Color(0xFF7C3AED), CircleShape))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (clockFont == key) FontWeight.Bold else FontWeight.Normal,
                                color = if (clockFont == key) Color(0xFF7C3AED) else Color(0xFF1E1B2E)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClockFontDialog = false }) {
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
                        placeholder = { Text("e.g. Home, Bank, Tools, AI") },
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
                                icon = "★"
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

    // Category Icon Dialog (Gallery Picker + Curated Symbols, no bottom letter input box)
    if (showIconDialog) {
        val currentCategory = categories.firstOrNull { it.id == editingCategoryId }

        val bAndWIcons = listOf(
            "★", "☆", "🏠", "📁", "⚡", "🔍", "⚙️", "⏱️",
            "🏛️", "💳", "💰", "📈", "🏦", "💵",
            "🤖", "🧠", "💻", "🔬", "📡", "🌐",
            "🛠️", "🔧", "🔨", "📱", "🔋", "🔑",
            "✈️", "🧭", "🚗", "🚆", "📍", "🗺️",
            "🎬", "🍿", "📺", "🎵", "🎧", "📷",
            "🎮", "🕹️", "🎲", "👾", "🎯", "🏆",
            "💬", "✉️", "📞", "👥", "🔔", "📣",
            "🛍️", "🛒", "🏷️", "☕", "🍕", "🍔"
        )

        AlertDialog(
            onDismissRequest = { showIconDialog = false },
            title = { Text("Category Icon", fontWeight = FontWeight.Bold, color = Color(0xFF1E1B2E)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Upload from Gallery Button
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("📁 Upload Icon from Gallery", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Or select a symbol:", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(bAndWIcons.chunked(6)) { rowIcons ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                rowIcons.forEach { symbol ->
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(Color(0xFFF3E8FF), CircleShape)
                                            .clickable {
                                                if (currentCategory != null) {
                                                    categories = categories.map {
                                                        if (it.id == currentCategory.id) it.copy(icon = symbol) else it
                                                    }
                                                }
                                                showIconDialog = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(symbol, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
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
