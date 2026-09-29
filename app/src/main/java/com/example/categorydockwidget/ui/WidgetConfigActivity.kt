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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val version: Int = 2,
    val timestamp: Long = System.currentTimeMillis(),
    val categories: List<CategoryBackupItem>,
    val sidebarPosition: String,
    val sidebarAlignment: String,
    val sidebarDisplayType: String,
    val sidebarTextSizeSp: Int = 14,
    val sidebarIconSizeSp: Int = 28,
    val sidebarSizeSp: Int = 14,
    val categoryIconSizeDp: Int,
    val sidebarFontFamily: String,
    val unifiedIconStyle: String,
    val clockEnabled: Boolean,
    val clockFont: String,
    val clockSizeSp: Int,
    val animationStyle: String = "fade"
)

data class NovaThemePalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val searchBarBg: Color,
    val cardBorder: Color,
    val primary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val iconTint: Color,
    val divider: Color,
    val bottomBarBg: Color,
    val pillActive: Color
)

class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            val prefs = remember { getSharedPreferences("widget_prefs", Context.MODE_PRIVATE) }
            var isDarkTheme by remember { mutableStateOf(prefs.getBoolean("app_theme_dark", true)) }

            val theme = if (isDarkTheme) {
                NovaThemePalette(
                    isDark = true,
                    background = Color(0xFF1E2029),
                    surface = Color(0xFF262836),
                    searchBarBg = Color(0xFF2B2D3A),
                    cardBorder = Color(0xFF333647),
                    primary = Color(0xFF9095A6),
                    textPrimary = Color(0xFFF1F2F6),
                    textSecondary = Color(0xFF8F94A6),
                    iconTint = Color(0xFFB0B5C6),
                    divider = Color(0xFF2C2F3E),
                    bottomBarBg = Color(0xFF181A22),
                    pillActive = Color(0xFF36394A)
                )
            } else {
                NovaThemePalette(
                    isDark = false,
                    background = Color(0xFFF4F5F9),
                    surface = Color(0xFFFFFFFF),
                    searchBarBg = Color(0xFFE5E7EB),
                    cardBorder = Color(0xFFE2E4EB),
                    primary = Color(0xFF4B5563),
                    textPrimary = Color(0xFF1A1C23),
                    textSecondary = Color(0xFF6B7280),
                    iconTint = Color(0xFF4B5563),
                    divider = Color(0xFFE5E7EB),
                    bottomBarBg = Color(0xFFFFFFFF),
                    pillActive = Color(0xFFE2E4EB)
                )
            }

            MaterialTheme(
                colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = theme.background
                ) {
                    MainScreen(
                        theme = theme,
                        onToggleTheme = { dark ->
                            isDarkTheme = dark
                            prefs.edit().putBoolean("app_theme_dark", dark).apply()
                        },
                        onSave = { updatedCategories, pos, align, display, sidebarTextSp, sidebarIconSp, appDp, font, unifiedStyle, clockEnabled, clockFont, clockSize, animStyle ->
                            saveAndSync(updatedCategories, pos, align, display, sidebarTextSp, sidebarIconSp, appDp, font, unifiedStyle, clockEnabled, clockFont, clockSize, animStyle, appWidgetId)
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
        sidebarDisplayType: String,
        sidebarTextSizeSp: Int,
        sidebarIconSizeSp: Int,
        categoryIconSizeDp: Int,
        sidebarFont: String,
        unifiedIconStyle: String,
        clockEnabled: Boolean,
        clockFont: String,
        clockSizeSp: Int,
        animationStyle: String,
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
            .putString("sidebar_display_type", sidebarDisplayType)
            .putInt("sidebar_text_size_sp", sidebarTextSizeSp)
            .putInt("sidebar_icon_size_sp", sidebarIconSizeSp)
            .putInt("category_icon_size_dp", categoryIconSizeDp)
            .putString("sidebar_font_family", sidebarFont)
            .putString("unified_icon_style", unifiedIconStyle)
            .putBoolean("clock_enabled", clockEnabled)
            .putString("clock_font", clockFont)
            .putInt("clock_size_sp", clockSizeSp)
            .putString("animation_style", animationStyle)
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

enum class NovaScreen { HOME, CATEGORIES, SIDEBAR, ANIMATION, ICONS, BACKUP }

@Composable
fun CategoryBadgeView(category: Category, size: Dp, theme: NovaThemePalette, modifier: Modifier = Modifier) {
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
            .background(if (theme.isDark) Color(0xFF333647) else Color(0xFFE2E4EB), CircleShape),
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
                color = theme.textPrimary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    theme: NovaThemePalette,
    onToggleTheme: (Boolean) -> Unit,
    onSave: (List<Category>, String, String, String, Int, Int, Int, String, String, Boolean, String, Int, String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE) }

    var currentScreen by remember { mutableStateOf(NovaScreen.HOME) }
    var editingCategoryId by remember { mutableStateOf<String?>(null) }
    var mainSearchQuery by remember { mutableStateOf("") }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var showIconDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showClockFontDialog by remember { mutableStateOf(false) }

    var editingAppForIcon by remember { mutableStateOf<AppModel?>(null) }
    var iconUpdateCounter by remember { mutableIntStateOf(0) }

    var categories by remember {
        val rawJson = prefs.getString("categories_json", null)
        val initial = if (rawJson != null) {
            try {
                val type = object : TypeToken<List<Category>>() {}.type
                Gson().fromJson<List<Category>>(rawJson, type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
        } else emptyList()
        mutableStateOf(initial)
    }

    var sidebarPosition by remember { mutableStateOf(prefs.getString("sidebar_position", "left") ?: "left") }
    var sidebarAlignment by remember { mutableStateOf(prefs.getString("sidebar_alignment", "bottom") ?: "bottom") }
    var sidebarDisplayType by remember { mutableStateOf(prefs.getString("sidebar_display_type", "icons") ?: "icons") }

    var sidebarTextSizeSp by remember { mutableFloatStateOf(prefs.getInt("sidebar_text_size_sp", 14).toFloat()) }
    var sidebarIconSizeSp by remember { mutableFloatStateOf(prefs.getInt("sidebar_icon_size_sp", 28).toFloat()) }
    var categoryIconSizeDp by remember { mutableFloatStateOf(prefs.getInt("category_icon_size_dp", 46).toFloat()) }

    var sidebarFont by remember { mutableStateOf(prefs.getString("sidebar_font_family", "sans-serif") ?: "sans-serif") }
    var unifiedIconStyle by remember {
        val saved = prefs.getString("unified_icon_style", "default") ?: "default"
        mutableStateOf(if (saved == "white" || saved == "black") "default" else saved)
    }
    var installedIconPacks by remember { mutableStateOf<List<AppModel>>(emptyList()) }
    var animationStyle by remember { mutableStateOf(prefs.getString("animation_style", "fade") ?: "fade") }

    var clockEnabled by remember { mutableStateOf(prefs.getBoolean("clock_enabled", true)) }
    var clockFont by remember { mutableStateOf(prefs.getString("clock_font", "sans-serif") ?: "sans-serif") }
    var clockSizeSp by remember { mutableFloatStateOf(prefs.getInt("clock_size_sp", 26).toFloat()) }

    val cachedList = remember { AppRepository.getCachedApps(context) }
    var installedApps by remember { mutableStateOf(cachedList) }
    var appSearchQuery by remember { mutableStateOf("") }
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

    BackHandler(enabled = currentScreen != NovaScreen.HOME || editingCategoryId != null) {
        if (editingCategoryId != null) {
            editingCategoryId = null
        } else {
            currentScreen = NovaScreen.HOME
        }
    }

    val currentCategory = categories.firstOrNull { it.id == editingCategoryId }
    val selectedSet = remember(currentCategory?.packageNames, editingCategoryId) {
        currentCategory?.packageNames?.toSet() ?: emptySet()
    }

    val filteredApps = remember(appSearchQuery, installedApps, selectedSet) {
        val query = appSearchQuery.trim().lowercase()
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
        list.sortedWith(
            compareByDescending<AppModel> { app ->
                selectedSet.contains(app.id) || selectedSet.contains(app.packageName)
            }.thenBy { it.appName.lowercase() }
        )
    }

    val modernCuratedSymbols = listOf(
        "⌂", "✦", "◈", "⌘", "⚡", "⚙", "◉", "★",
        "✆", "✉", "💬", "◎", "👥", "🔔", "📣", "📡",
        "💳", "🏛", "💼", "₹", "$", "📈", "💰", "🏷",
        "🛠", "✎", "⌕", "📁", "🔒", "⊞", "▦", "⬡",
        "▶", "♫", "🎬", "📷", "🎮", "🕹", "🎧", "📺",
        "✈", "🧭", "🚗", "☕", "🛒", "🛍", "♥", "⏱"
    )

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
                    sidebarTextSizeSp = sidebarTextSizeSp.toInt(),
                    sidebarIconSizeSp = sidebarIconSizeSp.toInt(),
                    sidebarSizeSp = sidebarTextSizeSp.toInt(),
                    categoryIconSizeDp = categoryIconSizeDp.toInt(),
                    sidebarFontFamily = sidebarFont,
                    unifiedIconStyle = unifiedIconStyle,
                    clockEnabled = clockEnabled,
                    clockFont = clockFont,
                    clockSizeSp = clockSizeSp.toInt(),
                    animationStyle = animationStyle
                )

                val jsonContent = Gson().toJson(backupBundle)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonContent.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Backup exported successfully!", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }

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

                        sidebarTextSizeSp = (if (backupBundle.sidebarTextSizeSp > 0) backupBundle.sidebarTextSizeSp else backupBundle.sidebarSizeSp).toFloat()
                        sidebarIconSizeSp = (if (backupBundle.sidebarIconSizeSp > 0) backupBundle.sidebarIconSizeSp else backupBundle.sidebarSizeSp).toFloat()

                        categoryIconSizeDp = backupBundle.categoryIconSizeDp.toFloat()
                        sidebarFont = backupBundle.sidebarFontFamily
                        unifiedIconStyle = backupBundle.unifiedIconStyle
                        clockEnabled = backupBundle.clockEnabled
                        clockFont = backupBundle.clockFont
                        clockSizeSp = backupBundle.clockSizeSp.toFloat()
                        animationStyle = backupBundle.animationStyle

                        prefs.edit()
                            .putString("categories_json", Gson().toJson(restoredCategories))
                            .putString("sidebar_position", backupBundle.sidebarPosition)
                            .putString("sidebar_alignment", backupBundle.sidebarAlignment)
                            .putString("sidebar_display_type", backupBundle.sidebarDisplayType)
                            .putInt("sidebar_text_size_sp", sidebarTextSizeSp.toInt())
                            .putInt("sidebar_icon_size_sp", sidebarIconSizeSp.toInt())
                            .putInt("category_icon_size_dp", backupBundle.categoryIconSizeDp)
                            .putString("sidebar_font_family", backupBundle.sidebarFontFamily)
                            .putString("unified_icon_style", backupBundle.unifiedIconStyle)
                            .putBoolean("clock_enabled", backupBundle.clockEnabled)
                            .putString("clock_font", backupBundle.clockFont)
                            .putInt("clock_size_sp", backupBundle.clockSizeSp)
                            .putString("animation_style", backupBundle.animationStyle)
                            .apply()

                        AppIconHelper.clearCache(context)
                        AppIconHelper.prewarmIcons(context, restoredCategories)
                        CategoryWidgetProvider.updateAllWidgets(context)

                        showSettingsDialog = false
                        Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {}
        }
    }

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
            } catch (_: Exception) {}
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

    val menuItems = listOf(
        Triple("Categories", "Configure category tabs, app list, and shortcuts", NovaScreen.CATEGORIES),
        Triple("Side bar", "Dock position, clock settings, alignment, and scale", NovaScreen.SIDEBAR),
        Triple("Animation", "Transition styles: fade, slide, zoom, and snappy", NovaScreen.ANIMATION),
        Triple("Icons", "App icon packs, custom overrides, and default styling", NovaScreen.ICONS),
        Triple("Backup & restore", "Export configuration, restore from file, and data backup", NovaScreen.BACKUP)
    )

    val filteredMenuItems = remember(mainSearchQuery) {
        val q = mainSearchQuery.trim().lowercase()
        if (q.isBlank()) {
            menuItems
        } else {
            menuItems.filter {
                it.first.lowercase().contains(q) || it.second.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            if (currentScreen == NovaScreen.HOME && editingCategoryId == null) {
                // Main Header: Includes statusBarsPadding to safely avoid clock/battery collision
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, top = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Apps Widget",
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = theme.textPrimary
                    )
                    IconButton(onClick = { showSettingsDialog = true }) {
                        NovaOutlineIcon(type = IconType.SETTINGS, tint = theme.iconTint, size = 26.dp)
                    }
                }
            } else {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    windowInsets = WindowInsets(0.dp),
                    title = {
                        val title = when {
                            editingCategoryId != null -> currentCategory?.name ?: "Category"
                            currentScreen == NovaScreen.CATEGORIES -> "Categories"
                            currentScreen == NovaScreen.SIDEBAR -> "Side bar"
                            currentScreen == NovaScreen.ANIMATION -> "Animation"
                            currentScreen == NovaScreen.ICONS -> "Icons"
                            currentScreen == NovaScreen.BACKUP -> "Backup & restore"
                            else -> "Settings"
                        }
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = theme.textPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (editingCategoryId != null) {
                                editingCategoryId = null
                            } else {
                                currentScreen = NovaScreen.HOME
                            }
                        }) {
                            Text("←", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            NovaOutlineIcon(type = IconType.SETTINGS, tint = theme.iconTint, size = 24.dp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background)
                )
            }
        },
        bottomBar = {
            Surface(
                color = theme.bottomBarBg,
                modifier = Modifier.fillMaxWidth().height(68.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(theme.pillActive)
                            .clickable {
                                onSave(
                                    categories,
                                    sidebarPosition,
                                    sidebarAlignment,
                                    sidebarDisplayType,
                                    sidebarTextSizeSp.toInt(),
                                    sidebarIconSizeSp.toInt(),
                                    categoryIconSizeDp.toInt(),
                                    sidebarFont,
                                    unifiedIconStyle,
                                    clockEnabled,
                                    clockFont,
                                    clockSizeSp.toInt(),
                                    animationStyle
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NovaOutlineIcon(type = IconType.CHECK, tint = theme.textPrimary, size = 18.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save & Apply Changes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = theme.textPrimary
                        )
                    }
                }
            }
        },
        containerColor = theme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                editingCategoryId != null && currentCategory != null -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surface),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, theme.cardBorder)
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
                                    theme = theme,
                                    modifier = Modifier.clickable { showIconDialog = true }
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentCategory.name,
                                        color = theme.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Rename",
                                            color = theme.textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable {
                                                renameValue = currentCategory.name
                                                showRenameDialog = true
                                            }
                                        )
                                        Text("•", color = theme.textSecondary, fontSize = 12.sp)
                                        Text(
                                            text = "Change Icon",
                                            color = theme.textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable { showIconDialog = true }
                                        )
                                        Text("•", color = theme.textSecondary, fontSize = 12.sp)
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentCategory.packageNames.size} apps selected",
                                color = theme.textSecondary,
                                fontSize = 13.sp
                            )
                            Button(
                                onClick = {
                                    val pickIntent = Intent(Intent.ACTION_CREATE_SHORTCUT)
                                    val chooser = Intent.createChooser(pickIntent, "Add App Shortcut")
                                    shortcutPickerLauncher.launch(chooser)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.surface),
                                border = BorderStroke(1.dp, theme.cardBorder),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("+ App Shortcut", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                            }
                        }

                        OutlinedTextField(
                            value = appSearchQuery,
                            onValueChange = { appSearchQuery = it },
                            placeholder = { Text("Search installed apps...", color = theme.textSecondary, fontSize = 14.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            trailingIcon = {
                                if (appSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { appSearchQuery = "" }) {
                                        Text("✕", color = theme.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = theme.searchBarBg,
                                unfocusedContainerColor = theme.searchBarBg,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = theme.textPrimary,
                                unfocusedTextColor = theme.textPrimary
                            )
                        )

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = theme.textPrimary)
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
                                        theme = theme,
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
                                        onChangeIcon = { editingAppForIcon = app },
                                        updateCounter = iconUpdateCounter
                                    )
                                }
                            }
                        }
                    }
                }

                currentScreen == NovaScreen.CATEGORIES -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
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
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.surface),
                                border = BorderStroke(1.dp, theme.cardBorder)
                            ) {
                                Text("+ Add Category", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }

                        item {
                            NovaSettingsCard(
                                title = "App Icon Size",
                                subtitle = "Adjust how large app icons appear inside the widget grid.",
                                theme = theme
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Dimension", fontSize = 14.sp, color = theme.textPrimary, fontWeight = FontWeight.Medium)
                                        Text("${categoryIconSizeDp.toInt()} dp", fontSize = 14.sp, color = theme.textSecondary, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = categoryIconSizeDp,
                                        onValueChange = { categoryIconSizeDp = it },
                                        valueRange = 32f..54f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = theme.textPrimary,
                                            activeTrackColor = theme.textPrimary,
                                            inactiveTrackColor = theme.cardBorder
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Configured Categories (${categories.size}/6):",
                                color = theme.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        items(categories, key = { it.id }) { cat ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingCategoryId = cat.id },
                                colors = CardDefaults.cardColors(containerColor = theme.surface),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, theme.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CategoryBadgeView(category = cat, size = 46.dp, theme = theme)

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cat.name,
                                            color = theme.textPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${cat.packageNames.size} apps configured",
                                            color = theme.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Text(
                                        text = "Edit →",
                                        color = theme.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                currentScreen == NovaScreen.SIDEBAR -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            NovaSettingsCard(
                                title = "Sidebar Placement",
                                subtitle = "Dock the category sidebar to the left or right side of your screen.",
                                theme = theme
                            ) {
                                Column {
                                    NovaRadioOption(
                                        label = "Left Side",
                                        isSelected = sidebarPosition == "left",
                                        theme = theme,
                                        onClick = { sidebarPosition = "left" }
                                    )
                                    NovaRadioOption(
                                        label = "Right Side",
                                        isSelected = sidebarPosition == "right",
                                        theme = theme,
                                        onClick = { sidebarPosition = "right" }
                                    )
                                }
                            }
                        }

                        item {
                            NovaSettingsCard(
                                title = "Sidebar Clock",
                                subtitle = "Enable or customize the stacked digital clock above the sidebar.",
                                theme = theme
                            ) {
                                Column {
                                    NovaRadioOption(
                                        label = "Show Clock (On)",
                                        isSelected = clockEnabled,
                                        theme = theme,
                                        onClick = { clockEnabled = true }
                                    )
                                    NovaRadioOption(
                                        label = "Hide Clock (Off)",
                                        isSelected = !clockEnabled,
                                        theme = theme,
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
                                                Text("Clock Font", fontSize = 12.sp, color = theme.textSecondary)
                                                Text(getFontLabel(clockFont), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                                            }
                                            Text("Change Font →", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                                        }

                                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Clock Size", fontSize = 13.sp, color = theme.textPrimary, fontWeight = FontWeight.Medium)
                                                Text("${clockSizeSp.toInt()} sp", fontSize = 13.sp, color = theme.textSecondary, fontWeight = FontWeight.Bold)
                                            }
                                            Slider(
                                                value = clockSizeSp,
                                                onValueChange = { clockSizeSp = it },
                                                valueRange = 18f..38f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = theme.textPrimary,
                                                    activeTrackColor = theme.textPrimary,
                                                    inactiveTrackColor = theme.cardBorder
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            NovaSettingsCard(
                                title = "Vertical Alignment",
                                subtitle = "Position the sidebar tabs at the top, middle, or bottom of the widget.",
                                theme = theme
                            ) {
                                Column {
                                    NovaRadioOption(
                                        label = "Bottom",
                                        isSelected = sidebarAlignment == "bottom",
                                        theme = theme,
                                        onClick = { sidebarAlignment = "bottom" }
                                    )
                                    NovaRadioOption(
                                        label = "Middle (Center)",
                                        isSelected = sidebarAlignment == "center",
                                        theme = theme,
                                        onClick = { sidebarAlignment = "center" }
                                    )
                                    NovaRadioOption(
                                        label = "Top",
                                        isSelected = sidebarAlignment == "top",
                                        theme = theme,
                                        onClick = { sidebarAlignment = "top" }
                                    )
                                }
                            }
                        }

                        item {
                            NovaSettingsCard(
                                title = "Tab Display Style",
                                subtitle = "Choose whether category tabs display icons or full text labels.",
                                theme = theme
                            ) {
                                Column {
                                    NovaRadioOption(
                                        label = "Icons (Symbols / Emoji / Gallery)",
                                        isSelected = sidebarDisplayType == "icons",
                                        theme = theme,
                                        onClick = { sidebarDisplayType = "icons" }
                                    )
                                    NovaRadioOption(
                                        label = "Heading (Full Text Labels)",
                                        isSelected = sidebarDisplayType == "heading",
                                        theme = theme,
                                        onClick = { sidebarDisplayType = "heading" }
                                    )
                                }
                            }
                        }

                        item {
                            NovaSettingsCard(
                                title = "Sidebar Heading Font",
                                subtitle = "Select font typeface for sidebar labels and headings.",
                                theme = theme
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
                                        Text("Current Font", fontSize = 12.sp, color = theme.textSecondary)
                                        Text(getFontLabel(sidebarFont), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                                    }
                                    Text("Change Font →", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                                }
                            }
                        }

                        item {
                            NovaSettingsCard(
                                title = "Sidebar Item Size",
                                subtitle = "Independently adjust the scale of text labels and symbol/gallery icons.",
                                theme = theme
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Text Size Scale", fontSize = 14.sp, color = theme.textPrimary, fontWeight = FontWeight.Medium)
                                        Text("${sidebarTextSizeSp.toInt()} sp", fontSize = 14.sp, color = theme.textSecondary, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = sidebarTextSizeSp,
                                        onValueChange = { sidebarTextSizeSp = it },
                                        valueRange = 10f..22f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = theme.textPrimary,
                                            activeTrackColor = theme.textPrimary,
                                            inactiveTrackColor = theme.cardBorder
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Icon / Gallery Scale", fontSize = 14.sp, color = theme.textPrimary, fontWeight = FontWeight.Medium)
                                        Text("${sidebarIconSizeSp.toInt()} sp", fontSize = 14.sp, color = theme.textSecondary, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = sidebarIconSizeSp,
                                        onValueChange = { sidebarIconSizeSp = it },
                                        valueRange = 10f..50f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = theme.textPrimary,
                                            activeTrackColor = theme.textPrimary,
                                            inactiveTrackColor = theme.cardBorder
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                currentScreen == NovaScreen.ANIMATION -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            NovaSettingsCard(
                                title = "Category Switch Animation",
                                subtitle = "Select transition effect when switching between sidebar categories.",
                                theme = theme
                            ) {
                                Column {
                                    listOf(
                                        "fade" to "Fade (Smooth Crossfade)",
                                        "slide_h" to "Slide Horizontal (Lateral Swipe)",
                                        "slide_v" to "Slide Vertical (Bottom-Up Rise)",
                                        "zoom" to "Zoom & Scale (Pop Transition)",
                                        "none" to "None (Instant / 0ms Snappy)"
                                    ).forEach { (key, label) ->
                                        NovaRadioOption(
                                            label = label,
                                            isSelected = animationStyle == key,
                                            theme = theme,
                                            onClick = { animationStyle = key }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                currentScreen == NovaScreen.ICONS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            NovaSettingsCard(
                                title = "App Icon Style & Pack",
                                subtitle = "Select whether to use default system app icons or an installed third-party icon pack.",
                                theme = theme
                            ) {
                                Column {
                                    NovaRadioOption(
                                        label = "Default (Original System Colors)",
                                        isSelected = unifiedIconStyle == "default",
                                        theme = theme,
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
                                                color = theme.textSecondary
                                            )
                                        }

                                        installedIconPacks.forEach { pack ->
                                            val packVal = "pack:${pack.packageName}"
                                            NovaRadioOption(
                                                label = "${pack.appName} (Icon Pack)",
                                                isSelected = unifiedIconStyle == packVal,
                                                theme = theme,
                                                onClick = { unifiedIconStyle = packVal }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                currentScreen == NovaScreen.BACKUP -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            NovaSettingsCard(
                                title = "Backup & Restore",
                                subtitle = "Save your setup to a JSON file or restore anytime.",
                                theme = theme
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Exports all configured categories, custom app icons, gallery images, and layouts into an offline backup file.",
                                        color = theme.textSecondary,
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = { exportBackupLauncher.launch("apps_widget_backup_${System.currentTimeMillis()}.json") },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.surface),
                                        border = BorderStroke(1.dp, theme.cardBorder)
                                    ) {
                                        Text("💾 Backup Configuration to File", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedButton(
                                        onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, theme.cardBorder)
                                    ) {
                                        Text("📂 Restore Configuration from File", color = theme.textSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                .height(50.dp)
                                .clip(RoundedCornerShape(25.dp))
                                .background(theme.searchBarBg)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NovaOutlineIcon(type = IconType.SEARCH, tint = theme.iconTint, size = 18.dp)
                                Spacer(modifier = Modifier.width(12.dp))

                                BasicTextFieldWithPlaceholder(
                                    value = mainSearchQuery,
                                    onValueChange = { mainSearchQuery = it },
                                    placeholder = "Search",
                                    textColor = theme.textPrimary,
                                    placeholderColor = theme.textSecondary
                                )

                                if (mainSearchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { mainSearchQuery = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("✕", color = theme.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredMenuItems) { item ->
                                val (title, subtitle, screen) = item
                                NovaSettingsRow(
                                    title = title,
                                    subtitle = subtitle,
                                    type = when (screen) {
                                        NovaScreen.CATEGORIES -> IconType.CATEGORIES
                                        NovaScreen.SIDEBAR -> IconType.SIDEBAR
                                        NovaScreen.ANIMATION -> IconType.ANIMATION
                                        NovaScreen.ICONS -> IconType.ICONS
                                        NovaScreen.BACKUP -> IconType.BACKUP
                                        else -> IconType.SETTINGS
                                    },
                                    theme = theme,
                                    onClick = { currentScreen = screen }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "Preferences",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = theme.textPrimary
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "App Theme",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    NovaRadioOption(
                        label = "Dark Mode",
                        isSelected = theme.isDark,
                        theme = theme,
                        onClick = { onToggleTheme(true) }
                    )
                    NovaRadioOption(
                        label = "Light Mode",
                        isSelected = !theme.isDark,
                        theme = theme,
                        onClick = { onToggleTheme(false) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = theme.divider)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Reset All Data",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Restore categories, shortcuts, and layout settings to initial state.",
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showResetConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("⚠️ Reset to Defaults", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Done", color = theme.textPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = theme.surface
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Confirm Reset", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444)) },
            text = {
                Text(
                    text = "Are you sure you want to clear all categories, assigned apps, custom gallery icons, and layout settings?",
                    color = theme.textPrimary,
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
                        sidebarTextSizeSp = 14f
                        sidebarIconSizeSp = 28f
                        categoryIconSizeDp = 46f
                        sidebarFont = "sans-serif"
                        unifiedIconStyle = "default"
                        clockEnabled = true
                        clockFont = "sans-serif"
                        clockSizeSp = 26f
                        animationStyle = "fade"

                        prefs.edit().clear().apply()
                        val iconDir = File(context.filesDir, "category_icons")
                        if (iconDir.exists()) iconDir.deleteRecursively()
                        AppIconHelper.clearCache(context)
                        CategoryWidgetProvider.updateAllWidgets(context)

                        showResetConfirmDialog = false
                        showSettingsDialog = false
                        Toast.makeText(context, "Reset complete!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Yes, Reset All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            },
            containerColor = theme.surface
        )
    }

    if (editingAppForIcon != null) {
        val app = editingAppForIcon!!
        AlertDialog(
            onDismissRequest = { editingAppForIcon = null },
            title = {
                Text(text = "Icon for ${app.appName}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = theme.textPrimary)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { appGalleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surface),
                        border = BorderStroke(1.dp, theme.cardBorder)
                    ) {
                        Text("📁 Upload Icon from Gallery", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            prefs.edit().remove("custom_app_icon_${app.packageName}").remove("custom_app_icon_${app.id}").apply()
                            AppIconHelper.clearCache(context)
                            editingAppForIcon = null
                            iconUpdateCounter++
                            Toast.makeText(context, "Reset to default icon", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Text("🔄 Reset to Default Icon", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Or choose a modern symbol:", color = theme.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(modernCuratedSymbols.chunked(6)) { rowIcons ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                rowIcons.forEach { symbol ->
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(if (theme.isDark) Color(0xFF333647) else Color(0xFFE2E4EB), CircleShape)
                                            .clickable {
                                                prefs.edit().putString("custom_app_icon_${app.packageName}", "symbol:$symbol").apply()
                                                AppIconHelper.clearCache(context)
                                                editingAppForIcon = null
                                                iconUpdateCounter++
                                                Toast.makeText(context, "Symbol applied!", Toast.LENGTH_SHORT).show()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(symbol, fontSize = 18.sp, color = theme.textPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingAppForIcon = null }) {
                    Text("Cancel", color = theme.textPrimary)
                }
            },
            containerColor = theme.surface
        )
    }

    if (showIconDialog) {
        AlertDialog(
            onDismissRequest = { showIconDialog = false },
            title = { Text("Category Icon", fontWeight = FontWeight.Bold, color = theme.textPrimary) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surface),
                        border = BorderStroke(1.dp, theme.cardBorder)
                    ) {
                        Text("📁 Upload Icon from Gallery", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Or choose a modern minimal symbol:", color = theme.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(modernCuratedSymbols.chunked(6)) { rowIcons ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                rowIcons.forEach { symbol ->
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(if (theme.isDark) Color(0xFF333647) else Color(0xFFE2E4EB), CircleShape)
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
                                        Text(symbol, fontSize = 18.sp, color = theme.textPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIconDialog = false }) {
                    Text("Cancel", color = theme.textPrimary)
                }
            },
            containerColor = theme.surface
        )
    }

    if (showFontDialog) {
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text("Select Sidebar Font", fontWeight = FontWeight.Bold, color = theme.textPrimary) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
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
                                    .border(2.dp, if (sidebarFont == key) theme.textPrimary else theme.textSecondary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (sidebarFont == key) {
                                    Box(modifier = Modifier.size(9.dp).background(theme.textPrimary, CircleShape))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (sidebarFont == key) FontWeight.Bold else FontWeight.Normal,
                                color = if (sidebarFont == key) theme.textPrimary else theme.textSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontDialog = false }) {
                    Text("Close", color = theme.textPrimary)
                }
            },
            containerColor = theme.surface
        )
    }

    if (showClockFontDialog) {
        AlertDialog(
            onDismissRequest = { showClockFontDialog = false },
            title = { Text("Select Clock Font", fontWeight = FontWeight.Bold, color = theme.textPrimary) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
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
                                    .border(2.dp, if (clockFont == key) theme.textPrimary else theme.textSecondary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (clockFont == key) {
                                    Box(modifier = Modifier.size(9.dp).background(theme.textPrimary, CircleShape))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (clockFont == key) FontWeight.Bold else FontWeight.Normal,
                                color = if (clockFont == key) theme.textPrimary else theme.textSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClockFontDialog = false }) {
                    Text("Close", color = theme.textPrimary)
                }
            },
            containerColor = theme.surface
        )
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("New Category", fontWeight = FontWeight.Bold, color = theme.textPrimary) },
            text = {
                Column {
                    Text("Enter category name (max 12 characters):", color = theme.textSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { if (it.length <= 12) newCategoryName = it },
                        placeholder = { Text("e.g. Home, Bank, Tools, AI") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = theme.searchBarBg,
                            unfocusedContainerColor = theme.searchBarBg,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary
                        )
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
                    colors = ButtonDefaults.buttonColors(containerColor = theme.surface),
                    border = BorderStroke(1.dp, theme.cardBorder)
                ) {
                    Text("Create & Open", color = theme.textPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            },
            containerColor = theme.surface
        )
    }

    if (showRenameDialog) {
        val currentCat = categories.firstOrNull { it.id == editingCategoryId }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Category", fontWeight = FontWeight.Bold, color = theme.textPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { if (it.length <= 12) renameValue = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = theme.searchBarBg,
                            unfocusedContainerColor = theme.searchBarBg,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameValue.trim()
                        if (trimmed.isNotEmpty() && currentCat != null) {
                            categories = categories.map {
                                if (it.id == currentCat.id) it.copy(name = trimmed) else it
                            }
                            showRenameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.surface),
                    border = BorderStroke(1.dp, theme.cardBorder)
                ) {
                    Text("Rename", color = theme.textPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            },
            containerColor = theme.surface
        )
    }
}

@Composable
fun NovaSettingsRow(
    title: String,
    subtitle: String,
    type: IconType,
    theme: NovaThemePalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            NovaOutlineIcon(type = type, tint = theme.iconTint, size = 24.dp)
        }

        Spacer(modifier = Modifier.width(18.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = theme.textSecondary,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
fun NovaSettingsCard(
    title: String,
    subtitle: String,
    theme: NovaThemePalette,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, theme.cardBorder)
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
                    color = theme.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(theme.divider)
            )
            content()
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun NovaRadioOption(
    label: String,
    isSelected: Boolean,
    theme: NovaThemePalette,
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
                    color = if (isSelected) theme.textPrimary else theme.textSecondary,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(theme.textPrimary, CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) theme.textPrimary else theme.textSecondary
        )
    }
}

@Composable
fun BasicTextFieldWithPlaceholder(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textColor: Color,
    placeholderColor: Color
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (value.isEmpty()) {
            Text(text = placeholder, color = placeholderColor, fontSize = 15.sp)
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

enum class IconType {
    CATEGORIES,
    SIDEBAR,
    ANIMATION,
    ICONS,
    BACKUP,
    SEARCH,
    SETTINGS,
    CHECK
}

@Composable
fun NovaOutlineIcon(type: IconType, tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = 2.dp.toPx())

        when (type) {
            IconType.CATEGORIES -> {
                val s = w * 0.38f
                drawRoundRect(tint, Offset(w * 0.08f, h * 0.08f), Size(s, s), CornerRadius(4f), stroke)
                drawRoundRect(tint, Offset(w * 0.54f, h * 0.08f), Size(s, s), CornerRadius(4f), stroke)
                drawRoundRect(tint, Offset(w * 0.08f, h * 0.54f), Size(s, s), CornerRadius(4f), stroke)
                drawRoundRect(tint, Offset(w * 0.54f, h * 0.54f), Size(s, s), CornerRadius(4f), stroke)
            }
            IconType.SIDEBAR -> {
                drawRoundRect(tint, Offset(w * 0.1f, h * 0.1f), Size(w * 0.8f, h * 0.8f), CornerRadius(8f), stroke)
                drawLine(tint, Offset(w * 0.38f, h * 0.1f), Offset(w * 0.38f, h * 0.9f), strokeWidth = 2.dp.toPx())
            }
            IconType.ANIMATION -> {
                val path = Path().apply {
                    moveTo(w * 0.2f, h * 0.15f)
                    lineTo(w * 0.85f, h * 0.5f)
                    lineTo(w * 0.2f, h * 0.85f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            IconType.ICONS -> {
                drawCircle(tint, radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.35f, h * 0.38f))
                drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.65f, h * 0.38f))
            }
            IconType.BACKUP -> {
                drawRoundRect(tint, Offset(w * 0.15f, h * 0.25f), Size(w * 0.7f, h * 0.55f), CornerRadius(6f), stroke)
                drawLine(tint, Offset(w * 0.35f, h * 0.52f), Offset(w * 0.65f, h * 0.52f), strokeWidth = 2.dp.toPx())
            }
            IconType.SEARCH -> {
                drawCircle(tint, radius = w * 0.32f, center = Offset(w * 0.42f, h * 0.42f), style = stroke)
                drawLine(tint, Offset(w * 0.66f, h * 0.66f), Offset(w * 0.92f, h * 0.92f), strokeWidth = 2.dp.toPx())
            }
            IconType.SETTINGS -> {
                drawCircle(tint, radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawCircle(tint, radius = w * 0.40f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
            }
            IconType.CHECK -> {
                val path = Path().apply {
                    moveTo(w * 0.2f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.75f)
                    lineTo(w * 0.82f, h * 0.28f)
                }
                drawPath(path, tint, style = stroke)
            }
        }
    }
}

@Composable
fun AppItemRow(
    app: AppModel,
    isChecked: Boolean,
    theme: NovaThemePalette,
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
            .background(theme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, theme.cardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                        .background(theme.searchBarBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", color = theme.textSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                color = theme.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = app.packageName,
                color = theme.textSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }

        TextButton(
            onClick = onChangeIcon,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("Icon ✎", fontSize = 11.sp, color = theme.textSecondary, fontWeight = FontWeight.Bold)
        }

        Checkbox(
            checked = isChecked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = theme.textPrimary,
                uncheckedColor = theme.textSecondary
            )
        )
    }
}
