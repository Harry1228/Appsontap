package com.example.categorydockwidget.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.categorydockwidget.R
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

data class CategoryBackupItem(
    val id: String,
    val name: String,
    val packageNames: List<String>,
    val icon: String?,
    val galleryBase64: String? = null
)

data class WidgetFullBackup(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val categories: List<CategoryBackupItem>,
    val sidebarPosition: String,
    val sidebarAlignment: String,
    val sidebarDisplayType: String,
    val sidebarSizeSp: Int,
    val categoryIconSizeDp: Int,
    val sidebarFontFamily: String,
    val unifiedIconStyle: String,
    val clockEnabled: Boolean,
    val clockFont: String,
    val clockSizeSp: Int
)

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

    var selectedMainTab by remember { mutableIntStateOf(0) }
    var editingCategoryId by remember { mutableStateOf<String?>(null) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var showIconDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showClockFontDialog by remember { mutableStateOf(false) }

    // App-specific icon customization state
    var editingAppForIcon by remember { mutableStateOf<AppModel?>(null) }
    var iconUpdateCounter by remember { mutableIntStateOf(0) }

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
    var unifiedIconStyle by remember {
        val saved = prefs.getString("unified_icon_style", "default") ?: "default"
        mutableStateOf(if (saved == "white" || saved == "black") "default" else saved)
    }
    var installedIconPacks by remember {
        mutableStateOf<List<AppModel>>(emptyList())
    }

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

    val currentCategory = categories.firstOrNull { it.id == editingCategoryId }
    val selectedSet = remember(currentCategory?.packageNames, editingCategoryId) {
        currentCategory?.packageNames?.toSet() ?: emptySet()
    }

    // 1. FILTER & SORT: Selected apps float automatically to the top
    val filteredApps = remember(searchQuery, installedApps, selectedSet) {
        val query = searchQuery.trim().lowercase()
        val list = if (query.isBlank()) {
            installedApps
        } else {
            val isPhoneQuery = query == "phone" || query == "call" || query == "dial" || query == "dialer"
            installedApps.filter { app ->
                val name = app.appName.lowercase()
                val pkg = app.packageName.lowercase()
                name.contains(query) || pkg.contains(query) || (isPhoneQuery && (
                    name.contains("dial") || 
                    name.contains("call") || 
                    pkg.contains("dialer") || 
                    pkg.contains("telecom") || 
                    name.contains("phone")
                ))
            }
        }

        // Float selected apps directly to top
        list.sortedWith(
            compareByDescending<AppModel> { app ->
                selectedSet.contains(app.id) || selectedSet.contains(app.packageName)
            }.thenBy { it.appName.lowercase() }
        )
    }

    // Modern Minimal Category & App Symbols
    val modernCuratedSymbols = listOf(
        // Core & Action
        "⌂", "✦", "◈", "⌘", "⚡", "⚙", "◉", "★",
        // Communication
        "✆", "✉", "💬", "◎", "👥", "🔔", "📣", "📡",
        // Finance
        "💳", "🏛", "💼", "₹", "$", "📈", "💰", "🏷",
        // Tools & Tech
        "🛠", "✎", "⌕", "📁", "🔒", "⊞", "▦", "⬡",
        // Media & Entertainment
        "▶", "♫", "🎬", "📷", "🎮", "🕹", "🎧", "📺",
        // Travel & Lifestyle
        "✈", "🧭", "🚗", "☕", "🛒", "🛍", "♥", "⏱"
    )

    // Launcher for App-Specific Custom Gallery Icon
    val appGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && editingAppForIcon != null) {
            try {
                val app = editingAppForIcon!!
                val inputStream = context.contentResolver.openInputStream(uri)
                val rawBitmap = BitmapFactory.decodeStream(inputStream)
                if (rawBitmap != null) {
                    val iconDir = File(context.filesDir, "category_icons")
                    if (!iconDir.exists()) iconDir.mkdirs()
                    val fileName = "app_${app.packageName.replace('.', '_')}_${System.currentTimeMillis()}.png"
                    val file = File(iconDir, fileName)

                    val minDim = minOf(rawBitmap.width, rawBitmap.height)
                    val square = Bitmap.createBitmap(rawBitmap, (rawBitmap.width - minDim) / 2, (rawBitmap.height - minDim) / 2, minDim, minDim)
                    val scaled = Bitmap.createScaledBitmap(square, 96, 96, true)
                    FileOutputStream(file).use { out ->
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }

                    prefs.edit().putString("custom_app_icon_${app.packageName}", "gallery:$fileName").apply()
                    AppIconHelper.clearCache(context)
                    editingAppForIcon = null
                    iconUpdateCounter++
                    Toast.makeText(context, "App icon updated!", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 5. NOVA-STYLE APP SHORTCUT PICKER
    val shortcutPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null && editingCategoryId != null) {
            val intentData = result.data!!
            val shortcutIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intentData.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intentData.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
            }

            val shortcutName = intentData.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Shortcut"
            val shortcutBitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intentData.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
            } else {
                @Suppress("DEPRECATION")
                intentData.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON)
            }

            if (shortcutIntent != null) {
                val shortcutId = "sc_${System.currentTimeMillis()}"
                val iconDir = File(context.filesDir, "cached_icons")
                if (!iconDir.exists()) iconDir.mkdirs()

                if (shortcutBitmap != null) {
                    val iconFile = File(iconDir, "$shortcutId.png")
                    FileOutputStream(iconFile).use { out ->
                        shortcutBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }

                prefs.edit()
                    .putString("shortcut_uri_$shortcutId", shortcutIntent.toUri(Intent.URI_INTENT_SCHEME))
                    .putString("shortcut_label_$shortcutId", shortcutName)
                    .apply()

                val shortcutKey = "shortcut:$shortcutId"
                categories = categories.map {
                    if (it.id == editingCategoryId) it.copy(packageNames = it.packageNames + shortcutKey) else it
                }
                Toast.makeText(context, "Shortcut \"$shortcutName\" added!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Backup Export
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val backupCategoryItems = categories.map { cat ->
                    var base64Data: String? = null
                    if (cat.isGallery && cat.galleryFileName != null) {
                        val f = File(context.filesDir, "category_icons/${cat.galleryFileName}")
                        if (f.exists()) {
                            base64Data = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                        }
                    }
                    CategoryBackupItem(
                        id = cat.id,
                        name = cat.name,
                        packageNames = cat.packageNames,
                        icon = cat.icon,
                        galleryBase64 = base64Data
                    )
                }

                val backupBundle = WidgetFullBackup(
                    categories = backupCategoryItems,
                    sidebarPosition = sidebarPosition,
                    sidebarAlignment = sidebarAlignment,
                    sidebarDisplayType = sidebarDisplayType,
                    sidebarSizeSp = sidebarSizeSp.toInt(),
                    categoryIconSizeDp = categoryIconSizeDp.toInt(),
                    sidebarFontFamily = sidebarFont,
                    unifiedIconStyle = unifiedIconStyle,
                    clockEnabled = clockEnabled,
                    clockFont = clockFont,
                    clockSizeSp = clockSizeSp.toInt()
                )

                val jsonContent = Gson().toJson(backupBundle)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonContent.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Backup exported successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to export: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Backup Restore
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().use { it.readText() }
                }

                if (!jsonString.isNullOrBlank()) {
                    val backupBundle = Gson().fromJson(jsonString, WidgetFullBackup::class.java)
                    if (backupBundle != null) {
                        val iconDir = File(context.filesDir, "category_icons")
                        if (!iconDir.exists()) iconDir.mkdirs()

                        val restoredCategories = backupBundle.categories.map { item ->
                            if (!item.galleryBase64.isNullOrBlank() && item.icon?.startsWith("gallery:") == true) {
                                val fileName = item.icon.removePrefix("gallery:")
                                val f = File(iconDir, fileName)
                                val bytes = Base64.decode(item.galleryBase64, Base64.DEFAULT)
                                f.writeBytes(bytes)
                            }
                            Category(
                                id = item.id,
                                name = item.name,
                                packageNames = item.packageNames,
                                icon = item.icon
                            )
                        }

                        categories = restoredCategories
                        sidebarPosition = backupBundle.sidebarPosition
                        sidebarAlignment = backupBundle.sidebarAlignment
                        sidebarDisplayType = backupBundle.sidebarDisplayType
                        sidebarSizeSp = backupBundle.sidebarSizeSp.toFloat()
                        categoryIconSizeDp = backupBundle.categoryIconSizeDp.toFloat()
                        sidebarFont = backupBundle.sidebarFontFamily
                        unifiedIconStyle = backupBundle.unifiedIconStyle
                        clockEnabled = backupBundle.clockEnabled
                        clockFont = backupBundle.clockFont
                        clockSizeSp = backupBundle.clockSizeSp.toFloat()

                        prefs.edit()
                            .putString("categories_json", Gson().toJson(restoredCategories))
                            .putString("sidebar_position", backupBundle.sidebarPosition)
                            .putString("sidebar_alignment", backupBundle.sidebarAlignment)
                            .putString("sidebar_display_type", backupBundle.sidebarDisplayType)
                            .putInt("sidebar_icon_size_sp", backupBundle.sidebarSizeSp)
                            .putInt("category_icon_size_dp", backupBundle.categoryIconSizeDp)
                            .putString("sidebar_font_family", backupBundle.sidebarFontFamily)
                            .putString("unified_icon_style", backupBundle.unifiedIconStyle)
                            .putBoolean("clock_enabled", backupBundle.clockEnabled)
                            .putString("clock_font", backupBundle.clockFont)
                            .putInt("clock_size_sp", backupBundle.clockSizeSp)
                            .apply()

                        AppIconHelper.clearCache(context)
                        AppIconHelper.prewarmIcons(context, restoredCategories)
                        CategoryWidgetProvider.updateAllWidgets(context)

                        showSettingsDialog = false
                        Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to restore: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Category Gallery Picker
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
                    val square = Bitmap.createBitmap(rawBitmap, (rawBitmap.width - minDim) / 2, (rawBitmap.height - minDim) / 2, minDim, minDim)
                    val scaled = Bitmap.createScaledBitmap(square, 128, 128, true)
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
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = "Settings & Backup",
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(24.dp)
                        )
                    }
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

                        // App Shortcuts Integration Button (Nova-grade)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    val pickIntent = Intent(Intent.ACTION_CREATE_SHORTCUT)
                                    val chooser = Intent.createChooser(pickIntent, "Add App Shortcut")
                                    shortcutPickerLauncher.launch(chooser)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("✦ + Add App Shortcut", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        // 4. SEARCH BAR WITH CLEAR CROSS BUTTON
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search installed apps...", color = Color.Gray, fontSize = 14.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Text(
                                            text = "✕",
                                            color = Color(0xFF6B7280),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color(0xFF7C3AED),
                                unfocusedBorderColor = Color(0xFFEDE9FE)
                            )
                        )

                        // App List with selected apps floated to top
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF7C3AED))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                items(
                                    items = filteredApps,
                                    key = { it.id }
                                ) { app ->
                                    val isChecked = selectedSet.contains(app.id) || selectedSet.contains(app.packageName)
                                    AppItemRow(
                                        app = app,
                                        isChecked = isChecked,
                                        onToggle = {
                                            val updatedList = if (isChecked) {
                                                currentCategory.packageNames - app.id - app.packageName
                                            } else {
                                                currentCategory.packageNames + app.id
                                            }
                                            categories = categories.map {
                                                if (it.id == currentCategory.id) it.copy(packageNames = updatedList) else it
                                            }
                                        },
                                        onChangeIcon = {
                                            editingAppForIcon = app
                                        },
                                        updateCounter = iconUpdateCounter
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

            // TAB 2: GENERAL (Exclusive Default vs Icon Pack)
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
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // 2. CHANGE SPECIFIC APP ICON DIALOG
    if (editingAppForIcon != null) {
        val app = editingAppForIcon!!
        AlertDialog(
            onDismissRequest = { editingAppForIcon = null },
            title = {
                Text(
                    text = "Icon for ${app.appName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E1B2E)
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { appGalleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("📁 Upload Icon from Gallery", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            prefs.edit().remove("custom_app_icon_${app.packageName}").remove("custom_app_icon_${app.id}").apply()
                            AppIconHelper.clearCache(context)
                            editingAppForIcon = null
                            iconUpdateCounter++
                            Toast.makeText(context, "Reset to default app icon", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Text("🔄 Reset to Default Icon", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Or choose a modern minimal symbol:", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                    ) {
                        items(modernCuratedSymbols.chunked(6)) { rowIcons ->
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
                                                prefs.edit().putString("custom_app_icon_${app.packageName}", "symbol:$symbol").apply()
                                                AppIconHelper.clearCache(context)
                                                editingAppForIcon = null
                                                iconUpdateCounter++
                                                Toast.makeText(context, "Symbol applied!", Toast.LENGTH_SHORT).show()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(symbol, fontSize = 18.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingAppForIcon = null }) {
                    Text("Cancel", color = Color(0xFF7C3AED))
                }
            },
            containerColor = Color.White
        )
    }

    // 3. CATEGORY ICON DIALOG: Clean modern symbols without letter text box
    if (showIconDialog) {
        AlertDialog(
            onDismissRequest = { showIconDialog = false },
            title = { Text("Category Icon", fontWeight = FontWeight.Bold, color = Color(0xFF1E1B2E)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("📁 Upload Icon from Gallery", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Or choose a modern minimal symbol:", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                    ) {
                        items(modernCuratedSymbols.chunked(6)) { rowIcons ->
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
                                        Text(symbol, fontSize = 18.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
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

    // Preferences & Backup Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "Preferences & Backup",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = Color(0xFF1E1B2E)
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Backup your entire configuration, categories, assigned apps, and custom icons to a JSON file, or restore them anytime.",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            exportBackupLauncher.launch("apps_widget_backup_${System.currentTimeMillis()}.json")
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Text("💾 Backup Configuration to File", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF7C3AED))
                    ) {
                        Text("📂 Restore Configuration from File", color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = Color(0xFFF3E8FF))
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            showResetConfirmDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("⚠️ Reset Everything to Defaults", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Close", color = Color(0xFF7C3AED), fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = Color.White
        )
    }

    // Reset Confirm Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text("Confirm Reset", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
            },
            text = {
                Text(
                    text = "Are you sure you want to clear all categories, assigned apps, custom gallery icons, and reset all layout settings to default?",
                    color = Color(0xFF374151),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
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

                        prefs.edit().clear().apply()
                        val iconDir = File(context.filesDir, "category_icons")
                        if (iconDir.exists()) iconDir.deleteRecursively()
                        AppIconHelper.clearCache(context)
                        CategoryWidgetProvider.updateAllWidgets(context)

                        showResetConfirmDialog = false
                        showSettingsDialog = false
                        Toast.makeText(context, "Reset complete! Everything restored to default.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Yes, Reset All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color.White
        )
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
    app: AppModel,
    isChecked: Boolean,
    onToggle: () -> Unit,
    onChangeIcon: () -> Unit,
    updateCounter: Int
) {
    val context = LocalContext.current
    val appBitmap = remember(app.id, updateCounter) {
        AppIconHelper.getAppBitmap(context, app.id) ?: AppIconHelper.getAppBitmap(context, app.packageName)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFEDE9FE), RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon preview with quick-tap customization
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onChangeIcon),
            contentAlignment = Alignment.Center
        ) {
            if (appBitmap != null) {
                Image(
                    bitmap = appBitmap.asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFEDE9FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", color = Color(0xFF7C3AED))
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                color = Color(0xFF1E1B2E),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = app.packageName,
                color = Color(0xFF6B7280),
                fontSize = 11.sp,
                maxLines = 1
            )
        }

        // Tap icon button to trigger custom app icon dialog
        TextButton(
            onClick = onChangeIcon,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("Icon ✎", fontSize = 11.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
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
