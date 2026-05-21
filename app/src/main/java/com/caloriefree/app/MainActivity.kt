package com.caloriefree.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private const val AI_DEVELOPER_MODE = false
private const val GITHUB_RELEASES_URL = "https://github.com/guaidaojide666/CalorieFree/releases/latest"
private const val LANZOU_UPDATE_URL = "https://wwbuu.lanzoub.com/b01bjf4c4d"
private const val LANZOU_UPDATE_PASSWORD = "gzcs"
private const val VISION_PROBE_IMAGE_DATA_URL =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7ZfH0AAAAASUVORK5CYII="
private val MEAL_OCCASION_TAGS = listOf("早餐", "午餐", "晚餐", "下午加餐", "夜宵", "零食", "加餐")

enum class ServingType {
    PerItem,
    Per100g
}

enum class NutritionInputMode {
    PerServing,
    Total
}

enum class RecognitionImageType {
    FoodPhoto,
    NutritionLabel
}

enum class BiologicalSex {
    Male,
    Female
}

data class MealRecord(
    val name: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val time: String,
    val date: String = currentDateText(),
    val tags: List<String> = listOf("早餐"),
    val servingType: ServingType = ServingType.Per100g,
    val quantity: Double = 100.0,
    val servingCount: Double = 1.0,
    val unitLabel: String = "个"
)

data class FoodItem(
    val name: String,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val servingType: ServingType = ServingType.Per100g,
    val category: String = "自定义",
    val unitLabel: String = "个",
    val isHidden: Boolean = false,
    val calories: Double = calculateCalories(protein, fat, carbs)
)

data class FoodEditorState(
    val editingIndex: Int,
    val originalFood: FoodItem
)

data class MealEditorState(
    val originalMeal: MealRecord
)

data class NutritionPlan(
    val id: Long,
    val name: String,
    val targetCalories: Double,
    val targetProtein: Double,
    val targetFat: Double,
    val targetCarbs: Double,
    val waterTargetMl: Double = 2000.0,
    val dailyCalorieDeficit: Double = 0.0,
    val isDefault: Boolean = false,
    val isHidden: Boolean = false,
    val note: String = ""
)

data class DailyPlanSelection(
    val date: String,
    val planId: Long
)

data class WaterRecord(
    val id: Long,
    val date: String,
    val amountMl: Double
)

data class ExerciseBurnRecord(
    val date: String,
    val calories: Double,
    val description: String = ""
)

enum class VisionImageUploadFormat {
    Auto,
    ImageUrlDataUrl,
    ImageUrlBase64,
    AnthropicBase64
}

data class AiSettings(
    val id: Long = 1L,
    val providerName: String = "OpenAI Compatible",
    val baseUrl: String = "https://api.openai.com/v1/chat/completions",
    val apiKey: String = "",
    val modelName: String = "gpt-4o-mini",
    val temperature: Double = 0.2,
    val enabled: Boolean = false,
    val selectedForVisionWork: Boolean = false,
    val selectedForTextWork: Boolean = false,
    val supportsVision: Boolean = true,
    val manualVisionConfirmed: Boolean = false,
    val visionImageUploadFormat: VisionImageUploadFormat = VisionImageUploadFormat.Auto,
    val verifiedSignature: String = ""
)

fun AiSettings.canHandleVisionTasks(): Boolean = supportsVision || manualVisionConfirmed

fun AiSettings.resolvedVisionImageUploadFormat(): VisionImageUploadFormat {
    if (visionImageUploadFormat != VisionImageUploadFormat.Auto) return visionImageUploadFormat
    return when {
        providerNeedsAnthropicVersion(this) -> VisionImageUploadFormat.AnthropicBase64
        providerLooksZhipuLike(this) -> VisionImageUploadFormat.ImageUrlBase64
        else -> VisionImageUploadFormat.ImageUrlDataUrl
    }
}

fun VisionImageUploadFormat.displayName(): String {
    return when (this) {
        VisionImageUploadFormat.Auto -> "自动判断"
        VisionImageUploadFormat.ImageUrlDataUrl -> "image_url + 完整 data URL"
        VisionImageUploadFormat.ImageUrlBase64 -> "image_url + 原始 Base64"
        VisionImageUploadFormat.AnthropicBase64 -> "Anthropic base64 source"
    }
}

fun parseVisionImageUploadFormatStoredValue(value: String?): VisionImageUploadFormat {
    return enumValues<VisionImageUploadFormat>().firstOrNull { it.name.equals(value.orEmpty(), ignoreCase = true) }
        ?: VisionImageUploadFormat.Auto
}

data class AiVerificationResult(
    val availabilityResponse: String,
    val supportsVision: Boolean,
    val visionImageUploadFormat: VisionImageUploadFormat = VisionImageUploadFormat.Auto
)

data class VisionSupportProbeResult(
    val uploadFormat: VisionImageUploadFormat,
    val response: String
)

data class AiPresetGroup(
    val companyName: String,
    val presets: List<AiSettings>
)

data class AiPresetSection(
    val sectionName: String,
    val groups: List<AiPresetGroup>
)

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val changelog: String
)

data class UserProfile(
    val heightCm: Double = 0.0,
    val weightKg: Double = 0.0,
    val ageYears: Int = 0,
    val biologicalSex: BiologicalSex = BiologicalSex.Female
)

data class FavoriteFoodProfile(
    val name: String,
    val category: String,
    val servingType: ServingType,
    val unitLabel: String,
    val carbsPerServing: Double,
    val proteinPerServing: Double,
    val fatPerServing: Double,
    val usageCount: Int,
    val totalQuantity: Double
)

data class RemainingFoodEquivalent(
    val staple: FavoriteFoodProfile,
    val proteinSource: FavoriteFoodProfile,
    val stapleServingCount: Double,
    val proteinServingCount: Double,
    val totalCarbs: Double,
    val totalProtein: Double
)

data class AppState(
    val targetCalories: Double,
    val targetProtein: Double,
    val targetFat: Double,
    val targetCarbs: Double,
    val hasAnyStoredData: Boolean = false,
    val userProfile: UserProfile = UserProfile(),
    val meals: List<MealRecord>,
    val foods: List<FoodItem>,
    val tags: List<String>,
    val plans: List<NutritionPlan> = emptyList(),
    val dailyPlanSelections: List<DailyPlanSelection> = emptyList(),
    val waterRecords: List<WaterRecord> = emptyList(),
    val exerciseBurnRecords: List<ExerciseBurnRecord> = emptyList(),
    val aiSettings: AiSettings = AiSettings(),
    val aiSettingsList: List<AiSettings> = emptyList()
)

enum class Screen {
    Home,
    AddMeal,
    Plan,
    FoodLibrary,
    History,
    Report,
    Sponsor,
    AuthorWords
}

class MainActivity : ComponentActivity() {
    private val requestPhotoPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = 1f
                )
            ) {
                CalorieFreeApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ensurePhotoAccessPermission()
    }

    private fun ensurePhotoAccessPermission() {
        if (hasPhotoAccessPermission()) return
        requestPhotoPermissionsLauncher.launch(photoPermissionsToRequest())
    }

    private fun hasPhotoAccessPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= 34 -> {
                isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES) ||
                    isPermissionGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            Build.VERSION.SDK_INT >= 33 -> isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES)
            else -> isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun photoPermissionsToRequest(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun CalorieFreeApp() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var targetCalories by remember { mutableStateOf(2000.0) }
    var targetProtein by remember { mutableStateOf(120.0) }
    var targetFat by remember { mutableStateOf(65.0) }
    var targetCarbs by remember { mutableStateOf(250.0) }
    var userProfile by remember { mutableStateOf(UserProfile()) }
    var hasLoadedStorage by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(currentDateText()) }
    var mealEditorState by remember { mutableStateOf<MealEditorState?>(null) }
    var aiSettings by remember { mutableStateOf(AiSettings()) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var latestCheckedUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateErrorText by remember { mutableStateOf<String?>(null) }
    val currentVersionCode = remember { currentAppVersionCode(context) }
    val meals = remember { mutableStateListOf<MealRecord>() }
    val foods = remember { mutableStateListOf<FoodItem>() }
    val tags = remember { mutableStateListOf("早餐", "午餐", "晚餐", "下午加餐", "夜宵", "零食", "加餐") }
    val plans = remember { mutableStateListOf<NutritionPlan>() }
    val dailyPlanSelections = remember { mutableStateListOf<DailyPlanSelection>() }
    val waterRecords = remember { mutableStateListOf<WaterRecord>() }
    val exerciseBurnRecords = remember { mutableStateListOf<ExerciseBurnRecord>() }
    val aiSettingsList = remember { mutableStateListOf<AiSettings>() }

    LaunchedEffect(Unit) {
        runCatching { fetchLatestUpdateInfo() }
            .onSuccess { latest ->
                if (latest.versionCode > currentVersionCode) {
                    updateInfo = latest
                } else {
                    latestCheckedUpdateInfo = latest
                }
            }
            .onFailure { error ->
                updateErrorText = error.message
            }
    }

    LaunchedEffect(Unit) {
        val appState = loadAppState(context)
        targetCalories = appState.targetCalories
        targetProtein = appState.targetProtein
        targetFat = appState.targetFat
        targetCarbs = appState.targetCarbs
        userProfile = appState.userProfile
        meals.clear()
        meals.addAll(appState.meals)
        foods.clear()
        foods.addAll(
            if (appState.foods.isEmpty() && !appState.hasAnyStoredData) {
                defaultPresetFoods()
            } else {
                appState.foods
            }
        )
        tags.clear()
        tags.addAll(
            if (appState.tags.isEmpty()) {
                MEAL_OCCASION_TAGS
            } else {
                (MEAL_OCCASION_TAGS + appState.tags).distinct()
            }
        )
        plans.clear()
        plans.addAll(appState.plans)
        dailyPlanSelections.clear()
        dailyPlanSelections.addAll(appState.dailyPlanSelections)
        waterRecords.clear()
        waterRecords.addAll(appState.waterRecords)
        exerciseBurnRecords.clear()
        exerciseBurnRecords.addAll(appState.exerciseBurnRecords)
        aiSettingsList.clear()
        aiSettingsList.addAll(appState.aiSettingsList)
        aiSettings = aiSettingsList.firstOrNull { it.selectedForTextWork || it.selectedForVisionWork } ?: aiSettingsList.firstOrNull() ?: AiSettings()
        hasLoadedStorage = true
    }

    LaunchedEffect(hasLoadedStorage, targetCalories, targetProtein, targetFat, targetCarbs, userProfile, meals.toList(), foods.toList(), tags.toList(), plans.toList(), dailyPlanSelections.toList(), waterRecords.toList(), exerciseBurnRecords.toList(), aiSettingsList.toList()) {
        if (hasLoadedStorage) {
            saveAppState(
                context = context,
                targetCalories = targetCalories,
                targetProtein = targetProtein,
                targetFat = targetFat,
                targetCarbs = targetCarbs,
                userProfile = userProfile,
                meals = meals,
                foods = foods,
                tags = tags,
                plans = plans,
                dailyPlanSelections = dailyPlanSelections,
                waterRecords = waterRecords,
                exerciseBurnRecords = exerciseBurnRecords,
                aiSettings = aiSettingsList.firstOrNull() ?: AiSettings(),
                aiSettingsList = aiSettingsList
            )
        }
    }

    val currentPlan = plans.firstOrNull { it.id == dailyPlanSelections.firstOrNull { selection -> selection.date == selectedDate }?.planId }
        ?: plans.firstOrNull { it.isDefault }
        ?: plans.firstOrNull()
    val activeTargetCalories = currentPlan?.targetCalories ?: targetCalories
    val activeTargetProtein = currentPlan?.targetProtein ?: targetProtein
    val activeTargetFat = currentPlan?.targetFat ?: targetFat
    val activeTargetCarbs = currentPlan?.targetCarbs ?: targetCarbs
    val activeWaterTargetMl = currentPlan?.waterTargetMl ?: 2000.0
    val selectedDateWaterRecords = waterRecords.filter { it.date == selectedDate }
    val drunkWaterMl = selectedDateWaterRecords.sumOf { it.amountMl }
    val selectedExerciseBurn = exerciseBurnRecords.firstOrNull { it.date == selectedDate }
    val exerciseCalories = selectedExerciseBurn?.calories ?: 0.0
    val exerciseExtraCarbs = exerciseCarbsGrams(exerciseCalories)
    val exerciseExtraProtein = exerciseProteinGrams(exerciseCalories)
    val adjustedTargetCalories = activeTargetCalories + exerciseCalories
    val adjustedTargetProtein = activeTargetProtein + exerciseExtraProtein
    val adjustedTargetCarbs = activeTargetCarbs + exerciseExtraCarbs
    val textWorkAiSettings = aiSettingsList.firstOrNull { settings ->
        settings.enabled &&
            settings.selectedForTextWork &&
            settings.apiKey.isNotBlank() &&
            settings.baseUrl.isNotBlank()
    }

    AiSettingsDialog(
        visible = showAiSettingsDialog,
        settingsList = aiSettingsList,
        onDismiss = { showAiSettingsDialog = false },
        onUpdate = { updatedSettingsList ->
            aiSettingsList.clear()
            aiSettingsList.addAll(updatedSettingsList)
            aiSettings = updatedSettingsList.firstOrNull { it.selectedForTextWork || it.selectedForVisionWork } ?: updatedSettingsList.firstOrNull() ?: AiSettings()
        }
    )

    updateInfo?.let { latest ->
        UpdateAvailableDialog(
            currentVersionCode = currentVersionCode,
            updateInfo = latest,
            onDismiss = { updateInfo = null },
            onOpen = {
                openUrl(context, latest.url)
                updateInfo = null
            }
        )
    }

    latestCheckedUpdateInfo?.let { latest ->
        UpdateAlreadyLatestDialog(
            currentVersionCode = currentVersionCode,
            updateInfo = latest,
            onDismiss = { latestCheckedUpdateInfo = null }
        )
    }

    updateErrorText?.let { errorText ->
        ManualUpdateFallbackDialog(
            errorText = errorText,
            githubUrl = GITHUB_RELEASES_URL,
            lanzouUrl = LANZOU_UPDATE_URL,
            lanzouPassword = LANZOU_UPDATE_PASSWORD,
            onOpenGithub = {
                openUrl(context, GITHUB_RELEASES_URL)
                updateErrorText = null
            },
            onOpenLanzou = {
                openUrl(context, LANZOU_UPDATE_URL)
                updateErrorText = null
            },
            onDismiss = { updateErrorText = null }
        )
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (targetState == Screen.Home) {
                        (slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn())
                            .togetherWith(slideOutHorizontally(targetOffsetX = { it / 5 }) + fadeOut())
                    } else {
                        (slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn())
                            .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 5 }) + fadeOut())
                    }
                },
                label = "main_screen_transition"
            ) { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(
                        planName = currentPlan?.name.orEmpty(),
                        targetCalories = adjustedTargetCalories,
                        targetProtein = adjustedTargetProtein,
                        targetFat = activeTargetFat,
                        targetCarbs = adjustedTargetCarbs,
                        userProfile = userProfile,
                        baseTargetCalories = activeTargetCalories,
                        baseTargetProtein = activeTargetProtein,
                        baseTargetCarbs = activeTargetCarbs,
                        exerciseBurnRecord = selectedExerciseBurn,
                        textWorkAiSettings = textWorkAiSettings,
                        waterTargetMl = activeWaterTargetMl,
                        drunkWaterMl = drunkWaterMl,
                        waterRecords = selectedDateWaterRecords,
                        meals = meals.filter { it.date == selectedDate },
                        allMeals = meals.toList(),
                        recentFoods = foods.filterNot { it.isHidden }.take(3),
                        selectedDate = selectedDate,
                        onPreviousDayClick = { selectedDate = offsetDateText(selectedDate, -1) },
                        onNextDayClick = { selectedDate = offsetDateText(selectedDate, 1) },
                        onTodayClick = { selectedDate = currentDateText() },
                        onHistoryClick = { currentScreen = Screen.History },
                        onAddMealClick = {
                            mealEditorState = null
                            currentScreen = Screen.AddMeal
                        },
                        onPlanClick = { currentScreen = Screen.Plan },
                        onFoodLibraryClick = { currentScreen = Screen.FoodLibrary },
                        onReportClick = { currentScreen = Screen.Report },
                        onSponsorClick = { currentScreen = Screen.Sponsor },
                        onAuthorWordsClick = { currentScreen = Screen.AuthorWords },
                        onAiSettingsClick = { showAiSettingsDialog = true },
                        onQuickAddFood = { food, quantity, recordTags ->
                            val servingCount = servingCountFromQuantity(quantity, food.servingType)
                            val totalProtein = roundOneDecimal(food.protein * servingCount)
                            val totalFat = roundOneDecimal(food.fat * servingCount)
                            val totalCarbs = roundOneDecimal(food.carbs * servingCount)
                            meals.add(
                                0,
                                MealRecord(
                                    name = food.name,
                                    calories = calculateCalories(totalProtein, totalFat, totalCarbs),
                                    protein = totalProtein,
                                    fat = totalFat,
                                    carbs = totalCarbs,
                                    time = currentTimeText(),
                                    date = selectedDate,
                                    tags = recordTags,
                                    servingType = food.servingType,
                                    quantity = quantity,
                                    servingCount = servingCount,
                                    unitLabel = food.unitLabel
                                )
                            )
                            recordTags.forEach { tag -> if (tags.none { it == tag }) tags.add(tag) }
                        },
                        onAddWater = { amountMl ->
                            waterRecords.add(
                                0,
                                WaterRecord(
                                    id = (waterRecords.maxOfOrNull { it.id } ?: 0L) + 1L,
                                    date = selectedDate,
                                    amountMl = amountMl
                                )
                            )
                        },
                        onDeleteWater = { record -> waterRecords.remove(record) },
                        onSaveExerciseBurn = { record ->
                            exerciseBurnRecords.removeAll { it.date == selectedDate }
                            if (record.calories > 0) {
                                exerciseBurnRecords.add(record.copy(date = selectedDate))
                            }
                        },
                        onClearExerciseBurn = {
                            exerciseBurnRecords.removeAll { it.date == selectedDate }
                        },
                        onEditMeal = { meal ->
                            mealEditorState = MealEditorState(meal)
                            currentScreen = Screen.AddMeal
                        },
                        onDeleteMeal = { meal -> meals.remove(meal) },
                        onClearMeals = { meals.removeAll { it.date == selectedDate } }
                    )

                    Screen.AddMeal -> AddMealScreen(
                        tags = tags,
                        aiSettingsList = aiSettingsList,
                        initialMeal = mealEditorState?.originalMeal,
                        onBack = {
                            mealEditorState = null
                            currentScreen = Screen.Home
                        },
                        onAddPresetTag = { tag -> if (tags.none { it == tag }) tags.add(tag) },
                        onSave = { meal, saveToLibrary, libraryCategory ->
                            if (saveToLibrary && foods.none { it.name.equals(meal.name, ignoreCase = true) }) {
                                foods.add(
                                    FoodItem(
                                        name = meal.name,
                                        protein = roundOneDecimal(meal.protein / meal.servingCount),
                                        fat = roundOneDecimal(meal.fat / meal.servingCount),
                                        carbs = roundOneDecimal(meal.carbs / meal.servingCount),
                                        servingType = meal.servingType,
                                        category = libraryCategory,
                                        unitLabel = meal.unitLabel
                                    )
                                )
                            }
                            val editingMeal = mealEditorState?.originalMeal
                            val savedMeal = meal.copy(date = editingMeal?.date ?: selectedDate)
                            if (editingMeal != null) {
                                val editingIndex = meals.indexOfFirst { it === editingMeal }
                                if (editingIndex >= 0) {
                                    meals[editingIndex] = savedMeal
                                } else {
                                    meals.add(0, savedMeal)
                                }
                            } else {
                                meals.add(0, savedMeal)
                            }
                            mealEditorState = null
                            currentScreen = Screen.Home
                        }
                    )

                    Screen.Plan -> PlanScreen(
                        selectedDate = selectedDate,
                        userProfile = userProfile,
                        plans = plans,
                        selectedPlanId = dailyPlanSelections.firstOrNull { it.date == selectedDate }?.planId
                            ?: plans.firstOrNull { it.isDefault }?.id,
                        onBack = { currentScreen = Screen.Home },
                        onSaveUserProfile = { userProfile = it },
                        onSelectPlanForDate = { planId ->
                            val existingIndex = dailyPlanSelections.indexOfFirst { it.date == selectedDate }
                            if (existingIndex >= 0) {
                                dailyPlanSelections[existingIndex] = DailyPlanSelection(selectedDate, planId)
                            } else {
                                dailyPlanSelections.add(DailyPlanSelection(selectedDate, planId))
                            }
                            val selectedPlan = plans.firstOrNull { it.id == planId }
                            if (selectedPlan != null) {
                                targetCalories = selectedPlan.targetCalories
                                targetProtein = selectedPlan.targetProtein
                                targetFat = selectedPlan.targetFat
                                targetCarbs = selectedPlan.targetCarbs
                            }
                        },
                        onSavePlan = { plan ->
                            val existingIndex = plans.indexOfFirst { it.id == plan.id }
                            if (existingIndex >= 0) {
                                plans[existingIndex] = plan.copy(isDefault = if (plan.isDefault) true else plans[existingIndex].isDefault)
                            } else {
                                plans.add(plan)
                            }
                            if (plan.isDefault) {
                                plans.replaceAll { existing ->
                                    if (existing.id == plan.id) plan.copy(isDefault = true) else existing.copy(isDefault = false)
                                }
                            }
                            if (dailyPlanSelections.none { it.date == selectedDate }) {
                                dailyPlanSelections.add(DailyPlanSelection(selectedDate, plan.id))
                            }
                        },
                        onDeletePlan = { planId ->
                            plans.removeAll { it.id == planId }
                            dailyPlanSelections.removeAll { it.planId == planId }
                            val fallbackPlan = plans.firstOrNull { it.isDefault } ?: plans.firstOrNull()
                            if (fallbackPlan != null) {
                                targetCalories = fallbackPlan.targetCalories
                                targetProtein = fallbackPlan.targetProtein
                                targetFat = fallbackPlan.targetFat
                                targetCarbs = fallbackPlan.targetCarbs
                            }
                        },
                        onSetDefaultPlan = { planId ->
                            plans.replaceAll { plan -> plan.copy(isDefault = plan.id == planId) }
                        }
                    )

                    Screen.History -> HistoryScreen(
                        meals = meals,
                        waterRecords = waterRecords,
                        exerciseBurnRecords = exerciseBurnRecords,
                        plans = plans,
                        dailyPlanSelections = dailyPlanSelections,
                        selectedDate = selectedDate,
                        onBack = { currentScreen = Screen.Home },
                        onSelectDate = { date ->
                            selectedDate = date
                            currentScreen = Screen.Home
                        }
                    )

                    Screen.Report -> ReportScreen(
                        selectedDate = selectedDate,
                        plan = currentPlan,
                        meals = meals.filter { it.date == selectedDate },
                        exerciseBurnRecord = selectedExerciseBurn,
                        adjustedTargetCalories = adjustedTargetCalories,
                        adjustedTargetProtein = adjustedTargetProtein,
                        adjustedTargetCarbs = adjustedTargetCarbs,
                        waterTargetMl = activeWaterTargetMl,
                        drunkWaterMl = drunkWaterMl,
                        aiSettingsList = aiSettingsList,
                        onBack = { currentScreen = Screen.Home }
                    )

                    Screen.Sponsor -> SponsorScreen(
                        onBack = { currentScreen = Screen.Home }
                    )

                    Screen.AuthorWords -> AuthorWordsScreen(
                        onBack = { currentScreen = Screen.Home }
                    )

                    Screen.FoodLibrary -> FoodLibraryScreen(
                        foods = foods,
                        tags = tags,
                        onBack = { currentScreen = Screen.Home },
                        onUseFood = { food, quantity, recordTags ->
                            val servingCount = servingCountFromQuantity(quantity, food.servingType)
                            val totalProtein = roundOneDecimal(food.protein * servingCount)
                            val totalFat = roundOneDecimal(food.fat * servingCount)
                            val totalCarbs = roundOneDecimal(food.carbs * servingCount)
                            meals.add(
                                0,
                                MealRecord(
                                    name = food.name,
                                    calories = calculateCalories(totalProtein, totalFat, totalCarbs),
                                    protein = totalProtein,
                                    fat = totalFat,
                                    carbs = totalCarbs,
                                    time = currentTimeText(),
                                    date = selectedDate,
                                    tags = recordTags,
                                    servingType = food.servingType,
                                    quantity = quantity,
                                    servingCount = servingCount,
                                    unitLabel = food.unitLabel
                                )
                            )
                            recordTags.forEach { tag -> if (tags.none { it == tag }) tags.add(tag) }
                            currentScreen = Screen.Home
                        },
                        onDeleteFood = { food -> foods.remove(food) },
                        onClearFoods = { foods.clear() },
                        onUpdateFood = { index, updatedFood -> foods[index] = updatedFood },
                        onAddFood = { newFood -> foods.add(newFood) }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    planName: String,
    targetCalories: Double,
    targetProtein: Double,
    targetFat: Double,
    targetCarbs: Double,
    userProfile: UserProfile,
    baseTargetCalories: Double,
    baseTargetProtein: Double,
    baseTargetCarbs: Double,
    exerciseBurnRecord: ExerciseBurnRecord?,
    textWorkAiSettings: AiSettings?,
    waterTargetMl: Double,
    drunkWaterMl: Double,
    waterRecords: List<WaterRecord>,
    meals: List<MealRecord>,
    allMeals: List<MealRecord>,
    recentFoods: List<FoodItem>,
    selectedDate: String,
    onPreviousDayClick: () -> Unit,
    onNextDayClick: () -> Unit,
    onTodayClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onAddMealClick: () -> Unit,
    onPlanClick: () -> Unit,
    onFoodLibraryClick: () -> Unit,
    onReportClick: () -> Unit,
    onSponsorClick: () -> Unit,
    onAuthorWordsClick: () -> Unit,
    onAiSettingsClick: () -> Unit,
    onQuickAddFood: (FoodItem, Double, List<String>) -> Unit,
    onAddWater: (Double) -> Unit,
    onDeleteWater: (WaterRecord) -> Unit,
    onSaveExerciseBurn: (ExerciseBurnRecord) -> Unit,
    onClearExerciseBurn: () -> Unit,
    onEditMeal: (MealRecord) -> Unit,
    onDeleteMeal: (MealRecord) -> Unit,
    onClearMeals: () -> Unit
) {
    val eatenCalories = meals.sumOf { it.calories }
    val eatenProtein = meals.sumOf { it.protein }
    val eatenFat = meals.sumOf { it.fat }
    val eatenCarbs = meals.sumOf { it.carbs }
    val remainingCalories = targetCalories - eatenCalories
    var mealPendingDelete by remember { mutableStateOf<MealRecord?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var showExerciseBurnDialog by remember { mutableStateOf(false) }
    var showRemainingEquivalentDialog by remember { mutableStateOf(false) }
    val remainingProtein = targetProtein - eatenProtein
    val remainingCarbs = targetCarbs - eatenCarbs
    val remainingEquivalent = remember(allMeals, remainingProtein, remainingCarbs) {
        estimateRemainingFoodEquivalent(
            mealHistory = allMeals,
            remainingCarbs = remainingCarbs,
            remainingProtein = remainingProtein
        )
    }
    val basalMetabolism = remember(userProfile) { calculateBasalMetabolism(userProfile) }
    val shouldShowBmrReminder = basalMetabolism != null && selectedDate <= currentDateText() && eatenCalories < basalMetabolism
    val recognitionImageType = RecognitionImageType.FoodPhoto
    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCropLabel by remember { mutableStateOf<String?>(null) }
    var selectedImageDataUrl by remember { mutableStateOf<String?>(null) }
    var selectedImageLabel by remember { mutableStateOf<String?>(null) }
    var showAiAnalysisDialog by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    if (showRemainingEquivalentDialog) {
        RemainingFoodEquivalentDialog(
            remainingCarbs = remainingCarbs,
            remainingProtein = remainingProtein,
            equivalent = remainingEquivalent,
            onDismiss = { showRemainingEquivalentDialog = false }
        )
    }

    mealPendingDelete?.let { meal ->
        ConfirmDialog(
            title = "删除记录",
            message = "确定要删除「${meal.name}」吗？此操作无法撤销。",
            confirmText = "删除",
            onConfirm = {
                onDeleteMeal(meal)
                mealPendingDelete = null
            },
            onDismiss = { mealPendingDelete = null }
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "清空记录",
            message = "确定要清空 $selectedDate 的所有饮食记录吗？此操作无法撤销。",
            confirmText = "清空",
            onConfirm = {
                onClearMeals()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false }
        )
    }

    if (showQuickAddDialog) {
        QuickAddFoodDialog(
            foods = recentFoods,
            tags = MEAL_OCCASION_TAGS,
            onDismiss = { showQuickAddDialog = false },
            onConfirm = { food, quantity, recordTags ->
                onQuickAddFood(food, quantity, recordTags)
                showQuickAddDialog = false
            }
        )
    }

    if (showExerciseBurnDialog) {
        ExerciseBurnDialog(
            selectedDate = selectedDate,
            currentRecord = exerciseBurnRecord,
            textWorkAiSettings = textWorkAiSettings,
            onDismiss = { showExerciseBurnDialog = false },
            onSave = { record ->
                onSaveExerciseBurn(record)
                showExerciseBurnDialog = false
            },
            onClear = {
                onClearExerciseBurn()
                showExerciseBurnDialog = false
            }
        )
    }

    AppBackground {
        pendingCropBitmap?.let { bitmap ->
            ImageCropDialog(
                bitmap = bitmap,
                imageType = recognitionImageType,
                sourceLabel = pendingCropLabel,
                onDismiss = {
                    pendingCropBitmap = null
                    pendingCropLabel = null
                },
                onUseOriginal = { originalBitmap ->
                    selectedImageDataUrl = bitmapToRecognitionDataUrl(originalBitmap, recognitionImageType)
                    selectedImageLabel = pendingCropLabel
                    pendingCropBitmap = null
                    pendingCropLabel = null
                    showAiAnalysisDialog = true
                    errorText = null
                },
                onConfirmCrop = { croppedBitmap ->
                    selectedImageDataUrl = bitmapToRecognitionDataUrl(croppedBitmap, recognitionImageType)
                    selectedImageLabel = listOfNotNull(pendingCropLabel, "已截取").joinToString(" · ")
                    pendingCropBitmap = null
                    pendingCropLabel = null
                    showAiAnalysisDialog = true
                    errorText = null
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CalorieFree",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF161A23)
                    )
                    Text(
                        text = "今天也轻松掌控饮食",
                        fontSize = 16.sp,
                        color = Color(0xFF6F7785)
                    )
                }
                OutlinedButton(onClick = onPlanClick, shape = RoundedCornerShape(16.dp)) {
                    Text(text = "计划")
                }
            }

            DateNavigatorCard(
                selectedDate = selectedDate,
                onPreviousDayClick = onPreviousDayClick,
                onNextDayClick = onNextDayClick,
                onTodayClick = onTodayClick,
                onHistoryClick = onHistoryClick
            )

            SummaryCard(
                planName = planName,
                targetCalories = targetCalories,
                eatenCalories = eatenCalories,
                remainingCalories = remainingCalories
            )

            ExerciseBurnCard(
                exerciseBurnRecord = exerciseBurnRecord,
                baseTargetCalories = baseTargetCalories,
                adjustedTargetCalories = targetCalories,
                extraProtein = targetProtein - baseTargetProtein,
                extraCarbs = targetCarbs - baseTargetCarbs,
                onClick = { showExerciseBurnDialog = true }
            )

            if (shouldShowBmrReminder) {
                BmrReminderCard(
                    basalMetabolism = basalMetabolism ?: 0.0,
                    eatenCalories = eatenCalories
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "营养进度",
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF161A23)
                )
                OutlinedButton(
                    onClick = { showRemainingEquivalentDialog = true },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = "剩余能吃什么", fontSize = 12.sp)
                }
            }

            NutritionRow(
                label = "碳水",
                value = "${formatNumber(eatenCarbs)} / ${formatNumber(targetCarbs)} g",
                color = Color(0xFF66BB6A),
                progress = nutritionProgress(eatenCarbs, targetCarbs)
            )
            NutritionRow(
                label = "蛋白质",
                value = "${formatNumber(eatenProtein)} / ${formatNumber(targetProtein)} g",
                color = Color(0xFF5B8DEF),
                progress = nutritionProgress(eatenProtein, targetProtein)
            )
            NutritionRow(
                label = "脂肪",
                value = "${formatNumber(eatenFat)} / ${formatNumber(targetFat)} g",
                color = Color(0xFFFFA726),
                progress = nutritionProgress(eatenFat, targetFat)
            )
            WaterSummaryCard(
                waterTargetMl = waterTargetMl,
                drunkWaterMl = drunkWaterMl,
                waterRecords = waterRecords,
                onAddWater = onAddWater,
                onDeleteWater = onDeleteWater
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onAddMealClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(text = "添加记录", fontSize = 16.sp)
                }
                OutlinedButton(
                    onClick = onFoodLibraryClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(text = "食物库", fontSize = 16.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onReportClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(text = "AI 营养分析", fontSize = 16.sp)
                }
                OutlinedButton(
                    onClick = onAiSettingsClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(text = "AI 设置", fontSize = 16.sp)
                }
            }

            OutlinedButton(
                onClick = onSponsorClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(text = "赞助支持以及反馈BUG", fontSize = 16.sp)
            }

            OutlinedButton(
                onClick = onAuthorWordsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(text = "作者的话以及减脂建议", fontSize = 16.sp)
            }

            if (recentFoods.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showQuickAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(text = "快速添加常用食物", fontSize = 16.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "今日记录",
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF161A23)
                )
                if (meals.isNotEmpty()) {
                    OutlinedButton(onClick = { showClearConfirm = true }, shape = RoundedCornerShape(16.dp)) {
                        Text(text = "清空")
                    }
                }
            }

            if (meals.isEmpty()) {
                EmptyMealCard()
            } else {
                val mealsByTag = groupMealsByPrimaryTag(meals)
                mealsByTag.forEach { (tag, tagMeals) ->
                    val tagCalories = tagMeals.sumOf { it.calories }
                    Text(
                        text = "$tag · ${formatNumber(tagCalories)} kcal",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF5B8DEF)
                    )
                    tagMeals.forEach { meal ->
                        MealCard(
                            name = meal.name,
                            calories = "${formatNumber(meal.calories)} kcal",
                            amountText = mealAmountText(meal),
                            tagText = meal.tags.joinToString(" · "),
                            nutritionText = "碳水${formatNumber(meal.carbs)}g · 蛋白${formatNumber(meal.protein)}g · 脂肪${formatNumber(meal.fat)}g",
                            onEdit = { onEditMeal(meal) },
                            onDelete = { mealPendingDelete = meal }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun AddMealScreen(
    tags: List<String>,
    aiSettingsList: List<AiSettings>,
    initialMeal: MealRecord? = null,
    onBack: () -> Unit,
    onAddPresetTag: (String) -> Unit,
    onSave: (MealRecord, Boolean, String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val initialServingCount = initialMeal?.servingCount?.takeIf { it > 0 } ?: 1.0
    var name by remember(initialMeal) { mutableStateOf(initialMeal?.name.orEmpty()) }
    var perServingProtein by remember(initialMeal) { mutableStateOf(initialMeal?.let { formatNumber(it.protein / initialServingCount) }.orEmpty()) }
    var perServingFat by remember(initialMeal) { mutableStateOf(initialMeal?.let { formatNumber(it.fat / initialServingCount) }.orEmpty()) }
    var perServingCarbs by remember(initialMeal) { mutableStateOf(initialMeal?.let { formatNumber(it.carbs / initialServingCount) }.orEmpty()) }
    var totalProteinInput by remember(initialMeal) { mutableStateOf(initialMeal?.protein?.let(::formatNumber).orEmpty()) }
    var totalFatInput by remember(initialMeal) { mutableStateOf(initialMeal?.fat?.let(::formatNumber).orEmpty()) }
    var totalCarbsInput by remember(initialMeal) { mutableStateOf(initialMeal?.carbs?.let(::formatNumber).orEmpty()) }
    var quantityText by remember(initialMeal) { mutableStateOf(initialMeal?.quantity?.takeIf { it > 0 }?.let(::formatServingCount).orEmpty()) }
    var servingType by remember(initialMeal) { mutableStateOf(initialMeal?.servingType ?: ServingType.Per100g) }
    var unitLabel by remember(initialMeal) { mutableStateOf(initialMeal?.unitLabel?.takeIf { it.isNotBlank() } ?: "个") }
    var inputMode by remember(initialMeal) { mutableStateOf(if (initialMeal == null) NutritionInputMode.PerServing else NutritionInputMode.Total) }
    var customTag by remember(initialMeal) { mutableStateOf(initialMeal?.tags?.joinToString(";").orEmpty()) }
    var aiFoodDescription by remember(initialMeal) { mutableStateOf("") }
    var saveToLibrary by remember(initialMeal) { mutableStateOf(false) }
    var libraryCategory by remember(initialMeal) { mutableStateOf("自定义") }
    var customLibraryCategory by remember(initialMeal) { mutableStateOf("") }
    var showAiAnalysisDialog by remember(initialMeal) { mutableStateOf(false) }
    var selectedImageDataUrl by remember(initialMeal) { mutableStateOf<String?>(null) }
    var selectedImageLabel by remember(initialMeal) { mutableStateOf<String?>(null) }
    var recognitionImageType by remember(initialMeal) { mutableStateOf(RecognitionImageType.FoodPhoto) }
    var pendingCropBitmap by remember(initialMeal) { mutableStateOf<Bitmap?>(null) }
    var pendingCropLabel by remember(initialMeal) { mutableStateOf<String?>(null) }
    var pendingCameraImageUri by remember(initialMeal) { mutableStateOf<Uri?>(null) }
    var showAiAccuracyTipsDialog by remember(initialMeal) { mutableStateOf(false) }
    var errorText by remember(initialMeal) { mutableStateOf<String?>(null) }
    val hasVisionWorkingAi = aiSettingsList.any { it.enabled && it.selectedForVisionWork && it.canHandleVisionTasks() && it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val imageDataUrl = imageDataUrlFromUri(context, uri, recognitionImageType)
            if (imageDataUrl != null) {
                selectedImageDataUrl = imageDataUrl
                selectedImageLabel = "相册图片 · ${if (recognitionImageType == RecognitionImageType.NutritionLabel) "营养成分表" else "食物照片"}"
                showAiAnalysisDialog = true
                errorText = null
            } else {
                errorText = "图片读取失败，请换一张照片再试"
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            selectedImageDataUrl = bitmapToRecognitionDataUrl(bitmap, recognitionImageType)
            selectedImageLabel = "相机照片 · ${if (recognitionImageType == RecognitionImageType.NutritionLabel) "营养成分表" else "食物照片"}"
            showAiAnalysisDialog = true
            errorText = null
        } else {
            errorText = "没有获取到照片，请重新拍摄"
        }
    }
    val galleryCropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val selectedBitmap = bitmapFromUri(context, uri)
            if (selectedBitmap != null) {
                pendingCropBitmap = selectedBitmap
                pendingCropLabel = "相册图片 · ${if (recognitionImageType == RecognitionImageType.NutritionLabel) "营养成分表" else "食物照片"}"
                errorText = null
            } else {
                errorText = "图片读取失败，请换一张照片再试"
            }
        }
    }
    val cameraCropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            pendingCropBitmap = bitmap
            pendingCropLabel = "相机照片 · ${if (recognitionImageType == RecognitionImageType.NutritionLabel) "营养成分表" else "食物照片"}"
            errorText = null
        } else {
            errorText = "没有获取到照片，请重新拍摄"
        }
    }
    val cameraFullResLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val captureUri = pendingCameraImageUri
        if (success && captureUri != null) {
            val capturedBitmap = bitmapFromUri(context, captureUri)
            if (capturedBitmap != null) {
                pendingCropBitmap = capturedBitmap
                pendingCropLabel = "相机照片 · ${if (recognitionImageType == RecognitionImageType.NutritionLabel) "营养成分表" else "食物照片"}"
                errorText = null
            } else {
                errorText = "照片读取失败，请重新拍摄"
            }
        } else {
            errorText = "没有获取到照片，请重新拍摄"
        }
        pendingCameraImageUri = null
    }
    val protein = if (inputMode == NutritionInputMode.PerServing) perServingProtein else totalProteinInput
    val fat = if (inputMode == NutritionInputMode.PerServing) perServingFat else totalFatInput
    val carbs = if (inputMode == NutritionInputMode.PerServing) perServingCarbs else totalCarbsInput
    val quantity = quantityText.toDoubleOrNull() ?: 0.0
    val servingBaseAmount = if (servingType == ServingType.PerItem) 1.0 else 100.0
    val inputCalories = calculateCalories(
        protein = parseOneDecimal(protein) ?: 0.0,
        fat = parseOneDecimal(fat) ?: 0.0,
        carbs = parseOneDecimal(carbs) ?: 0.0
    )
    val previewTotalCalories = if (inputMode == NutritionInputMode.PerServing) {
        roundOneDecimal(inputCalories * quantity / servingBaseAmount)
    } else {
        inputCalories
    }
    val previewFormulaText = if (inputMode == NutritionInputMode.PerServing && quantity > 0) {
        "自动计算热量：${formatNumber(inputCalories)}kcal/${servingTypeLabel(servingType, unitLabel)} × ${formatServingCount(quantity)}${quantityUnitText(servingType, unitLabel)} = ${formatNumber(previewTotalCalories)} kcal"
    } else {
        "自动计算热量：${formatNumber(previewTotalCalories)} kcal"
    }

    AppBackground {
        pendingCropBitmap?.let { bitmap ->
            ImageCropDialog(
                bitmap = bitmap,
                imageType = recognitionImageType,
                sourceLabel = pendingCropLabel,
                onDismiss = {
                    pendingCropBitmap = null
                    pendingCropLabel = null
                },
                onUseOriginal = { originalBitmap ->
                    selectedImageDataUrl = bitmapToRecognitionDataUrl(originalBitmap, recognitionImageType)
                    selectedImageLabel = pendingCropLabel
                    pendingCropBitmap = null
                    pendingCropLabel = null
                    showAiAnalysisDialog = true
                    errorText = null
                },
                onConfirmCrop = { croppedBitmap ->
                    selectedImageDataUrl = bitmapToRecognitionDataUrl(croppedBitmap, recognitionImageType)
                    selectedImageLabel = listOfNotNull(pendingCropLabel, "已截取").joinToString(" · ")
                    pendingCropBitmap = null
                    pendingCropLabel = null
                    showAiAnalysisDialog = true
                    errorText = null
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (initialMeal == null) "添加饮食记录" else "编辑饮食记录",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF161A23)
            )
            Text(
                text = if (initialMeal == null) "可手动录入，也可以拍照或从相册选择后让 AI 识别营养表或食物。" else "修改后保存，将直接更新这条记录。",
                fontSize = 15.sp,
                color = Color(0xFF6F7785)
            )

            AiMealAnalysisDialog(
                visible = showAiAnalysisDialog,
                aiSettingsList = aiSettingsList,
                foodName = name,
                servingType = servingType,
                quantityText = quantityText,
                carbsText = carbs,
                proteinText = protein,
                fatText = fat,
                inputMode = inputMode,
                foodDescription = aiFoodDescription,
                imageDataUrl = selectedImageDataUrl,
                imageLabel = selectedImageLabel,
                imageType = recognitionImageType,
                onDismiss = { showAiAnalysisDialog = false },
                onApplySuggestion = { _, shouldSaveToLibrary, suggestedCategory ->
                    saveToLibrary = shouldSaveToLibrary
                    libraryCategory = suggestedCategory
                    showAiAnalysisDialog = false
                },
                onApplyRecognizedFood = { recognizedFood, suggestedQuantity, _ ->
                    val recognizedServingCount = servingCountFromQuantity(suggestedQuantity, recognizedFood.servingType)
                    val recognizedTotalProtein = roundOneDecimal(recognizedFood.protein * recognizedServingCount)
                    val recognizedTotalFat = roundOneDecimal(recognizedFood.fat * recognizedServingCount)
                    val recognizedTotalCarbs = roundOneDecimal(recognizedFood.carbs * recognizedServingCount)
                    name = recognizedFood.name
                    perServingProtein = formatNumber(recognizedFood.protein)
                    perServingFat = formatNumber(recognizedFood.fat)
                    perServingCarbs = formatNumber(recognizedFood.carbs)
                    totalProteinInput = formatNumber(recognizedTotalProtein)
                    totalFatInput = formatNumber(recognizedTotalFat)
                    totalCarbsInput = formatNumber(recognizedTotalCarbs)
                    servingType = recognizedFood.servingType
                    quantityText = formatServingCount(suggestedQuantity)
                    saveToLibrary = true
                    libraryCategory = recognizedFood.category
                    showAiAnalysisDialog = false
                }
            )

            FormCard {
                Text(
                    text = "识别类型",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF303747)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    WebStyleTab(
                        text = "食物照片",
                        selected = recognitionImageType == RecognitionImageType.FoodPhoto,
                        onClick = { recognitionImageType = RecognitionImageType.FoodPhoto },
                        modifier = Modifier.weight(1f)
                    )
                    WebStyleTab(
                        text = "营养成分表",
                        selected = recognitionImageType == RecognitionImageType.NutritionLabel,
                        onClick = { recognitionImageType = RecognitionImageType.NutritionLabel },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = if (recognitionImageType == RecognitionImageType.NutritionLabel) {
                        "适合包装背面的营养成分表，AI 会优先直接读取每份或每100g的数值。"
                    } else {
                        "适合菜品、餐盘和外卖照片，AI 会根据画面估算食物和份量。"
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF6F7785)
                )
                AppTextField(
                    label = "食物文字描述（可选，例如：炸鸡柳，表面比较油，肉占比约50%）",
                    value = aiFoodDescription,
                    onValueChange = { aiFoodDescription = it }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "如何提高 AI 分析准确率？",
                        modifier = Modifier.clickable { showAiAccuracyTipsDialog = true },
                        fontSize = 12.sp,
                        color = Color(0xFF5B8DEF),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                OutlinedButton(
                    onClick = { showAiAnalysisDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "通过文字描述分析食物", fontSize = 15.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (hasVisionWorkingAi) {
                                val captureUri = createTempCameraImageUri(context)
                                if (captureUri != null) {
                                    pendingCameraImageUri = captureUri
                                    cameraFullResLauncher.launch(captureUri)
                                } else {
                                    errorText = "无法创建相机照片文件，请稍后再试"
                                }
                            } else {
                                errorText = "没有可用的图片识别工作 AI。请先在 AI 设置中启用支持图片的模型并填写 API Key。"
                            }
                        },
                        enabled = hasVisionWorkingAi,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = "拍照识别", fontSize = 14.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            if (hasVisionWorkingAi) {
                                galleryCropLauncher.launch("image/*")
                            } else {
                                errorText = "没有可用的图片识别工作 AI。请先在 AI 设置中启用支持图片的模型并填写 API Key。"
                            }
                        },
                        enabled = hasVisionWorkingAi,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = "相册识别", fontSize = 14.sp)
                    }
                }
                selectedImageLabel?.let { label ->
                    Text(
                        text = "已选择：$label，打开 AI 分析时会优先识别图片内容。",
                        fontSize = 12.sp,
                        color = Color(0xFF6F7785)
                    )
                }
                if (!hasVisionWorkingAi) {
                    Text(
                        text = "拍照/相册识别需要至少一个已启用、已选为图片识别工作 AI、支持图片且已填写 API Key 的模型。",
                        fontSize = 12.sp,
                        color = Color(0xFFEF5350)
                    )
                }
            }

            FormCard {
                AppTextField(label = "食物名称", value = name, onValueChange = { name = it })
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { servingType = ServingType.PerItem },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = if (servingType == ServingType.PerItem) "✓ 每个" else "每个")
                    }
                    OutlinedButton(
                        onClick = { servingType = ServingType.Per100g },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = if (servingType == ServingType.Per100g) "✓ 每100g" else "每100g")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    WebStyleTab(
                        text = "记录${servingTypeLabel(servingType, unitLabel)}营养",
                        selected = inputMode == NutritionInputMode.PerServing,
                        onClick = { inputMode = NutritionInputMode.PerServing },
                        modifier = Modifier.weight(1f)
                    )
                    WebStyleTab(
                        text = "记录总共营养",
                        selected = inputMode == NutritionInputMode.Total,
                        onClick = { inputMode = NutritionInputMode.Total },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (inputMode == NutritionInputMode.PerServing) {
                    AppTextField(label = "${servingTypeLabel(servingType, unitLabel)}碳水 g", value = perServingCarbs, onValueChange = { perServingCarbs = it }, isNumber = true)
                    AppTextField(label = "${servingTypeLabel(servingType, unitLabel)}蛋白质 g", value = perServingProtein, onValueChange = { perServingProtein = it }, isNumber = true)
                    AppTextField(label = "${servingTypeLabel(servingType, unitLabel)}脂肪 g", value = perServingFat, onValueChange = { perServingFat = it }, isNumber = true)
                } else {
                    AppTextField(label = "总碳水 g", value = totalCarbsInput, onValueChange = { totalCarbsInput = it }, isNumber = true)
                    AppTextField(label = "总蛋白质 g", value = totalProteinInput, onValueChange = { totalProteinInput = it }, isNumber = true)
                    AppTextField(label = "总脂肪 g", value = totalFatInput, onValueChange = { totalFatInput = it }, isNumber = true)
                }
                AppTextField(
                    label = if (servingType == ServingType.PerItem) "食物总量（${unitLabel.ifBlank { "个" }}）" else "食物总量（克）",
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    isNumber = true
                )
                if (servingType == ServingType.PerItem) {
                    AppTextField(
                        label = "量词（可选，不填默认为个）",
                        value = if (unitLabel == "个") "" else unitLabel,
                        onValueChange = { unitLabel = it.trim().ifBlank { "个" } }
                    )
                }
                Text(
                    text = "标签",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF303747)
                )
                if (tags.isNotEmpty()) {
                    tags.take(7).chunked(3).forEach { rowTags ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowTags.forEach { tag ->
                                OutlinedButton(
                                    onClick = {
                                        val existingTags = customTag
                                            .split(";", "；", "\n")
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                        customTag = toggleSelectableTag(existingTags, tag).joinToString(";")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    val currentTags = customTag
                                        .split(";", "；", "\n")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                    Text(text = if (currentTags.contains(tag)) "✓ $tag" else tag, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                            repeat(3 - rowTags.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                AppTextField(
                    label = "输入标签后用分号分隔，例如：早餐;训练后;低脂",
                    value = customTag,
                    onValueChange = { customTag = it }
                )
                Text(
                    text = previewFormulaText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF303747)
                )
                OutlinedButton(
                    onClick = { saveToLibrary = !saveToLibrary },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = if (saveToLibrary) "✓ 保存到食物库" else "保存到食物库")
                }
                if (saveToLibrary) {
                    CategorySelectorRow(
                        categories = defaultFoodCategories(),
                        selectedCategory = libraryCategory,
                        onSelectCategory = { libraryCategory = it }
                    )
                    if (libraryCategory == "自定义") {
                        AppTextField(
                            label = "自定义分类名称",
                            value = customLibraryCategory,
                            onValueChange = { customLibraryCategory = it }
                        )
                    }
                }
            }

            errorText?.let {
                Text(text = it, color = Color(0xFFEF5350), fontSize = 14.sp)
            }

            Button(
                onClick = {
                    val inputProtein = parseOneDecimal(protein) ?: 0.0
                    val inputFat = parseOneDecimal(fat) ?: 0.0
                    val inputCarbs = parseOneDecimal(carbs) ?: 0.0
                    val inputQuantity = quantityText.toDoubleOrNull() ?: 0.0
                    val servings = servingCountFromQuantity(inputQuantity, servingType)
                    val quantityRequired = inputMode == NutritionInputMode.PerServing || saveToLibrary

                    val finalTags = customTag
                        .split(";", "；", "\n")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .let(::normalizeSelectableTags)

                    if (name.isBlank()) {
                        errorText = "请输入食物名称"
                        return@Button
                    }
                    if (finalTags.isEmpty()) {
                        errorText = "请至少选择或输入一个标签"
                        return@Button
                    }
                    if (inputProtein < 0 || inputFat < 0 || inputCarbs < 0) {
                        errorText = "营养素不能为负数"
                        return@Button
                    }
                    if (quantityRequired && (inputQuantity <= 0 || servings <= 0.0)) {
                        errorText = if (servingType == ServingType.PerItem) "请输入有效个数" else "请输入有效克数"
                        return@Button
                    }

                    val totalProtein: Double
                    val totalFat: Double
                    val totalCarbs: Double

                    if (inputMode == NutritionInputMode.PerServing) {
                        totalProtein = roundOneDecimal(inputProtein * servings)
                        totalFat = roundOneDecimal(inputFat * servings)
                        totalCarbs = roundOneDecimal(inputCarbs * servings)
                    } else {
                        totalProtein = inputProtein
                        totalFat = inputFat
                        totalCarbs = inputCarbs
                    }

                    val calculatedCalories = calculateCalories(totalProtein, totalFat, totalCarbs)

                    if (calculatedCalories <= 0) {
                        errorText = "请至少输入一种有效营养素"
                        return@Button
                    }

                    finalTags.forEach { tag ->
                        onAddPresetTag(tag)
                    }

                    val finalLibraryCategory = if (libraryCategory == "自定义") {
                        customLibraryCategory.trim().ifBlank { "自定义" }
                    } else {
                        libraryCategory
                    }

                    val finalQuantity = if (inputMode == NutritionInputMode.Total && !saveToLibrary && inputQuantity <= 0.0) 0.0 else inputQuantity
                    val finalServingCount = if (inputMode == NutritionInputMode.Total && !saveToLibrary && inputQuantity <= 0.0) 0.0 else servings

                    onSave(
                        MealRecord(
                            name = name.trim(),
                            calories = calculatedCalories,
                            protein = totalProtein,
                            fat = totalFat,
                            carbs = totalCarbs,
                            time = initialMeal?.time ?: currentTimeText(),
                            tags = finalTags,
                            servingType = servingType,
                            quantity = finalQuantity,
                            servingCount = finalServingCount,
                            unitLabel = normalizedUnitLabel(servingType, unitLabel)
                        ),
                        saveToLibrary,
                        finalLibraryCategory
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(text = if (initialMeal == null) "保存记录" else "保存修改", fontSize = 17.sp)
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(text = "返回首页")
            }
        }
    }

    if (showAiAccuracyTipsDialog) {
        AlertDialog(
            onDismissRequest = { showAiAccuracyTipsDialog = false },
            title = { Text(text = "如何提高 AI 分析准确率？", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "①详细描述食物特征：名称、食材、比例、烹饪方式、在什么地方用餐\n②一次只拍一份食物\n③设置多个拍照分析 AI 来综合计算，取平均值",
                    fontSize = 14.sp,
                    color = Color(0xFF303747)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showAiAccuracyTipsDialog = false },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = "知道了")
                }
            }
        )
    }
}

@Composable
fun AiSettingsDialog(
    visible: Boolean,
    settingsList: List<AiSettings>,
    onDismiss: () -> Unit,
    onUpdate: (List<AiSettings>) -> Unit
) {
    if (!visible) return

    val editingSettings = remember(visible) { mutableStateListOf<AiSettings>().apply { addAll(settingsList) } }
    val coroutineScope = rememberCoroutineScope()
    var editingId by remember(visible) { mutableStateOf<Long?>(null) }
    val editingAi = editingSettings.firstOrNull { it.id == editingId }
    var providerName by remember(editingId, visible) { mutableStateOf(editingAi?.providerName.orEmpty()) }
    var baseUrl by remember(editingId, visible) { mutableStateOf(editingAi?.baseUrl.orEmpty()) }
    var apiKey by remember(editingId, visible) { mutableStateOf(editingAi?.apiKey.orEmpty()) }
    var modelName by remember(editingId, visible) { mutableStateOf(editingAi?.modelName.orEmpty()) }
    var temperature by remember(editingId, visible) { mutableStateOf(editingAi?.temperature?.toString() ?: "0.2") }
    var enabled by remember(editingId, visible) { mutableStateOf(editingAi?.enabled ?: false) }
    var selectedForVisionWork by remember(editingId, visible) { mutableStateOf(editingAi?.selectedForVisionWork ?: false) }
    var selectedForTextWork by remember(editingId, visible) { mutableStateOf(editingAi?.selectedForTextWork ?: false) }
    var supportsVision by remember(editingId, visible) { mutableStateOf(editingAi?.supportsVision ?: true) }
    var manualVisionConfirmed by remember(editingId, visible) { mutableStateOf(editingAi?.manualVisionConfirmed ?: false) }
    var errorText by remember(visible) { mutableStateOf<String?>(null) }
    var viewMode by remember(settingsList) { mutableStateOf("list") }
    var expandedPresetCompany by remember(visible) { mutableStateOf<String?>(null) }
    var expandedPresetSection by remember(visible) { mutableStateOf<String?>(null) }
    var pendingDeleteAiId by remember(visible) { mutableStateOf<Long?>(null) }
    var isTestingAi by remember(visible) { mutableStateOf(false) }
    var isFetchingModels by remember(visible) { mutableStateOf(false) }
    var fetchedModels by remember(visible) { mutableStateOf<List<String>>(emptyList()) }
    var showAiHelpDialog by remember(visible) { mutableStateOf(false) }
    var showPresetPickerDialog by remember(visible) { mutableStateOf(false) }
    var showModelPickerDialog by remember(visible) { mutableStateOf(false) }

    fun pushSettingsUpdate() {
        onUpdate(editingSettings.toList())
    }

    fun replaceEditingSetting(updated: AiSettings) {
        val index = editingSettings.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            editingSettings[index] = updated
            pushSettingsUpdate()
        }
    }

    fun openConfig(aiId: Long) {
        editingId = aiId
        viewMode = "list"
        errorText = null
    }

    fun currentAiSignature(): String {
        return aiVerificationSignature(providerName, baseUrl, apiKey.trim(), modelName.trim())
    }
    fun currentAiVerified(): Boolean {
        return editingAi?.verifiedSignature?.isNotBlank() == true && editingAi.verifiedSignature == currentAiSignature()
    }

    fun probeVisionUploadFormat(currentSettings: AiSettings, announceSelection: Boolean = false) {
        isTestingAi = true
        errorText = "正在询问该模型的图片上传格式..."
        coroutineScope.launch {
            val detectedFormat = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    withTransientAiRetry { requestVisionSupportProbe(currentSettings).uploadFormat }
                }
            }
                .getOrElse { currentSettings.resolvedVisionImageUploadFormat() }
            isTestingAi = false
            val latest = editingSettings.firstOrNull { it.id == currentSettings.id } ?: currentSettings
            replaceEditingSetting(latest.copy(visionImageUploadFormat = detectedFormat))
            if (announceSelection) {
                errorText = "已选为图片识别工作 AI，图片上传格式为 ${detectedFormat.displayName()}。"
            } else {
                errorText = "已记住该模型的图片上传格式：${detectedFormat.displayName()}。"
            }
        }
    }

    fun draftCurrentEditingAi(): AiSettings? {
        val currentEditingId = editingId ?: return null
        val storedAi = editingSettings.firstOrNull { it.id == currentEditingId } ?: return null
        val normalizedBaseUrl = normalizeAiBaseUrl(
            baseUrl,
            providerLooksAnthropicLike(providerName, baseUrl, modelName),
            providerLooksGeminiLike(providerName, baseUrl, modelName),
            providerLooksZhipuLike(providerName, baseUrl, modelName)
        )
        val signature = aiVerificationSignature(providerName, baseUrl, apiKey.trim(), modelName.trim())
        val verifiedSignature = if (storedAi.verifiedSignature == signature) storedAi.verifiedSignature else ""
        return storedAi.copy(
            providerName = providerName.trim(),
            baseUrl = normalizedBaseUrl ?: baseUrl.trim(),
            apiKey = apiKey.trim(),
            modelName = modelName.trim(),
            temperature = temperature.toDoubleOrNull() ?: storedAi.temperature,
            enabled = enabled,
            selectedForVisionWork = selectedForVisionWork && (supportsVision || manualVisionConfirmed),
            selectedForTextWork = selectedForTextWork,
            supportsVision = supportsVision,
            manualVisionConfirmed = manualVisionConfirmed,
            visionImageUploadFormat = storedAi.visionImageUploadFormat,
            verifiedSignature = verifiedSignature
        )
    }

    fun syncCurrentEditingAi() {
        draftCurrentEditingAi()?.let(::replaceEditingSetting)
    }

    fun commitCurrentEditingAi(): Boolean {
        val currentEditingId = editingId ?: return true
        val parsedTemperature = temperature.toDoubleOrNull()
        val normalizedBaseUrl = normalizeAiBaseUrl(
            baseUrl,
            providerLooksAnthropicLike(providerName, baseUrl, modelName),
            providerLooksGeminiLike(providerName, baseUrl, modelName),
            providerLooksZhipuLike(providerName, baseUrl, modelName)
        )
        val verifiedSignature = if (editingAi?.verifiedSignature == currentAiSignature()) editingAi.verifiedSignature else ""
        when {
            providerName.isBlank() -> {
                errorText = "请输入 Provider 名称"
                return false
            }
            baseUrl.isBlank() -> {
                errorText = "请输入接口地址"
                return false
            }
            normalizedBaseUrl == null -> {
                errorText = "接口地址需为合法 http/https 地址"
                return false
            }
            modelName.isBlank() -> {
                errorText = "请输入模型名称"
                return false
            }
            parsedTemperature == null || parsedTemperature < 0.0 || parsedTemperature > 2.0 -> {
                errorText = "Temperature 需在 0 到 2 之间"
                return false
            }
            else -> {
                baseUrl = normalizedBaseUrl
                replaceEditingSetting(
                    AiSettings(
                        id = currentEditingId,
                        providerName = providerName.trim(),
                        baseUrl = normalizedBaseUrl,
                        apiKey = apiKey.trim(),
                        modelName = modelName.trim(),
                        temperature = parsedTemperature,
                        enabled = enabled,
                        selectedForVisionWork = selectedForVisionWork && (supportsVision || manualVisionConfirmed),
                        selectedForTextWork = selectedForTextWork,
                        supportsVision = supportsVision,
                        manualVisionConfirmed = manualVisionConfirmed,
                        visionImageUploadFormat = editingAi?.visionImageUploadFormat ?: VisionImageUploadFormat.Auto,
                        verifiedSignature = verifiedSignature
                    )
                )
                return true
            }
        }
    }

    fun buildSettingsForModelListFetch(): AiSettings? {
        val currentEditingId = editingId ?: run {
            errorText = "褰撳墠娌℃湁鍙厤缃殑 AI 鏉＄洰"
            return null
        }
        val storedAi = editingSettings.firstOrNull { it.id == currentEditingId } ?: run {
            errorText = "褰撳墠娌℃湁鍙厤缃殑 AI 鏉＄洰"
            return null
        }
        val normalizedBaseUrl = normalizeAiBaseUrl(
            baseUrl,
            providerLooksAnthropicLike(providerName, baseUrl, modelName),
            providerLooksGeminiLike(providerName, baseUrl, modelName),
            providerLooksZhipuLike(providerName, baseUrl, modelName)
        )
        when {
            baseUrl.isBlank() -> {
                errorText = "璇疯緭鍏ユ帴鍙ｅ湴鍧€"
                return null
            }
            normalizedBaseUrl == null -> {
                errorText = "鎺ュ彛鍦板潃闇€涓哄悎娉?http/https 鍦板潃"
                return null
            }
            apiKey.isBlank() -> {
                errorText = "璇疯緭鍏?API Key"
                return null
            }
            else -> {
                baseUrl = normalizedBaseUrl
                val signature = aiVerificationSignature(providerName, normalizedBaseUrl, apiKey.trim(), modelName.trim())
                val verifiedSignature = if (storedAi.verifiedSignature == signature) storedAi.verifiedSignature else ""
                val updated = storedAi.copy(
                    providerName = providerName.trim(),
                    baseUrl = normalizedBaseUrl,
                    apiKey = apiKey.trim(),
                    modelName = modelName.trim(),
                    temperature = temperature.toDoubleOrNull() ?: storedAi.temperature,
                    enabled = enabled,
                    selectedForVisionWork = selectedForVisionWork && (supportsVision || manualVisionConfirmed),
                    selectedForTextWork = selectedForTextWork,
                    supportsVision = supportsVision,
                    manualVisionConfirmed = manualVisionConfirmed,
                    visionImageUploadFormat = storedAi.visionImageUploadFormat,
                    verifiedSignature = verifiedSignature
                )
                replaceEditingSetting(updated)
                return updated
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "AI 大模型设置", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "可配置多个 OpenAI Compatible 接口。图片识别工作 AI 最多 3 个；文字生成工作 AI 只能 1 个，负责营养报告和汇总图片识别结果。",
                    fontSize = 12.sp,
                    color = Color(0xFF6F7785)
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (viewMode == "config") {
                        OutlinedButton(
                            onClick = {
                                if (commitCurrentEditingAi()) {
                                    viewMode = "list"
                                    errorText = null
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "返回 AI 列表", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Transparent)
                            .clickable { showAiHelpDialog = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "不知道怎么设置 AI？",
                            fontSize = 12.sp,
                            color = Color(0xFF5B8DEF),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = "图片工作 ${editingSettings.count { it.selectedForVisionWork }} / 3，可用 ${editingSettings.count { it.selectedForVisionWork && it.enabled && it.canHandleVisionTasks() && it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() }} 个 · 文字工作 ${editingSettings.count { it.selectedForTextWork }} / 1，可用 ${editingSettings.count { it.selectedForTextWork && it.enabled && it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() }} 个",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF5B8DEF)
                )
                when (viewMode) {
                    "list" -> {
                        OutlinedButton(
                            onClick = {
                                val newId = (editingSettings.maxOfOrNull { it.id } ?: 0L) + 1L
                                editingSettings.add(
                                    AiSettings(
                                        id = newId,
                                        providerName = "",
                                        baseUrl = "",
                                        apiKey = "",
                                        modelName = "",
                                        temperature = 0.2,
                                        enabled = false,
                                        selectedForVisionWork = false,
                                        selectedForTextWork = false,
                                        supportsVision = true,
                                        verifiedSignature = ""
                                    )
                                )
                                pushSettingsUpdate()
                                errorText = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(text = "新增 AI", fontSize = 13.sp)
                        }
                        Text(text = "已配置的 AI", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                        if (editingSettings.isEmpty()) {
                            Text(
                                text = "当前没有 AI 配置。你仍然可以手动记录饮食；真实图片识别和 AI 报告会保持不可用。",
                                fontSize = 13.sp,
                                color = Color(0xFF8A92A1)
                            )
                        } else {
                            editingSettings.forEach { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = item.providerName.ifBlank { "未命名 AI" },
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF161A23)
                                        )
                                        Text(
                                            text = item.modelName.ifBlank { "未填模型名称" },
                                            fontSize = 12.sp,
                                            color = Color(0xFF6F7785)
                                        )
                                        Text(
                                            text = listOfNotNull(
                                                if (item.enabled) "已启用" else "未启用",
                                                if (item.selectedForVisionWork) "图片工作" else null,
                                                if (item.selectedForTextWork) "文字工作" else null,
                                                when {
                                                    item.supportsVision -> "支持图片"
                                                    item.manualVisionConfirmed -> "手动确认图片"
                                                    else -> "文本模型"
                                                },
                                                if (item.apiKey.isNotBlank()) "已填 Key" else "未填 Key"
                                            ).joinToString(" · "),
                                            fontSize = 12.sp,
                                            color = Color(0xFF8A92A1)
                                        )
                                        OutlinedButton(
                                            onClick = { openConfig(item.id) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text(text = "配置", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        val currentAi = editingAi
                        if (currentAi == null) {
                            Text(
                                text = "请先从列表里选择一个 AI 条目进行配置。",
                                fontSize = 13.sp,
                                color = Color(0xFF8A92A1)
                            )
                        } else {
                            Text(text = "当前配置", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = currentAi.providerName.ifBlank { "未命名 AI" },
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF161A23)
                                            )
                                            Text(
                                                text = currentAi.modelName.ifBlank { "未填模型名称" },
                                                fontSize = 12.sp,
                                                color = Color(0xFF8A92A1)
                                            )
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { showPresetPickerDialog = true },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text(text = "应用预设", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val currentSettings = buildSettingsForModelListFetch() ?: return@OutlinedButton
                                                isFetchingModels = true
                                                errorText = "正在获取模型列表..."
                                                coroutineScope.launch {
                                                    val result = runCatching { fetchAiModelList(currentSettings) }
                                                    isFetchingModels = false
                                                    result.onSuccess { models ->
                                                        fetchedModels = models
                                                        showModelPickerDialog = true
                                                        errorText = if (models.isEmpty()) "接口可访问，但没有解析到模型列表。" else null
                                                    }.onFailure { error ->
                                                        errorText = "获取模型列表失败：${friendlyAiReportErrorMessage(error)}"
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            enabled = !isFetchingModels,
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text(text = if (isFetchingModels) "获取模型中..." else "选择模型", fontSize = 12.sp)
                                        }
                                    }
                                    AppTextField(label = "Provider 名称", value = providerName, onValueChange = {
                                        providerName = it
                                        enabled = false
                                    })
                                    AppTextField(label = "接口地址（/v1 或 /v1/chat/completions）", value = baseUrl, onValueChange = {
                                        baseUrl = it
                                        enabled = false
                                    })
                                    AppTextField(label = "API Key", value = apiKey, onValueChange = {
                                        apiKey = it
                                        enabled = false
                                    })
                                    AppTextField(label = "模型名称", value = modelName, onValueChange = {
                                        modelName = it
                                        enabled = false
                                    })
                                    AppTextField(label = "Temperature", value = temperature, onValueChange = { temperature = it }, isNumber = true)
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        if (enabled) {
                                            enabled = false
                                            errorText = null
                                        } else if (currentAiVerified()) {
                                            enabled = true
                                            errorText = null
                                        } else {
                                            errorText = "请先点击“检测当前配置”；通过后才能启用。"
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = when {
                                            enabled -> "停用此配置"
                                            currentAiVerified() -> "启用此配置"
                                            else -> "未检测，暂不能启用"
                                        },
                                        fontSize = 12.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = { pendingDeleteAiId = currentAi.id },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(text = "删除", fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    if (editingId == null) {
                                        errorText = "当前没有可配置的 AI 条目"
                                        return@OutlinedButton
                                    }
                                    if (!currentAiVerified()) {
                                        errorText = "请先检测当前配置，系统会在检测通过时自动询问该模型是否支持图片识别。"
                                        return@OutlinedButton
                                    }
                                    if (!supportsVision) {
                                        errorText = "该模型已检测为不支持图片识别，不能作为图片识别工作 AI"
                                        return@OutlinedButton
                                    }
                                    val selectedCount = editingSettings.count { it.selectedForVisionWork && it.id != editingId } + if (!selectedForVisionWork) 1 else 0
                                    if (!selectedForVisionWork && selectedCount > 3) {
                                        errorText = "图片识别工作 AI 最多只能选择 3 个"
                                    } else {
                                        val nextSelectedForVisionWork = !selectedForVisionWork
                                        selectedForVisionWork = nextSelectedForVisionWork
                                        val index = editingSettings.indexOfFirst { it.id == editingId }
                                        if (index >= 0) {
                                            editingSettings[index] = editingSettings[index].copy(selectedForVisionWork = nextSelectedForVisionWork)
                                            if (nextSelectedForVisionWork) {
                                                probeVisionUploadFormat(editingSettings[index], announceSelection = true)
                                            }
                                        }
                                        if (!nextSelectedForVisionWork) errorText = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(text = if (selectedForVisionWork) "✓ 已选为图片识别工作 AI" else "选为图片识别工作 AI")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (editingId == null) {
                                        errorText = "当前没有可配置的 AI 条目"
                                        return@OutlinedButton
                                    }
                                    val nextSelectedForTextWork = !selectedForTextWork
                                    selectedForTextWork = nextSelectedForTextWork
                                    if (nextSelectedForTextWork && !currentAiVerified()) {
                                        errorText = "已选为文字生成工作 AI；还需要检测并启用此 AI 配置后才会参与工作。"
                                    }
                                    editingSettings.replaceAll { item ->
                                        if (item.id == editingId) {
                                            item.copy(selectedForTextWork = nextSelectedForTextWork)
                                        } else if (nextSelectedForTextWork) {
                                            item.copy(selectedForTextWork = false)
                                        } else {
                                            item
                                        }
                                    }
                                    if (!nextSelectedForTextWork) errorText = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(text = if (selectedForTextWork) "✓ 已选为文字生成工作 AI" else "选为文字生成工作 AI")
                            }

                            Text(
                                text = if (currentAiVerified()) "检测状态：已通过。现在可以启用此 AI 配置。修改地址、Key 或模型名后需要重新检测。" else "检测状态：未通过或未检测。请先点击“检测当前配置”。",
                                fontSize = 12.sp,
                                color = if (currentAiVerified()) Color(0xFF2E9D63) else Color(0xFF8A92A1)
                            )
                            Text(
                                text = if (currentAiVerified()) {
                                    if (supportsVision) "图片能力：已自动确认支持图片识别。" else "图片能力：已自动确认不支持图片识别。"
                                } else {
                                    "图片能力：待检测；检测通过后会自动询问模型是否支持图片识别。"
                                },
                                fontSize = 12.sp,
                                color = if (currentAiVerified() && supportsVision) Color(0xFF2E9D63) else Color(0xFF8A92A1)
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        if (!commitCurrentEditingAi()) return@OutlinedButton
                                        val currentSettings = editingSettings.firstOrNull { it.id == editingId } ?: return@OutlinedButton
                                        isTestingAi = true
                                        errorText = "正在检测当前 AI 配置..."
                                        coroutineScope.launch {
                                            val result = runCatching { verifyAiSettings(currentSettings) }
                                            isTestingAi = false
                                            result.onSuccess { verification ->
                                                supportsVision = verification.supportsVision
                                                if (!verification.supportsVision) {
                                                    selectedForVisionWork = false
                                                }
                                                val signature = aiVerificationSignature(currentSettings.providerName, currentSettings.baseUrl, currentSettings.apiKey, currentSettings.modelName)
                                                val index = editingSettings.indexOfFirst { it.id == currentSettings.id }
                                                if (index >= 0) {
                                                    editingSettings[index] = editingSettings[index].copy(
                                                        verifiedSignature = signature,
                                                        supportsVision = verification.supportsVision,
                                                        visionImageUploadFormat = verification.visionImageUploadFormat,
                                                        selectedForVisionWork = editingSettings[index].selectedForVisionWork && verification.supportsVision
                                                    )
                                                }
                                                errorText = "检测通过：${currentSettings.modelName} 可以正常响应，且已自动询问图片能力，结果为${if (verification.supportsVision) "支持图片识别" else "不支持图片识别"}。"
                                            }.onFailure { error ->
                                                val index = editingSettings.indexOfFirst { it.id == currentSettings.id }
                                                if (index >= 0) {
                                                    editingSettings[index] = editingSettings[index].copy(enabled = false, verifiedSignature = "")
                                                }
                                                enabled = false
                                                errorText = "检测失败：${friendlyAiReportErrorMessage(error)}"
                                            }
                                        }
                                    },
                                    enabled = !isTestingAi,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(text = if (isTestingAi) "检测中..." else "检测当前配置", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (commitCurrentEditingAi()) {
                                            errorText = "当前 AI 配置已更新"
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(text = "更新当前配置", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                errorText?.let { Text(text = it, color = Color(0xFFEF5350), fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                Text(text = "关闭")
            }
        },
        dismissButton = {}
    )

    editingAi?.let { currentAi ->
        AlertDialog(
            onDismissRequest = { editingId = null },
            title = { Text(text = "配置 AI", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "当前配置", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentAi.providerName.ifBlank { "未命名 AI" },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF161A23)
                                    )
                                    Text(
                                        text = currentAi.modelName.ifBlank { "未填模型名称" },
                                        fontSize = 12.sp,
                                        color = Color(0xFF8A92A1)
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showPresetPickerDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(text = "应用预设", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { pendingDeleteAiId = currentAi.id },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(text = "删除", fontSize = 12.sp)
                                }
                            }
                            AppTextField(label = "Provider 名称", value = providerName, onValueChange = {
                                providerName = it
                                enabled = false
                                syncCurrentEditingAi()
                            })
                            AppTextField(label = "接口地址（v1 或 /v1/chat/completions）", value = baseUrl, onValueChange = {
                                baseUrl = it
                                enabled = false
                                syncCurrentEditingAi()
                            })
                            AppTextField(label = "API Key", value = apiKey, onValueChange = {
                                apiKey = it
                                enabled = false
                                syncCurrentEditingAi()
                            })
                            AppTextField(label = "模型名称", value = modelName, onValueChange = {
                                modelName = it
                                enabled = false
                                syncCurrentEditingAi()
                            })
                            AppTextField(label = "Temperature", value = temperature, onValueChange = {
                                temperature = it
                                syncCurrentEditingAi()
                            }, isNumber = true)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (enabled) {
                                    enabled = false
                                    syncCurrentEditingAi()
                                    errorText = null
                                } else if (currentAiVerified()) {
                                    enabled = true
                                    syncCurrentEditingAi()
                                    errorText = null
                                } else {
                                    errorText = "请先点击“检测当前配置”；通过后才能启用。"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = when {
                                    enabled -> "停用此配置"
                                    currentAiVerified() -> "启用此配置"
                                    else -> "未检测，暂不能启用"
                                },
                                fontSize = 12.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val currentSettings = buildSettingsForModelListFetch() ?: return@OutlinedButton
                            isFetchingModels = true
                            errorText = "正在获取模型列表..."
                            coroutineScope.launch {
                                val result = runCatching { fetchAiModelList(currentSettings) }
                                isFetchingModels = false
                                result.onSuccess { models ->
                                    fetchedModels = models
                                    showModelPickerDialog = true
                                    errorText = if (models.isEmpty()) "接口可访问，但没有解析到模型列表。" else null
                                }.onFailure { error ->
                                    errorText = "获取模型列表失败：${friendlyAiReportErrorMessage(error)}"
                                }
                            }
                        },
                        enabled = !isFetchingModels,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(text = if (isFetchingModels) "获取模型中..." else "根据API Key获取模型列表", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            manualVisionConfirmed = !manualVisionConfirmed
                            if (!manualVisionConfirmed && !supportsVision) {
                                selectedForVisionWork = false
                            }
                            syncCurrentEditingAi()
                            errorText = if (manualVisionConfirmed) "已手动确认该模型能够识别图像。" else null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(text = if (manualVisionConfirmed) "✓ 我确认该模型能够识别图像" else "我确认该模型能够识别图像", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (editingId == null) {
                                errorText = "当前没有可配置的 AI 条目"
                                return@OutlinedButton
                            }
                            if (!(supportsVision || manualVisionConfirmed)) {
                                errorText = "请先检测当前配置，系统会在检测通过时自动询问该模型是否支持图片识别。"
                                return@OutlinedButton
                            }
                            if (!supportsVision && !manualVisionConfirmed) {
                                errorText = "该模型已检测为不支持图片识别，不能作为图片识别工作 AI"
                                return@OutlinedButton
                            }
                            val selectedCount = editingSettings.count { it.selectedForVisionWork && it.id != editingId } + if (!selectedForVisionWork) 1 else 0
                            if (!selectedForVisionWork && selectedCount > 3) {
                                errorText = "图片识别工作 AI 最多只能选择 3 个"
                            } else {
                                selectedForVisionWork = !selectedForVisionWork
                                syncCurrentEditingAi()
                                if (selectedForVisionWork) {
                                    editingSettings.firstOrNull { it.id == editingId }?.let { latestSettings ->
                                        probeVisionUploadFormat(latestSettings, announceSelection = true)
                                    }
                                }
                                if (selectedForVisionWork && !currentAiVerified()) {
                                    errorText = "已选为图片识别工作 AI；还需要检测并启用此 AI 配置后才会参与工作。"
                                } else if (!selectedForVisionWork) {
                                    errorText = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(text = if (selectedForVisionWork) "✓ 已选为图片识别工作 AI" else "选为图片识别工作 AI")
                    }

                    OutlinedButton(
                        onClick = {
                            if (editingId == null) {
                                errorText = "当前没有可配置的 AI 条目"
                                return@OutlinedButton
                            }
                            val nextSelectedForTextWork = !selectedForTextWork
                            selectedForTextWork = nextSelectedForTextWork
                            if (nextSelectedForTextWork && !currentAiVerified()) {
                                errorText = "已选为文字生成工作 AI；还需要检测并启用此 AI 配置后才会参与工作。"
                            }
                            editingSettings.replaceAll { item ->
                                if (item.id == editingId) {
                                    item.copy(selectedForTextWork = nextSelectedForTextWork)
                                } else if (nextSelectedForTextWork) {
                                    item.copy(selectedForTextWork = false)
                                } else {
                                    item
                                }
                            }
                            pushSettingsUpdate()
                            if (!nextSelectedForTextWork) errorText = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(text = if (selectedForTextWork) "✓ 已选为文字生成工作 AI" else "选为文字生成工作 AI")
                    }

                    Text(
                        text = if (currentAiVerified()) "检测状态：已通过。现在可以启用此 AI 配置。修改地址、Key 或模型名后需要重新检测。" else "检测状态：未通过或未检测。请先点击“检测当前配置”。",
                        fontSize = 12.sp,
                        color = if (currentAiVerified()) Color(0xFF2E9D63) else Color(0xFF8A92A1)
                    )
                    Text(
                        text = if (currentAiVerified()) {
                            if (supportsVision) "图片能力：已自动确认支持图片识别。" else "图片能力：已自动确认不支持图片识别。"
                        } else {
                            "图片能力：待检测；检测通过后会自动询问模型是否支持图片识别。"
                        },
                        fontSize = 12.sp,
                        color = if (supportsVision || manualVisionConfirmed) Color(0xFF2E9D63) else Color(0xFF8A92A1)
                    )

                    OutlinedButton(
                        onClick = {
                            if (!commitCurrentEditingAi()) return@OutlinedButton
                            val currentSettings = editingSettings.firstOrNull { it.id == editingId } ?: return@OutlinedButton
                            isTestingAi = true
                            errorText = "正在检测当前 AI 配置..."
                            coroutineScope.launch {
                                val result = runCatching { verifyAiSettings(currentSettings) }
                                isTestingAi = false
                                result.onSuccess { verification ->
                                    supportsVision = verification.supportsVision
                                    if (!verification.supportsVision && !manualVisionConfirmed) {
                                        selectedForVisionWork = false
                                    }
                                    val signature = aiVerificationSignature(currentSettings.providerName, currentSettings.baseUrl, currentSettings.apiKey, currentSettings.modelName)
                                    replaceEditingSetting(
                                        currentSettings.copy(
                                            verifiedSignature = signature,
                                            supportsVision = verification.supportsVision,
                                            manualVisionConfirmed = manualVisionConfirmed,
                                            visionImageUploadFormat = verification.visionImageUploadFormat,
                                            selectedForVisionWork = currentSettings.selectedForVisionWork && (verification.supportsVision || manualVisionConfirmed)
                                        )
                                    )
                                    errorText = "检测通过：${currentSettings.modelName} 可以正常响应，且已自动询问图片能力，结果为 ${if (verification.supportsVision) "支持图片识别" else "不支持图片识别"}。"
                                }.onFailure { error ->
                                    replaceEditingSetting(currentSettings.copy(enabled = false, verifiedSignature = ""))
                                    enabled = false
                                    errorText = "检测失败：${friendlyAiReportErrorMessage(error)}"
                                }
                            }
                        },
                        enabled = !isTestingAi,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(text = if (isTestingAi) "检测中..." else "检测当前配置", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { editingId = null }, shape = RoundedCornerShape(14.dp)) {
                    Text(text = "完成")
                }
            },
            dismissButton = {}
        )
    }

    val deleteTarget = editingSettings.firstOrNull { it.id == pendingDeleteAiId }
    if (deleteTarget != null) {
        ConfirmDialog(
            title = "删除 AI 配置",
            message = "确定要删除「${deleteTarget.providerName.ifBlank { "未命名 AI" }} / ${deleteTarget.modelName.ifBlank { "未填模型" }}」吗？这个操作不会删除云端模型，但会移除本机保存的接口、Key 和工作 AI 设置。",
            confirmText = "删除",
            onConfirm = {
                editingSettings.removeAll { it.id == deleteTarget.id }
                if (editingId == deleteTarget.id) {
                    editingId = null
                    viewMode = "list"
                }
                pushSettingsUpdate()
                pendingDeleteAiId = null
                errorText = null
            },
            onDismiss = {
                pendingDeleteAiId = null
            }
        )
    }

    if (showPresetPickerDialog) {
        AlertDialog(
            onDismissRequest = { showPresetPickerDialog = false },
            title = { Text(text = "应用预设", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "按公司选择预设", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                    defaultAiPresetSections().forEach { section ->
                        val sectionExpanded = expandedPresetSection == section.sectionName
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedPresetSection = if (sectionExpanded) null else section.sectionName
                                            if (!sectionExpanded) expandedPresetCompany = null
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = section.sectionName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
                                        Text(
                                            text = "${section.groups.size} 家公司 · ${section.groups.sumOf { it.presets.size }} 个模型",
                                            fontSize = 12.sp,
                                            color = Color(0xFF8A92A1)
                                        )
                                    }
                                    Text(text = if (sectionExpanded) "收起" else "展开", fontSize = 12.sp, color = Color(0xFF5B8DEF), fontWeight = FontWeight.SemiBold)
                                }
                                if (sectionExpanded) {
                                    section.groups.forEach { group ->
                                        val companyKey = "${section.sectionName}/${group.companyName}"
                                        val companyExpanded = expandedPresetCompany == companyKey
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0x66FFFFFF))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            expandedPresetCompany = if (companyExpanded) null else companyKey
                                                        },
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(text = group.companyName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                                                        Text(text = "${group.presets.size} 个模型 · ${group.presets.count { it.supportsVision }} 个支持图片", fontSize = 11.sp, color = Color(0xFF8A92A1))
                                                    }
                                                    Text(text = if (companyExpanded) "收起" else "展开", fontSize = 11.sp, color = Color(0xFF5B8DEF), fontWeight = FontWeight.SemiBold)
                                                }
                                                if (companyExpanded) {
                                                    group.presets.forEach { preset ->
                                                        OutlinedButton(
                                                            onClick = {
                                                                providerName = preset.providerName
                                                                baseUrl = preset.baseUrl
                                                                modelName = preset.modelName
                                                                temperature = preset.temperature.toString()
                                                                supportsVision = preset.supportsVision
                                                                enabled = false
                                                                syncCurrentEditingAi()
                                                                errorText = "已应用 ${preset.modelName} 预设，请填写 API Key 后检测。"
                                                                showPresetPickerDialog = false
                                                            },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            shape = RoundedCornerShape(12.dp)
                                                        ) {
                                                            Text(text = "${preset.modelName} · ${if (preset.supportsVision) "支持图片" else "文本模型"}", fontSize = 12.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPresetPickerDialog = false }, shape = RoundedCornerShape(14.dp)) {
                    Text(text = "关闭")
                }
            }
        )
    }

    if (showModelPickerDialog) {
        AlertDialog(
            onDismissRequest = { showModelPickerDialog = false },
            title = { Text(text = "选择模型", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (fetchedModels.isEmpty()) {
                        Text(text = "当前没有可选模型，请先检查接口地址和 API Key。", fontSize = 13.sp, color = Color(0xFF8A92A1))
                    } else {
                        fetchedModels.take(60).forEach { fetchedModel ->
                            OutlinedButton(
                                onClick = {
                                    modelName = fetchedModel
                                    enabled = false
                                    syncCurrentEditingAi()
                                    errorText = "已选择 $fetchedModel，请先检测当前配置；检测通过后会自动确认是否支持图片识别。"
                                    showModelPickerDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = fetchedModel, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showModelPickerDialog = false }, shape = RoundedCornerShape(14.dp)) {
                    Text(text = "关闭")
                }
            }
        )
    }

    if (showAiHelpDialog) {
        AlertDialog(
            onDismissRequest = { showAiHelpDialog = false },
            title = { Text(text = "怎么设置 AI？", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "你需要先新增 AI，然后可以选择一个预设，然后填入服务商地址、API Key、模型名称，当然你也可以检测模型名称然后自动填入。不知道怎么搞这些？快去请教你擅长计算机的朋友吧！是时候和朋友们增进情感了！",
                    fontSize = 14.sp,
                    color = Color(0xFF303747)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showAiHelpDialog = false },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = "我知道了")
                }
            }
        )
    }
}

@Composable
fun UpdateAvailableDialog(
    currentVersionCode: Int,
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onOpen: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "发现新版本 ${updateInfo.versionName}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "当前版本号：$currentVersionCode，新版本号：${updateInfo.versionCode}",
                    fontSize = 13.sp,
                    color = Color(0xFF6F7785)
                )
                Text(
                    text = updateInfo.changelog.ifBlank { "这个版本没有填写更新说明。" },
                    fontSize = 14.sp,
                    color = Color(0xFF303747)
                )
                Text(
                    text = "点击“前往下载”会打开 GitHub Releases 页面，请在浏览器中下载最新版 APK。",
                    fontSize = 12.sp,
                    color = Color(0xFF8A92A1)
                )
            }
        },
        confirmButton = {
            Button(onClick = onOpen, shape = RoundedCornerShape(14.dp)) {
                Text(text = "前往下载")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                Text(text = "稍后再说")
            }
        }
    )
}

@Composable
fun UpdateAlreadyLatestDialog(
    currentVersionCode: Int,
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "已是最新版本", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "GitHub 更新检测成功，目前没有发现新版本。",
                    fontSize = 14.sp,
                    color = Color(0xFF303747)
                )
                Text(
                    text = "当前版本号：$currentVersionCode，线上版本号：${updateInfo.versionCode}",
                    fontSize = 13.sp,
                    color = Color(0xFF6F7785)
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                Text(text = "知道了")
            }
        }
    )
}

@Composable
fun ManualUpdateFallbackDialog(
    errorText: String,
    githubUrl: String,
    lanzouUrl: String,
    lanzouPassword: String,
    onOpenGithub: () -> Unit,
    onOpenLanzou: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lanzouQrBitmap = remember { loadLanzouUpdateQrBitmap(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "自动检测更新失败", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "可能是当前网络无法连接 GitHub。你可以用下面任意一种方式手动检查更新。",
                    fontSize = 14.sp,
                    color = Color(0xFF303747)
                )
                Text(
                    text = "GitHub：$githubUrl",
                    fontSize = 12.sp,
                    color = Color(0xFF6F7785)
                )
                Text(
                    text = "蓝奏云：$lanzouUrl\n密码：$lanzouPassword",
                    fontSize = 12.sp,
                    color = Color(0xFF6F7785)
                )
                if (lanzouQrBitmap != null) {
                    Image(
                        bitmap = lanzouQrBitmap.asImageBitmap(),
                        contentDescription = "蓝奏云更新二维码",
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(180.dp)
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                if (AI_DEVELOPER_MODE) {
                    Text(
                        text = "开发者信息：${errorText.ifBlank { "未知错误" }}",
                        fontSize = 11.sp,
                        color = Color(0xFFB45309)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenLanzou, shape = RoundedCornerShape(14.dp)) {
                Text(text = "打开蓝奏云")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenGithub, shape = RoundedCornerShape(14.dp)) {
                    Text(text = "打开 GitHub")
                }
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                    Text(text = "稍后再说")
                }
            }
        }
    )
}

@Composable
fun AiMealAnalysisDialog(
    visible: Boolean,
    aiSettingsList: List<AiSettings>,
    foodName: String,
    servingType: ServingType,
    quantityText: String,
    carbsText: String,
    proteinText: String,
    fatText: String,
    inputMode: NutritionInputMode,
    foodDescription: String = "",
    imageDataUrl: String? = null,
    imageLabel: String? = null,
    imageType: RecognitionImageType = RecognitionImageType.FoodPhoto,
    onDismiss: () -> Unit,
    onApplySuggestion: (List<String>, Boolean, String) -> Unit,
    onApplyRecognizedFood: (FoodItem, Double, List<String>) -> Unit
) {
    if (!visible) return

    val availableWorkingAiSettings = aiSettingsList.filter { settings ->
        settings.enabled &&
            settings.apiKey.isNotBlank() &&
            settings.baseUrl.isNotBlank() &&
            if (imageDataUrl != null) {
                settings.selectedForVisionWork && settings.canHandleVisionTasks()
            } else {
                settings.selectedForTextWork
            }
    }
    val workingAiSettings = availableWorkingAiSettings.take(3)
    val textSummaryAiSettings = aiSettingsList.firstOrNull { settings ->
        settings.enabled &&
            settings.selectedForTextWork &&
            settings.apiKey.isNotBlank() &&
            settings.baseUrl.isNotBlank()
    }
    val skippedVisionModels = if (imageDataUrl != null) {
        aiSettingsList.filter { it.enabled && it.selectedForVisionWork && !it.canHandleVisionTasks() }
    } else {
        emptyList()
    }
    var isAnalyzing by remember(visible, foodName, foodDescription, quantityText, carbsText, proteinText, fatText, imageDataUrl, workingAiSettings, textSummaryAiSettings) { mutableStateOf(true) }
    var analysisRound by remember(visible, foodName, foodDescription, quantityText, carbsText, proteinText, fatText, imageDataUrl, workingAiSettings, textSummaryAiSettings) { mutableStateOf(0) }
    var aiErrorText by remember(visible, foodName, foodDescription, quantityText, carbsText, proteinText, fatText, imageDataUrl, workingAiSettings, textSummaryAiSettings) { mutableStateOf<String?>(null) }
    var aiDebugText by remember(visible, foodName, foodDescription, quantityText, carbsText, proteinText, fatText, imageDataUrl, workingAiSettings, textSummaryAiSettings) { mutableStateOf<String?>(null) }
    var aiCandidates by remember(visible, foodName, foodDescription, quantityText, carbsText, proteinText, fatText, imageDataUrl, workingAiSettings, textSummaryAiSettings) { mutableStateOf<List<RecognizedFoodCandidate>>(emptyList()) }

    LaunchedEffect(visible, foodName, foodDescription, quantityText, carbsText, proteinText, fatText, imageDataUrl, analysisRound, workingAiSettings, textSummaryAiSettings) {
        if (visible) {
            isAnalyzing = true
            aiErrorText = null
            aiDebugText = null
            aiCandidates = emptyList()
            if (workingAiSettings.isNotEmpty()) {
                val modelResults = kotlinx.coroutines.coroutineScope {
                    workingAiSettings
                        .map { settings ->
                            async {
                                settings to runCatching {
                                    requestAiMealRecognitionResponse(
                                        settings = settings,
                                        foodName = foodName,
                                        servingType = servingType,
                                        quantityText = quantityText,
                                        carbsText = carbsText,
                                        proteinText = proteinText,
                                        fatText = fatText,
                                        inputMode = inputMode,
                                        foodDescription = foodDescription,
                                        imageDataUrl = imageDataUrl,
                                        imageType = imageType
                                    )
                                }
                            }
                        }
                        .map { deferred -> deferred.await() }
                }
                val successfulCandidates = modelResults.mapNotNull { (settings, result) ->
                    result.getOrNull()?.candidates?.firstOrNull()?.copy(
                        sourceName = "${settings.providerName} · ${settings.modelName}"
                    )
                }
                if (AI_DEVELOPER_MODE) {
                    aiDebugText = modelResults.joinToString("\n\n") { (settings, result) ->
                        val header = "【${settings.providerName} · ${settings.modelName}】"
                        result.fold(
                            onSuccess = { response ->
                                "$header\n解析内容：\n${response.rawContent.take(1800)}\n\n原始响应：\n${response.rawResponse.take(1800)}"
                            },
                            onFailure = { error ->
                                "$header\n错误：\n${error.message.orEmpty().take(1800)}"
                            }
                        )
                    }
                }
                val errors = modelResults.mapNotNull { (_, result) -> result.exceptionOrNull() }
                if (errors.isNotEmpty()) {
                    aiErrorText = errors.take(2).joinToString("\n") { friendlyAiErrorMessage(it) }
                }
                aiCandidates = if (successfulCandidates.isNotEmpty()) {
                    if (imageType == RecognitionImageType.NutritionLabel) {
                        val bestCandidate = selectBestRecognizedFoodCandidate(
                            candidates = successfulCandidates,
                            fallbackName = foodName.ifBlank { "营养成分表食品" }
                        )
                        listOf(bestCandidate) + successfulCandidates
                    } else {
                        val summaryCandidate = if (successfulCandidates.size >= 2 && textSummaryAiSettings != null) {
                            runCatching {
                                requestAiCandidateSummary(
                                    settings = textSummaryAiSettings,
                                    candidates = successfulCandidates,
                                    fallbackName = foodName,
                                    foodDescription = foodDescription,
                                    fallbackServingType = servingType
                                )
                            }.getOrElse { error ->
                                aiErrorText = listOfNotNull(aiErrorText, "文字生成工作 AI 汇总失败：${friendlyAiReportErrorMessage(error)}").joinToString("\n")
                                averageRecognizedFoodCandidate(successfulCandidates)
                            }
                        } else {
                            averageRecognizedFoodCandidate(successfulCandidates)
                        }
                        successfulCandidates + summaryCandidate
                    }
                } else {
                    aiErrorText = errors.firstOrNull()?.let(::friendlyAiErrorMessage) ?: "工作 AI 未返回可用结果，已切换为本地候选结果。"
                    if (imageDataUrl != null) {
                        mockImageRecognitionCandidates(foodName)
                    } else {
                        mockRecognizedFoodCandidates(foodName, servingType, parseOneDecimal(proteinText) ?: 0.0, parseOneDecimal(fatText) ?: 0.0, parseOneDecimal(carbsText) ?: 0.0)
                    }
                }
            } else {
                kotlinx.coroutines.delay(650)
                if (imageDataUrl != null && availableWorkingAiSettings.isNotEmpty()) {
                    aiErrorText = "当前选择的工作 AI 都未标记为支持图片识别，已使用本地候选结果。请在 AI 设置中选择 GPT-4o、Gemini、Qwen-VL 等视觉模型。"
                }
                aiCandidates = if (imageDataUrl != null) {
                    mockImageRecognitionCandidates(foodName)
                } else {
                    mockRecognizedFoodCandidates(foodName, servingType, parseOneDecimal(proteinText) ?: 0.0, parseOneDecimal(fatText) ?: 0.0, parseOneDecimal(carbsText) ?: 0.0)
                }
            }
            isAnalyzing = false
        }
    }

    val carbs = parseOneDecimal(carbsText) ?: 0.0
    val protein = parseOneDecimal(proteinText) ?: 0.0
    val fat = parseOneDecimal(fatText) ?: 0.0
    val calories = calculateCalories(protein, fat, carbs)
    val category = suggestFoodCategory(foodName)
    val tags = suggestMealTags(foodName, calories, protein, fat, carbs)
    val saveSuggestion = foodName.isNotBlank() && calories > 0
    val recognizedCandidates = if (isAnalyzing) emptyList() else aiCandidates

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (imageDataUrl == null) "通过文字描述分析食物" else "AI 图片识别食物", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isAnalyzing) {
                    Text(
                        text = "正在分析当前记录...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF5B8DEF)
                    )
                    Text(
                        text = if (workingAiSettings.isNotEmpty()) {
                            "正在调用 ${workingAiSettings.size} 个工作 AI ${if (imageDataUrl != null) "识别${imageLabel ?: "图片"}" else "生成候选结果"}。"
                        } else {
                            "未选择可用工作 AI 或未填写 API Key，正在使用本地规则生成候选结果。"
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF8A92A1)
                    )
                } else {
                    Text(
                        text = if (imageDataUrl != null) {
                            "图片识别：${imageLabel ?: "已选择图片"}${if (foodDescription.isNotBlank()) " · 已加入描述线索" else ""}"
                        } else if (foodName.isBlank()) {
                            "请先输入食物名称，分析会更准确。"
                        } else {
                            "食物：$foodName"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF161A23)
                    )
                }
                Text(
                    text = if (workingAiSettings.isNotEmpty()) {
                        "本次将并行调用 ${workingAiSettings.size} 个图片识别工作 AI，并${
                            if (imageType == RecognitionImageType.NutritionLabel) {
                                "自动优先选择最像直接读取营养成分表的结果"
                            } else if (textSummaryAiSettings != null) {
                                "由文字生成工作 AI 汇总 1 个结果"
                            } else {
                                "生成 1 个本地平均值结果"
                            }
                        }。" +
                            if (skippedVisionModels.isNotEmpty()) " 已跳过 ${skippedVisionModels.size} 个不支持图片的文本模型。" else ""
                    } else {
                        "当前没有可用工作 AI，将使用本地规则候选。"
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF8A92A1)
                )
                if (aiErrorText != null) {
                    Text(text = aiErrorText.orEmpty(), fontSize = 13.sp, color = Color(0xFFEF5350))
                }
                if (AI_DEVELOPER_MODE && aiDebugText != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x22EF5350))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "开发者模式：AI 原始返回", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF5350))
                            Text(text = aiDebugText.orEmpty(), fontSize = 11.sp, color = Color(0xFF303747))
                        }
                    }
                }
                if (!isAnalyzing) {
                    Text(text = "建议标签：${tags.joinToString(" · ")}", fontSize = 13.sp, color = Color(0xFF5B8DEF), fontWeight = FontWeight.SemiBold)
                    Text(text = "建议分类：$category", fontSize = 13.sp, color = Color(0xFF5B8DEF), fontWeight = FontWeight.SemiBold)
                }
                if (recognizedCandidates.isNotEmpty()) {
                    Text(text = "候选识别结果", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
                    recognizedCandidates.forEach { candidate ->
                        val suggestedServingCount = servingCountFromQuantity(candidate.quantity, candidate.food.servingType)
                        val suggestedTotalProtein = roundOneDecimal(candidate.food.protein * suggestedServingCount)
                        val suggestedTotalFat = roundOneDecimal(candidate.food.fat * suggestedServingCount)
                        val suggestedTotalCarbs = roundOneDecimal(candidate.food.carbs * suggestedServingCount)
                        val suggestedTotalCalories = calculateCalories(suggestedTotalProtein, suggestedTotalFat, suggestedTotalCarbs)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = candidate.food.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161A23))
                                Text(text = candidate.sourceName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B8DEF))
                                Text(
                                    text = "${candidate.food.category} · ${formatNumber(candidate.food.calories)} kcal/${foodServingText(candidate.food)} · 建议 ${formatServingCount(candidate.quantity)}${quantityUnitText(candidate.food.servingType, candidate.food.unitLabel)}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF6F7785)
                                )
                                Text(
                                    text = "${foodServingText(candidate.food)}：碳水${formatNumber(candidate.food.carbs)}g · 蛋白${formatNumber(candidate.food.protein)}g · 脂肪${formatNumber(candidate.food.fat)}g",
                                    fontSize = 12.sp,
                                    color = Color(0xFF303747)
                                )
                                Text(
                                    text = "按建议份量约：${formatNumber(suggestedTotalCalories)} kcal · 碳水${formatNumber(suggestedTotalCarbs)}g · 蛋白${formatNumber(suggestedTotalProtein)}g · 脂肪${formatNumber(suggestedTotalFat)}g",
                                    fontSize = 12.sp,
                                    color = Color(0xFF5B8DEF)
                                )
                                Text(text = "置信度 ${(candidate.confidence * 100).toInt()}% · ${candidate.note}", fontSize = 12.sp, color = Color(0xFF8A92A1))
                                OutlinedButton(
                                    onClick = { onApplyRecognizedFood(candidate.food, candidate.quantity, candidate.tags) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(text = "应用此结果", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                if (!isAnalyzing) {
                    OutlinedButton(
                        onClick = { analysisRound += 1 },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(text = "重新分析", fontSize = 12.sp)
                    }
                }
                Text(
                    text = if (imageDataUrl != null) {
                        if (imageType == RecognitionImageType.NutritionLabel) {
                            "说明：如果图片是营养成分表，AI 会优先直接读取表格里的数值，而不是按食物照片去估算。相关估算口径会尽量参考《中国居民膳食指南》。"
                        } else {
                            "说明：图片识别会估算食物类型、份量与营养素，相关估算口径会尽量参考《中国居民膳食指南》；保存前仍建议按实际重量校对。"
                        }
                    } else {
                        "说明：当前记录分析会结合手动输入生成候选结果，也可通过拍照或相册进行图片识别；相关估算口径会尽量参考《中国居民膳食指南》。"
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF8A92A1)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isAnalyzing) {
                        isAnalyzing = false
                    } else {
                        onApplySuggestion(tags, saveSuggestion, category)
                    }
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = if (isAnalyzing) "跳过等待" else "应用建议")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                Text(text = "关闭")
            }
        }
    )
}

@Composable
fun HistoryScreen(
    meals: List<MealRecord>,
    waterRecords: List<WaterRecord>,
    exerciseBurnRecords: List<ExerciseBurnRecord>,
    plans: List<NutritionPlan>,
    dailyPlanSelections: List<DailyPlanSelection>,
    selectedDate: String,
    onBack: () -> Unit,
    onSelectDate: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val allRecordDates = (meals.map { it.date } + waterRecords.map { it.date } + exerciseBurnRecords.map { it.date })
        .distinct()
        .sortedDescending()
    var visibleMonth by remember(selectedDate) { mutableStateOf(selectedDate.take(7)) }
    val visibleMonthMeals = meals.filter { it.date.startsWith(visibleMonth) }
    val visibleMonthExerciseBurnRecords = exerciseBurnRecords.filter { it.date.startsWith(visibleMonth) }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "历史记录",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF161A23)
            )
            Text(
                text = "用真正的月历查看每天的饮食、饮水和运动消耗。",
                fontSize = 15.sp,
                color = Color(0xFF6F7785)
            )

            MonthNavigatorCard(
                visibleMonth = visibleMonth,
                onPreviousMonthClick = { visibleMonth = offsetMonthText(visibleMonth, -1) },
                onNextMonthClick = { visibleMonth = offsetMonthText(visibleMonth, 1) },
                onCurrentMonthClick = { visibleMonth = currentDateText().take(7) }
            )

            MonthSummaryCard(
                visibleMonth = visibleMonth,
                meals = visibleMonthMeals,
                plans = plans,
                dailyPlanSelections = dailyPlanSelections,
                exerciseBurnRecords = visibleMonthExerciseBurnRecords
            )

            CalendarMonthCard(
                visibleMonth = visibleMonth,
                selectedDate = selectedDate,
                meals = meals,
                waterRecords = waterRecords,
                exerciseBurnRecords = exerciseBurnRecords,
                plans = plans,
                dailyPlanSelections = dailyPlanSelections,
                onSelectDate = onSelectDate
            )

            CalendarLegendCard()

            if (allRecordDates.isEmpty()) {
                EmptyHistoryCard()
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(text = "返回首页")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun FoodLibraryScreen(
    foods: List<FoodItem>,
    tags: List<String>,
    onBack: () -> Unit,
    onUseFood: (FoodItem, Double, List<String>) -> Unit,
    onDeleteFood: (FoodItem) -> Unit,
    onClearFoods: () -> Unit,
    onUpdateFood: (Int, FoodItem) -> Unit,
    onAddFood: (FoodItem) -> Unit = {}
) {
    BackHandler(onBack = onBack)
    var foodPendingDelete by remember { mutableStateOf<FoodItem?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var editorState by remember { mutableStateOf<FoodEditorState?>(null) }
    var showAddFoodDialog by remember { mutableStateOf(false) }
    var usePanelFood by remember { mutableStateOf<FoodItem?>(null) }
    var useQuantityText by remember { mutableStateOf("") }
    var useCustomTag by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }
    var showHiddenFoodsDialog by remember { mutableStateOf(false) }
    var hiddenFoodSearchText by remember { mutableStateOf("") }
    val visibleFoods = foods.filterNot { it.isHidden }
    val hiddenFoods = foods.filter { it.isHidden }
    val categories = listOf("全部") + visibleFoods.map { it.category }.distinct().sorted()
    val filteredFoods = visibleFoods.filter { food ->
        val matchesSearch = searchText.isBlank() || food.name.contains(searchText.trim(), ignoreCase = true)
        val matchesCategory = selectedCategory == "全部" || food.category == selectedCategory
        matchesSearch && matchesCategory
    }
    val filteredHiddenFoods = hiddenFoods.filter { food ->
        val keyword = hiddenFoodSearchText.trim()
        keyword.isBlank() ||
            food.name.contains(keyword, ignoreCase = true) ||
            food.category.contains(keyword, ignoreCase = true)
    }

    foodPendingDelete?.let { food ->
        ConfirmDialog(
            title = "删除食物",
            message = "确定要从食物库删除「${food.name}」吗？此操作无法撤销。",
            confirmText = "删除",
            onConfirm = {
                onDeleteFood(food)
                foodPendingDelete = null
            },
            onDismiss = { foodPendingDelete = null }
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "清空食物库",
            message = "确定要清空所有常用食物吗？此操作无法撤销。",
            confirmText = "清空",
            onConfirm = {
                onClearFoods()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false }
        )
    }

    if (showHiddenFoodsDialog) {
        AlertDialog(
            onDismissRequest = { showHiddenFoodsDialog = false },
            title = { Text(text = "隐藏食物列表", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "隐藏食物不会出现在食物库主页面和首页常用食物里，但不会被删除。",
                        fontSize = 12.sp,
                        color = Color(0xFF6F7785)
                    )
                    AppTextField(
                        label = "搜索隐藏食物",
                        value = hiddenFoodSearchText,
                        onValueChange = { hiddenFoodSearchText = it }
                    )
                    if (hiddenFoods.isEmpty()) {
                        Text(text = "当前没有隐藏食物。", fontSize = 13.sp, color = Color(0xFF8A92A1))
                    } else if (filteredHiddenFoods.isEmpty()) {
                        Text(text = "没有匹配的隐藏食物。", fontSize = 13.sp, color = Color(0xFF8A92A1))
                    } else {
                        filteredHiddenFoods.forEach { food ->
                            val index = foods.indexOfFirst { it === food }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(text = food.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
                                    Text(
                                        text = "${food.category} · ${formatNumber(food.calories)} kcal · ${foodServingText(food)}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF6F7785)
                                    )
                                    Text(
                                        text = "碳水${formatNumber(food.carbs)}g · 蛋白${formatNumber(food.protein)}g · 脂肪${formatNumber(food.fat)}g",
                                        fontSize = 12.sp,
                                        color = Color(0xFF8A92A1)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            if (index >= 0) onUpdateFood(index, food.copy(isHidden = false))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(text = "恢复显示", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showHiddenFoodsDialog = false }, shape = RoundedCornerShape(14.dp)) {
                    Text(text = "关闭")
                }
            }
        )
    }

    if (showAddFoodDialog) {
        FoodEditorResponsiveDialog(
            initialFood = FoodItem(name = "", protein = 0.0, fat = 0.0, carbs = 0.0, category = "自定义"),
            onDismiss = { showAddFoodDialog = false },
            onSave = { newFood ->
                onAddFood(newFood)
                showAddFoodDialog = false
            }
        )
    }

    editorState?.let { state ->
        FoodEditorResponsiveDialog(
            initialFood = state.originalFood,
            onDismiss = { editorState = null },
            onSave = { updatedFood ->
                onUpdateFood(state.editingIndex, updatedFood)
                editorState = null
            }
        )
    }

    usePanelFood?.let { food ->
        FoodUseDialog(
            food = food,
            tags = tags,
            quantityText = useQuantityText,
            tagText = useCustomTag,
            onQuantityChange = { useQuantityText = it },
            onTagTextChange = { useCustomTag = it },
            onDismiss = { usePanelFood = null },
            onConfirm = { quantity, recordTags ->
                onUseFood(food, quantity, recordTags)
                usePanelFood = null
            }
        )
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "食物库",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF161A23)
                    )
                    Text(
                        text = "保存常吃食物，下次一键添加。",
                        fontSize = 15.sp,
                        color = Color(0xFF6F7785)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showAddFoodDialog = true }, shape = RoundedCornerShape(16.dp)) {
                        Text(text = "新增")
                    }
                    if (foods.isNotEmpty()) {
                        OutlinedButton(onClick = { showClearConfirm = true }, shape = RoundedCornerShape(16.dp)) {
                            Text(text = "清空")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showHiddenFoodsDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = "查看隐藏食物列表（${hiddenFoods.size}）")
            }

            if (visibleFoods.isNotEmpty()) {
                AppTextField(
                    label = "搜索食物",
                    value = searchText,
                    onValueChange = { searchText = it }
                )
                CategorySelectorRow(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )
            }

            if (visibleFoods.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (foods.isEmpty()) "食物库还是空的" else "主页面没有显示中的食物",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF161A23)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (foods.isEmpty()) {
                                "添加饮食记录时点击“保存到食物库”即可收藏。"
                            } else {
                                "可以新增食物，或在隐藏食物列表里恢复已有食物。"
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF8A92A1)
                        )
                    }
                }
            } else if (filteredFoods.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = "没有匹配的食物", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161A23))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "试试换个关键词搜索。", fontSize = 13.sp, color = Color(0xFF8A92A1))
                    }
                }
            } else {
                filteredFoods.forEach { food ->
                    val index = foods.indexOfFirst { it === food }
                    FoodLibraryCard(
                        food = food,
                        onUse = {
                            usePanelFood = food
                            useQuantityText = if (food.servingType == ServingType.PerItem) "1" else "100"
                            useCustomTag = tags.firstOrNull().orEmpty()
                        },
                        onEdit = { if (index >= 0) editorState = FoodEditorState(index, food) },
                        onHide = { if (index >= 0) onUpdateFood(index, food.copy(isHidden = true)) },
                        onDelete = { foodPendingDelete = food }
                    )
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(text = "返回首页")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun PlanScreen(
    selectedDate: String,
    userProfile: UserProfile,
    plans: List<NutritionPlan>,
    selectedPlanId: Long?,
    onBack: () -> Unit,
    onSaveUserProfile: (UserProfile) -> Unit,
    onSelectPlanForDate: (Long) -> Unit,
    onSavePlan: (NutritionPlan) -> Unit,
    onDeletePlan: (Long) -> Unit,
    onSetDefaultPlan: (Long) -> Unit
) {
    BackHandler(onBack = onBack)

    var editingPlanId by remember { mutableStateOf<Long?>(null) }
    var showPlanEditor by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var waterTargetMl by remember { mutableStateOf("2000") }
    var dailyCalorieDeficit by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    var heightCm by remember(userProfile) { mutableStateOf(userProfile.heightCm.takeIf { it > 0 }?.let(::formatNumber).orEmpty()) }
    var weightKg by remember(userProfile) { mutableStateOf(userProfile.weightKg.takeIf { it > 0 }?.let(::formatNumber).orEmpty()) }
    var ageYears by remember(userProfile) { mutableStateOf(userProfile.ageYears.takeIf { it > 0 }?.toString().orEmpty()) }
    var biologicalSex by remember(userProfile) { mutableStateOf(userProfile.biologicalSex) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var generatedPlanInfoText by remember { mutableStateOf<String?>(null) }
    var pendingDeletePlanId by remember { mutableStateOf<Long?>(null) }
    var showHiddenPlansDialog by remember { mutableStateOf(false) }
    var hiddenPlanSearchText by remember { mutableStateOf("") }
    val planScrollState = rememberScrollState()

    val previewCalories = calculateCalories(
        protein = parseOneDecimal(protein) ?: 0.0,
        fat = parseOneDecimal(fat) ?: 0.0,
        carbs = parseOneDecimal(carbs) ?: 0.0
    )
    val previewBmr = calculateBasalMetabolism(
        UserProfile(
            heightCm = parseOneDecimal(heightCm) ?: 0.0,
            weightKg = parseOneDecimal(weightKg) ?: 0.0,
            ageYears = ageYears.toIntOrNull() ?: 0,
            biologicalSex = biologicalSex
        )
    )
    val currentPlan = plans.firstOrNull { it.id == selectedPlanId } ?: plans.firstOrNull { it.isDefault } ?: plans.firstOrNull()
    val visiblePlans = plans.filterNot { it.isHidden }
    val hiddenPlans = plans.filter { it.isHidden }
    val filteredHiddenPlans = hiddenPlans.filter { plan ->
        val keyword = hiddenPlanSearchText.trim()
        keyword.isBlank() ||
            plan.name.contains(keyword, ignoreCase = true) ||
            plan.note.contains(keyword, ignoreCase = true)
    }

    LaunchedEffect(showPlanEditor, editingPlanId) {
        if (showPlanEditor || editingPlanId != null) {
            kotlinx.coroutines.delay(120)
            planScrollState.animateScrollTo(planScrollState.maxValue)
        }
    }

    pendingDeletePlanId?.let { planId ->
        val plan = plans.firstOrNull { it.id == planId }
        if (plan != null) {
            ConfirmDialog(
                title = "删除计划",
                message = "确定要删除「${plan.name}」吗？此操作无法撤销。",
                confirmText = "删除",
                onConfirm = {
                    onDeletePlan(planId)
                    if (editingPlanId == planId) {
                        editingPlanId = null
                        showPlanEditor = false
                        name = ""
                        protein = ""
                        fat = ""
                        carbs = ""
                        waterTargetMl = "2000"
                        dailyCalorieDeficit = "0"
                        note = ""
                    }
                    pendingDeletePlanId = null
                },
                onDismiss = { pendingDeletePlanId = null }
            )
        }
    }

    if (showHiddenPlansDialog) {
        AlertDialog(
            onDismissRequest = { showHiddenPlansDialog = false },
            title = { Text(text = "隐藏计划列表", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "隐藏计划不会出现在计划设置主页面，但不会删除，也不会影响历史记录里的计划显示。",
                        fontSize = 12.sp,
                        color = Color(0xFF6F7785)
                    )
                    AppTextField(
                        label = "搜索隐藏计划",
                        value = hiddenPlanSearchText,
                        onValueChange = { hiddenPlanSearchText = it }
                    )
                    if (hiddenPlans.isEmpty()) {
                        Text(text = "当前没有隐藏计划。", fontSize = 13.sp, color = Color(0xFF8A92A1))
                    } else if (filteredHiddenPlans.isEmpty()) {
                        Text(text = "没有匹配的隐藏计划。", fontSize = 13.sp, color = Color(0xFF8A92A1))
                    } else {
                        filteredHiddenPlans.forEach { plan ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(text = plan.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
                                    Text(
                                        text = "${formatNumber(plan.targetCalories)} kcal · 蛋白${formatNumber(plan.targetProtein)}g 脂肪${formatNumber(plan.targetFat)}g 碳水${formatNumber(plan.targetCarbs)}g · 缺口${formatNumber(plan.dailyCalorieDeficit)}kcal",
                                        fontSize = 12.sp,
                                        color = Color(0xFF6F7785)
                                    )
                                    if (plan.note.isNotBlank()) {
                                        Text(text = plan.note, fontSize = 12.sp, color = Color(0xFF8A92A1))
                                    }
                                    OutlinedButton(
                                        onClick = { onSavePlan(plan.copy(isHidden = false)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(text = "恢复显示", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showHiddenPlansDialog = false }, shape = RoundedCornerShape(14.dp)) {
                    Text(text = "关闭")
                }
            }
        )
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(planScrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "每日计划",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF161A23)
            )
            Text(
                text = "为不同日期配置不同计划，并可维护默认计划。",
                fontSize = 15.sp,
                color = Color(0xFF6F7785)
            )

            FormCard {
                Text(text = "当前日期：$selectedDate", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                Text(
                    text = "当前使用：${currentPlan?.name ?: "未选择计划"}",
                    fontSize = 14.sp,
                    color = Color(0xFF5B8DEF),
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(
                    onClick = {
                        editingPlanId = null
                        showPlanEditor = true
                        name = ""
                        protein = ""
                        fat = ""
                        carbs = ""
                        waterTargetMl = "2000"
                        dailyCalorieDeficit = "0"
                        note = ""
                        errorText = null
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "新建计划")
                }
                OutlinedButton(
                    onClick = { showHiddenPlansDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "查看隐藏计划列表（${hiddenPlans.size}）")
                }
            }

            FormCard {
                Text(
                    text = "个人基础资料",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF161A23)
                )
                AppTextField(label = "身高 cm", value = heightCm, onValueChange = { heightCm = it }, isNumber = true)
                AppTextField(label = "体重 kg", value = weightKg, onValueChange = { weightKg = it }, isNumber = true)
                AppTextField(label = "年龄（周岁）", value = ageYears, onValueChange = { ageYears = it.filter { ch -> ch.isDigit() } }, isNumber = true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { biologicalSex = BiologicalSex.Male },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = if (biologicalSex == BiologicalSex.Male) "✓ 生理男性" else "生理男性")
                    }
                    OutlinedButton(
                        onClick = { biologicalSex = BiologicalSex.Female },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = if (biologicalSex == BiologicalSex.Female) "✓ 生理女性" else "生理女性")
                    }
                }
                Text(
                    text = if (previewBmr != null) {
                        "按当前资料估算基础代谢：${formatNumber(previewBmr)} kcal"
                    } else {
                        "填写完整后会自动估算基础代谢"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF303747)
                )
                OutlinedButton(
                    onClick = {
                        val parsedHeight = parseOneDecimal(heightCm)
                        val parsedWeight = parseOneDecimal(weightKg)
                        val parsedAge = ageYears.toIntOrNull()
                        when {
                            parsedHeight == null || parsedWeight == null || parsedAge == null -> {
                                errorText = "请输入有效的身高、体重和年龄"
                            }
                            parsedHeight <= 0.0 || parsedWeight <= 0.0 || parsedAge <= 0 -> {
                                errorText = "身高、体重和年龄都需要大于 0"
                            }
                            else -> {
                                onSaveUserProfile(
                                    UserProfile(
                                        heightCm = parsedHeight,
                                        weightKg = parsedWeight,
                                        ageYears = parsedAge,
                                        biologicalSex = biologicalSex
                                    )
                                )
                                errorText = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "保存基础资料")
                }
                OutlinedButton(
                    onClick = {
                        val parsedHeight = parseOneDecimal(heightCm)
                        val parsedWeight = parseOneDecimal(weightKg)
                        val parsedAge = ageYears.toIntOrNull()
                        when {
                            parsedHeight == null || parsedWeight == null || parsedAge == null -> {
                                errorText = "请先填写完整且有效的身高、体重、年龄和生理性别"
                                generatedPlanInfoText = null
                            }
                            parsedHeight <= 0.0 || parsedWeight <= 0.0 || parsedAge <= 0 -> {
                                errorText = "身高、体重和年龄都需要大于 0"
                                generatedPlanInfoText = null
                            }
                            else -> {
                                val currentProfile = UserProfile(
                                    heightCm = parsedHeight,
                                    weightKg = parsedWeight,
                                    ageYears = parsedAge,
                                    biologicalSex = biologicalSex
                                )
                                val generatedPlans = generateAutoCutPlans(currentProfile, plans)
                                if (generatedPlans.isEmpty()) {
                                    errorText = "当前资料不足，暂时无法生成计划"
                                    generatedPlanInfoText = null
                                } else {
                                    generatedPlans.forEach(onSavePlan)
                                    generatedPlans.firstOrNull { it.name.startsWith("低碳活动日") }?.let { lowCarbActivePlan ->
                                        if (selectedPlanId == null) {
                                            onSelectPlanForDate(lowCarbActivePlan.id)
                                        }
                                    }
                                    errorText = null
                                    generatedPlanInfoText = "已生成或更新 3 份减脂计划：高碳日、低碳活动日、低碳休息日。"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "一键生成当前体重的三份减脂计划")
                }
                Text(
                    text = "生成规则：蛋白质按体重 1.8 倍保证；高碳日脂肪按生理男性体重 0.8 倍、生理女性体重 0.9 倍；低碳活动日和低碳休息日脂肪按体重 1.1 倍；剩余热量全部分配给碳水。",
                    fontSize = 12.sp,
                    color = Color(0xFF8A92A1)
                )
                generatedPlanInfoText?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = Color(0xFF2E9D63),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (visiblePlans.isEmpty()) {
                FormCard {
                    Text(
                        text = if (plans.isEmpty()) "还没有计划" else "主页面没有显示中的计划",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF161A23)
                    )
                    Text(
                        text = if (plans.isEmpty()) "先新建一个计划，用于今天或设为默认。" else "可以新建计划，或在隐藏计划列表里恢复已有计划。",
                        fontSize = 13.sp,
                        color = Color(0xFF8A92A1)
                    )
                }
            } else {
                visiblePlans.forEach { plan ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = if (plan.id == selectedPlanId) Color(0xFFE7F0FF) else Color(0xBFFFFFFF))
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = plan.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
                                    Text(
                                        text = buildString {
                                            if (plan.isDefault) append("默认计划")
                                            if (plan.id == selectedPlanId) {
                                                if (isNotEmpty()) append(" · ")
                                                append("今日使用")
                                            }
                                            if (isEmpty()) append("普通计划")
                                        },
                                        fontSize = 13.sp,
                                        color = Color(0xFF8A92A1)
                                    )
                                }
                            }
                            Text(
                                text = "${formatNumber(plan.targetCalories)} kcal · 蛋白${formatNumber(plan.targetProtein)}g 脂肪${formatNumber(plan.targetFat)}g 碳水${formatNumber(plan.targetCarbs)}g · 饮水${formatNumber(plan.waterTargetMl)}ml · 缺口${formatNumber(plan.dailyCalorieDeficit)}kcal",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF303747)
                            )
                            if (plan.note.isNotBlank()) {
                                Text(text = plan.note, fontSize = 13.sp, color = Color(0xFF6F7785))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onSelectPlanForDate(plan.id) }, shape = RoundedCornerShape(14.dp)) {
                                    Text(text = if (plan.id == selectedPlanId) "今日已使用" else "用于今天", fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { onSetDefaultPlan(plan.id) }, shape = RoundedCornerShape(14.dp)) {
                                    Text(text = if (plan.isDefault) "默认中" else "设为默认", fontSize = 12.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        editingPlanId = plan.id
                                        showPlanEditor = true
                                        name = plan.name
                                        protein = formatNumber(plan.targetProtein)
                                        fat = formatNumber(plan.targetFat)
                                        carbs = formatNumber(plan.targetCarbs)
                                        waterTargetMl = formatNumber(plan.waterTargetMl)
                                        dailyCalorieDeficit = formatNumber(plan.dailyCalorieDeficit)
                                        note = plan.note
                                        errorText = null
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(text = "编辑", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { onSavePlan(plan.copy(isHidden = true)) },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(text = "隐藏", fontSize = 12.sp)
                                }
                                if (plans.size > 1) {
                                    OutlinedButton(onClick = { pendingDeletePlanId = plan.id }, shape = RoundedCornerShape(14.dp)) {
                                        Text(text = "删除", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showPlanEditor || editingPlanId != null) {
                FormCard {
                    Text(
                        text = if (editingPlanId == null) "新建计划" else "编辑计划",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF161A23)
                    )
                    AppTextField(label = "计划名称", value = name, onValueChange = { name = it })
                    AppTextField(label = "目标碳水 g", value = carbs, onValueChange = { carbs = it }, isNumber = true)
                    AppTextField(label = "目标蛋白质 g", value = protein, onValueChange = { protein = it }, isNumber = true)
                    AppTextField(label = "目标脂肪 g", value = fat, onValueChange = { fat = it }, isNumber = true)
                    AppTextField(label = "饮水目标 ml", value = waterTargetMl, onValueChange = { waterTargetMl = it }, isNumber = true)
                    AppTextField(label = "热量缺口 kcal", value = dailyCalorieDeficit, onValueChange = { dailyCalorieDeficit = it }, isNumber = true)
                    Text(
                        text = "热量缺口表示这个计划本身预计每天能带来多少热量缺口。月度总结会把它和每天实际少吃/多吃的部分一起累计。",
                        fontSize = 12.sp,
                        color = Color(0xFF8A92A1)
                    )
                    AppTextField(label = "备注", value = note, onValueChange = { note = it })
                    Text(
                        text = "自动计算目标热量：${formatNumber(previewCalories)} kcal（蛋白质×4 + 脂肪×9 + 碳水×4）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF303747)
                    )
                }

                errorText?.let {
                    Text(text = it, color = Color(0xFFEF5350), fontSize = 14.sp)
                }

                Button(
                    onClick = {
                        val parsedProtein = parseOneDecimal(protein)
                        val parsedFat = parseOneDecimal(fat)
                        val parsedCarbs = parseOneDecimal(carbs)
                        val parsedWater = parseOneDecimal(waterTargetMl)
                        val parsedDailyCalorieDeficit = parseOneDecimal(dailyCalorieDeficit)

                        if (name.isBlank()) {
                            errorText = "请输入计划名称"
                            return@Button
                        }
                        if (parsedProtein == null || parsedFat == null || parsedCarbs == null || parsedWater == null || parsedDailyCalorieDeficit == null) {
                            errorText = "请输入有效计划数据"
                            return@Button
                        }
                        if (parsedProtein < 0 || parsedFat < 0 || parsedCarbs < 0 || parsedWater <= 0 || parsedDailyCalorieDeficit < 0) {
                            errorText = "目标值和热量缺口不能为负数，饮水目标需大于 0"
                            return@Button
                        }

                        val calculatedCalories = calculateCalories(parsedProtein, parsedFat, parsedCarbs)
                        if (calculatedCalories <= 0) {
                            errorText = "请至少输入一种有效营养素目标"
                            return@Button
                        }

                        onSavePlan(
                            NutritionPlan(
                                id = editingPlanId ?: ((plans.maxOfOrNull { it.id } ?: 0L) + 1L),
                                name = name.trim(),
                                targetCalories = calculatedCalories,
                                targetProtein = parsedProtein,
                                targetFat = parsedFat,
                                targetCarbs = parsedCarbs,
                                waterTargetMl = parsedWater,
                                dailyCalorieDeficit = parsedDailyCalorieDeficit,
                                isDefault = plans.none { it.isDefault } && editingPlanId == null,
                                note = note.trim()
                            )
                        )
                        editingPlanId = null
                        showPlanEditor = false
                        name = ""
                        protein = ""
                        fat = ""
                        carbs = ""
                        waterTargetMl = "2000"
                        dailyCalorieDeficit = "0"
                        note = ""
                        errorText = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(text = if (editingPlanId == null) "保存计划" else "保存修改", fontSize = 17.sp)
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(text = "返回首页")
            }
        }
    }
}

@Composable
fun ReportScreen(
    selectedDate: String,
    plan: NutritionPlan?,
    meals: List<MealRecord>,
    exerciseBurnRecord: ExerciseBurnRecord?,
    adjustedTargetCalories: Double,
    adjustedTargetProtein: Double,
    adjustedTargetCarbs: Double,
    waterTargetMl: Double,
    drunkWaterMl: Double,
    aiSettingsList: List<AiSettings>,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val consumedCalories = meals.sumOf { it.calories }
    val consumedProtein = meals.sumOf { it.protein }
    val consumedFat = meals.sumOf { it.fat }
    val consumedCarbs = meals.sumOf { it.carbs }
    val targetCalories = adjustedTargetCalories
    val targetProtein = adjustedTargetProtein
    val targetFat = plan?.targetFat ?: 0.0
    val targetCarbs = adjustedTargetCarbs
    val remainingCalories = targetCalories - consumedCalories
    val remainingProtein = targetProtein - consumedProtein
    val remainingFat = targetFat - consumedFat
    val remainingCarbs = targetCarbs - consumedCarbs
    val remainingWater = waterTargetMl - drunkWaterMl
    val reportAiSettings = aiSettingsList.firstOrNull { it.enabled && it.selectedForTextWork && it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() }
    var aiReportText by remember(selectedDate, meals, plan, exerciseBurnRecord, drunkWaterMl, reportAiSettings) { mutableStateOf<String?>(null) }
    var aiReportError by remember(selectedDate, meals, plan, exerciseBurnRecord, drunkWaterMl, reportAiSettings) { mutableStateOf<String?>(null) }
    var isGeneratingAiReport by remember(selectedDate, meals, plan, exerciseBurnRecord, drunkWaterMl, reportAiSettings) { mutableStateOf(false) }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "营养分析报告",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF161A23)
            )
            Text(
                text = "$selectedDate · 本地规则分析，可使用文字生成工作 AI 生成自然语言报告。",
                fontSize = 15.sp,
                color = Color(0xFF6F7785)
            )

            ReportSectionCard(title = "今日总结") {
                Text(
                    text = if (meals.isEmpty()) {
                        "今天还没有饮食记录。建议先添加早餐、午餐、晚餐或加餐记录，再查看完整分析。"
                    } else {
                        "今天共记录 ${meals.size} 条饮食，已摄入 ${formatNumber(consumedCalories)} kcal。" +
                            if (plan != null) " 当前计划为「${plan.name}」，运动补回后目标 ${formatNumber(targetCalories)} kcal。" else " 当前还没有可用计划。"
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF303747)
                )
            }

            ReportSectionCard(title = "热量状态") {
                ReportMetricRow(label = "目标热量", value = if (targetCalories > 0) "${formatNumber(targetCalories)} kcal" else "未设置")
                if ((exerciseBurnRecord?.calories ?: 0.0) > 0) {
                    ReportMetricRow(label = "额外运动消耗", value = "+${formatNumber(exerciseBurnRecord?.calories ?: 0.0)} kcal")
                }
                ReportMetricRow(label = "已摄入", value = "${formatNumber(consumedCalories)} kcal")
                ReportMetricRow(
                    label = if (remainingCalories >= 0) "剩余可摄入" else "已超出",
                    value = "${formatNumber(kotlin.math.abs(remainingCalories))} kcal",
                    color = if (remainingCalories >= 0) Color(0xFF35C759) else Color(0xFFEF5350)
                )
                Text(
                    text = when {
                        plan == null -> "请先创建或选择热量计划，以便计算剩余热量。"
                        consumedCalories == 0.0 -> "暂无摄入数据，无法判断热量完成度。"
                        remainingCalories < 0 -> "今日热量已经超出目标，后续饮食建议以低热量、高饱腹感食物为主。"
                        remainingCalories <= targetCalories * 0.15 -> "今日热量已经接近目标，继续保持即可。"
                        else -> "今日仍有一定热量空间，可以根据蛋白质、碳水和脂肪缺口补充。"
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF6F7785)
                )
                if ((exerciseBurnRecord?.calories ?: 0.0) > 0) {
                    Text(
                        text = "补回公式：运动消耗热量补充 = 85% 碳水 + 15% 蛋白质。",
                        fontSize = 12.sp,
                        color = Color(0xFF8A5A00)
                    )
                }
            }

            ReportSectionCard(title = "宏量营养素缺口") {
                ReportMetricRow(label = "蛋白质", value = nutritionGapText(consumedProtein, targetProtein, "g"), color = gapColor(remainingProtein))
                ReportMetricRow(label = "脂肪", value = nutritionGapText(consumedFat, targetFat, "g"), color = gapColor(remainingFat))
                ReportMetricRow(label = "碳水", value = nutritionGapText(consumedCarbs, targetCarbs, "g"), color = gapColor(remainingCarbs))
                Text(
                    text = macroSuggestionText(remainingProtein, remainingFat, remainingCarbs, plan != null),
                    fontSize = 13.sp,
                    color = Color(0xFF6F7785)
                )
            }

            ReportSectionCard(title = "饮水建议") {
                ReportMetricRow(label = "饮水目标", value = "${formatNumber(waterTargetMl)} ml")
                ReportMetricRow(label = "已饮水", value = "${formatNumber(drunkWaterMl)} ml")
                ReportMetricRow(
                    label = if (remainingWater > 0) "剩余饮水" else "完成状态",
                    value = if (remainingWater > 0) "${formatNumber(remainingWater)} ml" else "已完成",
                    color = if (remainingWater > 0) Color(0xFF29B6F6) else Color(0xFF35C759)
                )
                Text(
                    text = if (remainingWater > 0) "可以分多次少量饮水，避免临睡前一次性大量饮水。" else "今日饮水目标已完成，继续保持稳定饮水节奏。",
                    fontSize = 13.sp,
                    color = Color(0xFF6F7785)
                )
            }

            ReportSectionCard(title = "AI 自然语言建议") {
                Text(
                    text = if (reportAiSettings != null) {
                        "将使用 ${reportAiSettings.providerName} · ${reportAiSettings.modelName} 生成报告，相关建议会尽量参考《中国居民膳食指南》，并结合食物名称估算膳食纤维、常见矿物质及食物质量。"
                    } else {
                        "当前没有可用文字生成工作 AI，请在 AI 设置中启用并选择至少一个文字生成工作 AI。"
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF6F7785)
                )
                aiReportText?.let {
                    Text(text = it, fontSize = 13.sp, color = Color(0xFF303747))
                }
                aiReportError?.let {
                    Text(text = it, fontSize = 13.sp, color = Color(0xFFEF5350))
                }
                Button(
                    onClick = {
                        val settings = reportAiSettings
                        if (settings == null) {
                            aiReportError = "没有可用文字生成工作 AI"
                            return@Button
                        }
                        isGeneratingAiReport = true
                        aiReportText = null
                        aiReportError = null
                    },
                    enabled = reportAiSettings != null && !isGeneratingAiReport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = if (isGeneratingAiReport) "正在生成..." else "生成 AI 报告", fontSize = 13.sp)
                }
            }

            LaunchedEffect(isGeneratingAiReport) {
                if (isGeneratingAiReport && reportAiSettings != null) {
                    val result = runCatching {
                        requestAiNutritionReport(
                            settings = reportAiSettings,
                            selectedDate = selectedDate,
                            plan = plan,
                            meals = meals,
                            exerciseBurnRecord = exerciseBurnRecord,
                            waterTargetMl = waterTargetMl,
                            drunkWaterMl = drunkWaterMl
                        )
                    }
                    aiReportText = result.getOrNull()
                    aiReportError = result.exceptionOrNull()?.let(::friendlyAiReportErrorMessage)
                    isGeneratingAiReport = false
                }
            }

            ReportSectionCard(title = "注意事项") {
                Text(
                    text = "本报告及 app 内相关营养建议、AI 估算会尽量参考《中国居民膳食指南》，仅基于你记录的数据供日常参考，不构成医疗建议。特殊人群或有疾病管理需求时，请咨询专业人士。",
                    fontSize = 13.sp,
                    color = Color(0xFF6F7785)
                )
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(text = "返回首页")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SponsorScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val sponsorBitmap = remember { loadSponsorEntryBitmap(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF9B72EA),
                        Color(0xFF7C57D9),
                        Color(0xFF6E4CCB)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(16.dp)) {
                    Text(text = "返回首页")
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "赞助支持以及反馈BUG",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = "写App花费的token有点多QwQ，如果喜欢我的作品，不妨来支持我一下吧！反馈BUG也请来爱发电的主页评论吧！",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            if (sponsorBitmap != null) {
                Image(
                    bitmap = sponsorBitmap.asImageBitmap(),
                    contentDescription = "爱发电赞助入口",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "缺少赞助入口图片",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF101018)
                        )
                        Text(
                            text = "请将真实爱发电图片放到 app/src/main/assets/溪云Siena.jpg，App 会在这里原样显示整张图。",
                            fontSize = 14.sp,
                            color = Color(0xFF6F6678)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AuthorWordsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val authorWordsText = remember { loadAuthorWordsText(context) }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(16.dp)) {
                Text(text = "返回首页")
            }

            Text(
                text = "作者的话以及减脂建议",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF161A23)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
            ) {
                MarkdownArticle(
                    markdown = authorWordsText,
                    modifier = Modifier.padding(18.dp)
                )
            }
        }
    }
}

@Composable
fun MarkdownArticle(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block.headingLevel) {
                1 -> Text(
                    text = markdownInlineText(block.text),
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF161A23)
                )
                2 -> Text(
                    text = markdownInlineText(block.text),
                    fontSize = 21.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF161A23)
                )
                3 -> Text(
                    text = markdownInlineText(block.text),
                    fontSize = 19.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5B8DEF)
                )
                else -> Text(
                    text = markdownInlineText(block.text),
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = Color(0xFF303747)
                )
            }
        }
    }
}

data class MarkdownBlock(
    val headingLevel: Int?,
    val text: String
)

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphLines = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphLines.isNotEmpty()) {
            blocks.add(MarkdownBlock(null, paragraphLines.joinToString("\n").trim()))
            paragraphLines.clear()
        }
    }

    markdown.lines().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isBlank() -> flushParagraph()
            line.startsWith("# ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock(1, line.removePrefix("# ").trim()))
            }
            line.startsWith("## ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock(2, line.removePrefix("## ").trim()))
            }
            line.startsWith("### ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock(3, line.removePrefix("### ").trim()))
            }
            else -> paragraphLines.add(line.removeSuffix("  "))
        }
    }
    flushParagraph()
    return blocks
}

fun markdownInlineText(text: String) = buildAnnotatedString {
    val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
    var cursor = 0
    boldRegex.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

@Composable
fun ReportSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCCFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
            content()
        }
    }
}

@Composable
fun ReportMetricRow(
    label: String,
    value: String,
    color: Color = Color(0xFF303747)
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.weight(1f), fontSize = 14.sp, color = Color(0xFF6F7785))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

fun nutritionGapText(consumed: Double, target: Double, unit: String): String {
    if (target <= 0) return "已摄入 ${formatNumber(consumed)}$unit · 未设置目标"
    val remaining = target - consumed
    return if (remaining >= 0) {
        "已摄入 ${formatNumber(consumed)}$unit / 目标 ${formatNumber(target)}$unit · 剩余 ${formatNumber(remaining)}$unit"
    } else {
        "已摄入 ${formatNumber(consumed)}$unit / 目标 ${formatNumber(target)}$unit · 超出 ${formatNumber(kotlin.math.abs(remaining))}$unit"
    }
}

fun gapColor(remaining: Double): Color {
    return if (remaining >= 0) Color(0xFF303747) else Color(0xFFEF5350)
}

fun macroSuggestionText(
    remainingProtein: Double,
    remainingFat: Double,
    remainingCarbs: Double,
    hasPlan: Boolean
): String {
    if (!hasPlan) return "请先创建或选择计划，以便生成更准确的宏量营养素建议。"
    val suggestions = mutableListOf<String>()
    if (remainingProtein > 15) suggestions.add("蛋白质还有明显缺口，可优先补充鸡蛋、牛奶、鱼虾、鸡胸肉或豆制品。")
    if (remainingCarbs > 30) suggestions.add("碳水仍有空间，可选择米饭、燕麦、土豆、玉米等主食。")
    if (remainingFat > 10) suggestions.add("脂肪仍有空间，可适量选择坚果、牛油果或橄榄油等来源。")
    if (remainingProtein < 0 || remainingFat < 0 || remainingCarbs < 0) suggestions.add("已有部分营养素超出目标，后续饮食建议清淡并控制份量。")
    return suggestions.ifEmpty { listOf("宏量营养素整体接近目标，保持当前节奏即可。") }.joinToString("\n")
}

fun calculateBasalMetabolism(profile: UserProfile): Double? {
    if (profile.heightCm <= 0.0 || profile.weightKg <= 0.0 || profile.ageYears <= 0) return null
    val base = 10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * profile.ageYears
    return roundOneDecimal(
        when (profile.biologicalSex) {
            BiologicalSex.Male -> base + 5.0
            BiologicalSex.Female -> base - 161.0
        }
    )
}

fun calculateNormalActivityTotalCalories(profile: UserProfile): Double? {
    val basalMetabolism = calculateBasalMetabolism(profile) ?: return null
    val normalActivityCalories = profile.weightKg * 8.2
    return roundOneDecimal(basalMetabolism + normalActivityCalories)
}

fun generatedWeightPlanName(prefix: String, weightKg: Double): String {
    return "$prefix（${formatNumber(weightKg)}kg）"
}

fun solveCarbsWithFixedFat(targetCalories: Double, proteinGrams: Double, fatGrams: Double): Pair<Double, Double> {
    val normalizedFat = fatGrams.coerceAtLeast(0.0)
    val remainingCalories = targetCalories - proteinGrams * 4.0 - normalizedFat * 9.0
    val carbs = if (remainingCalories > 0.0) roundOneDecimal(remainingCalories / 4.0) else 0.0
    return carbs to roundOneDecimal(normalizedFat)
}

fun solveFatWithFixedCarbs(targetCalories: Double, proteinGrams: Double, carbsGrams: Double): Pair<Double, Double> {
    val normalizedCarbs = carbsGrams.coerceAtLeast(0.0)
    val remainingCalories = targetCalories - proteinGrams * 4.0 - normalizedCarbs * 4.0
    val fat = if (remainingCalories > 0.0) roundOneDecimal(remainingCalories / 9.0) else 0.0
    return roundOneDecimal(normalizedCarbs) to fat
}

fun minimumFatTargetForHighCarbDay(profile: UserProfile): Double {
    return roundOneDecimal(
        when (profile.biologicalSex) {
            BiologicalSex.Male -> profile.weightKg * 0.8
            BiologicalSex.Female -> profile.weightKg * 0.9
        }
    )
}

fun generateAutoCutPlans(profile: UserProfile, existingPlans: List<NutritionPlan>): List<NutritionPlan> {
    val weightKg = profile.weightKg
    val basalMetabolism = calculateBasalMetabolism(profile) ?: return emptyList()
    val normalActivityTotalCalories = calculateNormalActivityTotalCalories(profile) ?: return emptyList()
    if (weightKg <= 0.0) return emptyList()

    val proteinTarget = roundOneDecimal(weightKg * 1.8)
    val highCarbCalories = roundOneDecimal(normalActivityTotalCalories - 500.0)
    val lowCarbActiveCalories = roundOneDecimal(normalActivityTotalCalories - 500.0)
    val lowCarbRestCalories = roundOneDecimal(basalMetabolism + 100.0)
    val highCarbFatTarget = minimumFatTargetForHighCarbDay(profile)
    val lowCarbFatTarget = roundOneDecimal(weightKg * 1.1)
    val (highCarbCarbs, highCarbFat) = solveCarbsWithFixedFat(
        targetCalories = highCarbCalories,
        proteinGrams = proteinTarget,
        fatGrams = highCarbFatTarget
    )
    val (lowCarbActiveCarbs, lowCarbActiveFat) = solveCarbsWithFixedFat(
        targetCalories = lowCarbActiveCalories,
        proteinGrams = proteinTarget,
        fatGrams = lowCarbFatTarget
    )
    val (lowCarbRestCarbs, lowCarbRestFat) = solveCarbsWithFixedFat(
        targetCalories = lowCarbRestCalories,
        proteinGrams = proteinTarget,
        fatGrams = lowCarbFatTarget
    )

    val generatedPlanSpecs = listOf(
        Pair(
            generatedWeightPlanName("高碳日", weightKg),
            NutritionPlan(
                id = 0L,
                name = "",
                targetCalories = highCarbCalories,
                targetProtein = proteinTarget,
                targetFat = highCarbFat,
                targetCarbs = highCarbCarbs,
                waterTargetMl = 2000.0,
                dailyCalorieDeficit = 500.0,
                note = "自动生成：按正常活动日总消耗 ${formatNumber(normalActivityTotalCalories)} kcal 减 500 kcal；蛋白质按 ${formatNumber(proteinTarget)}g 保证，脂肪按${if (profile.biologicalSex == BiologicalSex.Male) "体重的 0.8 倍即 ${formatNumber(highCarbFatTarget)}g" else "体重的 0.9 倍即 ${formatNumber(highCarbFatTarget)}g"}，剩余热量全部分配给碳水。"
            )
        ),
        Pair(
            generatedWeightPlanName("低碳活动日", weightKg),
            NutritionPlan(
                id = 0L,
                name = "",
                targetCalories = lowCarbActiveCalories,
                targetProtein = proteinTarget,
                targetFat = lowCarbActiveFat,
                targetCarbs = lowCarbActiveCarbs,
                waterTargetMl = 2000.0,
                dailyCalorieDeficit = 500.0,
                note = "自动生成：按正常活动日总消耗 ${formatNumber(normalActivityTotalCalories)} kcal 减 500 kcal；蛋白质按 ${formatNumber(proteinTarget)}g 保证，脂肪按体重的 1.1 倍即 ${formatNumber(lowCarbFatTarget)}g，剩余热量全部分配给碳水。"
            )
        ),
        Pair(
            generatedWeightPlanName("低碳休息日", weightKg),
            NutritionPlan(
                id = 0L,
                name = "",
                targetCalories = lowCarbRestCalories,
                targetProtein = proteinTarget,
                targetFat = lowCarbRestFat,
                targetCarbs = lowCarbRestCarbs,
                waterTargetMl = 2000.0,
                dailyCalorieDeficit = roundOneDecimal(normalActivityTotalCalories - lowCarbRestCalories),
                note = "自动生成：按基础代谢 ${formatNumber(basalMetabolism)} kcal + 100 kcal；蛋白质按 ${formatNumber(proteinTarget)}g 保证，脂肪按体重的 1.1 倍即 ${formatNumber(lowCarbFatTarget)}g，剩余热量全部分配给碳水。"
            )
        )
    )

    val existingByName = existingPlans.associateBy { it.name }
    var nextId = (existingPlans.maxOfOrNull { it.id } ?: 0L) + 1L
    val hasDefaultPlan = existingPlans.any { it.isDefault }

    return generatedPlanSpecs.map { (planName, templatePlan) ->
        val existing = existingByName[planName]
        val isDefault = existing?.isDefault ?: (!hasDefaultPlan && planName.startsWith("低碳活动日"))
        templatePlan.copy(
            id = existing?.id ?: nextId++,
            name = planName,
            waterTargetMl = existing?.waterTargetMl ?: templatePlan.waterTargetMl,
            isDefault = isDefault,
            isHidden = false
        )
    }
}

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val pagePadding = when {
        screenWidthDp <= 360 -> 12.dp
        screenWidthDp <= 412 -> 16.dp
        else -> 20.dp
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFDF2F6),
                        Color(0xFFF4F0FF),
                        Color(0xFFEFF6FF),
                        Color(0xFFFFFFFF)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(Color(0x33FF4D6D))
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(Color(0x336C63FF))
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.BottomCenter)
                .clip(CircleShape)
                .background(Color(0x2235C759))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pagePadding)
        ) {
            content()
        }
    }
}

@Composable
fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCCFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isNumber: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { nextValue ->
            onValueChange(if (isNumber) sanitizeOneDecimalInput(nextValue) else nextValue)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = if (isNumber) {
            KeyboardOptions(keyboardType = KeyboardType.Decimal)
        } else {
            KeyboardOptions.Default
        }
    )
}

@Composable
fun SummaryCard(
    planName: String,
    targetCalories: Double,
    eatenCalories: Double,
    remainingCalories: Double
) {
    val progress = nutritionProgress(eatenCalories, targetCalories)
    val percentText = if (targetCalories > 0) "${formatNumber(eatenCalories * 100 / targetCalories)}%" else "0%"
    val statusColor = if (remainingCalories >= 0) Color(0xFF111827) else Color(0xFFEF5350)
    val progressColor = when {
        targetCalories <= 0 -> Color(0xFF8A92A1)
        remainingCalories < 0 -> Color(0xFFEF5350)
        progress >= 0.85f -> Color(0xFFFFA726)
        else -> Color(0xFF35C759)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCCFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = if (planName.isBlank()) "今日剩余" else "今日剩余 · $planName",
                fontSize = 16.sp,
                color = Color(0xFF6F7785)
            )
            Text(
                text = if (remainingCalories >= 0) "${formatNumber(remainingCalories)} kcal" else "超出 ${formatNumber(kotlin.math.abs(remainingCalories))} kcal",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFFE6EAF2))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(10.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(progressColor)
                )
            }
            Text(
                text = when {
                    targetCalories <= 0 -> "请先设置热量目标"
                    remainingCalories < 0 -> "今日热量已超标，建议后续选择低热量食物。"
                    progress >= 0.85f -> "已经接近目标，继续保持并注意份量。"
                    eatenCalories == 0.0 -> "还没有摄入记录，点击下方按钮添加第一餐。"
                    else -> "今日仍有热量空间，可结合营养素缺口安排饮食。"
                },
                fontSize = 13.sp,
                color = Color(0xFF6F7785)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryItem(label = "目标", value = formatNumber(targetCalories))
                SummaryItem(label = "已摄入", value = formatNumber(eatenCalories))
                SummaryItem(label = "完成", value = percentText)
            }
        }
    }
}

@Composable
fun RemainingFoodEquivalentDialog(
    remainingCarbs: Double,
    remainingProtein: Double,
    equivalent: RemainingFoodEquivalent?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "剩余营养素相当于多少食物？", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "当前剩余：碳水 ${formatNumber(remainingCarbs.coerceAtLeast(0.0))}g，蛋白质 ${formatNumber(remainingProtein.coerceAtLeast(0.0))}g。",
                    fontSize = 13.sp,
                    color = Color(0xFF6F7785)
                )
                when {
                    remainingCarbs <= 0.0 && remainingProtein <= 0.0 -> {
                        Text(
                            text = "碳水和蛋白质都已经没有剩余空间了，今天后续饮食建议以控制总量为主。",
                            fontSize = 14.sp,
                            color = Color(0xFF303747)
                        )
                    }
                    remainingCarbs < 0.0 || remainingProtein < 0.0 -> {
                        Text(
                            text = "当前至少有一项已经超标，暂时不适合再按最喜爱主食和蛋白来源做补充换算。",
                            fontSize = 14.sp,
                            color = Color(0xFF303747)
                        )
                    }
                    equivalent == null -> {
                        Text(
                            text = "历史记录里还不足以稳定判断你最喜爱的主食和蛋白来源，或者暂时找不到能精确配平剩余碳水、蛋白的组合。",
                            fontSize = 14.sp,
                            color = Color(0xFF303747)
                        )
                    }
                    else -> {
                        Text(
                            text = "根据过去记录，系统推断你常吃的主食和蛋白来源如下，并把它们换算到刚好配平当前剩余碳水、蛋白质。",
                            fontSize = 13.sp,
                            color = Color(0xFF6F7785)
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "最喜爱主食：${equivalent.staple.name}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF161A23)
                                )
                                Text(
                                    text = "建议 ${formatServingCount(foodProfileDisplayQuantity(equivalent.staple, equivalent.stapleServingCount))} ${quantityUnitText(equivalent.staple.servingType, equivalent.staple.unitLabel)} · ${foodProfileMacroText(equivalent.staple)}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF303747)
                                )
                                Text(
                                    text = "最喜欢的蛋白来源：${equivalent.proteinSource.name}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF161A23)
                                )
                                Text(
                                    text = "建议 ${formatServingCount(foodProfileDisplayQuantity(equivalent.proteinSource, equivalent.proteinServingCount))} ${quantityUnitText(equivalent.proteinSource.servingType, equivalent.proteinSource.unitLabel)} · ${foodProfileMacroText(equivalent.proteinSource)}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF303747)
                                )
                            }
                        }
                        Text(
                            text = "换算后合计：碳水 ${formatNumber(equivalent.totalCarbs)}g，蛋白质 ${formatNumber(equivalent.totalProtein)}g。",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF5B8DEF)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                Text(text = "知道了")
            }
        }
    )
}

@Composable
fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
        Text(text = label, fontSize = 13.sp, color = Color(0xFF8A92A1))
    }
}

@Composable
fun BmrReminderCard(
    basalMetabolism: Double,
    eatenCalories: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6D8))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "基础代谢提醒",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8A5A00)
            )
            Text(
                text = "当前已摄入 ${formatNumber(eatenCalories)} kcal，低于估算基础代谢 ${formatNumber(basalMetabolism)} kcal。",
                fontSize = 14.sp,
                color = Color(0xFF7A5A00)
            )
            Text(
                text = "这是中等提醒：如果当天长期明显吃不够基础代谢，可能影响状态、恢复和执行感受。",
                fontSize = 12.sp,
                color = Color(0xFF8A6A12)
            )
        }
    }
}

@Composable
fun ExerciseBurnCard(
    exerciseBurnRecord: ExerciseBurnRecord?,
    baseTargetCalories: Double,
    adjustedTargetCalories: Double,
    extraProtein: Double,
    extraCarbs: Double,
    onClick: () -> Unit
) {
    val exerciseCalories = exerciseBurnRecord?.calories ?: 0.0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "额外运动消耗",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF161A23)
                    )
                    Text(
                        text = if (exerciseCalories > 0) {
                            "今日额外消耗 ${formatNumber(exerciseCalories)} kcal，已自动加到当日额度"
                        } else {
                            "力量训练、有氧训练的额外消耗可以在这里补回"
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF6F7785)
                    )
                }
                OutlinedButton(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
                    Text(text = if (exerciseCalories > 0) "修改" else "设置", fontSize = 12.sp)
                }
            }
            Text(
                text = "补回公式：运动消耗热量补充 = 85% 碳水 + 15% 蛋白质。",
                fontSize = 12.sp,
                color = Color(0xFF8A5A00)
            )
            val record = exerciseBurnRecord
            if (record != null && exerciseCalories > 0) {
                Text(
                    text = "基础 ${formatNumber(baseTargetCalories)} kcal → 今日 ${formatNumber(adjustedTargetCalories)} kcal；额外碳水 +${formatNumber(extraCarbs.coerceAtLeast(0.0))}g，蛋白 +${formatNumber(extraProtein.coerceAtLeast(0.0))}g。",
                    fontSize = 12.sp,
                    color = Color(0xFF6F7785)
                )
                if (record.description.isNotBlank()) {
                    Text(
                        text = "描述：${record.description}",
                        fontSize = 12.sp,
                        color = Color(0xFF8A92A1)
                    )
                }
            }
        }
    }
}

@Composable
fun ExerciseBurnDialog(
    selectedDate: String,
    currentRecord: ExerciseBurnRecord?,
    textWorkAiSettings: AiSettings?,
    onDismiss: () -> Unit,
    onSave: (ExerciseBurnRecord) -> Unit,
    onClear: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var caloriesText by remember(currentRecord) { mutableStateOf(currentRecord?.calories?.takeIf { it > 0 }?.let(::formatNumber).orEmpty()) }
    var descriptionText by remember(currentRecord) { mutableStateOf(currentRecord?.description.orEmpty()) }
    var isEstimating by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val parsedCalories = parseOneDecimal(caloriesText)?.coerceAtLeast(0.0) ?: 0.0
    val extraCarbs = exerciseCarbsGrams(parsedCalories)
    val extraProtein = exerciseProteinGrams(parsedCalories)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "额外运动消耗", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "$selectedDate 的力量训练、有氧训练等额外运动消耗。AI 估算时会结合运动强度、时长、热量区间校验和运动后 EPOC，保存后会直接加到当天额度里。",
                    fontSize = 13.sp,
                    color = Color(0xFF6F7785)
                )
                AppTextField(
                    label = "运动消耗热量 kcal（可手动填写）",
                    value = caloriesText,
                    onValueChange = { caloriesText = it },
                    isNumber = true
                )
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it.take(400) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "运动描述（可选，用于 AI 估算）") },
                    placeholder = { Text(text = "例如：力量训练 60 分钟，深蹲卧推较多；跑步 30 分钟，配速 7 分钟/km") },
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp)
                )
                Text(
                    text = "补回公式：运动消耗热量补充 = 85% 碳水 + 15% 蛋白质。",
                    fontSize = 12.sp,
                    color = Color(0xFF8A5A00)
                )
                Text(
                    text = "当前会额外加入：热量 +${formatNumber(parsedCalories)} kcal，碳水 +${formatNumber(extraCarbs)}g，蛋白 +${formatNumber(extraProtein)}g，脂肪不变。",
                    fontSize = 12.sp,
                    color = Color(0xFF303747)
                )
                Button(
                    onClick = {
                        val settings = textWorkAiSettings
                        if (settings == null) {
                            errorText = "当前没有可用文字生成工作 AI，请先在 AI 设置里启用。"
                            return@Button
                        }
                        if (descriptionText.isBlank()) {
                            errorText = "请先输入运动描述，再让 AI 估算。"
                            return@Button
                        }
                        isEstimating = true
                        errorText = null
                        coroutineScope.launch {
                            val result = runCatching {
                                requestAiExerciseCalories(
                                    settings = settings,
                                    selectedDate = selectedDate,
                                    description = descriptionText
                                )
                            }
                            result.onSuccess { estimatedCalories ->
                                caloriesText = formatNumber(estimatedCalories.coerceAtLeast(0.0))
                            }.onFailure { error ->
                                errorText = friendlyAiReportErrorMessage(error)
                            }
                            isEstimating = false
                        }
                    },
                    enabled = !isEstimating,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = if (isEstimating) "AI 估算中..." else "用文字工作 AI 估算消耗", fontSize = 13.sp)
                }
                errorText?.let {
                    Text(text = it, fontSize = 12.sp, color = Color(0xFFEF5350))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ExerciseBurnRecord(
                            date = selectedDate,
                            calories = parsedCalories,
                            description = descriptionText.trim()
                        )
                    )
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentRecord != null) {
                    OutlinedButton(onClick = onClear, shape = RoundedCornerShape(14.dp)) {
                        Text(text = "清除")
                    }
                }
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                    Text(text = "取消")
                }
            }
        }
    )
}

@Composable
fun WaterSummaryCard(
    waterTargetMl: Double,
    drunkWaterMl: Double,
    waterRecords: List<WaterRecord>,
    onAddWater: (Double) -> Unit,
    onDeleteWater: (WaterRecord) -> Unit
) {
    val remainingWaterMl = waterTargetMl - drunkWaterMl
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "饮水进度",
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF161A23)
                )
                if (waterRecords.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { expanded = !expanded },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(text = if (expanded) "收起记录" else "展开记录", fontSize = 12.sp)
                    }
                }
            }
            Text(
                text = if (remainingWaterMl >= 0) {
                    "${formatNumber(drunkWaterMl)} / ${formatNumber(waterTargetMl)} ml（剩余 ${formatNumber(remainingWaterMl)} ml）"
                } else {
                    "${formatNumber(drunkWaterMl)} / ${formatNumber(waterTargetMl)} ml（超出 ${formatNumber(kotlin.math.abs(remainingWaterMl))} ml）"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (remainingWaterMl > 0) Color(0xFF29B6F6) else Color(0xFF26A69A)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFFE6EAF2))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(waterProgress(drunkWaterMl, waterTargetMl))
                        .height(8.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (remainingWaterMl > 0) Color(0xFF29B6F6) else Color(0xFF26A69A))
                )
            }
            Text(
                text = when {
                    waterTargetMl <= 0 -> "请先设置饮水目标。"
                    drunkWaterMl == 0.0 -> "今天还没有饮水记录，可以从少量多次开始。"
                    remainingWaterMl > 0 -> "继续保持，建议分多次补足剩余饮水。"
                    remainingWaterMl == 0.0 -> "刚好完成今日饮水目标。"
                    else -> "今日饮水已超过目标，保持舒适节奏即可。"
                },
                fontSize = 13.sp,
                color = Color(0xFF8A92A1)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { onAddWater(100.0) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(text = "+100ml", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { onAddWater(250.0) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(text = "+250ml", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { onAddWater(500.0) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(text = "+500ml", fontSize = 12.sp)
                }
            }
            if (waterRecords.isEmpty()) {
                Text(text = "今天还没有饮水记录", fontSize = 13.sp, color = Color(0xFF8A92A1))
            } else if (!expanded) {
                Text(text = "今天共 ${waterRecords.size} 条饮水记录", fontSize = 13.sp, color = Color(0xFF8A92A1))
            } else {
                waterRecords.forEach { record ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatNumber(record.amountMl)} ml",
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            color = Color(0xFF303747)
                        )
                        OutlinedButton(onClick = { onDeleteWater(record) }, shape = RoundedCornerShape(12.dp)) {
                            Text(text = "删除", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NutritionRow(
    label: String,
    value: String,
    color: Color,
    progress: Float? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    color = Color(0xFF303747)
                )
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF303747)
                )
            }
            progress?.let { rawProgress ->
                val safeProgress = rawProgress.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0xFFE6EAF2))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(safeProgress)
                            .height(8.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

@Composable
fun MealCard(
    name: String,
    calories: String,
    amountText: String = "",
    tagText: String = "",
    nutritionText: String = "",
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161A23))
                Spacer(modifier = Modifier.height(4.dp))
                if (tagText.isNotBlank()) {
                    Text(text = tagText, fontSize = 13.sp, color = Color(0xFF5B8DEF), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                }
                if (amountText.isNotBlank()) {
                    Text(text = amountText, fontSize = 13.sp, color = Color(0xFF6F7785))
                    Spacer(modifier = Modifier.height(2.dp))
                }
                if (nutritionText.isNotBlank()) {
                    Text(text = nutritionText, fontSize = 13.sp, color = Color(0xFF8A92A1))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = calories, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF5350))
                if (onEdit != null || onDelete != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onEdit != null) {
                            OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(14.dp)) {
                                Text(text = "编辑", fontSize = 12.sp)
                            }
                        }
                        if (onDelete != null) {
                            OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(14.dp)) {
                                Text(text = "删除", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WebStyleTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 10.dp),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color(0xFF161A23) else Color(0xFF7A8290)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (selected) 3.dp else 1.dp)
                .background(if (selected) Color(0xFF5B8DEF) else Color(0xFFD8DEE8))
        )
    }
}

@Composable
fun DateNavigatorCard(
    selectedDate: String,
    onPreviousDayClick: () -> Unit,
    onNextDayClick: () -> Unit,
    onTodayClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCCFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedDate == currentDateText()) "今天" else selectedDate,
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF161A23)
                )
                OutlinedButton(onClick = onHistoryClick, shape = RoundedCornerShape(14.dp)) {
                    Text(text = "历史")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onPreviousDayClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(text = "前一天")
                }
                OutlinedButton(onClick = onTodayClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(text = "今天")
                }
                OutlinedButton(onClick = onNextDayClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(text = "后一天")
                }
            }
        }
    }
}

@Composable
fun MonthNavigatorCard(
    visibleMonth: String,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onCurrentMonthClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCCFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "当前月份：$visibleMonth",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF161A23)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onPreviousMonthClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(text = "上月")
                }
                OutlinedButton(onClick = onCurrentMonthClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(text = "本月")
                }
                OutlinedButton(onClick = onNextMonthClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(text = "下月")
                }
            }
        }
    }
}

@Composable
fun MonthSummaryCard(
    visibleMonth: String,
    meals: List<MealRecord>,
    plans: List<NutritionPlan>,
    dailyPlanSelections: List<DailyPlanSelection>,
    exerciseBurnRecords: List<ExerciseBurnRecord>
) {
    val today = currentDateText()
    val recordedMealDates = meals
        .map { it.date }
        .filter { it.startsWith(visibleMonth) && it < today }
        .distinct()
    val totalDeficit = recordedMealDates.sumOf { date ->
        val plan = planForDate(date, plans, dailyPlanSelections)
        val baseTargetCalories = plan?.targetCalories ?: 0.0
        val exerciseCalories = exerciseBurnRecords.firstOrNull { it.date == date }?.calories ?: 0.0
        val adjustedTargetCalories = baseTargetCalories + exerciseCalories
        val consumedCalories = meals.filter { it.date == date }.sumOf { it.calories }
        val actualDeficit = if (adjustedTargetCalories > 0) adjustedTargetCalories - consumedCalories else 0.0
        (plan?.dailyCalorieDeficit ?: 0.0) + actualDeficit
    }
    val estimatedFatKg = totalDeficit / 7700.0
    val fatText = String.format(Locale.getDefault(), "%.2f kg", estimatedFatKg)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCCFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "$visibleMonth 月度汇总", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
            SummaryItem(label = "累计热量缺口", value = "${formatNumber(totalDeficit)} kcal")
            SummaryItem(label = "估算减少脂肪", value = fatText)
            Text(
                text = "只统计当前查看月份内、且早于今天并有饮食记录的日期。按 1 kg 脂肪约等于 7700 kcal 估算；未达到计划会增加缺口，超出计划会抵消缺口。",
                fontSize = 13.sp,
                color = Color(0xFF8A92A1)
            )
        }
    }
}

@Composable
fun CalendarMonthCard(
    visibleMonth: String,
    selectedDate: String,
    meals: List<MealRecord>,
    waterRecords: List<WaterRecord>,
    exerciseBurnRecords: List<ExerciseBurnRecord>,
    plans: List<NutritionPlan>,
    dailyPlanSelections: List<DailyPlanSelection>,
    onSelectDate: (String) -> Unit
) {
    val calendarDays = calendarCellsForMonth(visibleMonth)
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCCFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val ultraCompact = maxWidth < 318.dp
            val compact = maxWidth < 348.dp
            val cardPadding = when {
                ultraCompact -> 8.dp
                compact -> 10.dp
                else -> 14.dp
            }
            val gridSpacing = when {
                ultraCompact -> 2.dp
                compact -> 4.dp
                else -> 6.dp
            }
            val weekFontSize = when {
                ultraCompact -> 10.sp
                compact -> 11.sp
                else -> 12.sp
            }
            val contentSpacing = if (compact) 8.dp else 10.dp

            Column(modifier = Modifier.padding(cardPadding), verticalArrangement = Arrangement.spacedBy(contentSpacing)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gridSpacing)) {
                    weekLabels.forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            fontSize = weekFontSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF8A92A1)
                        )
                    }
                }
                calendarDays.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gridSpacing)) {
                        week.forEach { date ->
                            CalendarDayCell(
                                date = date,
                                visibleMonth = visibleMonth,
                                selectedDate = selectedDate,
                                meals = meals.filter { it.date == date },
                                waterMl = waterRecords.filter { it.date == date }.sumOf { it.amountMl },
                                hasExerciseRecord = exerciseBurnRecords.any { it.date == date },
                                targetCalories = targetCaloriesForDate(date, plans, dailyPlanSelections),
                                onClick = { onSelectDate(date) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayCell(
    date: String,
    visibleMonth: String,
    selectedDate: String,
    meals: List<MealRecord>,
    waterMl: Double,
    hasExerciseRecord: Boolean,
    targetCalories: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCurrentMonth = date.startsWith(visibleMonth)
    val isToday = date == currentDateText()
    val isSelected = date == selectedDate
    val calories = meals.sumOf { it.calories }
    val hasMeal = meals.isNotEmpty()
    val hasWater = waterMl > 0
    val hasExercise = hasExerciseRecord
    val statusColor = calorieStatusColor(calories, targetCalories)
    val backgroundColor = when {
        isSelected -> Color(0xFFE7F0FF)
        isToday -> Color(0xFFFFF3D8)
        isCurrentMonth -> Color(0xFFFFFFFF)
        else -> Color(0x66FFFFFF)
    }
    val borderColor = when {
        isSelected -> Color(0xFF5B8DEF)
        isToday -> Color(0xFFFFA726)
        else -> Color.Transparent
    }

    Card(
        modifier = modifier
            .height(88.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 4.dp)
        ) {
            val calorieText = formatNumber(calories)
            val ultraCompact = maxWidth < 36.dp
            val tinyCell = maxWidth < 39.dp
            val compactCell = maxWidth < 44.dp
            val dayFontSize = when {
                ultraCompact -> 9.sp
                tinyCell -> 10.sp
                compactCell -> 12.sp
                else -> 13.sp
            }
            val calorieFontSize = when {
                calorieText.length >= 4 && ultraCompact -> 6.sp
                calorieText.length >= 4 && tinyCell -> 6.5.sp
                calorieText.length >= 4 -> 7.5.sp
                ultraCompact -> 6.5.sp
                tinyCell -> 7.sp
                compactCell -> 8.sp
                else -> 9.sp
            }
            val topBottomSpacing = if (ultraCompact) 0.dp else 1.dp
            val calorieLineHeight = when {
                ultraCompact -> 7.sp
                tinyCell -> 8.sp
                compactCell -> 9.sp
                else -> 10.sp
            }
            val indicatorGap = if (ultraCompact) 1.dp else 2.dp
            val indicatorHeight = when {
                ultraCompact -> 11.dp
                tinyCell -> 12.dp
                compactCell -> 13.dp
                else -> 14.dp
            }
            val indicatorDotSize = when {
                ultraCompact -> 6.dp
                tinyCell -> 6.5.dp
                compactCell -> 7.dp
                else -> 8.dp
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = date.takeLast(2).trimStart('0').ifBlank { "1" },
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = dayFontSize,
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = if (isCurrentMonth) Color(0xFF161A23) else Color(0xFFB4BBC8)
                )
                Column(verticalArrangement = Arrangement.spacedBy(topBottomSpacing)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (ultraCompact) 11.dp else 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasMeal) {
                            Text(
                                text = calorieText,
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = calorieFontSize,
                                lineHeight = calorieLineHeight,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(indicatorGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CalendarIndicatorChip(
                            label = "椋?",
                            active = hasMeal,
                            activeColor = Color(0xFF5B8DEF),
                            height = indicatorHeight,
                            dotSize = indicatorDotSize,
                            modifier = Modifier.weight(1f)
                        )
                        CalendarIndicatorChip(
                            label = "姘?",
                            active = hasWater,
                            activeColor = Color(0xFF12B5FF),
                            height = indicatorHeight,
                            dotSize = indicatorDotSize,
                            modifier = Modifier.weight(1f)
                        )
                        CalendarIndicatorChip(
                            label = "杩?",
                            active = hasExercise,
                            activeColor = Color(0xFFFF8A3D),
                            height = indicatorHeight,
                            dotSize = indicatorDotSize,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun CalendarIndicatorChip(
    label: String,
    active: Boolean,
    activeColor: Color,
    height: androidx.compose.ui.unit.Dp,
    dotSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val containerColor = if (active) activeColor.copy(alpha = 0.12f) else Color(0xFFF2F5FA)
    val dotColor = if (active) activeColor else Color(0xFFC9D3E2)
    val dotBorderColor = if (active) activeColor.copy(alpha = 0.45f) else Color(0xFFB8C4D5)

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
                .border(BorderStroke(1.dp, dotBorderColor), CircleShape)
        )
    }
}

@Composable
fun CalendarDot(visible: Boolean, color: Color, size: androidx.compose.ui.unit.Dp = 7.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (visible) color else Color(0xFFDDE3EF))
    )
}

@Composable
fun CalendarLegendCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CalendarLegendItem(color = Color(0xFF5B8DEF), text = "饮食")
            CalendarLegendItem(color = Color(0xFF29B6F6), text = "饮水")
            CalendarLegendItem(color = Color(0xFFFF8A50), text = "运动")
        }
    }
}

@Composable
fun CalendarLegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CalendarDot(visible = true, color = color)
        Text(text = text, fontSize = 12.sp, color = Color(0xFF6F7785))
    }
}

@Composable
fun EmptyMonthCard(visibleMonth: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "$visibleMonth 暂无记录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161A23))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "切换月份或返回首页添加饮食、饮水记录。", fontSize = 13.sp, color = Color(0xFF8A92A1))
        }
    }
}

@Composable
fun HistoryDateCard(
    date: String,
    isSelected: Boolean,
    meals: List<MealRecord>,
    waterMl: Double,
    planStatusText: String,
    targetCalories: Double?,
    onClick: () -> Unit
) {
    val calories = meals.sumOf { it.calories }
    val protein = meals.sumOf { it.protein }
    val fat = meals.sumOf { it.fat }
    val carbs = meals.sumOf { it.carbs }
    val calorieStatusText = calorieStatusText(calories, targetCalories)
    val calorieStatusColor = calorieStatusColor(calories, targetCalories)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFE7F0FF) else Color(0xBFFFFFFF))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = if (date == currentDateText()) "$date · 今天" else date, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
                    Text(text = "${meals.size} 条饮食 · $planStatusText", fontSize = 13.sp, color = if (planStatusText == "计划出错") Color(0xFFEF5350) else Color(0xFF8A92A1))
                    Text(text = calorieStatusText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = calorieStatusColor)
                }
                Button(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
                    Text(text = "查看")
                }
            }
            Text(text = "${formatNumber(calories)} kcal · 蛋白${formatNumber(protein)}g 脂肪${formatNumber(fat)}g 碳水${formatNumber(carbs)}g · 饮水${formatNumber(waterMl)}ml", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
        }
    }
}

@Composable
fun EmptyHistoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "暂无历史记录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161A23))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "添加饮食记录后，这里会按日期显示汇总。", fontSize = 13.sp, color = Color(0xFF8A92A1))
        }
    }
}

@Composable
fun QuickPickSection(
    foods: List<FoodItem>,
    onPick: (FoodItem) -> Unit
) {
    var quickSearchText by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val matchedFoods = if (quickSearchText.isBlank()) foods else {
        foods.filter { it.name.contains(quickSearchText.trim(), ignoreCase = true) }
    }
    val displayFoods = if (showAll) matchedFoods else matchedFoods.take(5)

    FormCard {
        Text(text = "从食物库快速选择", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161A23))
        AppTextField(
            label = "搜索食物库",
            value = quickSearchText,
            onValueChange = { quickSearchText = it }
        )
        if (matchedFoods.isEmpty()) {
            Text(text = "没有匹配的食物", fontSize = 13.sp, color = Color(0xFF8A92A1))
        } else {
            displayFoods.forEach { food ->
                FoodQuickPickRow(food = food, onClick = { onPick(food) })
            }
            if (!showAll && matchedFoods.size > 5) {
                OutlinedButton(
                    onClick = { showAll = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = "展开全部（${matchedFoods.size} 项）", fontSize = 13.sp)
                }
            } else if (showAll && matchedFoods.size > 5) {
                OutlinedButton(
                    onClick = { showAll = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = "收起", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun FoodQuickPickRow(
    food: FoodItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = food.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161A23))
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${formatNumber(food.calories)} kcal · ${foodServingText(food)} · 碳水${formatNumber(food.carbs)}g 蛋白${formatNumber(food.protein)}g 脂肪${formatNumber(food.fat)}g",
                    fontSize = 12.sp,
                    color = Color(0xFF8A92A1)
                )
            }
            OutlinedButton(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
                Text(text = "选择", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun QuickAddFoodDialog(
    foods: List<FoodItem>,
    tags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (FoodItem, Double, List<String>) -> Unit
) {
    var selectedFood by remember(foods) { mutableStateOf(foods.firstOrNull()) }
    var quantityText by remember(selectedFood) {
        mutableStateOf(
            selectedFood?.let { if (it.servingType == ServingType.PerItem) "1" else "100" }.orEmpty()
        )
    }
    var tagText by remember(selectedFood) { mutableStateOf("") }
    var errorText by remember(selectedFood) { mutableStateOf<String?>(null) }
    val food = selectedFood
    val quantity = quantityText.toDoubleOrNull() ?: 0.0
    val servingCount = food?.let { servingCountFromQuantity(quantity, it.servingType) } ?: 0.0
    val previewProtein = food?.let { roundOneDecimal(it.protein * servingCount) } ?: 0.0
    val previewFat = food?.let { roundOneDecimal(it.fat * servingCount) } ?: 0.0
    val previewCarbs = food?.let { roundOneDecimal(it.carbs * servingCount) } ?: 0.0
    val previewCalories = calculateCalories(previewProtein, previewFat, previewCarbs)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "快速添加", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "选择食物", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                foods.forEach { item ->
                    OutlinedButton(
                        onClick = {
                            selectedFood = item
                            errorText = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "${if (item == selectedFood) "✓ " else ""}${item.name} · ${formatNumber(item.calories)} kcal/${foodServingText(item)}",
                            fontSize = 12.sp
                        )
                    }
                }
                if (food != null) {
                    AppTextField(
                        label = if (food.servingType == ServingType.PerItem) "食物总量（${food.unitLabel.ifBlank { "个" }}）" else "食物总量（克）",
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        isNumber = true
                    )
                    Text(text = "标签", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                    tags.take(7).chunked(3).forEach { rowTags ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTags.forEach { tag ->
                                OutlinedButton(
                                onClick = {
                                    val existingTags = tagText
                                        .split(";", "；", "\n")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                    tagText = toggleSelectableTag(existingTags, tag).joinToString(";")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                                ) {
                                    val currentTags = tagText
                                        .split(";", "；", "\n")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                    Text(
                                        text = if (currentTags.contains(tag)) "✓ $tag" else tag,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            repeat(3 - rowTags.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    AppTextField(
                        label = "输入标签后用分号分隔",
                        value = tagText,
                        onValueChange = { tagText = it }
                    )
                    Text(
                        text = "预计添加：${formatNumber(previewCalories)} kcal · 碳水${formatNumber(previewCarbs)}g 蛋白${formatNumber(previewProtein)}g 脂肪${formatNumber(previewFat)}g",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF303747)
                    )
                }
                errorText?.let { Text(text = it, color = Color(0xFFEF5350), fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val currentFood = selectedFood
                    val parsedQuantity = quantityText.toDoubleOrNull() ?: 0.0
                    val parsedTags = tagText
                        .split(";", "；", "\n")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .let(::normalizeSelectableTags)
                    when {
                        currentFood == null -> errorText = "请选择食物"
                        parsedQuantity <= 0.0 -> errorText = "请输入有效总量"
                        parsedTags.isEmpty() -> errorText = "请至少选择或输入一个标签"
                        else -> onConfirm(currentFood, parsedQuantity, parsedTags)
                    }
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "添加")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                Text(text = "取消")
            }
        }
    )
}

@Composable
fun CategorySelectorRow(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "分类", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
        categories.chunked(3).forEach { rowCategories ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCategories.forEach { category ->
                    OutlinedButton(
                        onClick = { onSelectCategory(category) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (category == selectedCategory) "✓ $category" else category,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
                repeat(3 - rowCategories.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun FoodLibraryCard(
    food: FoodItem,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = food.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
            Text(
                text = "${food.category} · ${formatNumber(food.calories)} kcal · ${foodServingText(food)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFEF5350)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = "编辑", fontSize = 12.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onHide,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = "隐藏", fontSize = 12.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(text = "删除", fontSize = 12.sp, maxLines = 1)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            NutritionRow(label = "碳水", value = "${formatNumber(food.carbs)} g", color = Color(0xFF66BB6A))
            NutritionRow(label = "蛋白质", value = "${formatNumber(food.protein)} g", color = Color(0xFF5B8DEF))
            NutritionRow(label = "脂肪", value = "${formatNumber(food.fat)} g", color = Color(0xFFFFA726))
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onUse,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = "添加到今日记录")
            }
        }
    }
}

@Composable
fun FoodUseDialog(
    food: FoodItem,
    tags: List<String>,
    quantityText: String,
    tagText: String,
    onQuantityChange: (String) -> Unit,
    onTagTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Double, List<String>) -> Unit
) {
    var errorText by remember(food) { mutableStateOf<String?>(null) }
    val quantity = quantityText.toDoubleOrNull() ?: 0.0
    val servingCount = servingCountFromQuantity(quantity, food.servingType)
    val previewProtein = roundOneDecimal(food.protein * servingCount)
    val previewFat = roundOneDecimal(food.fat * servingCount)
    val previewCarbs = roundOneDecimal(food.carbs * servingCount)
    val previewCalories = calculateCalories(previewProtein, previewFat, previewCarbs)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "添加到今日记录", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = food.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161A23))
                Text(
                    text = "${foodServingText(food)}：${formatNumber(food.calories)} kcal · 碳水${formatNumber(food.carbs)}g 蛋白${formatNumber(food.protein)}g 脂肪${formatNumber(food.fat)}g",
                    fontSize = 13.sp,
                    color = Color(0xFF6F7785)
                )
                AppTextField(
                        label = if (food.servingType == ServingType.PerItem) "食物总量（${food.unitLabel.ifBlank { "个" }}）" else "食物总量（克）",
                    value = quantityText,
                    onValueChange = onQuantityChange,
                    isNumber = true
                )
                Text(text = "标签", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                if (tags.isNotEmpty()) {
                    tags.take(7).chunked(3).forEach { rowTags ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowTags.forEach { tag ->
                                OutlinedButton(
                                    onClick = {
                                        val existingTags = tagText
                                            .split(";", "；", "\n")
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                        onTagTextChange(
                                            toggleSelectableTag(existingTags, tag).joinToString(";")
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    val currentTags = tagText
                                        .split(";", "；", "\n")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                    Text(
                                        text = if (currentTags.contains(tag)) "✓ $tag" else tag,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            repeat(3 - rowTags.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                AppTextField(
                    label = "输入标签后用分号分隔，例如：早餐;训练后;低脂",
                    value = tagText,
                    onValueChange = onTagTextChange
                )
                Text(
                    text = "预计添加：${formatNumber(previewCalories)} kcal · 碳水${formatNumber(previewCarbs)}g 蛋白${formatNumber(previewProtein)}g 脂肪${formatNumber(previewFat)}g",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF303747)
                )
                errorText?.let {
                    Text(text = it, color = Color(0xFFEF5350), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedQuantity = quantityText.toDoubleOrNull() ?: 0.0
                    val parsedTags = tagText
                        .split(";", "；", "\n")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .let(::normalizeSelectableTags)
                    if (parsedQuantity <= 0.0) {
                        errorText = if (food.servingType == ServingType.PerItem) "请输入有效个数" else "请输入有效克数"
                        return@Button
                    }
                    if (parsedTags.isEmpty()) {
                        errorText = "请至少选择或输入一个标签"
                        return@Button
                    }
                    onConfirm(parsedQuantity, parsedTags)
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "添加")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                Text(text = "取消")
            }
        }
    )
}

@Composable
fun FoodEditorDialog(
    initialFood: FoodItem,
    onDismiss: () -> Unit,
    onSave: (FoodItem) -> Unit
) {
    var name by remember { mutableStateOf(initialFood.name) }
    var carbs by remember { mutableStateOf(formatNumber(initialFood.carbs)) }
    var protein by remember { mutableStateOf(formatNumber(initialFood.protein)) }
    var fat by remember { mutableStateOf(formatNumber(initialFood.fat)) }
    var servingType by remember { mutableStateOf(initialFood.servingType) }
    var unitLabel by remember { mutableStateOf(initialFood.unitLabel.takeIf { it.isNotBlank() } ?: "个") }
    var category by remember { mutableStateOf(if (initialFood.category in defaultFoodCategories()) initialFood.category else "自定义") }
    var customCategory by remember { mutableStateOf(if (initialFood.category in defaultFoodCategories()) "" else initialFood.category) }
    var pendingType by remember { mutableStateOf<ServingType?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    pendingType?.let { targetType ->
        ConfirmDialog(
            title = "切换每份类型",
            message = "切换“每个 / 每100g”会清空当前营养素数据，是否继续？",
            confirmText = "继续",
            onConfirm = {
                servingType = targetType
                carbs = ""
                protein = ""
                fat = ""
                pendingType = null
            },
            onDismiss = { pendingType = null }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "编辑食物", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(label = "食物名称", value = name, onValueChange = { name = it })
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (servingType != ServingType.PerItem) pendingType = ServingType.PerItem
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = if (servingType == ServingType.PerItem) "✓ 每个" else "每个")
                    }
                    OutlinedButton(
                        onClick = {
                            if (servingType != ServingType.Per100g) pendingType = ServingType.Per100g
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = if (servingType == ServingType.Per100g) "✓ 每100g" else "每100g")
                    }
                }
                CategorySelectorRow(
                    categories = defaultFoodCategories(),
                    selectedCategory = category,
                    onSelectCategory = { category = it }
                )
                if (servingType == ServingType.PerItem) {
                    AppTextField(
                        label = "量词（可选，不填默认为个）",
                        value = if (unitLabel == "个") "" else unitLabel,
                        onValueChange = { unitLabel = it.trim().ifBlank { "个" } }
                    )
                }
                if (category == "自定义") {
                    AppTextField(
                        label = "自定义分类名称",
                        value = customCategory,
                        onValueChange = { customCategory = it }
                    )
                }
                AppTextField(label = "${servingTypeLabel(servingType, unitLabel)}碳水 g", value = carbs, onValueChange = { carbs = it }, isNumber = true)
                AppTextField(label = "${servingTypeLabel(servingType, unitLabel)}蛋白质 g", value = protein, onValueChange = { protein = it }, isNumber = true)
                AppTextField(label = "${servingTypeLabel(servingType, unitLabel)}脂肪 g", value = fat, onValueChange = { fat = it }, isNumber = true)
                errorText?.let {
                    Text(text = it, color = Color(0xFFEF5350), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedCarbs = parseOneDecimal(carbs)
                    val parsedProtein = parseOneDecimal(protein)
                    val parsedFat = parseOneDecimal(fat)

                    if (name.isBlank()) {
                        errorText = "请输入食物名称"
                        return@Button
                    }
                    if (parsedCarbs == null || parsedProtein == null || parsedFat == null) {
                        errorText = "请输入有效营养素"
                        return@Button
                    }
                    if (parsedCarbs < 0 || parsedProtein < 0 || parsedFat < 0) {
                        errorText = "营养素不能为负数"
                        return@Button
                    }
                    val finalCategory = if (category == "自定义") {
                        customCategory.trim().ifBlank { "自定义" }
                    } else {
                        category
                    }

                    onSave(
                        FoodItem(
                            name = name.trim(),
                            carbs = parsedCarbs,
                            protein = parsedProtein,
                            fat = parsedFat,
                            servingType = servingType,
                            category = finalCategory,
                            unitLabel = normalizedUnitLabel(servingType, unitLabel),
                            isHidden = initialFood.isHidden
                        )
                    )
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                Text(text = "取消")
            }
        }
    )
}

@Composable
fun FoodEditorResponsiveDialog(
    initialFood: FoodItem,
    onDismiss: () -> Unit,
    onSave: (FoodItem) -> Unit
) {
    val configuration = LocalConfiguration.current
    var name by remember { mutableStateOf(initialFood.name) }
    var carbs by remember { mutableStateOf(formatNumber(initialFood.carbs)) }
    var protein by remember { mutableStateOf(formatNumber(initialFood.protein)) }
    var fat by remember { mutableStateOf(formatNumber(initialFood.fat)) }
    var servingType by remember { mutableStateOf(initialFood.servingType) }
    var unitLabel by remember { mutableStateOf(initialFood.unitLabel.takeIf { it.isNotBlank() } ?: "个") }
    var category by remember { mutableStateOf(if (initialFood.category in defaultFoodCategories()) initialFood.category else "自定义") }
    var customCategory by remember { mutableStateOf(if (initialFood.category in defaultFoodCategories()) "" else initialFood.category) }
    var pendingType by remember { mutableStateOf<ServingType?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    pendingType?.let { targetType ->
        ConfirmDialog(
            title = "切换每份类型",
            message = "切换“每个/每100g”会清空当前营养素数据，是否继续？",
            confirmText = "继续",
            onConfirm = {
                servingType = targetType
                carbs = ""
                protein = ""
                fat = ""
                pendingType = null
            },
            onDismiss = { pendingType = null }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .heightIn(max = configuration.screenHeightDp.dp * 0.9f)
                .imePadding(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBFF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "编辑食物", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color(0xFF161A23))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AppTextField(label = "食物名称", value = name, onValueChange = { name = it })
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (servingType != ServingType.PerItem) pendingType = ServingType.PerItem
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(text = if (servingType == ServingType.PerItem) "✓ 每个" else "每个")
                        }
                        OutlinedButton(
                            onClick = {
                                if (servingType != ServingType.Per100g) pendingType = ServingType.Per100g
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(text = if (servingType == ServingType.Per100g) "✓ 每100g" else "每100g")
                        }
                    }
                    CategorySelectorRow(
                        categories = defaultFoodCategories(),
                        selectedCategory = category,
                        onSelectCategory = { category = it }
                    )
                    if (servingType == ServingType.PerItem) {
                        AppTextField(
                            label = "量词（可选，不填默认是个）",
                            value = if (unitLabel == "个") "" else unitLabel,
                            onValueChange = { unitLabel = it.trim().ifBlank { "个" } }
                        )
                    }
                    if (category == "自定义") {
                        AppTextField(
                            label = "自定义分类名称",
                            value = customCategory,
                            onValueChange = { customCategory = it }
                        )
                    }
                    AppTextField(label = "${servingTypeLabel(servingType, unitLabel)}碳水 g", value = carbs, onValueChange = { carbs = it }, isNumber = true)
                    AppTextField(label = "${servingTypeLabel(servingType, unitLabel)}蛋白质 g", value = protein, onValueChange = { protein = it }, isNumber = true)
                    AppTextField(label = "${servingTypeLabel(servingType, unitLabel)}脂肪 g", value = fat, onValueChange = { fat = it }, isNumber = true)
                    errorText?.let {
                        Text(text = it, color = Color(0xFFEF5350), fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = "取消")
                    }
                    Button(
                        onClick = {
                            val parsedCarbs = parseOneDecimal(carbs)
                            val parsedProtein = parseOneDecimal(protein)
                            val parsedFat = parseOneDecimal(fat)

                            if (name.isBlank()) {
                                errorText = "请输入食物名称"
                                return@Button
                            }
                            if (parsedCarbs == null || parsedProtein == null || parsedFat == null) {
                                errorText = "请输入有效营养素"
                                return@Button
                            }
                            if (parsedCarbs < 0 || parsedProtein < 0 || parsedFat < 0) {
                                errorText = "营养素不能为负数"
                                return@Button
                            }
                            val finalCategory = if (category == "自定义") {
                                customCategory.trim().ifBlank { "自定义" }
                            } else {
                                category
                            }

                            onSave(
                                FoodItem(
                                    name = name.trim(),
                                    carbs = parsedCarbs,
                                    protein = parsedProtein,
                                    fat = parsedFat,
                                    servingType = servingType,
                                    category = finalCategory,
                                    unitLabel = normalizedUnitLabel(servingType, unitLabel),
                                    isHidden = initialFood.isHidden
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = "保存")
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyMealCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "今天还没有记录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161A23))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "点击下方按钮添加第一餐。", fontSize = 13.sp, color = Color(0xFF8A92A1))
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = { Text(text = message) },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(14.dp)) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                Text(text = "取消")
            }
        }
    )
}

fun calculateCalories(protein: Double, fat: Double, carbs: Double): Double {
    return roundOneDecimal(protein * 4 + fat * 9 + carbs * 4)
}

fun parseOneDecimal(text: String): Double? {
    return text.trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()?.let(::roundOneDecimal)
}

fun roundOneDecimal(value: Double): Double {
    return kotlin.math.round(value * 10.0) / 10.0
}

fun formatNumber(value: Double): String {
    val rounded = roundOneDecimal(value)
    return if (kotlin.math.abs(rounded - rounded.toLong()) < 0.0001) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", rounded)
    }
}

fun sanitizeOneDecimalInput(raw: String): String {
    val normalized = raw.replace('，', '.')
    val builder = StringBuilder()
    var hasDecimalPoint = false
    var decimalDigits = 0
    normalized.forEach { char ->
        when {
            char.isDigit() && !hasDecimalPoint -> builder.append(char)
            char.isDigit() && decimalDigits < 1 -> {
                builder.append(char)
                decimalDigits += 1
            }
            char == '.' && !hasDecimalPoint -> {
                if (builder.isEmpty()) builder.append('0')
                builder.append('.')
                hasDecimalPoint = true
            }
        }
    }
    return builder.toString()
}

fun defaultFoodCategories(): List<String> {
    return listOf("主食", "肉蛋奶", "蔬菜", "水果", "饮品", "零食", "调味品", "自定义", "AI 识别")
}

fun defaultPresetFoods(): List<FoodItem> {
    return listOf(
        FoodItem(
            name = "熟米饭",
            servingType = ServingType.Per100g,
            carbs = 25.9,
            protein = 2.6,
            fat = 0.3,
            category = "主食"
        ),
        FoodItem(
            name = "鸡蛋（全蛋）",
            servingType = ServingType.PerItem,
            carbs = 1.0,
            protein = 6.6,
            fat = 4.4,
            category = "肉蛋奶"
        ),
        FoodItem(
            name = "鸡蛋（蛋白）",
            servingType = ServingType.PerItem,
            carbs = 0.6,
            protein = 3.5,
            fat = 0.0,
            category = "肉蛋奶"
        )
    )
}

fun preset(provider: String, baseUrl: String, model: String, vision: Boolean = true): AiSettings {
    return AiSettings(providerName = provider, baseUrl = baseUrl, modelName = model, supportsVision = vision)
}

fun defaultAiPresetSections(): List<AiPresetSection> {
    val openAi = "https://api.openai.com/v1/chat/completions"
    val anthropic = "https://api.anthropic.com/v1/messages"
    val gemini = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    val openRouter = "https://openrouter.ai/api/v1/chat/completions"
    val dashScope = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    val deepSeek = "https://api.deepseek.com/v1/chat/completions"
    val kimi = "https://api.moonshot.cn/v1/chat/completions"
    val zhipu = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    val placeholder = "https://example.com/v1/chat/completions"

    return listOf(
        AiPresetSection(
            sectionName = "国外热门模型",
            groups = listOf(
                AiPresetGroup("OpenAI", listOf(
                    preset("OpenAI", openAi, "gpt-5.2"),
                    preset("OpenAI", openAi, "gpt-5.1"),
                    preset("OpenAI", openAi, "gpt-5"),
                    preset("OpenAI", openAi, "gpt-5-mini"),
                    preset("OpenAI", openAi, "gpt-5-nano"),
                    preset("OpenAI", openAi, "gpt-4o"),
                    preset("OpenAI", openAi, "gpt-4o-mini"),
                    preset("OpenAI", openAi, "o3", vision = false),
                    preset("OpenAI", openAi, "o3-mini", vision = false),
                    preset("OpenAI", openAi, "o4-mini", vision = false),
                    preset("OpenAI", openAi, "gpt-4.1"),
                    preset("OpenAI", openAi, "gpt-4.1-mini")
                )),
                AiPresetGroup("Anthropic", listOf(
                    preset("Anthropic", anthropic, "claude-sonnet-4-20250514"),
                    preset("Anthropic", anthropic, "claude-opus-4-1-20250805"),
                    preset("Anthropic", anthropic, "claude-opus-4-20250514"),
                    preset("Anthropic", anthropic, "claude-3-7-sonnet-20250219"),
                    preset("Anthropic", anthropic, "claude-3-5-sonnet-20241022"),
                    preset("Anthropic", anthropic, "claude-3-5-haiku-20241022")
                )),
                AiPresetGroup("Google DeepMind", listOf(
                    preset("Google Gemini", gemini, "gemini-1.5-pro"),
                    preset("Google Gemini", gemini, "gemini-1.5-flash"),
                    preset("Google Gemini", gemini, "gemini-2.0-flash"),
                    preset("Google Gemini", gemini, "gemini-2.5-pro"),
                    preset("Google Gemini", gemini, "gemini-2.5-flash"),
                    preset("Google Gemini", gemini, "gemini-2.5-flash-lite")
                )),
                AiPresetGroup("Meta", listOf(
                    preset("Meta Llama", openRouter, "meta-llama/llama-3.2-vision"),
                    preset("Meta Llama", openRouter, "meta-llama/llama-4-scout-vision"),
                    preset("Meta Llama", openRouter, "meta-llama/llama-4-maverick-vision"),
                    preset("Meta Llama", openRouter, "meta-llama/llama-3.1-70b-instruct", vision = false),
                    preset("Meta Llama", openRouter, "meta-llama/llama-3.1-405b-instruct", vision = false)
                )),
                AiPresetGroup("Microsoft", listOf(
                    preset("Microsoft Azure", "https://YOUR_AZURE_RESOURCE.openai.azure.com/openai/deployments/YOUR_DEPLOYMENT/chat/completions", "azure-gpt-4o"),
                    preset("Microsoft Phi", openRouter, "microsoft/phi-3-vision"),
                    preset("Microsoft Phi", openRouter, "microsoft/phi-4-vision")
                ))
            )
        ),
        AiPresetSection(
            sectionName = "国内热门模型",
            groups = listOf(
                AiPresetGroup("百度 文心 ERNIE", listOf(
                    preset("Baidu ERNIE", placeholder, "ernie-4.5-vision"),
                    preset("Baidu ERNIE", placeholder, "ernie-5.0-ultra-multimodal"),
                    preset("Baidu ERNIE", placeholder, "ernie-6.0-vision"),
                    preset("Baidu ERNIE", placeholder, "ernie-4.5-turbo-vl")
                )),
                AiPresetGroup("阿里巴巴 通义 Qwen", listOf(
                    preset("Alibaba Qwen", dashScope, "qwen-4.0-vl"),
                    preset("Alibaba Qwen", dashScope, "qwen-5.0-vl"),
                    preset("Alibaba Qwen", dashScope, "qwen2-vl"),
                    preset("Alibaba Qwen", dashScope, "qwen2.5-vl"),
                    preset("Alibaba Qwen", dashScope, "qwen3-vl"),
                    preset("Alibaba Qwen", dashScope, "qwen-vl-plus"),
                    preset("Alibaba Qwen", dashScope, "qwen-vl-max"),
                    preset("Alibaba Qwen", dashScope, "qwen-turbo", vision = false),
                    preset("Alibaba Qwen", dashScope, "qwen-plus", vision = false),
                    preset("Alibaba Qwen", dashScope, "qwen-max", vision = false),
                    preset("Alibaba Qwen", dashScope, "qwen-long", vision = false)
                )),
                AiPresetGroup("腾讯 混元 Hunyuan", listOf(
                    preset("Tencent Hunyuan", placeholder, "hunyuan-4.0-vision"),
                    preset("Tencent Hunyuan", placeholder, "hunyuan-5.0-vision"),
                    preset("Tencent Hunyuan", placeholder, "hunyuan-vl-3.0")
                )),
                AiPresetGroup("字节跳动 豆包 Doubao", listOf(
                    preset("ByteDance Doubao", placeholder, "doubao-4.0-vision"),
                    preset("ByteDance Doubao", placeholder, "doubao-5.0-vision"),
                    preset("ByteDance Doubao", placeholder, "doubao-seed-1.6-vision"),
                    preset("ByteDance Doubao", placeholder, "seallm-vl-3.0")
                )),
                AiPresetGroup("华为 盘古 Pangu", listOf(
                    preset("Huawei Pangu", placeholder, "pangu-4.0-vision"),
                    preset("Huawei Pangu", placeholder, "pangu-5.0-vision"),
                    preset("Huawei Pangu", placeholder, "pangu-vl-3.0")
                )),
                AiPresetGroup("科大讯飞 星火", listOf(
                    preset("iFlytek Spark", placeholder, "spark-4.0-vision"),
                    preset("iFlytek Spark", placeholder, "spark-5.0-ocr-vision"),
                    preset("iFlytek Spark", placeholder, "spark-vl-light")
                )),
                AiPresetGroup("商汤 日日新", listOf(
                    preset("SenseTime SenseNova", placeholder, "sensenova-4.0-vision"),
                    preset("SenseTime SenseNova", placeholder, "sensenova-5.0-vision")
                )),
                AiPresetGroup("智谱AI GLM", listOf(
                    preset("Zhipu GLM", zhipu, "glm-4v"),
                    preset("Zhipu GLM", zhipu, "glm-4v-plus"),
                    preset("Zhipu GLM", zhipu, "glm-5v-turbo"),
                    preset("Zhipu GLM", zhipu, "glm-4.5", vision = false),
                    preset("Zhipu GLM", zhipu, "glm-4.5-air", vision = false)
                )),
                AiPresetGroup("月之暗面 Moonshot Kimi", listOf(
                    preset("Moonshot Kimi", kimi, "kimi-4.0-multimodal"),
                    preset("Moonshot Kimi", kimi, "kimi-k2.6"),
                    preset("Moonshot Kimi", kimi, "moonshot-v1-8k", vision = false),
                    preset("Moonshot Kimi", kimi, "moonshot-v1-32k", vision = false),
                    preset("Moonshot Kimi", kimi, "moonshot-v1-128k", vision = false),
                    preset("Moonshot Kimi", kimi, "kimi-k2", vision = false)
                )),
                AiPresetGroup("深度求索 DeepSeek", listOf(
                    preset("DeepSeek", deepSeek, "deepseek-vl-2.0"),
                    preset("DeepSeek", deepSeek, "deepseek-vl-3.0"),
                    preset("DeepSeek", deepSeek, "deepseek-chat", vision = false),
                    preset("DeepSeek", deepSeek, "deepseek-reasoner", vision = false)
                )),
                AiPresetGroup("MiniMax", listOf(
                    preset("MiniMax", placeholder, "abab-4.0-vision"),
                    preset("MiniMax", placeholder, "abab-5.0-vision")
                )),
                AiPresetGroup("阶跃星辰 StepFun", listOf(
                    preset("StepFun", placeholder, "step-4.0-vision"),
                    preset("StepFun", placeholder, "stepfun-vl-3.0")
                )),
                AiPresetGroup("澜舟科技", listOf(
                    preset("Lanzhou Mengzi", placeholder, "mengzi-vl-2.0"),
                    preset("Lanzhou Mengzi", placeholder, "mengzi-5.0-vision")
                ))
            )
        ),
        AiPresetSection(
            sectionName = "国内冷门模型",
            groups = listOf(
                AiPresetGroup("上海AI实验室 书生浦语 InternVL", listOf(
                    preset("InternVL", placeholder, "internvl-2.5"),
                    preset("InternVL", placeholder, "internvl-3.0"),
                    preset("InternVL", placeholder, "internvl-3.5")
                )),
                AiPresetGroup("其他国产开源视觉模型", listOf(
                    preset("01.AI Yi", placeholder, "yi-vl-3.0"),
                    preset("Mini-VL", placeholder, "mini-vl-edge"),
                    preset("Llama CN", placeholder, "llama3.2-vl-cn")
                ))
            )
        ),
        AiPresetSection(
            sectionName = "国外冷门模型",
            groups = listOf(
                AiPresetGroup("Mistral AI", listOf(
                    preset("Mistral AI", openRouter, "mistral-large-2-vision"),
                    preset("Mistral AI", openRouter, "mistral-large-3-vision"),
                    preset("Mistral AI", openRouter, "mistralai/mistral-large", vision = false),
                    preset("Mistral AI", openRouter, "mistralai/mixtral-8x22b-instruct", vision = false),
                    preset("Mistral AI", openRouter, "mistralai/codestral", vision = false)
                )),
                AiPresetGroup("Cohere", listOf(
                    preset("Cohere", placeholder, "command-r-plus-vision"),
                    preset("Cohere", placeholder, "command-r-ultra-vision"),
                    preset("Cohere", placeholder, "command-r-plus", vision = false),
                    preset("Cohere", placeholder, "command-r", vision = false)
                )),
                AiPresetGroup("xAI Grok", listOf(
                    preset("xAI", placeholder, "grok-2-vision"),
                    preset("xAI", placeholder, "grok-3-vision")
                ))
            )
        )
    )
}

fun defaultAiPresetGroups(): List<AiPresetGroup> {
    return defaultAiPresetSections().flatMap { it.groups }
}

fun defaultAiPresets(): List<AiSettings> {
    return defaultAiPresetSections().flatMap { section -> section.groups.flatMap { it.presets } }
}

fun modelNameLooksVisionCapable(modelName: String): Boolean {
    val name = modelName.lowercase(Locale.getDefault())
    return listOf("vision", "vl", "gpt-4o", "gpt-4.1", "gpt-5", "gemini", "claude", "qwen-vl").any { name.contains(it) }
}

fun normalizeAiBaseUrl(rawUrl: String, anthropicLike: Boolean = false, geminiLike: Boolean = false, zhipuLike: Boolean = false): String? {
    val compactUrl = rawUrl.trim()
    if (compactUrl.isBlank() || compactUrl.any { it.isWhitespace() }) return null

    val withProtocol = if (compactUrl.startsWith("http://") || compactUrl.startsWith("https://")) {
        compactUrl
    } else {
        "https://$compactUrl"
    }
    val parsedUrl = runCatching { URL(withProtocol) }.getOrNull() ?: return null
    if (parsedUrl.protocol != "http" && parsedUrl.protocol != "https") return null
    if (parsedUrl.host.isNullOrBlank() || !parsedUrl.host.contains(".")) return null

    val withoutTrailingSlash = withProtocol.trimEnd('/')
    if (anthropicLike) {
        return when {
            withoutTrailingSlash.endsWith("/v1/messages") -> withoutTrailingSlash
            withoutTrailingSlash.endsWith("/messages") -> withoutTrailingSlash
            withoutTrailingSlash.endsWith("/v1") -> "$withoutTrailingSlash/messages"
            else -> "$withoutTrailingSlash/v1/messages"
        }
    }
    if (geminiLike) {
        return when {
            withoutTrailingSlash.endsWith("/openai/chat/completions") -> withoutTrailingSlash
            withoutTrailingSlash.endsWith("/chat/completions") -> withoutTrailingSlash
            withoutTrailingSlash.endsWith("/openai") -> "$withoutTrailingSlash/chat/completions"
            withoutTrailingSlash.endsWith("/v1beta") -> "$withoutTrailingSlash/openai/chat/completions"
            withoutTrailingSlash.contains("generativelanguage.googleapis.com") -> "$withoutTrailingSlash/v1beta/openai/chat/completions"
            else -> "$withoutTrailingSlash/v1/chat/completions"
        }
    }
    if (zhipuLike) {
        return when {
            withoutTrailingSlash.endsWith("/chat/completions") -> withoutTrailingSlash
            withoutTrailingSlash.endsWith("/api/paas/v4") -> "$withoutTrailingSlash/chat/completions"
            withoutTrailingSlash.contains("bigmodel.cn") -> "$withoutTrailingSlash/api/paas/v4/chat/completions"
            withoutTrailingSlash.contains("z.ai") -> "$withoutTrailingSlash/api/paas/v4/chat/completions"
            else -> "$withoutTrailingSlash/v1/chat/completions"
        }
    }
    return when {
        withoutTrailingSlash.endsWith("/chat/completions") -> withoutTrailingSlash
        withoutTrailingSlash.endsWith("/messages") -> withoutTrailingSlash
        withoutTrailingSlash.endsWith("/v1") -> "$withoutTrailingSlash/chat/completions"
        else -> "$withoutTrailingSlash/v1/chat/completions"
    }
}

fun friendlyAiErrorMessage(error: Throwable): String {
    val rawMessage = error.message.orEmpty()
    return when {
        rawMessage.contains("接口地址格式不正确") -> rawMessage
        rawMessage.contains("<!doctype", ignoreCase = true) ||
            rawMessage.contains("<html", ignoreCase = true) -> {
            "AI 接口返回了网页内容而不是 JSON。请检查服务商地址是否填成了网页地址，建议使用类似 https://api.openai.com/v1 的 API 地址。已切换为本地候选结果。"
        }
        rawMessage.contains("AI 返回内容无法解析为候选食物") -> {
            "AI 返回内容无法解析为候选食物。请确认该模型按 JSON 格式返回，或开启代码里的开发者模式查看原始返回。已切换为本地候选结果。"
        }
        rawMessage.contains("image_url", ignoreCase = true) ||
            rawMessage.contains("vision", ignoreCase = true) ||
            rawMessage.contains("multimodal", ignoreCase = true) -> {
            "AI 模型或接口不支持图片识别，请在 AI 设置中关闭该模型的图片能力，或改用 GPT-4o、Gemini、Qwen-VL 等视觉模型。已切换为本地候选结果。"
        }
        rawMessage.contains("no protocol", ignoreCase = true) ||
            rawMessage.contains("unknown protocol", ignoreCase = true) ||
            rawMessage.contains("Illegal character", ignoreCase = true) -> {
            "AI 接口地址格式不正确，请到 AI 设置中重新填写，例如 https://api.openai.com/v1。已切换为本地候选结果。"
        }
        isTransientAiNetworkError(error) -> {
            "AI 网络连接被中断，已自动重试但仍失败。通常是中转站/API 连接瞬断或图片请求体过大导致，并不是其他 AI 配置把它改坏了。已切换为本地候选结果。"
        }
        rawMessage.isBlank() -> "AI 请求失败，已切换为本地候选结果。"
        else -> "${rawMessage.take(140)}${if (rawMessage.length > 140) "..." else ""} 已切换为本地候选结果。"
    }
}

fun friendlyAiReportErrorMessage(error: Throwable): String {
    val rawMessage = error.message.orEmpty()
    return when {
        rawMessage.contains("接口地址格式不正确") -> rawMessage
        rawMessage.contains("no protocol", ignoreCase = true) ||
            rawMessage.contains("unknown protocol", ignoreCase = true) ||
            rawMessage.contains("Illegal character", ignoreCase = true) -> {
            "AI 接口地址格式不正确，请到 AI 设置中重新填写，例如 https://api.openai.com/v1。"
        }
        rawMessage.isBlank() -> "AI 报告生成失败，请稍后重试。"
        else -> "${rawMessage.take(140)}${if (rawMessage.length > 140) "..." else ""}"
    }
}

fun aiVerificationSignature(providerName: String, baseUrl: String, apiKey: String, modelName: String): String {
    val anthropicLike = providerLooksAnthropicLike(providerName, baseUrl, modelName)
    val geminiLike = providerLooksGeminiLike(providerName, baseUrl, modelName)
    val zhipuLike = providerLooksZhipuLike(providerName, baseUrl, modelName)
    return listOf(providerName.trim(), normalizeAiBaseUrl(baseUrl, anthropicLike, geminiLike, zhipuLike) ?: baseUrl.trim(), apiKey.trim(), modelName.trim()).joinToString("|")
}

fun modelAllowsTemperature(modelName: String): Boolean {
    val name = modelName.lowercase(Locale.getDefault())
    return !listOf("o1", "o3", "o4", "reasoner").any { name.contains(it) }
}

fun modelListUrlFromBaseUrl(settings: AiSettings): String? {
    val anthropicLike = providerLooksAnthropicLike(settings)
    val geminiLike = providerLooksGeminiLike(settings)
    val zhipuLike = providerLooksZhipuLike(settings)
    val normalized = normalizeAiBaseUrl(settings.baseUrl, anthropicLike, geminiLike, zhipuLike) ?: return null
    return when {
        normalized.endsWith("/chat/completions") -> normalized.removeSuffix("/chat/completions") + "/models"
        normalized.endsWith("/messages") -> normalized.removeSuffix("/messages") + "/models"
        normalized.endsWith("/v1") -> "$normalized/models"
        else -> normalized.trimEnd('/') + "/models"
    }
}

fun providerLooksAnthropicLike(settings: AiSettings): Boolean {
    return providerLooksAnthropicLike(settings.providerName, settings.baseUrl, settings.modelName)
}

fun providerLooksGeminiLike(settings: AiSettings): Boolean {
    return providerLooksGeminiLike(settings.providerName, settings.baseUrl, settings.modelName)
}

fun providerLooksZhipuLike(settings: AiSettings): Boolean {
    return providerLooksZhipuLike(settings.providerName, settings.baseUrl, settings.modelName)
}

fun providerLooksAnthropicLike(providerName: String, baseUrl: String, modelName: String): Boolean {
    val text = "$providerName $baseUrl $modelName".lowercase(Locale.getDefault())
    return listOf("anthropic", "claude", "claude-aws").any { text.contains(it) }
}

fun providerLooksGeminiLike(providerName: String, baseUrl: String, modelName: String): Boolean {
    val text = "$providerName $baseUrl $modelName".lowercase(Locale.getDefault())
    return listOf("gemini", "generativelanguage.googleapis.com").any { text.contains(it) }
}

fun providerLooksZhipuLike(providerName: String, baseUrl: String, modelName: String): Boolean {
    val text = "$providerName $baseUrl $modelName".lowercase(Locale.getDefault())
    return listOf("zhipu", "智谱", "bigmodel", "z.ai", "glm").any { text.contains(it) }
}

fun zhipuModelLooksVisionCapable(modelName: String): Boolean {
    val name = modelName.lowercase(Locale.getDefault())
    return name.contains("glm-4v") ||
        name.contains("glm-5v") ||
        Regex("glm-[\\w.]*v").containsMatchIn(name) ||
        name.contains("vision") ||
        name.contains("vl")
}

fun providerNeedsAnthropicVersion(settings: AiSettings): Boolean {
    return providerLooksAnthropicLike(settings)
}

suspend fun <T> withTransientAiRetry(block: () -> T): T {
    var lastError: Throwable? = null
    repeat(2) { attempt ->
        try {
            return block()
        } catch (error: Throwable) {
            if (attempt == 1 || !isTransientAiNetworkError(error)) throw error
            lastError = error
            kotlinx.coroutines.delay(450)
        }
    }
    throw lastError ?: IOException("AI 网络请求失败")
}

fun isTransientAiNetworkError(error: Throwable): Boolean {
    val message = error.message.orEmpty()
    return listOf(
        "connection reset",
        "unexpected end of stream",
        "stream was reset",
        "socket closed",
        "connection timed out",
        "timeout"
    ).any { message.contains(it, ignoreCase = true) }
}

suspend fun testAiSettings(settings: AiSettings): String {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        withTransientAiRetry {
        val requestUrl = normalizeAiBaseUrl(settings.baseUrl, providerLooksAnthropicLike(settings), providerLooksGeminiLike(settings), providerLooksZhipuLike(settings))
            ?: throw IllegalStateException("AI 接口地址格式不正确，请到 AI 设置中重新填写 http/https 地址。")
        val payload = if (providerNeedsAnthropicVersion(settings)) {
            buildAnthropicPayload(settings, "请只回复 OK，用于检测模型是否可用。")
        } else if (providerLooksZhipuLike(settings)) {
            buildZhipuPayload(settings, "请只回复 OK，用于检测模型是否可用。")
        } else {
            buildOpenAiCompatiblePayload(settings, "请只回复 OK，用于检测模型是否可用。")
        }
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            setRequestProperty("Connection", "close")
            if (providerNeedsAnthropicVersion(settings)) {
                setRequestProperty("x-api-key", settings.apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
        }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload)
        }
        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            throw IllegalStateException("AI 请求失败：HTTP $responseCode ${errorBody.take(220)}")
        }
        extractAiMessageContent(responseText).ifBlank { responseText }
        }
    }
}

suspend fun verifyAiSettings(settings: AiSettings): AiVerificationResult {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        withTransientAiRetry {
            val availabilityResponse = requestAiCapabilityCheck(
                settings = settings,
                prompt = "请只回复 OK，用于检测模型是否可用。"
            )
            /* val visionResponse = requestAiCapabilityCheck(
                settings = settings,
                prompt = "请只回答 YES 或 NO：你当前这个模型是否支持图片识别，是否能在聊天消息中接收图片并理解图片内容？不要解释。"
            ) */
            val visionProbeResult = runCatching { requestVisionSupportProbe(settings) }.getOrNull()
            AiVerificationResult(
                availabilityResponse = availabilityResponse,
                supportsVision = visionProbeResult != null,
                visionImageUploadFormat = visionProbeResult?.uploadFormat ?: VisionImageUploadFormat.Auto
            )
        }
    }
}

fun requestAiCapabilityCheck(settings: AiSettings, prompt: String, imageDataUrl: String? = null): String {
    val requestUrl = normalizeAiBaseUrl(
        settings.baseUrl,
        providerLooksAnthropicLike(settings),
        providerLooksGeminiLike(settings),
        providerLooksZhipuLike(settings)
    ) ?: throw IllegalStateException("AI 接口地址格式不正确，请到 AI 设置中重新填写 http/https 地址。")
    val capabilityCheckSystemPrompt = "你是一个模型能力检测助手。严格按用户要求作答，不要输出多余内容。"
    val payload = if (providerNeedsAnthropicVersion(settings)) {
        buildAnthropicPayload(settings, prompt, imageDataUrl = imageDataUrl, systemPrompt = capabilityCheckSystemPrompt)
    } else if (providerLooksZhipuLike(settings)) {
        buildZhipuPayload(settings, prompt, imageDataUrl = imageDataUrl)
    } else {
        buildOpenAiCompatiblePayload(settings, prompt, imageDataUrl = imageDataUrl, systemPrompt = capabilityCheckSystemPrompt)
    }
    val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20000
        readTimeout = 30000
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
        setRequestProperty("Connection", "close")
        if (providerNeedsAnthropicVersion(settings)) {
            setRequestProperty("x-api-key", settings.apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
    }
    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
        writer.write(payload)
    }
    val responseCode = connection.responseCode
    val responseText = if (responseCode in 200..299) {
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } else {
        val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        throw IllegalStateException("AI 请求失败：HTTP $responseCode ${errorBody.take(220)}")
    }
    return extractAiMessageContent(responseText).trim().ifBlank { responseText.trim() }
}

fun parseVisionSupportAnswer(answer: String): Boolean {
    val normalized = answer
        .replace("`", " ")
        .replace("*", " ")
        .trim()
    val lowercase = normalized.lowercase(Locale.getDefault())
    return when {
        listOf("不支持图片", "不支持图像", "不支持图片识别", "不能识别图片", "无法识别图片", "仅文本", "纯文本", "不可以", "不是", "不行", "否").any { lowercase.contains(it) } -> false
        Regex("\\b(no|false|unsupported|text-only|text only|cannot)\\b").containsMatchIn(lowercase) -> false
        listOf("支持图片", "支持图像", "支持图片识别", "可以识别图片", "能识别图片", "可以接收图片", "能接收图片", "可以", "是").any { lowercase.contains(it) } -> true
        Regex("\\b(yes|true|supported|support)\\b").containsMatchIn(lowercase) -> true
        else -> throw IllegalStateException("模型没有明确回答是否支持图片识别，请重试。原始回答：${normalized.take(80)}")
    }
}

fun requestVisionImageUploadFormat(settings: AiSettings): VisionImageUploadFormat {
    val prompt = if (providerNeedsAnthropicVersion(settings)) {
        "你现在作为图片识别模型工作时，接收图片应该使用哪一种上传格式？请只回复 ANTHROPIC_BASE64 或 UNSUPPORTED，不要解释。"
    } else {
        "你现在作为图片识别模型工作时，接收图片更适合哪一种上传格式？请只回复 IMAGE_URL_DATA_URL、IMAGE_URL_BASE64 或 UNSUPPORTED，不要解释。IMAGE_URL_DATA_URL 表示传完整的 data:image/...;base64,...，IMAGE_URL_BASE64 表示只传纯 base64 内容。"
    }
    val answer = requestAiCapabilityCheck(settings = settings, prompt = prompt)
    return parseVisionImageUploadFormatAnswer(answer, settings)
}

fun parseVisionImageUploadFormatAnswer(answer: String, settings: AiSettings): VisionImageUploadFormat {
    val normalized = answer
        .replace("`", " ")
        .replace("*", " ")
        .trim()
    val uppercase = normalized.uppercase(Locale.getDefault())
    return when {
        "ANTHROPIC_BASE64" in uppercase -> VisionImageUploadFormat.AnthropicBase64
        "IMAGE_URL_BASE64" in uppercase -> VisionImageUploadFormat.ImageUrlBase64
        "IMAGE_URL_DATA_URL" in uppercase -> VisionImageUploadFormat.ImageUrlDataUrl
        listOf("DATA URL", "DATA_URL", "完整DATA", "完整DATAURL", "完整 BASE64 DATA URL").any { uppercase.contains(it) } ->
            VisionImageUploadFormat.ImageUrlDataUrl
        listOf("RAW BASE64", "纯BASE64", "原始BASE64").any { uppercase.contains(it) } ->
            if (providerNeedsAnthropicVersion(settings)) VisionImageUploadFormat.AnthropicBase64 else VisionImageUploadFormat.ImageUrlBase64
        Regex("\\b(UNSUPPORTED|UNKNOWN|AUTO)\\b").containsMatchIn(uppercase) -> settings.resolvedVisionImageUploadFormat()
        else -> settings.resolvedVisionImageUploadFormat()
    }
}

fun candidateVisionUploadFormats(settings: AiSettings): List<VisionImageUploadFormat> {
    return when {
        providerNeedsAnthropicVersion(settings) -> listOf(VisionImageUploadFormat.AnthropicBase64)
        providerLooksZhipuLike(settings) -> listOf(
            VisionImageUploadFormat.ImageUrlBase64,
            VisionImageUploadFormat.ImageUrlDataUrl
        )
        else -> listOf(
            VisionImageUploadFormat.ImageUrlDataUrl,
            VisionImageUploadFormat.ImageUrlBase64
        )
    }.distinct()
}

fun isVisionProbeNegativeAnswer(answer: String): Boolean {
    val lowercase = answer.lowercase(Locale.getDefault())
    return listOf(
        "no_image",
        "no image",
        "cannot see image",
        "can't see image",
        "unable to view image",
        "cannot view image",
        "unsupported",
        "text-only",
        "text only",
        "无法读取图片",
        "看不到图片",
        "没有看到图片",
        "不能看图",
        "不支持图片"
    ).any { lowercase.contains(it) }
}

fun isExplicitVisionUnsupportedError(error: Throwable): Boolean {
    val message = error.message.orEmpty().lowercase(Locale.getDefault())
    return listOf(
        "image_url",
        "vision",
        "multimodal",
        "unsupported image",
        "does not support image",
        "doesn't support image",
        "not support image",
        "图片",
        "图像",
        "多模态"
    ).any { message.contains(it) }
}

fun requestVisionSupportProbe(settings: AiSettings): VisionSupportProbeResult {
    val probePrompt = "你将收到一张测试图片。如果你确实收到了图片，请只回复 IMAGE_OK。若你没有看到图片或无法处理图片，请只回复 NO_IMAGE。不要解释。"
    val errors = mutableListOf<Throwable>()
    var sawNegativeAnswer = false

    candidateVisionUploadFormats(settings).forEach { uploadFormat ->
        val probeSettings = settings.copy(visionImageUploadFormat = uploadFormat)
        val result = runCatching {
            requestAiCapabilityCheck(
                settings = probeSettings,
                prompt = probePrompt,
                imageDataUrl = VISION_PROBE_IMAGE_DATA_URL
            )
        }
        result.onSuccess { answer ->
            val normalized = answer.trim()
            if (normalized.isBlank()) {
                return@onSuccess
            }
            if (isVisionProbeNegativeAnswer(normalized)) {
                sawNegativeAnswer = true
                return@onSuccess
            }
            return VisionSupportProbeResult(uploadFormat = uploadFormat, response = normalized)
        }.onFailure { error ->
            errors += error
        }
    }

    if (modelNameLooksVisionCapable(settings.modelName) && errors.none(::isExplicitVisionUnsupportedError) && !sawNegativeAnswer) {
        return VisionSupportProbeResult(
            uploadFormat = settings.resolvedVisionImageUploadFormat(),
            response = "MODEL_NAME_FALLBACK"
        )
    }

    if (errors.any(::isExplicitVisionUnsupportedError) || sawNegativeAnswer) {
        throw IllegalStateException("模型未通过真实图片测试，当前接口看起来不支持图片识别。")
    }

    throw errors.firstOrNull() ?: IllegalStateException("真实图片测试失败，暂时无法确认该模型的图片能力。")
}

suspend fun fetchAiModelList(settings: AiSettings): List<String> {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val requestUrl = modelListUrlFromBaseUrl(settings)
            ?: throw IllegalStateException("AI 接口地址格式不正确，请到 AI 设置中重新填写 http/https 地址。")
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 30000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            setRequestProperty("Connection", "close")
            if (providerNeedsAnthropicVersion(settings)) {
                setRequestProperty("x-api-key", settings.apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
        }
        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            throw IllegalStateException("模型列表请求失败：HTTP $responseCode ${errorBody.take(220)}")
        }
        extractModelIds(responseText)
    }
}

fun extractModelIds(responseText: String): List<String> {
    val idRegex = Regex("\\\"id\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
    val nameRegex = Regex("\\\"name\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
    return (idRegex.findAll(responseText) + nameRegex.findAll(responseText))
        .map { it.groupValues[1].unescapeJsonString() }
        .filter { it.isNotBlank() && !it.equals("model", ignoreCase = true) }
        .distinct()
        .sorted()
        .take(80)
        .toList()
}

data class RecognizedFoodCandidate(
    val food: FoodItem,
    val quantity: Double,
    val tags: List<String>,
    val confidence: Double,
    val note: String,
    val sourceName: String = "AI 候选"
)

data class AiRecognitionResponse(
    val candidates: List<RecognizedFoodCandidate>,
    val rawContent: String,
    val rawResponse: String
)

suspend fun requestAiMealRecognition(
    settings: AiSettings,
    foodName: String,
    servingType: ServingType,
    quantityText: String,
    carbsText: String,
    proteinText: String,
    fatText: String,
    inputMode: NutritionInputMode,
    foodDescription: String = "",
    imageDataUrl: String? = null,
    imageType: RecognitionImageType = RecognitionImageType.FoodPhoto
): List<RecognizedFoodCandidate> {
    return requestAiMealRecognitionResponse(
        settings = settings,
        foodName = foodName,
        servingType = servingType,
        quantityText = quantityText,
        carbsText = carbsText,
        proteinText = proteinText,
        fatText = fatText,
        inputMode = inputMode,
        foodDescription = foodDescription,
        imageDataUrl = imageDataUrl,
        imageType = imageType
    ).candidates
}

suspend fun requestAiMealRecognitionResponse(
    settings: AiSettings,
    foodName: String,
    servingType: ServingType,
    quantityText: String,
    carbsText: String,
    proteinText: String,
    fatText: String,
    inputMode: NutritionInputMode,
    foodDescription: String = "",
    imageDataUrl: String? = null,
    imageType: RecognitionImageType = RecognitionImageType.FoodPhoto
): AiRecognitionResponse {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        withTransientAiRetry {
        val prompt = buildAiMealRecognitionPrompt(
            foodName = foodName,
            servingType = servingType,
            quantityText = quantityText,
            carbsText = carbsText,
            proteinText = proteinText,
            fatText = fatText,
            inputMode = inputMode,
            foodDescription = foodDescription,
            hasImage = imageDataUrl != null,
            imageType = imageType
        )
        val payload = if (providerNeedsAnthropicVersion(settings)) {
            buildAnthropicPayload(settings, prompt, imageDataUrl)
        } else if (providerLooksZhipuLike(settings)) {
            if (imageDataUrl != null && !zhipuModelLooksVisionCapable(settings.modelName)) {
                throw IllegalStateException("智谱 GLM 模型 ${settings.modelName} 看起来不是视觉模型，不能用于图片识别。请改用 glm-4v、glm-4v-plus、glm-5v-turbo 等视觉模型，或关闭它的图片识别工作 AI。")
            }
            buildZhipuPayload(settings, prompt, imageDataUrl)
        } else {
            buildOpenAiCompatiblePayload(settings, prompt, imageDataUrl)
        }
        val requestUrl = normalizeAiBaseUrl(settings.baseUrl, providerLooksAnthropicLike(settings), providerLooksGeminiLike(settings), providerLooksZhipuLike(settings))
            ?: throw IllegalStateException("AI 接口地址格式不正确，请到 AI 设置中重新填写 http/https 地址。")
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            setRequestProperty("Connection", "close")
            if (providerNeedsAnthropicVersion(settings)) {
                setRequestProperty("x-api-key", settings.apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
        }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload)
        }
        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            throw IllegalStateException("AI 请求失败：HTTP $responseCode ${errorBody.take(160)}")
        }
        val content = extractAiMessageContent(responseText)
        val parsedFallbackName = if (imageType == RecognitionImageType.NutritionLabel) {
            foodName.ifBlank { "营养成分表食品" }
        } else {
            foodName
        }
        val candidates = parseAiCandidatesFromText(content, parsedFallbackName, servingType, parseOneDecimal(proteinText) ?: 0.0, parseOneDecimal(fatText) ?: 0.0, parseOneDecimal(carbsText) ?: 0.0)
            .ifEmpty { throw IllegalStateException("AI 返回内容无法解析为候选食物：${content.take(120)}") }
        AiRecognitionResponse(
            candidates = candidates,
            rawContent = content,
            rawResponse = responseText
        )
        }
    }
}

suspend fun requestAiNutritionReport(
    settings: AiSettings,
    selectedDate: String,
    plan: NutritionPlan?,
    meals: List<MealRecord>,
    exerciseBurnRecord: ExerciseBurnRecord?,
    waterTargetMl: Double,
    drunkWaterMl: Double
): String {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        withTransientAiRetry {
        val prompt = buildAiNutritionReportPrompt(selectedDate, plan, meals, exerciseBurnRecord, waterTargetMl, drunkWaterMl)
        val payload = if (providerNeedsAnthropicVersion(settings)) {
            buildAnthropicPayload(settings, prompt)
        } else if (providerLooksZhipuLike(settings)) {
            buildZhipuPayload(settings, prompt)
        } else {
            buildOpenAiCompatiblePayload(settings, prompt)
        }
        val requestUrl = normalizeAiBaseUrl(settings.baseUrl, providerLooksAnthropicLike(settings), providerLooksGeminiLike(settings), providerLooksZhipuLike(settings))
            ?: throw IllegalStateException("AI 接口地址格式不正确，请到 AI 设置中重新填写 http/https 地址。")
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            setRequestProperty("Connection", "close")
            if (providerNeedsAnthropicVersion(settings)) {
                setRequestProperty("x-api-key", settings.apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
        }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload)
        }
        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            throw IllegalStateException("AI 请求失败：HTTP $responseCode ${errorBody.take(160)}")
        }
        extractAiMessageContent(responseText).trim().ifBlank {
            throw IllegalStateException("AI 返回内容为空")
        }
        }
    }
}

suspend fun requestAiExerciseCalories(
    settings: AiSettings,
    selectedDate: String,
    description: String
): Double {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        withTransientAiRetry {
        val prompt = buildAiExerciseCaloriesPrompt(selectedDate, description)
        val payload = if (providerNeedsAnthropicVersion(settings)) {
            buildAnthropicPayload(settings, prompt)
        } else if (providerLooksZhipuLike(settings)) {
            buildZhipuPayload(settings, prompt)
        } else {
            buildOpenAiCompatiblePayload(settings, prompt)
        }
        val requestUrl = normalizeAiBaseUrl(settings.baseUrl, providerLooksAnthropicLike(settings), providerLooksGeminiLike(settings), providerLooksZhipuLike(settings))
            ?: throw IllegalStateException("AI 接口地址格式不正确，请到 AI 设置中重新填写 http/https 地址。")
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            setRequestProperty("Connection", "close")
            if (providerNeedsAnthropicVersion(settings)) {
                setRequestProperty("x-api-key", settings.apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
        }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload)
        }
        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            throw IllegalStateException("AI 请求失败：HTTP $responseCode ${errorBody.take(160)}")
        }
        val content = extractAiMessageContent(responseText).trim()
        parseExerciseCaloriesFromText(content)
            ?: throw IllegalStateException("文字生成工作 AI 返回内容无法解析为运动消耗：${content.take(120)}")
        }
    }
}

suspend fun requestAiCandidateSummary(
    settings: AiSettings,
    candidates: List<RecognizedFoodCandidate>,
    fallbackName: String,
    foodDescription: String = "",
    fallbackServingType: ServingType
): RecognizedFoodCandidate {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        withTransientAiRetry {
        val prompt = buildAiCandidateSummaryPrompt(candidates, fallbackName, foodDescription)
        val payload = if (providerNeedsAnthropicVersion(settings)) {
            buildAnthropicPayload(settings, prompt)
        } else if (providerLooksZhipuLike(settings)) {
            buildZhipuPayload(settings, prompt)
        } else {
            buildOpenAiCompatiblePayload(settings, prompt)
        }
        val requestUrl = normalizeAiBaseUrl(settings.baseUrl, providerLooksAnthropicLike(settings), providerLooksGeminiLike(settings), providerLooksZhipuLike(settings))
            ?: throw IllegalStateException("AI 接口地址格式不正确，请到 AI 设置中重新填写 http/https 地址。")
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            setRequestProperty("Connection", "close")
            if (providerNeedsAnthropicVersion(settings)) {
                setRequestProperty("x-api-key", settings.apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
        }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload)
        }
        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            throw IllegalStateException("AI 请求失败：HTTP $responseCode ${errorBody.take(160)}")
        }
        val content = extractAiMessageContent(responseText)
        parseAiCandidatesFromText(content, fallbackName, fallbackServingType, 0.0, 0.0, 0.0)
            .firstOrNull()
            ?.copy(sourceName = "文字 AI 汇总结果")
            ?: throw IllegalStateException("文字生成工作 AI 返回内容无法解析为汇总结果：${content.take(120)}")
        }
    }
}

fun buildAiNutritionReportPrompt(
    selectedDate: String,
    plan: NutritionPlan?,
    meals: List<MealRecord>,
    exerciseBurnRecord: ExerciseBurnRecord?,
    waterTargetMl: Double,
    drunkWaterMl: Double
): String {
    val consumedCalories = meals.sumOf { it.calories }
    val consumedProtein = meals.sumOf { it.protein }
    val consumedFat = meals.sumOf { it.fat }
    val consumedCarbs = meals.sumOf { it.carbs }
    val exerciseCalories = exerciseBurnRecord?.calories ?: 0.0
    val adjustedTargetCalories = (plan?.targetCalories ?: 0.0) + exerciseCalories
    val adjustedTargetProtein = (plan?.targetProtein ?: 0.0) + exerciseProteinGrams(exerciseCalories)
    val adjustedTargetCarbs = (plan?.targetCarbs ?: 0.0) + exerciseCarbsGrams(exerciseCalories)
    val mealLines = meals.joinToString("\n") { meal ->
        "- ${meal.time} ${meal.name} ${formatNumber(meal.calories)}kcal 碳水${formatNumber(meal.carbs)}g 蛋白${formatNumber(meal.protein)}g 脂肪${formatNumber(meal.fat)}g 标签:${meal.tags.joinToString("/")}"
    }.ifBlank { "暂无饮食记录" }
    return """
你是一个谨慎的营养建议助手。请根据当天记录生成中文营养报告。
要求：
1. 不要输出 Markdown 表格。
2. 按以下顺序输出：今日总评、宏量营养分析、食物质量分析、微量营养与膳食纤维估算、接下来怎么吃、饮水建议、注意事项。
3. 宏量营养分析里要判断蛋白质、脂肪、碳水是否大致达标，并分析分配是否合理。
4. 食物质量分析里必须判断：
   - 蛋白质来源是否优质，是否偏向鸡蛋、奶、瘦肉、鱼虾、大豆制品，还是主要来自零散低质量来源。
   - 脂肪来源是否较好，是否偏油炸、肥肉、奶茶、酱料、加工零食，是否存在饱和脂肪偏高风险。
   - 碳水来源更偏粗杂粮、薯类、豆类、全谷物，还是偏白米饭、白面、甜点、含糖饮料等精制碳水。
5. 微量营养与膳食纤维估算里，必须根据食物名称、常见食材组合、份量和烹饪方式，估算当天膳食纤维以及常见矿物质摄入情况，至少分析：钠、钾、钙、镁、铁、锌。
6. 微量营养和膳食纤维无法精准计算时，也要明确写“估算”，并结合常见食材来源判断可能偏多、一般、偏少。
7. 要分析食物多样性，指出蔬菜、水果、奶类、豆制品、全谷杂豆、坚果、海产品等是否明显不足或偏少。
8. 建议要具体到食物类型和替换方案，例如“下一餐补一份深色蔬菜+一盒牛奶”这种级别，不要只说“均衡饮食”。
9. 所有营养建议与估算都要尽量参考《中国居民膳食指南》，口径保持日常、稳妥、不过度极端。
10. 不要给医疗诊断，只做日常饮食建议。
11. 控制在约 500 到 900 字，信息要详细，但不要空泛重复。

日期：$selectedDate
计划：${plan?.name ?: "未选择计划"}
基础目标：热量${plan?.targetCalories ?: 0}kcal 蛋白${plan?.targetProtein ?: 0}g 脂肪${plan?.targetFat ?: 0}g 碳水${plan?.targetCarbs ?: 0}g 饮水${waterTargetMl}ml
额外运动消耗：${exerciseCalories}kcal，描述：${exerciseBurnRecord?.description?.ifBlank { "未填写" } ?: "无"}
运动补回公式：运动消耗热量补充 = 85% 碳水 + 15% 蛋白质。
补回后目标：热量${adjustedTargetCalories}kcal 蛋白${adjustedTargetProtein}g 脂肪${plan?.targetFat ?: 0}g 碳水${adjustedTargetCarbs}g 饮水${waterTargetMl}ml
已摄入：热量${consumedCalories}kcal 蛋白${consumedProtein}g 脂肪${consumedFat}g 碳水${consumedCarbs}g 饮水${drunkWaterMl}ml
饮食记录：
$mealLines
""".trimIndent()
}

fun buildAiExerciseCaloriesPrompt(selectedDate: String, description: String): String {
    return """
你是一个谨慎的运动热量估算助手。请根据用户描述估算当天额外运动消耗热量。
只返回 JSON 对象，不要返回 Markdown。
字段：calories, intensity, durationMinutes, exerciseCaloriesMin, exerciseCaloriesMax, epocCalories, note。
其中：
- intensity 表示你判断的运动强度，例如“低强度 / 中等强度 / 中高强度 / 高强度”。
- durationMinutes 表示你推断的总运动时长，单位分钟。
- exerciseCaloriesMin / exerciseCaloriesMax 表示按该运动强度和时长推算出的“运动过程本身”的合理热量消耗区间，不含基础代谢。
- epocCalories 表示运动后过量氧耗（EPOC）带来的额外热量消耗估算，静态拉伸、散步等低强度活动可接近 0；高强度有氧、间歇训练、较扎实的力量训练可适当加入，但不要夸张。
- calories 必须是最终采用的整数 kcal，表示“运动过程本身消耗 + EPOC”的总额外消耗，不包含基础代谢。
工作流程必须严格按下面步骤执行：
1. 先根据描述判断运动类型、运动强度、时长，必要时保守补全缺失信息。
2. 再按该强度和时长估算运动过程本身的合理热量消耗区间 exerciseCaloriesMin 到 exerciseCaloriesMax。
3. 然后单独估算 epocCalories。
4. 最后计算 calories，并检查它是否与前面的区间和 EPOC 一致：calories 应大致落在“exerciseCaloriesMin + epocCalories”到“exerciseCaloriesMax + epocCalories”附近。
5. 如果前后对不上，必须重新计算，直到最终 calories 与前面的强度判断、区间估算、EPOC 估算彼此一致。
6. note 里简短说明你的强度判断依据、区间判断依据、是否计入 EPOC，以及有哪些不确定性。
7. 如果描述不完整，请给保守估计，并在 note 里说明不确定性。
如果明显不是运动描述，calories 返回 0。

日期：$selectedDate
运动描述：${description.ifBlank { "未填写" }}
""".trimIndent()
}

fun buildAiCandidateSummaryPrompt(candidates: List<RecognizedFoodCandidate>, foodName: String, foodDescription: String): String {
    val candidateLines = candidates.mapIndexed { index, candidate ->
        """
候选${index + 1}：
来源：${candidate.sourceName}
名称：${candidate.food.name}
分类：${candidate.food.category}
记录方式：${candidate.food.servingType.name}
建议数量：${formatServingCount(candidate.quantity)}
每份/每100g营养：热量${formatNumber(candidate.food.calories)}kcal 蛋白${formatNumber(candidate.food.protein)}g 脂肪${formatNumber(candidate.food.fat)}g 碳水${formatNumber(candidate.food.carbs)}g
置信度：${candidate.confidence}
说明：${candidate.note}
标签：${candidate.tags.joinToString("/")}
""".trimIndent()
    }.joinToString("\n\n")
    return """
你是一个谨慎的营养记录汇总助手。下面是多个图片识别 AI 对同一张食物图片给出的候选结果。
请综合这些结果，生成 1 个最终建议结果。不要机械平均明显离谱的数值，要解释你采用哪些结果、忽略哪些不确定点。
如果需要做营养估算、分类归纳或份量判断，请尽量参考《中国居民膳食指南》，采用保守、日常的口径。
必须返回 protein、fat、carbs 三项营养素，不能省略、不能留空、不能只给热量；如果不确定，也要给出保守估算并在 note 说明依据。
只返回 JSON 数组，数组里只放 1 个对象，不要返回 Markdown。
字段：name, category, servingType, quantity, protein, fat, carbs, confidence, note, tags。
servingType 只能是 PerItem 或 Per100g。
protein/fat/carbs 表示每个或每100g的克数。
quantity 必须估算这道菜可食用部分的总量；如果 servingType 是 Per100g，quantity 用克数；不要在没有依据时默认 100g。
如果用户没有明确描述吃了哪些部分，请只计算实际可食用部分，不要把骨头、鱼刺、虾壳、蟹壳、贝壳、包装、明显剩下的汤汁、锅底、装饰性不可食用部分算进去。
如果图片里有带骨肉类、整鱼、汤面、麻辣烫、火锅、砂锅、带壳海鲜等，请默认按保守可食用部分估算，并在 note 简短说明是否已排除骨头、壳、汤汁等非实际入口部分。
用户已知菜名：${foodName.ifBlank { "未填写" }}
用户补充描述：${foodDescription.ifBlank { "未填写" }}

$candidateLines
""".trimIndent()
}

fun buildAiMealRecognitionPrompt(
    foodName: String,
    servingType: ServingType,
    quantityText: String,
    carbsText: String,
    proteinText: String,
    fatText: String,
    inputMode: NutritionInputMode,
    foodDescription: String = "",
    hasImage: Boolean = false,
    imageType: RecognitionImageType = RecognitionImageType.FoodPhoto
): String {
    return if (hasImage && imageType == RecognitionImageType.NutritionLabel) {
        """
你是一个营养成分表 OCR 读取助手。请直接读取用户上传图片中的营养成分表，不要把它当食物照片估算。
只返回 JSON 数组，数组里优先放 1 个对象，不要返回 Markdown，不要解释。
字段：name, category, servingType, quantity, protein, fat, carbs, confidence, note, tags。
规则：
1. protein/fat/carbs 只填表里直接写出的数值，单位都是 g。
2. 如果表里有“每100g/每100毫升”，优先用它，servingType=Per100g，quantity=100。
3. 如果表里只有“每份/每包/每瓶/每个”，servingType=PerItem，quantity=1。
4. 不要根据包装正面宣传语、口味名或食物照片外观去猜营养。
5. 如果某项在表里看不清或没写，就保守填写 0，并在 note 说明哪一项不清楚。
6. 如果 name 无法确定，可用用户输入名称；若用户也没填，就写“营养成分表食品”。
7. category 尽量使用：主食、肉蛋奶、蔬菜、水果、饮品、零食、调味品、自定义、AI 识别。
8. note 请简短说明你读的是“每100g”还是“每份”，以及是否有模糊字段。
9. 如果图片信息不足、必须补充保守判断时，口径尽量参考《中国居民膳食指南》。
10. 必须返回 protein、fat、carbs 三项；不要只返回热量或只写在说明文字里。

用户输入：
foodName: ${foodName.ifBlank { "未填写" }}
foodDescription: ${foodDescription.ifBlank { "未填写" }}
servingType: ${servingType.name}
quantity: ${quantityText.ifBlank { "未填写" }}
inputMode: ${inputMode.name}
carbs: ${carbsText.ifBlank { "0" }}
protein: ${proteinText.ifBlank { "0" }}
fat: ${fatText.ifBlank { "0" }}
""".trimIndent()
    } else {
        val sourceText = if (hasImage) {
            "请优先根据用户上传的食物图片识别食物类型、可见份量和营养素；用户填写的菜名和补充描述是强线索，可用于避免瞎猜。"
        } else {
            "请根据用户手动输入的食物信息生成候选饮食记录。"
        }
        """
你是一个营养记录助手。$sourceText
生成 1 到 3 个候选饮食记录。
所有营养估算、食物分类和份量判断都要尽量参考《中国居民膳食指南》，采用保守、日常、不过度夸张的口径。
必须返回 protein、fat、carbs 三项营养素，不能省略、不能留空、不能只给热量；如果不确定，也要根据常见做法给出保守估算，并在 note 说明依据。
只返回 JSON 数组，不要返回 Markdown。
字段：name, category, servingType, quantity, protein, fat, carbs, confidence, note, tags。
category 只能尽量使用：主食、肉蛋奶、蔬菜、水果、饮品、零食、调味品、自定义、AI 识别。
servingType 只能是 PerItem 或 Per100g。
营养素 protein/fat/carbs 表示每个或每100g的克数。
quantity 必须估算这道菜可食用部分的总量：如果 servingType 是 Per100g，quantity 用克数；如果 servingType 是 PerItem，quantity 用个数。
如果用户没有填写总量，也要根据图片餐盘、容器、食物形态和用户描述估算，不要默认 100g。
如果用户没有详细描述自己实际吃了哪些部分，请默认只计算实际可食用部分，不要把骨头、鱼刺、虾壳、蟹壳、贝壳、明显没喝掉的汤汁、锅底、垫底配料、装饰性不可食用部分算进去。
带骨鸡鸭鱼肉、整鱼、排骨、鸡翅、汤面、麻辣烫、火锅、砂锅、带壳海鲜这类场景，要优先按“实际入口部分”保守估算；如果做了这种排除，请在 note 里简短写明。
如果来自图片，请在 note 中说明菜名/描述/画面分别对判断的影响，以及总量估算依据。

用户输入：
foodName: ${foodName.ifBlank { "未填写" }}
foodDescription: ${foodDescription.ifBlank { "未填写" }}
servingType: ${servingType.name}
quantity: ${quantityText.ifBlank { "未填写" }}
inputMode: ${inputMode.name}
carbs: ${carbsText.ifBlank { "0" }}
protein: ${proteinText.ifBlank { "0" }}
fat: ${fatText.ifBlank { "0" }}
""".trimIndent()
    }
}

fun buildOpenAiCompatiblePayload(
    settings: AiSettings,
    prompt: String,
    imageDataUrl: String? = null,
    systemPrompt: String = "你是专业但谨慎的营养记录助手，必须只输出 JSON。涉及营养估算、饮食建议或食物判断时，要尽量参考《中国居民膳食指南》。"
): String {
    val userContent = if (imageDataUrl.isNullOrBlank()) {
        "\"${prompt.escapeJson()}\""
    } else {
        val imageUrlValue = when (settings.resolvedVisionImageUploadFormat()) {
            VisionImageUploadFormat.ImageUrlBase64 -> imageDataUrl.substringAfter("base64,", imageDataUrl)
            else -> imageDataUrl
        }
        """
[
      {"type": "text", "text": "${prompt.escapeJson()}"},
      {"type": "image_url", "image_url": {"url": "${imageUrlValue.escapeJson()}"}}
    ]
""".trimIndent()
    }
    val temperatureLine = if (modelAllowsTemperature(settings.modelName)) {
        "  \"temperature\": ${settings.temperature},\n"
    } else {
        ""
    }
    val maxTokensLine = if (providerLooksZhipuLike(settings)) "" else "  \"max_tokens\": 800,\n"
    return """
{
  "model": "${settings.modelName.escapeJson()}",
${temperatureLine}${maxTokensLine}  "stream": false,
  "messages": [
    {"role": "system", "content": "${systemPrompt.escapeJson()}"},
    {"role": "user", "content": $userContent}
  ]
}
""".trimIndent()
}

fun buildZhipuPayload(settings: AiSettings, prompt: String, imageDataUrl: String? = null): String {
    val userContent = if (imageDataUrl.isNullOrBlank()) {
        "\"${prompt.escapeJson()}\""
    } else {
        val imagePayload = when (settings.resolvedVisionImageUploadFormat()) {
            VisionImageUploadFormat.ImageUrlDataUrl -> imageDataUrl
            else -> imageDataUrl.substringAfter("base64,", imageDataUrl)
        }
        """
[
      {"type": "image_url", "image_url": {"url": "${imagePayload.escapeJson()}"}},
      {"type": "text", "text": "${prompt.escapeJson()}"}
    ]
""".trimIndent()
    }
    val temperatureLine = if (modelAllowsTemperature(settings.modelName)) {
        "  \"temperature\": ${settings.temperature},\n"
    } else {
        ""
    }
    return """
{
  "model": "${settings.modelName.escapeJson()}",
${temperatureLine}  "stream": false,
  "messages": [
    {"role": "user", "content": $userContent}
  ]
}
""".trimIndent()
}

fun buildAnthropicPayload(
    settings: AiSettings,
    prompt: String,
    imageDataUrl: String? = null,
    systemPrompt: String = "你是专业但谨慎的营养记录助手。涉及营养估算、饮食建议或食物判断时，要尽量参考《中国居民膳食指南》。"
): String {
    val imageContent = if (imageDataUrl.isNullOrBlank()) {
        ""
    } else {
        val mimeType = imageDataUrl.substringAfter("data:", "image/jpeg").substringBefore(";base64")
        val data = imageDataUrl.substringAfter("base64,", imageDataUrl)
        """
      {"type": "image", "source": {"type": "base64", "media_type": "${mimeType.escapeJson()}", "data": "${data.escapeJson()}"}},
""".trimIndent()
    }
    return """
{
  "model": "${settings.modelName.escapeJson()}",
  "max_tokens": 800,
  "system": "${systemPrompt.escapeJson()}",
  "messages": [
    {
      "role": "user",
      "content": [
$imageContent
        {"type": "text", "text": "${prompt.escapeJson()}"}
      ]
    }
  ]
}
""".trimIndent()
}

fun extractAiMessageContent(responseText: String): String {
    return extractOpenAiMessageContent(responseText)
}

fun extractOpenAiMessageContent(responseText: String): String {
    val anthropicTextRegex = Regex("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
    val contentRegex = Regex("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
    val match = anthropicTextRegex.find(responseText) ?: contentRegex.find(responseText) ?: return responseText
    return match.groupValues[1]
        .replace("\\n", "\n")
        .replace("\\\"", "\"")
        .replace("\\/", "/")
        .replace("\\\\", "\\")
}

fun parseAiCandidatesFromText(
    text: String,
    fallbackName: String,
    fallbackServingType: ServingType,
    fallbackProtein: Double,
    fallbackFat: Double,
    fallbackCarbs: Double
): List<RecognizedFoodCandidate> {
    val cleanedText = cleanAiJsonText(text)
    return extractCandidateJsonObjects(cleanedText).mapNotNull { json ->
        val name = extractJsonStringAny(json, "name", "foodName", "food", "title", "食物", "名称", "食物名称")
            ?: fallbackName.takeIf { it.isNotBlank() }
            ?: "营养成分表食品"
        val proteinValue = extractJsonNumberAny(
            json,
            "protein", "protein_g", "proteinGram", "proteinPer100g", "proteinPerServing",
            "proteins", "蛋白", "蛋白质", "每100g蛋白质", "每份蛋白质"
        )
        val fatValue = extractJsonNumberAny(
            json,
            "fat", "fat_g", "fatGram", "fatPer100g", "fatPerServing",
            "totalFat", "脂肪", "总脂肪", "每100g脂肪", "每份脂肪"
        )
        val carbsValue = extractJsonNumberAny(
            json,
            "carbs", "carbohydrate", "carbohydrates", "carbs_g", "carbohydrate_g",
            "carbsPer100g", "carbsPerServing", "碳水", "碳水化合物", "总碳水化合物", "每100g碳水", "每份碳水"
        )
        if (proteinValue == null && fatValue == null && carbsValue == null && fallbackProtein == 0.0 && fallbackFat == 0.0 && fallbackCarbs == 0.0) {
            return@mapNotNull null
        }
        val category = extractJsonStringAny(json, "category", "分类", "类别") ?: suggestFoodCategory(name)
        val servingTypeText = extractJsonStringAny(json, "servingType", "serving_type", "记录方式", "计量方式", "basis", "单位", "营养基准")
        val type = parseServingType(servingTypeText) ?: fallbackServingType
        val quantity = extractJsonNumberAny(
            json,
            "quantity", "totalQuantity", "estimatedQuantity", "amount", "weight", "grams",
            "totalWeight", "estimatedWeight", "servingSize", "serving_size", "netWeight", "netContent",
            "packageWeight", "建议数量", "数量", "总量", "重量", "估算重量", "每份", "每包", "净含量"
        ) ?: if (type == ServingType.PerItem) 1.0 else 100.0
        val protein = proteinValue?.let(::roundOneDecimal) ?: fallbackProtein
        val fat = fatValue?.let(::roundOneDecimal) ?: fallbackFat
        val carbs = carbsValue?.let(::roundOneDecimal) ?: fallbackCarbs
        val confidence = extractJsonNumberAny(json, "confidence", "置信度") ?: 0.65
        val note = extractJsonStringAny(json, "note", "reason", "explanation", "说明", "备注") ?: "AI 返回候选结果，请确认后使用"
        val tags = extractJsonStringArrayAny(json, "tags", "标签").ifEmpty { suggestMealTags(name, calculateCalories(protein, fat, carbs), protein, fat, carbs) }
        RecognizedFoodCandidate(
            food = FoodItem(name = name, protein = protein, fat = fat, carbs = carbs, servingType = type, category = category),
            quantity = quantity,
            tags = tags,
            confidence = confidence.coerceIn(0.0, 1.0),
            note = note
        )
    }.toList()
}

fun cleanAiJsonText(text: String): String {
    val trimmed = text
        .trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val arrayStart = trimmed.indexOf('[')
    val objectStart = trimmed.indexOf('{')
    val start = listOf(arrayStart, objectStart).filter { it >= 0 }.minOrNull() ?: return trimmed
    val arrayEnd = trimmed.lastIndexOf(']')
    val objectEnd = trimmed.lastIndexOf('}')
    val end = maxOf(arrayEnd, objectEnd)
    return if (end > start) trimmed.substring(start, end + 1).trim() else trimmed
}

fun extractCandidateJsonObjects(text: String): List<String> {
    val objects = mutableListOf<String>()
    var depth = 0
    var start = -1
    var inString = false
    var escaped = false
    text.forEachIndexed { index, char ->
        when {
            escaped -> escaped = false
            char == '\\' && inString -> escaped = true
            char == '"' -> inString = !inString
            !inString && char == '{' -> {
                if (depth == 0) start = index
                depth += 1
            }
            !inString && char == '}' -> {
                depth -= 1
                if (depth == 0 && start >= 0) {
                    objects.add(text.substring(start, index + 1))
                    start = -1
                }
            }
        }
    }
    return objects
}

fun parseServingType(raw: String?): ServingType? {
    val value = raw?.lowercase(Locale.getDefault()).orEmpty()
    return when {
        value.contains("peritem") || value.contains("item") || value.contains("个") || value.contains("份") || value.contains("包") || value.contains("瓶") || value.contains("支") || value.contains("袋") -> ServingType.PerItem
        value.contains("per100g") || value.contains("100g") || value.contains("每100") || value.contains("克") -> ServingType.Per100g
        else -> null
    }
}

fun selectBestRecognizedFoodCandidate(
    candidates: List<RecognizedFoodCandidate>,
    fallbackName: String
): RecognizedFoodCandidate {
    val best = candidates.maxByOrNull { candidate ->
        var score = candidate.confidence * 100.0
        if (candidate.food.name.isNotBlank()) score += 8.0
        if (candidate.food.protein > 0) score += 6.0
        if (candidate.food.fat > 0) score += 6.0
        if (candidate.food.carbs > 0) score += 6.0
        if (candidate.food.servingType == ServingType.Per100g) score += 4.0
        if (candidate.quantity > 0) score += 3.0
        if (candidate.note.contains("每100", ignoreCase = true) || candidate.note.contains("每份", ignoreCase = true) || candidate.note.contains("营养成分表")) score += 4.0
        score
    } ?: candidates.first()
    return best.copy(
        food = best.food.copy(name = best.food.name.ifBlank { fallbackName }),
        note = "自动优先选择了最像直接读取营养成分表的一条结果。${best.note}",
        sourceName = "自动优选结果"
    )
}

fun averageRecognizedFoodCandidate(candidates: List<RecognizedFoodCandidate>): RecognizedFoodCandidate {
    val first = candidates.first()
    val averageProtein = roundOneDecimal(candidates.map { it.food.protein }.average())
    val averageFat = roundOneDecimal(candidates.map { it.food.fat }.average())
    val averageCarbs = roundOneDecimal(candidates.map { it.food.carbs }.average())
    val averageQuantity = candidates.map { it.quantity }.average()
    val averageConfidence = candidates.map { it.confidence }.average().coerceIn(0.0, 1.0)
    val dominantServingType = candidates
        .groupingBy { it.food.servingType }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key ?: first.food.servingType
    val dominantCategory = candidates
        .groupingBy { it.food.category }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key ?: first.food.category
    val mergedTags = candidates.flatMap { it.tags }.distinct().take(5)
    return RecognizedFoodCandidate(
        food = FoodItem(
            name = "${first.food.name}（平均值）",
            protein = averageProtein,
            fat = averageFat,
            carbs = averageCarbs,
            servingType = dominantServingType,
            category = dominantCategory
        ),
        quantity = averageQuantity,
        tags = mergedTags.ifEmpty { first.tags },
        confidence = averageConfidence,
        note = "由 ${candidates.size} 个工作 AI 的结果平均生成，请结合实际食物确认。",
        sourceName = "平均值结果"
    )
}

fun extractJsonString(json: String, key: String): String? {
    return Regex("\\\"$key\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
        .find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?.decodeJsonString()
}

fun extractJsonStringAny(json: String, vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key -> extractJsonString(json, key) }
}

fun extractJsonNumber(json: String, key: String): Double? {
    val escapedKey = Regex.escape(key)
    val directNumber = Regex("\\\"$escapedKey\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(json)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    if (directNumber != null) return directNumber
    val quotedNumber = Regex("\\\"$escapedKey\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
        .find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { raw ->
            Regex("-?\\d+(?:\\.\\d+)?").find(raw.replace(",", ""))?.value?.toDoubleOrNull()
        }
    if (quotedNumber != null) return quotedNumber
    val nestedObjectNumber = Regex(
        "\\\"$escapedKey\\\"\\s*:\\s*\\{[^{}]{0,160}?\\\"(?:value|amount|grams|gram|g|number|估算值|数值)\\\"\\s*:\\s*\\\"?(-?\\d+(?:\\.\\d+)?)",
        setOf(RegexOption.DOT_MATCHES_ALL)
    ).find(json)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    if (nestedObjectNumber != null) return nestedObjectNumber
    return null
}

fun extractJsonNumberAny(json: String, vararg keys: String): Double? {
    return keys.firstNotNullOfOrNull { key -> extractJsonNumber(json, key) }
}

fun extractJsonStringArray(json: String, key: String): List<String> {
    val arrayText = Regex("\\\"$key\\\"\\s*:\\s*\\[([^]]*)]").find(json)?.groupValues?.getOrNull(1) ?: return emptyList()
    return Regex("\\\"((?:\\\\.|[^\\\"])*)\\\"").findAll(arrayText).map { it.groupValues[1].decodeJsonString() }.toList()
}

fun extractJsonStringArrayAny(json: String, vararg keys: String): List<String> {
    return keys.firstNotNullOfOrNull { key -> extractJsonStringArray(json, key).takeIf { it.isNotEmpty() } }.orEmpty()
}

fun parseExerciseCaloriesFromText(text: String): Double? {
    val cleanedText = cleanAiJsonText(text)
    val json = extractCandidateJsonObjects(cleanedText).firstOrNull() ?: cleanedText
    return extractJsonNumberAny(
        json,
        "calories", "finalCalories", "totalCalories", "calories_final", "kcal",
        "exerciseCalories", "burnedCalories", "消耗", "热量消耗"
    )
        ?.let(::roundOneDecimal)
        ?.coerceIn(0.0, 3000.0)
}

fun exerciseCarbsGrams(exerciseCalories: Double): Double {
    return roundOneDecimal(exerciseCalories.coerceAtLeast(0.0) * 0.85 / 4.0)
}

fun exerciseProteinGrams(exerciseCalories: Double): Double {
    return roundOneDecimal(exerciseCalories.coerceAtLeast(0.0) * 0.15 / 4.0)
}

fun String.decodeJsonString(): String {
    return this
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\/", "/")
        .replace("\\\\", "\\")
}

fun String.escapeJson(): String {
    return this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}

fun String.unescapeJsonString(): String {
    return this
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\/", "/")
        .replace("\\\\", "\\")
}

fun loadSponsorEntryBitmap(context: Context): Bitmap? {
    return runCatching {
        context.assets.open("溪云Siena.jpg").use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    }.getOrNull()
}

fun loadAuthorWordsText(context: Context): String {
    return runCatching {
        context.assets.open("Authorswords.md").bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
    }.getOrDefault("作者的话暂时无法加载，请确认 Authorswords.md 已打包进 app/src/main/assets。")
}

fun loadLanzouUpdateQrBitmap(context: Context): Bitmap? {
    return runCatching {
        context.assets.open("蓝奏云链接.png").use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    }.getOrNull()
}

fun currentAppVersionCode(context: Context): Int {
    return runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }.getOrDefault(1)
}

suspend fun fetchLatestUpdateInfo(): UpdateInfo {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val requestUrl = "https://raw.githubusercontent.com/guaidaojide666/CalorieFree/main/update.json"
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connection", "close")
        }
        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            throw IllegalStateException("更新检测失败：HTTP $responseCode ${errorBody.take(120)}")
        }
        UpdateInfo(
            versionCode = extractJsonNumber(responseText, "versionCode")?.toInt() ?: 0,
            versionName = extractJsonString(responseText, "versionName").orEmpty(),
            url = extractJsonString(responseText, "url").orEmpty().ifBlank { "https://github.com/guaidaojide666/CalorieFree/releases/latest" },
            changelog = extractJsonString(responseText, "changelog").orEmpty()
        )
    }
}

fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
fun ImageCropDialog(
    bitmap: Bitmap,
    imageType: RecognitionImageType,
    sourceLabel: String?,
    onDismiss: () -> Unit,
    onUseOriginal: (Bitmap) -> Unit,
    onConfirmCrop: (Bitmap) -> Unit
) {
    val density = LocalDensity.current
    var containerSize by remember(bitmap, imageType) { mutableStateOf(IntSize.Zero) }
    var scale by remember(bitmap, imageType) { mutableStateOf(1f) }
    var offset by remember(bitmap, imageType) { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBFF))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "截取识别区域", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
                Text(
                    text = sourceLabel?.let { "$it，可先拖动和缩放，再确认截取。" } ?: "可先拖动和缩放，再确认截取。",
                    fontSize = 12.sp,
                    color = Color(0xFF6F7785)
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (imageType == RecognitionImageType.NutritionLabel) 0.82f else 1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF11151D))
                ) {
                    val viewportWidthPx = with(density) { maxWidth.toPx() }
                    val viewportHeightPx = with(density) { maxHeight.toPx() }
                    val cropScaleFactor = if (imageType == RecognitionImageType.NutritionLabel) 0.82f else 0.78f
                    val cropWidthPx = viewportWidthPx * cropScaleFactor
                    val cropHeightPx = if (imageType == RecognitionImageType.NutritionLabel) cropWidthPx / 0.82f else cropWidthPx
                    val baseImageSize = fittedImageSize(
                        bitmapWidth = bitmap.width.toFloat(),
                        bitmapHeight = bitmap.height.toFloat(),
                        containerWidth = viewportWidthPx,
                        containerHeight = viewportHeightPx
                    )
                    val minScale = max(
                        1f,
                        max(
                            cropWidthPx / baseImageSize.width.coerceAtLeast(1f),
                            cropHeightPx / baseImageSize.height.coerceAtLeast(1f)
                        )
                    )

                    LaunchedEffect(bitmap, imageType, containerSize) {
                        if (containerSize.width > 0 && containerSize.height > 0) {
                            scale = minScale
                            offset = Offset.Zero
                        }
                    }

                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        val nextScale = (scale * zoomChange).coerceIn(minScale, 5f)
                        val nextOffset = clampCropOffset(
                            proposedOffset = offset + panChange,
                            scale = nextScale,
                            baseImageSize = baseImageSize,
                            cropSize = Size(cropWidthPx, cropHeightPx)
                        )
                        scale = nextScale
                        offset = nextOffset
                    }

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .transformable(state = transformState)
                            .onSizeChanged { containerSize = it }
                    ) {
                        val scaledWidthDp = with(density) { (baseImageSize.width * scale).toDp() }
                        val scaledHeightDp = with(density) { (baseImageSize.height * scale).toDp() }
                        val offsetXDp = with(density) { offset.x.toDp() }
                        val offsetYDp = with(density) { offset.y.toDp() }

                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = offsetXDp, y = offsetYDp)
                                .size(width = scaledWidthDp, height = scaledHeightDp)
                        )

                        Canvas(modifier = Modifier.matchParentSize()) {
                            val cropLeft = (size.width - cropWidthPx) / 2f
                            val cropTop = (size.height - cropHeightPx) / 2f
                            val overlayColor = Color(0x88000000)
                            drawRect(overlayColor, topLeft = Offset.Zero, size = Size(size.width, cropTop))
                            drawRect(
                                overlayColor,
                                topLeft = Offset(0f, cropTop),
                                size = Size(cropLeft, cropHeightPx)
                            )
                            drawRect(
                                overlayColor,
                                topLeft = Offset(cropLeft + cropWidthPx, cropTop),
                                size = Size(size.width - cropLeft - cropWidthPx, cropHeightPx)
                            )
                            drawRect(
                                overlayColor,
                                topLeft = Offset(0f, cropTop + cropHeightPx),
                                size = Size(size.width, size.height - cropTop - cropHeightPx)
                            )
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(cropLeft, cropTop),
                                size = Size(cropWidthPx, cropHeightPx),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )
                        }
                    }
                }
                Text(
                    text = "支持双指缩放和拖动。营养成分表建议只框住表格主体。",
                    fontSize = 12.sp,
                    color = Color(0xFF8A92A1)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(text = "取消", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onUseOriginal(bitmap) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(text = "直接使用", fontSize = 12.sp)
                    }
                }
                Button(
                    onClick = {
                        val croppedBitmap = cropBitmapFromViewport(
                            bitmap = bitmap,
                            containerSize = containerSize,
                            cropAspectRatio = if (imageType == RecognitionImageType.NutritionLabel) 0.82f else 1f,
                            cropScaleFactor = if (imageType == RecognitionImageType.NutritionLabel) 0.82f else 0.78f,
                            scale = scale,
                            offset = offset
                        )
                        onConfirmCrop(croppedBitmap ?: bitmap)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "确认截取并识别", fontSize = 14.sp)
                }
            }
        }
    }
}

fun bitmapFromUri(context: Context, uri: Uri, maxSide: Int = 2200): Bitmap? {
    if (Build.VERSION.SDK_INT >= 28) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longestSide = max(info.size.width, info.size.height).coerceAtLeast(1)
                if (longestSide > maxSide) {
                    val scale = maxSide.toDouble() / longestSide.toDouble()
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        }.getOrNull()?.let { return it }
    }

    context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, boundsOptions)
        val longestSide = max(boundsOptions.outWidth, boundsOptions.outHeight).coerceAtLeast(1)
        var inSampleSize = 1
        while (longestSide / inSampleSize > maxSide) {
            inSampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize.coerceAtLeast(1) }
        BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, decodeOptions)?.let { return it }
    }

    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream, null, boundsOptions)
    } ?: return null
    val longestSide = max(boundsOptions.outWidth, boundsOptions.outHeight).coerceAtLeast(1)
    var inSampleSize = 1
    while (longestSide / inSampleSize > maxSide) {
        inSampleSize *= 2
    }
    val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize.coerceAtLeast(1) }
    return context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream, null, decodeOptions)
    }
}

fun createTempCameraImageUri(context: Context): Uri? {
    return runCatching {
        val imagesDir = File(context.cacheDir, "camera_images").apply { mkdirs() }
        val imageFile = File.createTempFile("capture_", ".jpg", imagesDir)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    }.getOrNull()
}

fun fittedImageSize(
    bitmapWidth: Float,
    bitmapHeight: Float,
    containerWidth: Float,
    containerHeight: Float
): Size {
    if (bitmapWidth <= 0f || bitmapHeight <= 0f || containerWidth <= 0f || containerHeight <= 0f) {
        return Size(1f, 1f)
    }
    val bitmapAspect = bitmapWidth / bitmapHeight
    val containerAspect = containerWidth / containerHeight
    return if (bitmapAspect >= containerAspect) {
        Size(containerWidth, containerWidth / bitmapAspect)
    } else {
        Size(containerHeight * bitmapAspect, containerHeight)
    }
}

fun clampCropOffset(
    proposedOffset: Offset,
    scale: Float,
    baseImageSize: Size,
    cropSize: Size
): Offset {
    val scaledWidth = baseImageSize.width * scale
    val scaledHeight = baseImageSize.height * scale
    val maxOffsetX = ((scaledWidth - cropSize.width) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((scaledHeight - cropSize.height) / 2f).coerceAtLeast(0f)
    return Offset(
        x = proposedOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = proposedOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
    )
}

fun cropBitmapFromViewport(
    bitmap: Bitmap,
    containerSize: IntSize,
    cropAspectRatio: Float,
    cropScaleFactor: Float,
    scale: Float,
    offset: Offset
): Bitmap? {
    if (containerSize.width <= 0 || containerSize.height <= 0) return null
    val containerWidth = containerSize.width.toFloat()
    val containerHeight = containerSize.height.toFloat()
    val cropWidth = containerWidth * cropScaleFactor
    val cropHeight = cropWidth / cropAspectRatio
    val baseImageSize = fittedImageSize(
        bitmapWidth = bitmap.width.toFloat(),
        bitmapHeight = bitmap.height.toFloat(),
        containerWidth = containerWidth,
        containerHeight = containerHeight
    )
    val scaledWidth = baseImageSize.width * scale
    val scaledHeight = baseImageSize.height * scale
    val imageLeft = (containerWidth - scaledWidth) / 2f + offset.x
    val imageTop = (containerHeight - scaledHeight) / 2f + offset.y
    val cropLeft = (containerWidth - cropWidth) / 2f
    val cropTop = (containerHeight - cropHeight) / 2f

    val left = (((cropLeft - imageLeft) / scaledWidth) * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
    val top = (((cropTop - imageTop) / scaledHeight) * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
    val right = ((((cropLeft + cropWidth) - imageLeft) / scaledWidth) * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
    val bottom = ((((cropTop + cropHeight) - imageTop) / scaledHeight) * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
    val cropBitmapWidth = (right - left).coerceAtLeast(1)
    val cropBitmapHeight = (bottom - top).coerceAtLeast(1)
    return runCatching { Bitmap.createBitmap(bitmap, left, top, cropBitmapWidth, cropBitmapHeight) }.getOrNull()
}

fun imageDataUrlFromUri(context: Context, uri: Uri, imageType: RecognitionImageType = RecognitionImageType.FoodPhoto): String? {
    val bitmap = bitmapFromUri(context, uri) ?: return null
    return bitmapToRecognitionDataUrl(bitmap, imageType)
}

fun bitmapToJpegDataUrl(bitmap: Bitmap, maxSide: Int = 1024, quality: Int = 82): String {
    val longestSide = max(bitmap.width, bitmap.height).coerceAtLeast(1)
    val scaledBitmap = if (longestSide > maxSide) {
        val scale = maxSide.toDouble() / longestSide.toDouble()
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }
    val output = java.io.ByteArrayOutputStream()
    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), output)
    val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    return "data:image/jpeg;base64,$base64"
}

fun bitmapToRecognitionDataUrl(
    bitmap: Bitmap,
    imageType: RecognitionImageType
): String {
    return if (imageType == RecognitionImageType.NutritionLabel) {
        bitmapToJpegDataUrl(bitmap, maxSide = 1800, quality = 92)
    } else {
        bitmapToJpegDataUrl(bitmap, maxSide = 1280, quality = 85)
    }
}

fun mockImageRecognitionCandidates(foodName: String): List<RecognizedFoodCandidate> {
    if (foodName.isNotBlank()) {
        val category = suggestFoodCategory(foodName)
        val baseFood = FoodItem(
            name = foodName.trim(),
            protein = if (category == "肉蛋奶") 18.0 else 6.0,
            fat = if (category == "零食") 16.0 else 5.0,
            carbs = if (category == "主食") 28.0 else 12.0,
            category = category
        )
        return listOf(
            RecognizedFoodCandidate(
                food = baseFood,
                quantity = 150.0,
                tags = suggestMealTags(baseFood.name, baseFood.calories, baseFood.protein, baseFood.fat, baseFood.carbs),
                confidence = 0.52,
            note = "本地规则根据食物名称生成；要使用真实图片识别，请在 AI 设置里检测、启用并选为图片识别工作 AI"
            )
        )
    }
    return listOf(
        RecognizedFoodCandidate(
            food = FoodItem(name = "米饭配鸡胸肉", protein = 12.0, fat = 3.0, carbs = 25.0, category = "AI 识别"),
            quantity = 250.0,
            tags = listOf("正餐", "高蛋白"),
            confidence = 0.46,
            note = "本地示例候选；在 AI 设置里检测、启用并选为图片识别工作 AI 后，会根据照片内容识别"
        ),
        RecognizedFoodCandidate(
            food = FoodItem(name = "蔬菜沙拉", protein = 2.0, fat = 4.0, carbs = 8.0, category = "AI 识别"),
            quantity = 180.0,
            tags = listOf("加餐", "低脂"),
            confidence = 0.42,
            note = "本地示例候选，请按实际照片确认"
        ),
        RecognizedFoodCandidate(
            food = FoodItem(name = "拿铁咖啡", protein = 8.0, fat = 7.0, carbs = 14.0, servingType = ServingType.PerItem, category = "饮品"),
            quantity = 1.0,
            tags = listOf("饮品"),
            confidence = 0.38,
            note = "本地示例候选，适合用于测试图片识别流程"
        )
    )
}

fun mockRecognizedFoodCandidates(
    foodName: String,
    servingType: ServingType,
    protein: Double,
    fat: Double,
    carbs: Double
): List<RecognizedFoodCandidate> {
    val normalizedName = foodName.trim().ifBlank { "待识别食物" }
    val category = suggestFoodCategory(normalizedName)
    val baseFood = FoodItem(
        name = normalizedName,
        protein = protein.coerceAtLeast(0.0),
        fat = fat.coerceAtLeast(0.0),
        carbs = carbs.coerceAtLeast(0.0),
        servingType = servingType,
        category = category
    )
    val baseTags = suggestMealTags(normalizedName, baseFood.calories, baseFood.protein, baseFood.fat, baseFood.carbs)
    val defaultQuantity = if (servingType == ServingType.PerItem) 1.0 else 100.0
    val candidates = mutableListOf(
        RecognizedFoodCandidate(
            food = baseFood,
            quantity = defaultQuantity,
            tags = baseTags,
            confidence = if (foodName.isBlank()) 0.45 else 0.72,
            note = "根据当前手动输入生成的候选结果"
        )
    )

    if (category == "主食" && servingType == ServingType.Per100g) {
        candidates.add(
            RecognizedFoodCandidate(
                food = baseFood.copy(name = "$normalizedName（常规份量）"),
                quantity = 150.0,
                tags = (baseTags + "正餐").distinct(),
                confidence = 0.62,
                note = "主食常见单餐份量约 100-200g，请按实际情况确认"
            )
        )
    }
    if (category == "饮品") {
        candidates.add(
            RecognizedFoodCandidate(
                food = baseFood.copy(servingType = ServingType.PerItem),
                quantity = 1.0,
                tags = listOf("饮品"),
                confidence = 0.58,
                note = "饮品也可按 1 杯/1 瓶记录"
            )
        )
    }
    return candidates
}

fun suggestFoodCategory(foodName: String): String {
    val name = foodName.lowercase(Locale.getDefault())
    return when {
        listOf("米", "饭", "面", "粉", "粥", "馒头", "包子", "燕麦", "面包", "土豆", "玉米").any { name.contains(it) } -> "主食"
        listOf("鸡", "牛", "猪", "鱼", "虾", "蛋", "奶", "豆腐", "羊", "鸭").any { name.contains(it) } -> "肉蛋奶"
        listOf("菜", "生菜", "菠菜", "白菜", "西兰花", "番茄", "黄瓜", "萝卜").any { name.contains(it) } -> "蔬菜"
        listOf("苹果", "香蕉", "橙", "梨", "葡萄", "草莓", "西瓜", "水果").any { name.contains(it) } -> "水果"
        listOf("水", "茶", "咖啡", "奶茶", "可乐", "饮料", "果汁").any { name.contains(it) } -> "饮品"
        listOf("薯片", "饼干", "巧克力", "糖", "蛋糕", "零食").any { name.contains(it) } -> "零食"
        listOf("酱", "油", "盐", "醋", "调味").any { name.contains(it) } -> "调味品"
        else -> "自定义"
    }
}

fun suggestMealTags(
    foodName: String,
    calories: Double,
    protein: Double,
    fat: Double,
    carbs: Double
): List<String> {
    val tags = mutableListOf<String>()
    val category = suggestFoodCategory(foodName)
    tags.add(
        when (category) {
            "饮品" -> "饮品"
            "零食" -> "零食"
            else -> "加餐"
        }
    )
    if (protein >= 20) tags.add("高蛋白")
    if (fat <= 5 && calories > 0) tags.add("低脂")
    if (carbs >= 40) tags.add("高碳水")
    if (calories >= 600) tags.add("高热量")
    return tags.distinct()
}

fun toggleSelectableTag(currentTags: List<String>, clickedTag: String): List<String> {
    return if (currentTags.contains(clickedTag)) {
        currentTags.filter { it != clickedTag }
    } else {
        val filtered = if (clickedTag in MEAL_OCCASION_TAGS) {
            currentTags.filter { it !in MEAL_OCCASION_TAGS }
        } else {
            currentTags
        }
        (filtered + clickedTag).distinct()
    }
}

fun normalizeSelectableTags(tags: List<String>): List<String> {
    val normalized = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    val chosenMealOccasion = normalized.firstOrNull { it in MEAL_OCCASION_TAGS }
    return if (chosenMealOccasion == null) {
        normalized
    } else {
        listOf(chosenMealOccasion) + normalized.filter { it !in MEAL_OCCASION_TAGS }
    }
}

fun foodProfileMacroText(profile: FavoriteFoodProfile): String {
    return "${servingTypeLabel(profile.servingType, profile.unitLabel)}：碳水${formatNumber(profile.carbsPerServing)}g · 蛋白${formatNumber(profile.proteinPerServing)}g"
}

fun foodProfileDisplayQuantity(profile: FavoriteFoodProfile, servingCount: Double): Double {
    return if (profile.servingType == ServingType.PerItem) {
        roundOneDecimal(servingCount)
    } else {
        roundOneDecimal(servingCount * 100.0)
    }
}

fun mealToFavoriteFoodProfile(meal: MealRecord): FavoriteFoodProfile? {
    if (meal.servingCount <= 0.0) return null
    val carbsPerServing = roundOneDecimal(meal.carbs / meal.servingCount)
    val proteinPerServing = roundOneDecimal(meal.protein / meal.servingCount)
    val fatPerServing = roundOneDecimal(meal.fat / meal.servingCount)
    if (carbsPerServing <= 0.0 && proteinPerServing <= 0.0) return null
    return FavoriteFoodProfile(
        name = meal.name.trim(),
        category = suggestFoodCategory(meal.name),
        servingType = meal.servingType,
        unitLabel = normalizedUnitLabel(meal.servingType, meal.unitLabel),
        carbsPerServing = carbsPerServing,
        proteinPerServing = proteinPerServing,
        fatPerServing = fatPerServing,
        usageCount = 1,
        totalQuantity = meal.quantity
    )
}

fun inferFavoriteFoodProfiles(
    mealHistory: List<MealRecord>,
    targetCategory: String
): List<FavoriteFoodProfile> {
    return mealHistory
        .mapNotNull(::mealToFavoriteFoodProfile)
        .filter { profile ->
            when (targetCategory) {
                "主食" -> profile.category == "主食" && profile.carbsPerServing > 0.0
                "肉蛋奶" -> {
                    (profile.category == "肉蛋奶" || (profile.proteinPerServing >= 8.0 && profile.proteinPerServing >= profile.carbsPerServing)) &&
                        profile.proteinPerServing > 0.0
                }
                else -> false
            }
        }
        .groupBy { Triple(it.name, it.servingType, it.unitLabel) }
        .map { (_, items) ->
            FavoriteFoodProfile(
                name = items.first().name,
                category = items.first().category,
                servingType = items.first().servingType,
                unitLabel = items.first().unitLabel,
                carbsPerServing = roundOneDecimal(items.map { it.carbsPerServing }.average()),
                proteinPerServing = roundOneDecimal(items.map { it.proteinPerServing }.average()),
                fatPerServing = roundOneDecimal(items.map { it.fatPerServing }.average()),
                usageCount = items.size,
                totalQuantity = roundOneDecimal(items.sumOf { it.totalQuantity })
            )
        }
        .sortedWith(
            compareByDescending<FavoriteFoodProfile> { it.usageCount }
                .thenByDescending { it.totalQuantity }
                .thenBy { it.name.length }
        )
}

fun estimateRemainingFoodEquivalent(
    mealHistory: List<MealRecord>,
    remainingCarbs: Double,
    remainingProtein: Double
): RemainingFoodEquivalent? {
    if (remainingCarbs <= 0.0 || remainingProtein <= 0.0) return null
    val stapleCandidates = inferFavoriteFoodProfiles(mealHistory, "主食").take(8)
    val proteinCandidates = inferFavoriteFoodProfiles(mealHistory, "肉蛋奶").take(8)
    if (stapleCandidates.isEmpty() || proteinCandidates.isEmpty()) return null
    val epsilon = 0.12
    return stapleCandidates
        .flatMap { staple ->
            proteinCandidates.mapNotNull { proteinSource ->
                val determinant = staple.carbsPerServing * proteinSource.proteinPerServing -
                    proteinSource.carbsPerServing * staple.proteinPerServing
                if (kotlin.math.abs(determinant) < 1e-6) return@mapNotNull null
                val stapleServingCount = (remainingCarbs * proteinSource.proteinPerServing - remainingProtein * proteinSource.carbsPerServing) / determinant
                val proteinServingCount = (remainingProtein * staple.carbsPerServing - remainingCarbs * staple.proteinPerServing) / determinant
                if (stapleServingCount < 0.0 || proteinServingCount < 0.0) return@mapNotNull null
                val totalCarbs = roundOneDecimal(stapleServingCount * staple.carbsPerServing + proteinServingCount * proteinSource.carbsPerServing)
                val totalProtein = roundOneDecimal(stapleServingCount * staple.proteinPerServing + proteinServingCount * proteinSource.proteinPerServing)
                if (kotlin.math.abs(totalCarbs - remainingCarbs) > epsilon || kotlin.math.abs(totalProtein - remainingProtein) > epsilon) return@mapNotNull null
                RemainingFoodEquivalent(
                    staple = staple,
                    proteinSource = proteinSource,
                    stapleServingCount = roundOneDecimal(stapleServingCount),
                    proteinServingCount = roundOneDecimal(proteinServingCount),
                    totalCarbs = totalCarbs,
                    totalProtein = totalProtein
                )
            }
        }
        .sortedWith(
            compareByDescending<RemainingFoodEquivalent> { it.staple.usageCount + it.proteinSource.usageCount }
                .thenBy { it.stapleServingCount + it.proteinServingCount }
                .thenByDescending { it.staple.totalQuantity + it.proteinSource.totalQuantity }
        )
        .firstOrNull()
}

fun mealAnalysisRiskText(
    foodName: String,
    quantity: Double,
    calories: Double,
    protein: Double,
    fat: Double,
    carbs: Double,
    inputMode: NutritionInputMode
): String {
    if (foodName.isBlank()) return "建议先填写食物名称，再进行更有参考价值的分析。"
    if (quantity <= 0.0) return "当前食物总量未填写或无效，请补充数量后再保存。"
    if (calories <= 0) return "当前营养素不足以计算有效热量，请至少填写一种有效营养素。"
    val tips = mutableListOf<String>()
    if (calories >= 600) tips.add("这条记录热量较高，建议确认份量是否准确。")
    if (fat >= 30) tips.add("脂肪含量偏高，后续餐次可适当清淡。")
    if (protein >= 20) tips.add("蛋白质较充足，适合作为补充蛋白的一餐。")
    if (carbs >= 60) tips.add("碳水较高，适合放在训练前后或正餐中。")
    if (inputMode == NutritionInputMode.PerServing) tips.add("当前按每份营养换算，请确认每份类型和总量是否匹配。")
    return tips.ifEmpty { listOf("这条记录看起来比较常规，保存前请确认重量和营养素是否准确。") }.joinToString("\n")
}

fun groupMealsByPrimaryTag(meals: List<MealRecord>): List<Pair<String, List<MealRecord>>> {
    val tagOrder = listOf("早餐", "午餐", "晚餐", "下午加餐", "夜宵", "零食", "加餐")
    val grouped = mutableMapOf<String, MutableList<MealRecord>>()
    meals.forEach { meal ->
        val primaryTag = meal.tags.firstOrNull { it in tagOrder }
            ?: meal.tags.firstOrNull()
            ?: "其他"
        grouped.getOrPut(primaryTag) { mutableListOf() }.add(meal)
    }
    return grouped.entries
        .sortedBy { entry -> tagOrder.indexOf(entry.key).takeIf { it >= 0 } ?: tagOrder.size }
        .map { it.key to it.value }
}

fun nutritionProgress(value: Double, target: Double): Float {
    if (target <= 0) return 0f
    return (value.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

fun calorieStatusText(calories: Double, targetCalories: Double?): String {
    if (targetCalories == null || targetCalories <= 0) return "未设置热量目标"
    if (calories == 0.0) return "未记录饮食"
    val ratio = calories.toFloat() / targetCalories.toFloat()
    return when {
        ratio < 0.6f -> "摄入不足"
        ratio <= 1.0f -> "接近目标"
        else -> "超出目标"
    }
}

fun calorieStatusColor(calories: Double, targetCalories: Double?): Color {
    if (targetCalories == null || targetCalories <= 0) return Color(0xFF8A92A1)
    if (calories == 0.0) return Color(0xFF8A92A1)
    val ratio = calories.toFloat() / targetCalories.toFloat()
    return when {
        ratio < 0.6f -> Color(0xFF29B6F6)
        ratio <= 1.0f -> Color(0xFF35C759)
        else -> Color(0xFFEF5350)
    }
}

fun waterProgress(value: Double, target: Double): Float {
    if (target <= 0) return 0f
    return (value.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

fun loadAppState(context: Context): AppState {
    val prefs = context.getSharedPreferences("calorie_free_state", Context.MODE_PRIVATE)
    val hasAnyStoredData = prefs.all.isNotEmpty()
    val targetCalories = prefs.getString("targetCaloriesDecimal", null)?.toDoubleOrNull() ?: prefs.getInt("targetCalories", 2000).toDouble()
    val targetProtein = prefs.getString("targetProteinDecimal", null)?.toDoubleOrNull() ?: prefs.getInt("targetProtein", 120).toDouble()
    val targetFat = prefs.getString("targetFatDecimal", null)?.toDoubleOrNull() ?: prefs.getInt("targetFat", 65).toDouble()
    val targetCarbs = prefs.getString("targetCarbsDecimal", null)?.toDoubleOrNull() ?: prefs.getInt("targetCarbs", 250).toDouble()
    val userProfile = UserProfile(
        heightCm = prefs.getString("userHeightCm", null)?.toDoubleOrNull() ?: 0.0,
        weightKg = prefs.getString("userWeightKg", null)?.toDoubleOrNull() ?: 0.0,
        ageYears = prefs.getInt("userAgeYears", 0),
        biologicalSex = runCatching { BiologicalSex.valueOf(prefs.getString("userBiologicalSex", BiologicalSex.Female.name).orEmpty()) }.getOrDefault(BiologicalSex.Female)
    )
    val mealsText = prefs.getString("meals", "").orEmpty()
    val meals = mealsText
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line -> decodeMealRecord(line) }
        .toList()
    val foodsText = prefs.getString("foods", "").orEmpty()
    val foods = foodsText
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line -> decodeFoodItem(line) }
        .toList()
    val tagsText = prefs.getString("tags", MEAL_OCCASION_TAGS.joinToString("\n")).orEmpty()
    val tags = tagsText.lineSequence().filter { it.isNotBlank() }.toList()
    val plansText = prefs.getString("plans", "").orEmpty()
    val plans = plansText
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line -> decodeNutritionPlan(line) }
        .toList()
    val dailyPlanSelectionsText = prefs.getString("dailyPlanSelections", "").orEmpty()
    val dailyPlanSelections = dailyPlanSelectionsText
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line -> decodeDailyPlanSelection(line) }
        .toList()
    val waterRecordsText = prefs.getString("waterRecords", "").orEmpty()
    val waterRecords = waterRecordsText
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line -> decodeWaterRecord(line) }
        .toList()
    val exerciseBurnRecordsText = prefs.getString("exerciseBurnRecords", "").orEmpty()
    val exerciseBurnRecords = exerciseBurnRecordsText
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line -> decodeExerciseBurnRecord(line) }
        .toList()
    val aiSettings = decodeAiSettings(prefs.getString("aiSettings", "").orEmpty()) ?: AiSettings()
    val aiSettingsListText = prefs.getString("aiSettingsList", "").orEmpty()
    val aiSettingsList = aiSettingsListText
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line -> decodeAiSettings(line) }
        .toList()

    return AppState(
        targetCalories = targetCalories,
        targetProtein = targetProtein,
        targetFat = targetFat,
        targetCarbs = targetCarbs,
        hasAnyStoredData = hasAnyStoredData,
        userProfile = userProfile,
        meals = meals,
        foods = foods,
        tags = tags,
        plans = plans,
        dailyPlanSelections = dailyPlanSelections,
        waterRecords = waterRecords,
        exerciseBurnRecords = exerciseBurnRecords,
        aiSettings = aiSettings,
        aiSettingsList = aiSettingsList
    )
}

fun saveAppState(
    context: Context,
    targetCalories: Double,
    targetProtein: Double,
    targetFat: Double,
    targetCarbs: Double,
    userProfile: UserProfile = UserProfile(),
    meals: List<MealRecord>,
    foods: List<FoodItem>,
    tags: List<String>,
    plans: List<NutritionPlan> = emptyList(),
    dailyPlanSelections: List<DailyPlanSelection> = emptyList(),
    waterRecords: List<WaterRecord> = emptyList(),
    exerciseBurnRecords: List<ExerciseBurnRecord> = emptyList(),
    aiSettings: AiSettings = AiSettings(),
    aiSettingsList: List<AiSettings> = emptyList()
) {
    val mealsText = meals.joinToString("\n") { encodeMealRecord(it) }
    val foodsText = foods.joinToString("\n") { encodeFoodItem(it) }
    val tagsText = tags.joinToString("\n")
    val plansText = plans.joinToString("\n") { encodeNutritionPlan(it) }
    val dailyPlanSelectionsText = dailyPlanSelections.joinToString("\n") { encodeDailyPlanSelection(it) }
    val waterRecordsText = waterRecords.joinToString("\n") { encodeWaterRecord(it) }
    val exerciseBurnRecordsText = exerciseBurnRecords.joinToString("\n") { encodeExerciseBurnRecord(it) }
    val aiSettingsText = encodeAiSettings(aiSettings)
    val aiSettingsListText = aiSettingsList.joinToString("\n") { encodeAiSettings(it) }
    context.getSharedPreferences("calorie_free_state", Context.MODE_PRIVATE)
        .edit()
        .putString("targetCaloriesDecimal", targetCalories.toString())
        .putString("targetProteinDecimal", targetProtein.toString())
        .putString("targetFatDecimal", targetFat.toString())
        .putString("targetCarbsDecimal", targetCarbs.toString())
        .putString("userHeightCm", userProfile.heightCm.toString())
        .putString("userWeightKg", userProfile.weightKg.toString())
        .putInt("userAgeYears", userProfile.ageYears)
        .putString("userBiologicalSex", userProfile.biologicalSex.name)
        .putString("meals", mealsText)
        .putString("foods", foodsText)
        .putString("tags", tagsText)
        .putString("plans", plansText)
        .putString("dailyPlanSelections", dailyPlanSelectionsText)
        .putString("waterRecords", waterRecordsText)
        .putString("exerciseBurnRecords", exerciseBurnRecordsText)
        .putString("aiSettings", aiSettingsText)
        .putString("aiSettingsList", aiSettingsListText)
        .apply()
}

fun encodeAiSettings(settings: AiSettings): String {
    return listOf(
        settings.id.toString(),
        settings.providerName.encodeStorageField(),
        settings.baseUrl.encodeStorageField(),
        settings.apiKey.encodeStorageField(),
        settings.modelName.encodeStorageField(),
        settings.temperature.toString(),
        settings.enabled.toString(),
        settings.selectedForVisionWork.toString(),
        settings.selectedForTextWork.toString(),
        settings.supportsVision.toString(),
        settings.verifiedSignature.encodeStorageField(),
        settings.manualVisionConfirmed.toString(),
        settings.visionImageUploadFormat.name
    ).joinToString("|")
}

fun decodeAiSettings(line: String): AiSettings? {
    if (line.isBlank()) return null
    val parts = line.split("|")
    return when {
        parts.size >= 13 -> {
            val supportsVision = parts[9].toBooleanStrictOrNull() ?: modelNameLooksVisionCapable(parts[4].decodeStorageField())
            val manualVisionConfirmed = parts[11].toBooleanStrictOrNull() ?: false
            val uploadFormat = parseVisionImageUploadFormatStoredValue(parts.getOrNull(12))
            AiSettings(
                id = parts[0].toLongOrNull() ?: 1L,
                providerName = parts[1].decodeStorageField(),
                baseUrl = parts[2].decodeStorageField(),
                apiKey = parts[3].decodeStorageField(),
                modelName = parts[4].decodeStorageField(),
                temperature = parts[5].toDoubleOrNull() ?: 0.2,
                enabled = parts[6].toBooleanStrictOrNull() ?: false,
                selectedForVisionWork = (parts[7].toBooleanStrictOrNull() ?: false) && (supportsVision || manualVisionConfirmed),
                selectedForTextWork = parts[8].toBooleanStrictOrNull() ?: false,
                supportsVision = supportsVision,
                verifiedSignature = parts.getOrNull(10)?.decodeStorageField().orEmpty(),
                manualVisionConfirmed = manualVisionConfirmed,
                visionImageUploadFormat = if (uploadFormat == VisionImageUploadFormat.Auto) {
                    AiSettings(
                        providerName = parts[1].decodeStorageField(),
                        baseUrl = parts[2].decodeStorageField(),
                        modelName = parts[4].decodeStorageField()
                    ).resolvedVisionImageUploadFormat()
                } else {
                    uploadFormat
                }
            )
        }
        parts.size >= 12 -> {
            val supportsVision = parts[9].toBooleanStrictOrNull() ?: modelNameLooksVisionCapable(parts[4].decodeStorageField())
            val manualVisionConfirmed = parts[11].toBooleanStrictOrNull() ?: false
            AiSettings(
                id = parts[0].toLongOrNull() ?: 1L,
                providerName = parts[1].decodeStorageField(),
                baseUrl = parts[2].decodeStorageField(),
                apiKey = parts[3].decodeStorageField(),
                modelName = parts[4].decodeStorageField(),
                temperature = parts[5].toDoubleOrNull() ?: 0.2,
                enabled = parts[6].toBooleanStrictOrNull() ?: false,
                selectedForVisionWork = (parts[7].toBooleanStrictOrNull() ?: false) && (supportsVision || manualVisionConfirmed),
                selectedForTextWork = parts[8].toBooleanStrictOrNull() ?: false,
                supportsVision = supportsVision,
                verifiedSignature = parts.getOrNull(10)?.decodeStorageField().orEmpty(),
                manualVisionConfirmed = manualVisionConfirmed,
                visionImageUploadFormat = AiSettings(
                    providerName = parts[1].decodeStorageField(),
                    baseUrl = parts[2].decodeStorageField(),
                    modelName = parts[4].decodeStorageField()
                ).resolvedVisionImageUploadFormat()
            )
        }
        parts.size >= 10 -> {
            val supportsVision = parts[9].toBooleanStrictOrNull() ?: modelNameLooksVisionCapable(parts[4].decodeStorageField())
            AiSettings(
                id = parts[0].toLongOrNull() ?: 1L,
                providerName = parts[1].decodeStorageField(),
                baseUrl = parts[2].decodeStorageField(),
                apiKey = parts[3].decodeStorageField(),
                modelName = parts[4].decodeStorageField(),
                temperature = parts[5].toDoubleOrNull() ?: 0.2,
                enabled = parts[6].toBooleanStrictOrNull() ?: false,
                selectedForVisionWork = (parts[7].toBooleanStrictOrNull() ?: false) && supportsVision,
                selectedForTextWork = parts[8].toBooleanStrictOrNull() ?: false,
                supportsVision = supportsVision,
                verifiedSignature = parts.getOrNull(10)?.decodeStorageField().orEmpty()
            )
        }
        parts.size >= 9 -> {
            val oldSelectedForWork = parts[7].toBooleanStrictOrNull() ?: false
            val supportsVision = parts[8].toBooleanStrictOrNull() ?: modelNameLooksVisionCapable(parts[4].decodeStorageField())
            AiSettings(
                id = parts[0].toLongOrNull() ?: 1L,
                providerName = parts[1].decodeStorageField(),
                baseUrl = parts[2].decodeStorageField(),
                apiKey = parts[3].decodeStorageField(),
                modelName = parts[4].decodeStorageField(),
                temperature = parts[5].toDoubleOrNull() ?: 0.2,
                enabled = parts[6].toBooleanStrictOrNull() ?: false,
                selectedForVisionWork = oldSelectedForWork && supportsVision,
                selectedForTextWork = oldSelectedForWork,
                supportsVision = supportsVision
            )
        }
        parts.size >= 8 -> {
            val oldSelectedForWork = parts[7].toBooleanStrictOrNull() ?: false
            val supportsVision = modelNameLooksVisionCapable(parts[4].decodeStorageField())
            AiSettings(
                id = parts[0].toLongOrNull() ?: 1L,
                providerName = parts[1].decodeStorageField(),
                baseUrl = parts[2].decodeStorageField(),
                apiKey = parts[3].decodeStorageField(),
                modelName = parts[4].decodeStorageField(),
                temperature = parts[5].toDoubleOrNull() ?: 0.2,
                enabled = parts[6].toBooleanStrictOrNull() ?: false,
                selectedForVisionWork = oldSelectedForWork && supportsVision,
                selectedForTextWork = oldSelectedForWork,
                supportsVision = supportsVision
            )
        }
        parts.size >= 6 -> {
            val enabled = parts[5].toBooleanStrictOrNull() ?: false
            val supportsVision = modelNameLooksVisionCapable(parts[3].decodeStorageField())
            AiSettings(
                id = 1L,
                providerName = parts[0].decodeStorageField(),
                baseUrl = parts[1].decodeStorageField(),
                apiKey = parts[2].decodeStorageField(),
                modelName = parts[3].decodeStorageField(),
                temperature = parts[4].toDoubleOrNull() ?: 0.2,
                enabled = enabled,
                selectedForVisionWork = enabled && supportsVision,
                selectedForTextWork = enabled,
                supportsVision = supportsVision
            )
        }
        else -> null
    }
}

fun encodeWaterRecord(record: WaterRecord): String {
    return listOf(
        record.id.toString(),
        record.date.encodeStorageField(),
        record.amountMl.toString()
    ).joinToString("|")
}

fun decodeWaterRecord(line: String): WaterRecord? {
    val parts = line.split("|")
    return if (parts.size >= 3) {
        WaterRecord(
            id = parts[0].toLongOrNull() ?: return null,
            date = parts[1].decodeStorageField(),
            amountMl = parts[2].toDoubleOrNull() ?: return null
        )
    } else {
        null
    }
}

fun encodeExerciseBurnRecord(record: ExerciseBurnRecord): String {
    return listOf(
        record.date.encodeStorageField(),
        record.calories.toString(),
        record.description.encodeStorageField()
    ).joinToString("|")
}

fun decodeExerciseBurnRecord(line: String): ExerciseBurnRecord? {
    val parts = line.split("|")
    return if (parts.size >= 2) {
        ExerciseBurnRecord(
            date = parts[0].decodeStorageField(),
            calories = parts[1].toDoubleOrNull() ?: return null,
            description = parts.getOrNull(2)?.decodeStorageField().orEmpty()
        )
    } else {
        null
    }
}

fun encodeNutritionPlan(plan: NutritionPlan): String {
    return listOf(
        plan.id.toString(),
        plan.name.encodeStorageField(),
        plan.targetCalories.toString(),
        plan.targetProtein.toString(),
        plan.targetFat.toString(),
        plan.targetCarbs.toString(),
        plan.waterTargetMl.toString(),
        plan.dailyCalorieDeficit.toString(),
        plan.isDefault.toString(),
        plan.isHidden.toString(),
        plan.note.encodeStorageField()
    ).joinToString("|")
}

fun decodeNutritionPlan(line: String): NutritionPlan? {
    val parts = line.split("|")
    return if (parts.size >= 11) {
        NutritionPlan(
            id = parts[0].toLongOrNull() ?: return null,
            name = parts[1].decodeStorageField(),
            targetCalories = parts[2].toDoubleOrNull() ?: return null,
            targetProtein = parts[3].toDoubleOrNull() ?: return null,
            targetFat = parts[4].toDoubleOrNull() ?: return null,
            targetCarbs = parts[5].toDoubleOrNull() ?: return null,
            waterTargetMl = parts[6].toDoubleOrNull() ?: 2000.0,
            dailyCalorieDeficit = parts[7].toDoubleOrNull() ?: 0.0,
            isDefault = parts[8].toBooleanStrictOrNull() ?: false,
            isHidden = parts[9].toBooleanStrictOrNull() ?: false,
            note = parts[10].decodeStorageField()
        )
    } else if (parts.size >= 10) {
        NutritionPlan(
            id = parts[0].toLongOrNull() ?: return null,
            name = parts[1].decodeStorageField(),
            targetCalories = parts[2].toDoubleOrNull() ?: return null,
            targetProtein = parts[3].toDoubleOrNull() ?: return null,
            targetFat = parts[4].toDoubleOrNull() ?: return null,
            targetCarbs = parts[5].toDoubleOrNull() ?: return null,
            waterTargetMl = parts[6].toDoubleOrNull() ?: 2000.0,
            dailyCalorieDeficit = parts[7].toDoubleOrNull() ?: 0.0,
            isDefault = parts[8].toBooleanStrictOrNull() ?: false,
            isHidden = false,
            note = parts[9].decodeStorageField()
        )
    } else if (parts.size >= 9) {
        NutritionPlan(
            id = parts[0].toLongOrNull() ?: return null,
            name = parts[1].decodeStorageField(),
            targetCalories = parts[2].toDoubleOrNull() ?: return null,
            targetProtein = parts[3].toDoubleOrNull() ?: return null,
            targetFat = parts[4].toDoubleOrNull() ?: return null,
            targetCarbs = parts[5].toDoubleOrNull() ?: return null,
            waterTargetMl = parts[6].toDoubleOrNull() ?: 2000.0,
            dailyCalorieDeficit = 0.0,
            isDefault = parts[7].toBooleanStrictOrNull() ?: false,
            isHidden = false,
            note = parts[8].decodeStorageField()
        )
    } else {
        null
    }
}

fun encodeDailyPlanSelection(selection: DailyPlanSelection): String {
    return listOf(
        selection.date.encodeStorageField(),
        selection.planId.toString()
    ).joinToString("|")
}

fun decodeDailyPlanSelection(line: String): DailyPlanSelection? {
    val parts = line.split("|")
    return if (parts.size >= 2) {
        DailyPlanSelection(
            date = parts[0].decodeStorageField(),
            planId = parts[1].toLongOrNull() ?: return null
        )
    } else {
        null
    }
}

fun inferLegacyMealOccasionTag(timeText: String): String {
    val hour = Regex("""(\d{1,2})""")
        .find(timeText)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: return "加餐"
    return when (hour) {
        in 5..10 -> "早餐"
        in 11..14 -> "午餐"
        in 15..17 -> "下午加餐"
        in 18..20 -> "晚餐"
        in 21..23, 0, 1, 2, 3, 4 -> "夜宵"
        else -> "加餐"
    }
}

fun encodeMealRecord(meal: MealRecord): String {
    return listOf(
        meal.name.encodeStorageField(),
        meal.calories.toString(),
        meal.protein.toString(),
        meal.fat.toString(),
        meal.carbs.toString(),
        meal.time.encodeStorageField(),
        meal.date.encodeStorageField(),
        meal.servingType.name,
        meal.quantity.toString(),
        meal.servingCount.toString(),
        meal.tags.joinToString(",") { it.encodeStorageField() }.encodeStorageField(),
        meal.unitLabel.encodeStorageField()
    ).joinToString("|")
}

fun decodeMealRecord(line: String): MealRecord? {
    val parts = line.split("|")
    return when (parts.size) {
        6 -> MealRecord(
            name = parts[0].decodeStorageField(),
            calories = parts[1].toDoubleOrNull() ?: return null,
            protein = parts[2].toDoubleOrNull() ?: return null,
            fat = parts[3].toDoubleOrNull() ?: return null,
            carbs = parts[4].toDoubleOrNull() ?: return null,
            time = parts[5].decodeStorageField(),
            tags = listOf(inferLegacyMealOccasionTag(parts[5].decodeStorageField()))
        )
        10, 11, 12 -> {
            val type = runCatching { ServingType.valueOf(parts[7]) }.getOrDefault(ServingType.Per100g)
            val oldAmountOrQuantity = parts[8].toDoubleOrNull() ?: if (type == ServingType.PerItem) 1.0 else 100.0
            val oldServingCount = parts[9].toDoubleOrNull() ?: 1.0
            val quantity = if (oldAmountOrQuantity == 1.0 || oldAmountOrQuantity == 100.0) {
                if (type == ServingType.PerItem) oldServingCount else oldServingCount * 100.0
            } else {
                oldAmountOrQuantity
            }
            val storedTags = if (parts.size >= 11) {
                parts[10]
                    .decodeStorageField()
                    .split(",")
                    .map { it.decodeStorageField().trim() }
                    .filter { it.isNotBlank() }
            } else {
                listOf(inferLegacyMealOccasionTag(parts[5].decodeStorageField()))
            }
            val unitLabel = parts.getOrNull(11)?.decodeStorageField()?.takeIf { it.isNotBlank() }
            MealRecord(
                name = parts[0].decodeStorageField(),
                calories = parts[1].toDoubleOrNull() ?: return null,
                protein = parts[2].toDoubleOrNull() ?: return null,
                fat = parts[3].toDoubleOrNull() ?: return null,
                carbs = parts[4].toDoubleOrNull() ?: return null,
                time = parts[5].decodeStorageField(),
                date = parts[6].decodeStorageField(),
                tags = storedTags,
                servingType = type,
                quantity = quantity,
                servingCount = servingCountFromQuantity(quantity, type),
                unitLabel = normalizedUnitLabel(type, unitLabel)
            )
        }
        else -> null
    }
}

fun encodeFoodItem(food: FoodItem): String {
    return listOf(
        food.name.encodeStorageField(),
        food.protein.toString(),
        food.fat.toString(),
        food.carbs.toString(),
        food.servingType.name,
        food.category.encodeStorageField(),
        food.unitLabel.encodeStorageField(),
        food.isHidden.toString()
    ).joinToString("|")
}

fun decodeFoodItem(line: String): FoodItem? {
    val parts = line.split("|")
    return when (parts.size) {
        4 -> FoodItem(
            name = parts[0].decodeStorageField(),
            protein = parts[1].toDoubleOrNull() ?: return null,
            fat = parts[2].toDoubleOrNull() ?: return null,
            carbs = parts[3].toDoubleOrNull() ?: return null,
            category = suggestFoodCategory(parts[0].decodeStorageField())
        )
        5, 6, 7, 8 -> FoodItem(
            name = parts[0].decodeStorageField(),
            protein = parts[1].toDoubleOrNull() ?: return null,
            fat = parts[2].toDoubleOrNull() ?: return null,
            carbs = parts[3].toDoubleOrNull() ?: return null,
            servingType = runCatching { ServingType.valueOf(parts[4]) }.getOrDefault(ServingType.Per100g),
            category = parts.getOrNull(5)?.decodeStorageField()?.takeIf { it.isNotBlank() } ?: suggestFoodCategory(parts[0].decodeStorageField()),
            unitLabel = normalizedUnitLabel(
                runCatching { ServingType.valueOf(parts[4]) }.getOrDefault(ServingType.Per100g),
                parts.getOrNull(6)?.decodeStorageField()
            ),
            isHidden = parts.getOrNull(7)?.toBooleanStrictOrNull() ?: false
        )
        else -> null
    }
}

fun String.encodeStorageField(): String {
    return this
        .replace("%", "%25")
        .replace("|", "%7C")
        .replace("\n", "%0A")
}

fun String.decodeStorageField(): String {
    return this
        .replace("%0A", "\n")
        .replace("%7C", "|")
        .replace("%25", "%")
}

fun normalizedUnitLabel(servingType: ServingType, unitLabel: String?): String {
    return if (servingType == ServingType.PerItem) {
        unitLabel?.trim().takeUnless { it.isNullOrBlank() } ?: "个"
    } else {
        "g"
    }
}

fun quantityUnitText(servingType: ServingType, unitLabel: String?): String {
    return if (servingType == ServingType.PerItem) {
        normalizedUnitLabel(servingType, unitLabel)
    } else {
        "g"
    }
}

fun foodServingText(food: FoodItem): String {
    return servingTypeLabel(food.servingType, food.unitLabel)
}

fun mealAmountText(meal: MealRecord): String {
    if (meal.quantity <= 0.0 || meal.servingCount <= 0.0) return "未填写分量"
    return if (meal.servingType == ServingType.PerItem) {
        "${formatServingCount(meal.quantity)} ${normalizedUnitLabel(meal.servingType, meal.unitLabel)}"
    } else {
        "${formatServingCount(meal.quantity)} g"
    }
}

fun servingCountFromQuantity(quantity: Double, servingType: ServingType): Double {
    return if (servingType == ServingType.PerItem) {
        quantity
    } else {
        quantity / 100.0
    }
}

fun servingTypeLabel(servingType: ServingType, unitLabel: String? = null): String {
    return if (servingType == ServingType.PerItem) "每${normalizedUnitLabel(servingType, unitLabel)}" else "每100g"
}

fun formatServingCount(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }
}

fun currentDateText(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}

fun offsetDateText(dateText: String, offsetDays: Int): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val date = formatter.parse(dateText) ?: Date()
    val calendar = Calendar.getInstance().apply {
        time = date
        add(Calendar.DAY_OF_YEAR, offsetDays)
    }
    return formatter.format(calendar.time)
}

fun offsetMonthText(monthText: String, offsetMonths: Int): String {
    val formatter = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    val date = formatter.parse(monthText) ?: Date()
    val calendar = Calendar.getInstance().apply {
        time = date
        add(Calendar.MONTH, offsetMonths)
    }
    return formatter.format(calendar.time)
}

fun calendarCellsForMonth(monthText: String): List<String> {
    val monthFormatter = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val firstDate = monthFormatter.parse(monthText) ?: Date()
    val firstDayCalendar = Calendar.getInstance().apply {
        time = firstDate
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val leadingDays = (firstDayCalendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val startCalendar = (firstDayCalendar.clone() as Calendar).apply {
        add(Calendar.DAY_OF_MONTH, -leadingDays)
    }
    return (0 until 42).map { offset ->
        val cellCalendar = (startCalendar.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, offset)
        }
        dateFormatter.format(cellCalendar.time)
    }
}

fun datesInMonth(monthText: String): List<String> {
    val monthFormatter = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val firstDate = monthFormatter.parse(monthText) ?: Date()
    val calendar = Calendar.getInstance().apply {
        time = firstDate
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    return (1..maxDay).map { day ->
        val dayCalendar = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, day)
        }
        dateFormatter.format(dayCalendar.time)
    }
}

fun planForDate(
    date: String,
    plans: List<NutritionPlan>,
    dailyPlanSelections: List<DailyPlanSelection>
): NutritionPlan? {
    val selectedPlanId = dailyPlanSelections.firstOrNull { it.date == date }?.planId
    return plans.firstOrNull { it.id == selectedPlanId }
        ?: plans.firstOrNull { it.isDefault }
        ?: plans.firstOrNull()
}

fun targetCaloriesForDate(
    date: String,
    plans: List<NutritionPlan>,
    dailyPlanSelections: List<DailyPlanSelection>
): Double? {
    return planForDate(date, plans, dailyPlanSelections)?.targetCalories
}

fun currentTimeText(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    CalorieFreeApp()
}
