package com.caloriefree.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private const val AI_DEVELOPER_MODE = true

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

data class MealRecord(
    val name: String,
    val calories: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
    val time: String,
    val date: String = currentDateText(),
    val tags: List<String> = listOf("早餐"),
    val servingType: ServingType = ServingType.Per100g,
    val quantity: Double = 100.0,
    val servingCount: Double = 1.0
)

data class FoodItem(
    val name: String,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
    val servingType: ServingType = ServingType.Per100g,
    val category: String = "自定义",
    val calories: Int = calculateCalories(protein, fat, carbs)
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
    val targetCalories: Int,
    val targetProtein: Int,
    val targetFat: Int,
    val targetCarbs: Int,
    val waterTargetMl: Int = 2000,
    val isDefault: Boolean = false,
    val note: String = ""
)

data class DailyPlanSelection(
    val date: String,
    val planId: Long
)

data class WaterRecord(
    val id: Long,
    val date: String,
    val amountMl: Int
)

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
    val verifiedSignature: String = ""
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

data class AppState(
    val targetCalories: Int,
    val targetProtein: Int,
    val targetFat: Int,
    val targetCarbs: Int,
    val meals: List<MealRecord>,
    val foods: List<FoodItem>,
    val tags: List<String>,
    val plans: List<NutritionPlan> = emptyList(),
    val dailyPlanSelections: List<DailyPlanSelection> = emptyList(),
    val waterRecords: List<WaterRecord> = emptyList(),
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalorieFreeApp()
        }
    }
}

@Composable
fun CalorieFreeApp() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var targetCalories by remember { mutableStateOf(2000) }
    var targetProtein by remember { mutableStateOf(120) }
    var targetFat by remember { mutableStateOf(65) }
    var targetCarbs by remember { mutableStateOf(250) }
    var hasLoadedStorage by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(currentDateText()) }
    var mealEditorState by remember { mutableStateOf<MealEditorState?>(null) }
    var aiSettings by remember { mutableStateOf(AiSettings()) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateErrorText by remember { mutableStateOf<String?>(null) }
    val currentVersionCode = remember { currentAppVersionCode(context) }
    val meals = remember { mutableStateListOf<MealRecord>() }
    val foods = remember { mutableStateListOf<FoodItem>() }
    val tags = remember { mutableStateListOf("早餐", "午餐", "晚餐", "零食") }
    val plans = remember { mutableStateListOf<NutritionPlan>() }
    val dailyPlanSelections = remember { mutableStateListOf<DailyPlanSelection>() }
    val waterRecords = remember { mutableStateListOf<WaterRecord>() }
    val aiSettingsList = remember { mutableStateListOf<AiSettings>() }

    LaunchedEffect(Unit) {
        runCatching { fetchLatestUpdateInfo() }
            .onSuccess { latest ->
                if (latest.versionCode > currentVersionCode) {
                    updateInfo = latest
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
        meals.clear()
        meals.addAll(appState.meals)
        foods.clear()
        foods.addAll(appState.foods)
        tags.clear()
        tags.addAll(appState.tags.ifEmpty { listOf("早餐", "午餐", "晚餐", "零食") })
        plans.clear()
        plans.addAll(
            appState.plans.ifEmpty {
                listOf(
                    NutritionPlan(
                        id = 1L,
                        name = "默认计划",
                        targetCalories = appState.targetCalories,
                        targetProtein = appState.targetProtein,
                        targetFat = appState.targetFat,
                        targetCarbs = appState.targetCarbs,
                        isDefault = true
                    )
                )
            }
        )
        dailyPlanSelections.clear()
        dailyPlanSelections.addAll(appState.dailyPlanSelections)
        waterRecords.clear()
        waterRecords.addAll(appState.waterRecords)
        aiSettingsList.clear()
        aiSettingsList.addAll(
            appState.aiSettingsList.ifEmpty {
                listOf(appState.aiSettings.copy(id = 1L, selectedForVisionWork = appState.aiSettings.enabled && appState.aiSettings.supportsVision, selectedForTextWork = appState.aiSettings.enabled))
            }
        )
        aiSettings = aiSettingsList.firstOrNull { it.selectedForTextWork || it.selectedForVisionWork } ?: aiSettingsList.firstOrNull() ?: appState.aiSettings
        hasLoadedStorage = true
    }

    LaunchedEffect(hasLoadedStorage, targetCalories, targetProtein, targetFat, targetCarbs, meals.toList(), foods.toList(), tags.toList(), plans.toList(), dailyPlanSelections.toList(), waterRecords.toList(), aiSettingsList.toList()) {
        if (hasLoadedStorage) {
            saveAppState(
                context = context,
                targetCalories = targetCalories,
                targetProtein = targetProtein,
                targetFat = targetFat,
                targetCarbs = targetCarbs,
                meals = meals,
                foods = foods,
                tags = tags,
                plans = plans,
                dailyPlanSelections = dailyPlanSelections,
                waterRecords = waterRecords,
                aiSettings = aiSettingsList.firstOrNull() ?: aiSettings,
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
    val activeWaterTargetMl = currentPlan?.waterTargetMl ?: 2000
    val selectedDateWaterRecords = waterRecords.filter { it.date == selectedDate }
    val drunkWaterMl = selectedDateWaterRecords.sumOf { it.amountMl }

    AiSettingsDialog(
        visible = showAiSettingsDialog,
        settingsList = aiSettingsList,
        onDismiss = { showAiSettingsDialog = false },
        onSave = { updatedSettingsList ->
            aiSettingsList.clear()
            aiSettingsList.addAll(updatedSettingsList)
            aiSettings = updatedSettingsList.firstOrNull { it.selectedForTextWork || it.selectedForVisionWork } ?: updatedSettingsList.firstOrNull() ?: AiSettings()
            showAiSettingsDialog = false
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

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                Screen.Home -> HomeScreen(
                    planName = currentPlan?.name.orEmpty(),
                    targetCalories = activeTargetCalories,
                    targetProtein = activeTargetProtein,
                    targetFat = activeTargetFat,
                    targetCarbs = activeTargetCarbs,
                    waterTargetMl = activeWaterTargetMl,
                    drunkWaterMl = drunkWaterMl,
                    waterRecords = selectedDateWaterRecords,
                    meals = meals.filter { it.date == selectedDate },
                    recentFoods = foods.take(3),
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
                        val totalProtein = (food.protein * servingCount).toInt()
                        val totalFat = (food.fat * servingCount).toInt()
                        val totalCarbs = (food.carbs * servingCount).toInt()
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
                                servingCount = servingCount
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
                    onEditMeal = { meal ->
                        mealEditorState = MealEditorState(meal)
                        currentScreen = Screen.AddMeal
                    },
                    onDeleteMeal = { meal -> meals.remove(meal) },
                    onClearMeals = { meals.removeAll { it.date == selectedDate } }
                )

                Screen.AddMeal -> AddMealScreen(
                    foods = foods,
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
                                    protein = (meal.protein / meal.servingCount).toInt(),
                                    fat = (meal.fat / meal.servingCount).toInt(),
                                    carbs = (meal.carbs / meal.servingCount).toInt(),
                                    servingType = meal.servingType,
                                    category = libraryCategory
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
                    plans = plans,
                    selectedPlanId = dailyPlanSelections.firstOrNull { it.date == selectedDate }?.planId
                        ?: plans.firstOrNull { it.isDefault }?.id,
                    onBack = { currentScreen = Screen.Home },
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
                        val totalProtein = (food.protein * servingCount).toInt()
                        val totalFat = (food.fat * servingCount).toInt()
                        val totalCarbs = (food.carbs * servingCount).toInt()
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
                                servingCount = servingCount
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

@Composable
fun HomeScreen(
    planName: String,
    targetCalories: Int,
    targetProtein: Int,
    targetFat: Int,
    targetCarbs: Int,
    waterTargetMl: Int,
    drunkWaterMl: Int,
    waterRecords: List<WaterRecord>,
    meals: List<MealRecord>,
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
    onAddWater: (Int) -> Unit,
    onDeleteWater: (WaterRecord) -> Unit,
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
            tags = listOf("早餐", "午餐", "晚餐", "零食", "加餐"),
            onDismiss = { showQuickAddDialog = false },
            onConfirm = { food, quantity, recordTags ->
                onQuickAddFood(food, quantity, recordTags)
                showQuickAddDialog = false
            }
        )
    }

    AppBackground {
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

            Text(
                text = "营养进度",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF161A23)
            )

            NutritionRow(
                label = "碳水",
                value = "$eatenCarbs / $targetCarbs g",
                color = Color(0xFF66BB6A),
                progress = nutritionProgress(eatenCarbs, targetCarbs)
            )
            NutritionRow(
                label = "蛋白质",
                value = "$eatenProtein / $targetProtein g",
                color = Color(0xFF5B8DEF),
                progress = nutritionProgress(eatenProtein, targetProtein)
            )
            NutritionRow(
                label = "脂肪",
                value = "$eatenFat / $targetFat g",
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
                        text = "$tag · $tagCalories kcal",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF5B8DEF)
                    )
                    tagMeals.forEach { meal ->
                        MealCard(
                            name = meal.name,
                            calories = "${meal.calories} kcal",
                            amountText = mealAmountText(meal),
                            tagText = meal.tags.joinToString(" · "),
                            nutritionText = "碳水${meal.carbs}g · 蛋白${meal.protein}g · 脂肪${meal.fat}g",
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
    foods: List<FoodItem>,
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
    var perServingProtein by remember(initialMeal) { mutableStateOf(initialMeal?.let { (it.protein / initialServingCount).toInt().toString() }.orEmpty()) }
    var perServingFat by remember(initialMeal) { mutableStateOf(initialMeal?.let { (it.fat / initialServingCount).toInt().toString() }.orEmpty()) }
    var perServingCarbs by remember(initialMeal) { mutableStateOf(initialMeal?.let { (it.carbs / initialServingCount).toInt().toString() }.orEmpty()) }
    var totalProteinInput by remember(initialMeal) { mutableStateOf(initialMeal?.protein?.toString().orEmpty()) }
    var totalFatInput by remember(initialMeal) { mutableStateOf(initialMeal?.fat?.toString().orEmpty()) }
    var totalCarbsInput by remember(initialMeal) { mutableStateOf(initialMeal?.carbs?.toString().orEmpty()) }
    var quantityText by remember(initialMeal) { mutableStateOf(initialMeal?.quantity?.let(::formatServingCount).orEmpty()) }
    var servingType by remember(initialMeal) { mutableStateOf(initialMeal?.servingType ?: ServingType.Per100g) }
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
    var errorText by remember(initialMeal) { mutableStateOf<String?>(null) }
    val hasVisionWorkingAi = aiSettingsList.any { it.enabled && it.selectedForVisionWork && it.supportsVision && it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val imageDataUrl = imageDataUrlFromUri(context, uri)
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
            selectedImageDataUrl = bitmapToJpegDataUrl(bitmap)
            selectedImageLabel = "相机照片 · ${if (recognitionImageType == RecognitionImageType.NutritionLabel) "营养成分表" else "食物照片"}"
            showAiAnalysisDialog = true
            errorText = null
        } else {
            errorText = "没有获取到照片，请重新拍摄"
        }
    }
    val protein = if (inputMode == NutritionInputMode.PerServing) perServingProtein else totalProteinInput
    val fat = if (inputMode == NutritionInputMode.PerServing) perServingFat else totalFatInput
    val carbs = if (inputMode == NutritionInputMode.PerServing) perServingCarbs else totalCarbsInput
    val quantity = quantityText.toDoubleOrNull() ?: 0.0
    val servingBaseAmount = if (servingType == ServingType.PerItem) 1.0 else 100.0
    val inputCalories = calculateCalories(
        protein = protein.toIntOrNull() ?: 0,
        fat = fat.toIntOrNull() ?: 0,
        carbs = carbs.toIntOrNull() ?: 0
    )
    val previewTotalCalories = if (inputMode == NutritionInputMode.PerServing) {
        (inputCalories * quantity / servingBaseAmount).toInt()
    } else {
        inputCalories
    }
    val previewFormulaText = if (inputMode == NutritionInputMode.PerServing && quantity > 0) {
        "自动计算热量：${inputCalories}kcal/${if (servingType == ServingType.PerItem) "个" else "100g"} × ${formatServingCount(quantity)}${if (servingType == ServingType.PerItem) "个" else "g"} = ${previewTotalCalories} kcal"
    } else {
        "自动计算热量：${previewTotalCalories} kcal"
    }

    AppBackground {
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
                onApplySuggestion = { suggestedTags, shouldSaveToLibrary, suggestedCategory ->
                    customTag = suggestedTags.joinToString(";")
                    saveToLibrary = shouldSaveToLibrary
                    libraryCategory = suggestedCategory
                    showAiAnalysisDialog = false
                },
                onApplyRecognizedFood = { recognizedFood, suggestedQuantity, suggestedTags ->
                    name = recognizedFood.name
                    perServingProtein = recognizedFood.protein.toString()
                    perServingFat = recognizedFood.fat.toString()
                    perServingCarbs = recognizedFood.carbs.toString()
                    servingType = recognizedFood.servingType
                    inputMode = NutritionInputMode.PerServing
                    quantityText = formatServingCount(suggestedQuantity)
                    customTag = suggestedTags.joinToString(";")
                    saveToLibrary = true
                    libraryCategory = recognizedFood.category
                    showAiAnalysisDialog = false
                }
            )

            if (foods.isNotEmpty()) {
                QuickPickSection(
                    foods = foods,
                    onPick = { food ->
                        name = food.name
                        perServingProtein = food.protein.toString()
                        perServingFat = food.fat.toString()
                        perServingCarbs = food.carbs.toString()
                        servingType = food.servingType
                        inputMode = NutritionInputMode.PerServing
                        quantityText = ""
                    }
                )
                }

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
                    label = "AI 识别补充描述（可选，例如：炸鸡柳，表面比较油，肉占比约50%）",
                    value = aiFoodDescription,
                    onValueChange = { aiFoodDescription = it }
                )
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
                        text = "记录${servingTypeLabel(servingType)}营养",
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
                    AppTextField(label = "${servingTypeLabel(servingType)}碳水 g", value = perServingCarbs, onValueChange = { perServingCarbs = it }, isNumber = true)
                    AppTextField(label = "${servingTypeLabel(servingType)}蛋白质 g", value = perServingProtein, onValueChange = { perServingProtein = it }, isNumber = true)
                    AppTextField(label = "${servingTypeLabel(servingType)}脂肪 g", value = perServingFat, onValueChange = { perServingFat = it }, isNumber = true)
                } else {
                    AppTextField(label = "总碳水 g", value = totalCarbsInput, onValueChange = { totalCarbsInput = it }, isNumber = true)
                    AppTextField(label = "总蛋白质 g", value = totalProteinInput, onValueChange = { totalProteinInput = it }, isNumber = true)
                    AppTextField(label = "总脂肪 g", value = totalFatInput, onValueChange = { totalFatInput = it }, isNumber = true)
                }
                AppTextField(
                    label = if (servingType == ServingType.PerItem) "食物总量（个）" else "食物总量（克）",
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    isNumber = true
                )
                Text(
                    text = "标签",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF303747)
                )
                if (tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.take(4).forEach { tag ->
                            OutlinedButton(
                                onClick = {
                                    val existingTags = customTag
                                        .split(";", "；", "\n")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                    customTag = if (existingTags.contains(tag)) {
                                        existingTags.filter { it != tag }.joinToString(";")
                                    } else {
                                        (existingTags + tag).distinct().joinToString(";")
                                    }
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                val currentTags = customTag
                                    .split(";", "；", "\n")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                Text(text = if (currentTags.contains(tag)) "✓ $tag" else tag, fontSize = 12.sp)
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
                    onClick = { showAiAnalysisDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "AI 分析此记录", fontSize = 15.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (hasVisionWorkingAi) {
                                cameraLauncher.launch(null)
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
                                galleryLauncher.launch("image/*")
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
                    val inputProtein = protein.toIntOrNull() ?: 0
                    val inputFat = fat.toIntOrNull() ?: 0
                    val inputCarbs = carbs.toIntOrNull() ?: 0
                    val inputQuantity = quantityText.toDoubleOrNull() ?: 0.0
                    val servings = servingCountFromQuantity(inputQuantity, servingType)

                    val finalTags = customTag
                        .split(";", "；", "\n")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()

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
                    if (inputQuantity <= 0 || servings <= 0.0) {
                        errorText = if (servingType == ServingType.PerItem) "请输入有效个数" else "请输入有效克数"
                        return@Button
                    }

                    val totalProtein: Int
                    val totalFat: Int
                    val totalCarbs: Int

                    if (inputMode == NutritionInputMode.PerServing) {
                        totalProtein = (inputProtein * servings).toInt()
                        totalFat = (inputFat * servings).toInt()
                        totalCarbs = (inputCarbs * servings).toInt()
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
                            quantity = inputQuantity,
                            servingCount = servings
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
}

@Composable
fun AiSettingsDialog(
    visible: Boolean,
    settingsList: List<AiSettings>,
    onDismiss: () -> Unit,
    onSave: (List<AiSettings>) -> Unit
) {
    if (!visible) return

    val editingSettings = remember(settingsList) { mutableStateListOf<AiSettings>().apply { addAll(settingsList) } }
    val coroutineScope = rememberCoroutineScope()
    var editingId by remember(settingsList) { mutableStateOf(settingsList.firstOrNull()?.id) }
    val editingAi = editingSettings.firstOrNull { it.id == editingId }
    var providerName by remember(editingAi) { mutableStateOf(editingAi?.providerName.orEmpty()) }
    var baseUrl by remember(editingAi) { mutableStateOf(editingAi?.baseUrl.orEmpty()) }
    var apiKey by remember(editingAi) { mutableStateOf(editingAi?.apiKey.orEmpty()) }
    var modelName by remember(editingAi) { mutableStateOf(editingAi?.modelName.orEmpty()) }
    var temperature by remember(editingAi) { mutableStateOf(editingAi?.temperature?.toString() ?: "0.2") }
    var enabled by remember(editingAi) { mutableStateOf(editingAi?.enabled ?: false) }
    var selectedForVisionWork by remember(editingAi) { mutableStateOf(editingAi?.selectedForVisionWork ?: false) }
    var selectedForTextWork by remember(editingAi) { mutableStateOf(editingAi?.selectedForTextWork ?: false) }
    var supportsVision by remember(editingAi) { mutableStateOf(editingAi?.supportsVision ?: true) }
    var errorText by remember(settingsList) { mutableStateOf<String?>(null) }
    var activeTab by remember(settingsList) { mutableStateOf("presets") }
    var expandedPresetCompany by remember(settingsList) { mutableStateOf<String?>(null) }
    var expandedPresetSection by remember(settingsList) { mutableStateOf<String?>(null) }
    var pendingDeleteAiId by remember(settingsList) { mutableStateOf<Long?>(null) }
    var isTestingAi by remember(settingsList) { mutableStateOf(false) }
    var isFetchingModels by remember(settingsList) { mutableStateOf(false) }
    var fetchedModels by remember(settingsList) { mutableStateOf<List<String>>(emptyList()) }
    fun currentAiSignature(): String {
        return aiVerificationSignature(providerName, baseUrl, apiKey.trim(), modelName.trim())
    }
    fun currentAiVerified(): Boolean {
        return editingAi?.verifiedSignature?.isNotBlank() == true && editingAi.verifiedSignature == currentAiSignature()
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
                val index = editingSettings.indexOfFirst { it.id == currentEditingId }
                if (index >= 0) {
                    editingSettings[index] = AiSettings(
                        id = currentEditingId,
                        providerName = providerName.trim(),
                        baseUrl = normalizedBaseUrl,
                        apiKey = apiKey.trim(),
                        modelName = modelName.trim(),
                        temperature = parsedTemperature,
                        enabled = enabled,
                        selectedForVisionWork = selectedForVisionWork && supportsVision,
                        selectedForTextWork = selectedForTextWork,
                        supportsVision = supportsVision,
                        verifiedSignature = verifiedSignature
                    )
                }
                return true
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
                Text(
                    text = "图片工作 ${editingSettings.count { it.selectedForVisionWork }} / 3，可用 ${editingSettings.count { it.selectedForVisionWork && it.enabled && it.supportsVision && it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() }} 个 · 文字工作 ${editingSettings.count { it.selectedForTextWork }} / 1，可用 ${editingSettings.count { it.selectedForTextWork && it.enabled && it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() }} 个",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF5B8DEF)
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    WebStyleTab(
                        text = "预设",
                        selected = activeTab == "presets",
                        onClick = { activeTab = "presets" },
                        modifier = Modifier.weight(1f)
                    )
                    WebStyleTab(
                        text = "已配置",
                        selected = activeTab == "configs",
                        onClick = { activeTab = "configs" },
                        modifier = Modifier.weight(1f)
                    )
                    WebStyleTab(
                        text = "编辑",
                        selected = activeTab == "edit",
                        onClick = { activeTab = "edit" },
                        modifier = Modifier.weight(1f)
                    )
                }

                when (activeTab) {
                    "presets" -> {
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
                                                                    activeTab = "edit"
                                                                    errorText = "已套用 ${preset.modelName}，可填写 API Key 后新增或更新配置"
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

                    "configs" -> {
                        Text(text = "配置列表", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                        if (editingSettings.isEmpty()) {
                            Text(
                                text = "当前没有 AI 配置。你仍然可以手动记录饮食；真实图片识别和 AI 报告会保持不可用。",
                                fontSize = 13.sp,
                                color = Color(0xFF8A92A1)
                            )
                        } else {
                            editingSettings.forEach { item ->
                                val active = item.id == editingId
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (commitCurrentEditingAi()) {
                                                editingId = item.id
                                                activeTab = "edit"
                                                errorText = null
                                            }
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (active) Color(0xFFE7F0FF) else Color(0xBFFFFFFF))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = item.providerName.ifBlank { "未命名 AI" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
                                        Text(text = item.modelName.ifBlank { "未填模型" }, fontSize = 12.sp, color = Color(0xFF6F7785))
                                        Text(
                                            text = listOfNotNull(
                                                if (item.selectedForVisionWork) "图片工作" else null,
                                                if (item.selectedForTextWork) "文字工作" else null,
                                                if (item.enabled) "已启用" else "未启用",
                                                if (item.supportsVision) "支持图片" else "文本模型",
                                                if (item.apiKey.isNotBlank()) "已填 Key" else "未填 Key"
                                            ).joinToString(" · "),
                                            fontSize = 12.sp,
                                            color = Color(0xFF8A92A1)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Text(text = "当前编辑", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (!commitCurrentEditingAi()) return@OutlinedButton
                                    val newId = (editingSettings.maxOfOrNull { it.id } ?: 0L) + 1L
                                    editingSettings.add(
                                        AiSettings(
                                            id = newId,
                                            providerName = providerName.trim().ifBlank { "AI ${editingSettings.size + 1}" },
                                            baseUrl = normalizeAiBaseUrl(baseUrl) ?: "https://api.openai.com/v1/chat/completions",
                                            apiKey = apiKey.trim(),
                                            modelName = modelName.trim().ifBlank { "gpt-4o-mini" },
                                            temperature = temperature.toDoubleOrNull() ?: 0.2,
                                            selectedForVisionWork = false,
                                            selectedForTextWork = false,
                                            supportsVision = supportsVision
                                        )
                                    )
                                    editingId = newId
                                    errorText = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(text = "新增 AI", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (editingId != null) {
                                        pendingDeleteAiId = editingId
                                    } else {
                                        errorText = "当前没有可删除的 AI 配置"
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(text = "删除当前", fontSize = 12.sp)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                if (enabled) {
                                    enabled = false
                                    errorText = null
                                } else if (currentAiVerified()) {
                                    enabled = true
                                    errorText = null
                                } else {
                                    errorText = "请先点击“检测当前配置”，检测通过后才能启用这个 AI。"
                                }
                            },
                            enabled = editingId != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(text = if (enabled) "✓ 已启用真实 AI" else "检测通过后启用真实 AI")
                        }
                        OutlinedButton(
                            onClick = {
                                if (editingId == null) {
                                    errorText = "请先新增一个 AI 配置"
                                    return@OutlinedButton
                                }
                                if (!supportsVision) {
                                    errorText = "该模型被标记为不支持图片，不能作为图片识别工作 AI"
                                    return@OutlinedButton
                                }
                                val selectedCount = editingSettings.count { it.selectedForVisionWork && it.id != editingId } + if (!selectedForVisionWork) 1 else 0
                                if (!selectedForVisionWork && selectedCount > 3) {
                                    errorText = "图片识别工作 AI 最多只能选择 3 个"
                                } else {
                                    val nextSelectedForVisionWork = !selectedForVisionWork
                                    selectedForVisionWork = nextSelectedForVisionWork
                                    if (nextSelectedForVisionWork && !currentAiVerified()) {
                                        errorText = "已设为图片识别工作 AI；检测通过后才能启用。"
                                    }
                                    val index = editingSettings.indexOfFirst { it.id == editingId }
                                    if (index >= 0) {
                                        editingSettings[index] = editingSettings[index].copy(
                                            selectedForVisionWork = nextSelectedForVisionWork
                                        )
                                    }
                                    if (!nextSelectedForVisionWork) errorText = null
                                }
                            },
                            enabled = editingId != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(text = if (selectedForVisionWork) "✓ 作为图片识别工作 AI" else "作为图片识别工作 AI")
                        }
                        OutlinedButton(
                            onClick = {
                                if (editingId == null) {
                                    errorText = "请先新增一个 AI 配置"
                                    return@OutlinedButton
                                }
                                val nextSelectedForTextWork = !selectedForTextWork
                                selectedForTextWork = nextSelectedForTextWork
                                if (nextSelectedForTextWork && !currentAiVerified()) {
                                    errorText = "已设为文字生成工作 AI；检测通过后才能启用。"
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
                            enabled = editingId != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(text = if (selectedForTextWork) "✓ 作为文字生成工作 AI" else "作为文字生成工作 AI")
                        }
                        AppTextField(label = "Provider 名称", value = providerName, onValueChange = { providerName = it })
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
                        Text(
                            text = if (currentAiVerified()) "当前配置已检测通过，可以启用。修改地址、Key 或模型名后需要重新检测。" else "当前配置尚未检测通过；检测成功后才能启用。",
                            fontSize = 12.sp,
                            color = if (currentAiVerified()) Color(0xFF2E9D63) else Color(0xFF8A92A1)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (!commitCurrentEditingAi()) return@OutlinedButton
                                    val currentSettings = editingSettings.firstOrNull { it.id == editingId } ?: return@OutlinedButton
                                    isTestingAi = true
                                    errorText = "正在检测当前 AI 配置..."
                                    coroutineScope.launch {
                                        val result = runCatching { testAiSettings(currentSettings) }
                                        isTestingAi = false
                                        result.onSuccess {
                                            val signature = aiVerificationSignature(currentSettings.providerName, currentSettings.baseUrl, currentSettings.apiKey, currentSettings.modelName)
                                            val index = editingSettings.indexOfFirst { it.id == currentSettings.id }
                                            if (index >= 0) {
                                                editingSettings[index] = editingSettings[index].copy(verifiedSignature = signature)
                                            }
                                            errorText = "检测通过：${currentSettings.modelName} 可以正常响应。现在可以启用。"
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
                                enabled = editingId != null && !isTestingAi,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(text = if (isTestingAi) "检测中..." else "检测当前配置", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (!commitCurrentEditingAi()) return@OutlinedButton
                                    val currentSettings = editingSettings.firstOrNull { it.id == editingId } ?: return@OutlinedButton
                                    isFetchingModels = true
                                    fetchedModels = emptyList()
                                    errorText = "正在获取模型列表..."
                                    coroutineScope.launch {
                                        val result = runCatching { fetchAiModelList(currentSettings) }
                                        isFetchingModels = false
                                        result.onSuccess { models ->
                                            fetchedModels = models
                                            errorText = if (models.isEmpty()) "接口可访问，但没有解析到模型列表。" else "已获取 ${models.size} 个模型，点击下方模型名即可填入。"
                                        }.onFailure { error ->
                                            errorText = "获取模型列表失败：${friendlyAiReportErrorMessage(error)}"
                                        }
                                    }
                                },
                                enabled = editingId != null && !isFetchingModels,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(text = if (isFetchingModels) "获取中..." else "获取模型列表", fontSize = 12.sp)
                            }
                        }
                        if (fetchedModels.isNotEmpty()) {
                            Text(text = "接口返回的模型", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                            fetchedModels.take(40).forEach { fetchedModel ->
                                OutlinedButton(
                                    onClick = {
                                        modelName = fetchedModel
                                        supportsVision = modelNameLooksVisionCapable(fetchedModel)
                                        if (!supportsVision) selectedForVisionWork = false
                                        enabled = false
                                        errorText = "已选择 $fetchedModel，请检测通过后启用。"
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(text = fetchedModel, fontSize = 12.sp)
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val nextSupportsVision = !supportsVision
                                supportsVision = nextSupportsVision
                                if (!nextSupportsVision) selectedForVisionWork = false
                                enabled = false
                            },
                            enabled = editingId != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(text = if (supportsVision) "✓ 支持图片识别" else "不支持图片识别")
                        }
                        OutlinedButton(
                            onClick = {
                                if (editingId == null) {
                                    errorText = "请先新增一个 AI 配置"
                                    return@OutlinedButton
                                }
                                supportsVision = modelNameLooksVisionCapable(modelName)
                                if (!supportsVision) selectedForVisionWork = false
                                errorText = if (supportsVision) "已根据模型名判断为支持图片" else "已根据模型名判断为文本模型"
                            },
                            enabled = editingId != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(text = "按模型名自动判断图片能力", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                if (commitCurrentEditingAi()) errorText = "当前 AI 配置已更新"
                            },
                            enabled = editingId != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(text = "更新当前 AI 配置", fontSize = 12.sp)
                        }
                    }
                }
                errorText?.let { Text(text = it, color = Color(0xFFEF5350), fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!commitCurrentEditingAi()) return@Button
                    if (editingSettings.count { it.selectedForVisionWork } > 3) {
                        errorText = "图片识别工作 AI 最多只能选择 3 个"
                        return@Button
                    }
                    if (editingSettings.count { it.selectedForTextWork } > 1) {
                        errorText = "文字生成工作 AI 只能选择 1 个"
                        return@Button
                    }
                    onSave(editingSettings.toList())
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "保存全部")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                Text(text = "取消")
            }
        }
    )

    val deleteTarget = editingSettings.firstOrNull { it.id == pendingDeleteAiId }
    if (deleteTarget != null) {
        ConfirmDialog(
            title = "删除 AI 配置",
            message = "确定要删除「${deleteTarget.providerName.ifBlank { "未命名 AI" }} / ${deleteTarget.modelName.ifBlank { "未填模型" }}」吗？这个操作不会删除云端模型，但会移除本机保存的接口、Key 和工作 AI 设置。",
            confirmText = "删除",
            onConfirm = {
                editingSettings.removeAll { it.id == deleteTarget.id }
                if (editingId == deleteTarget.id) {
                    editingId = editingSettings.firstOrNull()?.id
                }
                pendingDeleteAiId = null
                errorText = null
            },
            onDismiss = {
                pendingDeleteAiId = null
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
                settings.selectedForVisionWork && settings.supportsVision
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
        aiSettingsList.filter { it.enabled && it.selectedForVisionWork && !it.supportsVision }
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
                } else {
                    aiErrorText = errors.firstOrNull()?.let(::friendlyAiErrorMessage) ?: "工作 AI 未返回可用结果，已切换为本地候选结果。"
                    if (imageDataUrl != null) {
                        mockImageRecognitionCandidates(foodName)
                    } else {
                        mockRecognizedFoodCandidates(foodName, servingType, proteinText.toIntOrNull() ?: 0, fatText.toIntOrNull() ?: 0, carbsText.toIntOrNull() ?: 0)
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
                    mockRecognizedFoodCandidates(foodName, servingType, proteinText.toIntOrNull() ?: 0, fatText.toIntOrNull() ?: 0, carbsText.toIntOrNull() ?: 0)
                }
            }
            isAnalyzing = false
        }
    }

    val carbs = carbsText.toIntOrNull() ?: 0
    val protein = proteinText.toIntOrNull() ?: 0
    val fat = fatText.toIntOrNull() ?: 0
    val calories = calculateCalories(protein, fat, carbs)
    val category = suggestFoodCategory(foodName)
    val tags = suggestMealTags(foodName, calories, protein, fat, carbs)
    val saveSuggestion = foodName.isNotBlank() && calories > 0
    val recognizedCandidates = if (isAnalyzing) emptyList() else aiCandidates

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "AI 分析此记录", fontWeight = FontWeight.Bold) },
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
                        "本次将并行调用 ${workingAiSettings.size} 个图片识别工作 AI，并${if (textSummaryAiSettings != null) "由文字生成工作 AI 汇总 1 个结果" else "生成 1 个本地平均值结果"}。" +
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
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = candidate.food.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161A23))
                                Text(text = candidate.sourceName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B8DEF))
                                Text(
                                    text = "${candidate.food.category} · ${candidate.food.calories} kcal/${foodServingText(candidate.food)} · 建议 ${formatServingCount(candidate.quantity)}${if (candidate.food.servingType == ServingType.PerItem) "个" else "g"}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF6F7785)
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
                            "说明：如果图片是营养成分表，AI 会优先直接读取表格里的数值，而不是按食物照片去估算。"
                        } else {
                            "说明：图片识别会估算食物类型、份量与营养素，保存前仍建议按实际重量校对。"
                        }
                    } else {
                        "说明：当前记录分析会结合手动输入生成候选结果，也可通过拍照或相册进行图片识别。"
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
    plans: List<NutritionPlan>,
    dailyPlanSelections: List<DailyPlanSelection>,
    selectedDate: String,
    onBack: () -> Unit,
    onSelectDate: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val allRecordDates = (meals.map { it.date } + waterRecords.map { it.date })
        .distinct()
        .sortedDescending()
    var visibleMonth by remember(selectedDate) { mutableStateOf(selectedDate.take(7)) }
    val visibleRecordDates = allRecordDates.filter { it.startsWith(visibleMonth) }
    val visibleMonthMeals = meals.filter { it.date.startsWith(visibleMonth) }
    val visibleMonthWaterRecords = waterRecords.filter { it.date.startsWith(visibleMonth) }

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
                text = "按月份查看摄入概览。",
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
                waterRecords = visibleMonthWaterRecords,
                recordDates = visibleRecordDates
            )

            if (allRecordDates.isEmpty()) {
                EmptyHistoryCard()
            } else if (visibleRecordDates.isEmpty()) {
                EmptyMonthCard(visibleMonth)
            } else {
                visibleRecordDates.forEach { date ->
                    val records = meals.filter { it.date == date }
                    val selectedPlanId = dailyPlanSelections.firstOrNull { it.date == date }?.planId
                    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId }
                    val waterMl = waterRecords.filter { it.date == date }.sumOf { it.amountMl }
                    val planStatusText = when {
                        selectedPlanId == null -> "未选择计划"
                        selectedPlan != null -> "计划：${selectedPlan.name}"
                        else -> "计划出错"
                    }
                    HistoryDateCard(
                        date = date,
                        isSelected = date == selectedDate,
                        meals = records,
                        waterMl = waterMl,
                        planStatusText = planStatusText,
                        targetCalories = selectedPlan?.targetCalories,
                        onClick = { onSelectDate(date) }
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
    val categories = listOf("全部") + foods.map { it.category }.distinct().sorted()
    val filteredFoods = foods.filter { food ->
        val matchesSearch = searchText.isBlank() || food.name.contains(searchText.trim(), ignoreCase = true)
        val matchesCategory = selectedCategory == "全部" || food.category == selectedCategory
        matchesSearch && matchesCategory
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

    if (showAddFoodDialog) {
        FoodEditorDialog(
            initialFood = FoodItem(name = "", protein = 0, fat = 0, carbs = 0, category = "自定义"),
            onDismiss = { showAddFoodDialog = false },
            onSave = { newFood ->
                onAddFood(newFood)
                showAddFoodDialog = false
            }
        )
    }

    editorState?.let { state ->
        FoodEditorDialog(
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

            if (foods.isNotEmpty()) {
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

            if (foods.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = "食物库还是空的", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161A23))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "添加饮食记录时点击“保存到食物库”即可收藏。", fontSize = 13.sp, color = Color(0xFF8A92A1))
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
    plans: List<NutritionPlan>,
    selectedPlanId: Long?,
    onBack: () -> Unit,
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
    var note by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var pendingDeletePlanId by remember { mutableStateOf<Long?>(null) }

    val previewCalories = calculateCalories(
        protein = protein.toIntOrNull() ?: 0,
        fat = fat.toIntOrNull() ?: 0,
        carbs = carbs.toIntOrNull() ?: 0
    )
    val currentPlan = plans.firstOrNull { it.id == selectedPlanId } ?: plans.firstOrNull { it.isDefault } ?: plans.firstOrNull()

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
                        note = ""
                    }
                    pendingDeletePlanId = null
                },
                onDismiss = { pendingDeletePlanId = null }
            )
        }
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
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
                        note = ""
                        errorText = null
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "新建计划")
                }
            }

            if (plans.isEmpty()) {
                FormCard {
                    Text(text = "还没有计划", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161A23))
                    Text(text = "先新建一个计划，用于今天或设为默认。", fontSize = 13.sp, color = Color(0xFF8A92A1))
                }
            } else {
                plans.forEach { plan ->
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
                                text = "${plan.targetCalories} kcal · 蛋白${plan.targetProtein}g 脂肪${plan.targetFat}g 碳水${plan.targetCarbs}g · 饮水${plan.waterTargetMl}ml",
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
                                        protein = plan.targetProtein.toString()
                                        fat = plan.targetFat.toString()
                                        carbs = plan.targetCarbs.toString()
                                        waterTargetMl = plan.waterTargetMl.toString()
                                        note = plan.note
                                        errorText = null
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(text = "编辑", fontSize = 12.sp)
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
                    AppTextField(label = "备注", value = note, onValueChange = { note = it })
                    Text(
                        text = "自动计算目标热量：$previewCalories kcal（蛋白质×4 + 脂肪×9 + 碳水×4）",
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
                        val parsedProtein = protein.toIntOrNull()
                        val parsedFat = fat.toIntOrNull()
                        val parsedCarbs = carbs.toIntOrNull()
                        val parsedWater = waterTargetMl.toIntOrNull()

                        if (name.isBlank()) {
                            errorText = "请输入计划名称"
                            return@Button
                        }
                        if (parsedProtein == null || parsedFat == null || parsedCarbs == null || parsedWater == null) {
                            errorText = "请输入有效计划数据"
                            return@Button
                        }
                        if (parsedProtein < 0 || parsedFat < 0 || parsedCarbs < 0 || parsedWater <= 0) {
                            errorText = "目标值不能为负数，饮水目标需大于 0"
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
    waterTargetMl: Int,
    drunkWaterMl: Int,
    aiSettingsList: List<AiSettings>,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val consumedCalories = meals.sumOf { it.calories }
    val consumedProtein = meals.sumOf { it.protein }
    val consumedFat = meals.sumOf { it.fat }
    val consumedCarbs = meals.sumOf { it.carbs }
    val targetCalories = plan?.targetCalories ?: 0
    val targetProtein = plan?.targetProtein ?: 0
    val targetFat = plan?.targetFat ?: 0
    val targetCarbs = plan?.targetCarbs ?: 0
    val remainingCalories = targetCalories - consumedCalories
    val remainingProtein = targetProtein - consumedProtein
    val remainingFat = targetFat - consumedFat
    val remainingCarbs = targetCarbs - consumedCarbs
    val remainingWater = waterTargetMl - drunkWaterMl
    val reportAiSettings = aiSettingsList.firstOrNull { it.enabled && it.selectedForTextWork && it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() }
    var aiReportText by remember(selectedDate, meals, plan, drunkWaterMl, reportAiSettings) { mutableStateOf<String?>(null) }
    var aiReportError by remember(selectedDate, meals, plan, drunkWaterMl, reportAiSettings) { mutableStateOf<String?>(null) }
    var isGeneratingAiReport by remember(selectedDate, meals, plan, drunkWaterMl, reportAiSettings) { mutableStateOf(false) }

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
                        "今天共记录 ${meals.size} 条饮食，已摄入 $consumedCalories kcal。" +
                            if (plan != null) " 当前计划为「${plan.name}」，目标 $targetCalories kcal。" else " 当前还没有可用计划。"
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF303747)
                )
            }

            ReportSectionCard(title = "热量状态") {
                ReportMetricRow(label = "目标热量", value = if (targetCalories > 0) "$targetCalories kcal" else "未设置")
                ReportMetricRow(label = "已摄入", value = "$consumedCalories kcal")
                ReportMetricRow(
                    label = if (remainingCalories >= 0) "剩余可摄入" else "已超出",
                    value = "${kotlin.math.abs(remainingCalories)} kcal",
                    color = if (remainingCalories >= 0) Color(0xFF35C759) else Color(0xFFEF5350)
                )
                Text(
                    text = when {
                        plan == null -> "请先创建或选择热量计划，以便计算剩余热量。"
                        consumedCalories == 0 -> "暂无摄入数据，无法判断热量完成度。"
                        remainingCalories < 0 -> "今日热量已经超出目标，后续饮食建议以低热量、高饱腹感食物为主。"
                        remainingCalories <= targetCalories * 0.15 -> "今日热量已经接近目标，继续保持即可。"
                        else -> "今日仍有一定热量空间，可以根据蛋白质、碳水和脂肪缺口补充。"
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF6F7785)
                )
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
                ReportMetricRow(label = "饮水目标", value = "$waterTargetMl ml")
                ReportMetricRow(label = "已饮水", value = "$drunkWaterMl ml")
                ReportMetricRow(
                    label = if (remainingWater > 0) "剩余饮水" else "完成状态",
                    value = if (remainingWater > 0) "$remainingWater ml" else "已完成",
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
                        "将使用 ${reportAiSettings.providerName} · ${reportAiSettings.modelName} 生成报告。"
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
                    text = "本报告仅基于你记录的数据进行计算和提示，仅供参考，不构成医疗建议。特殊人群或有疾病管理需求时，请咨询专业人士。",
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

fun nutritionGapText(consumed: Int, target: Int, unit: String): String {
    if (target <= 0) return "已摄入 $consumed$unit · 未设置目标"
    val remaining = target - consumed
    return if (remaining >= 0) {
        "已摄入 $consumed$unit / 目标 $target$unit · 剩余 $remaining$unit"
    } else {
        "已摄入 $consumed$unit / 目标 $target$unit · 超出 ${kotlin.math.abs(remaining)}$unit"
    }
}

fun gapColor(remaining: Int): Color {
    return if (remaining >= 0) Color(0xFF303747) else Color(0xFFEF5350)
}

fun macroSuggestionText(
    remainingProtein: Int,
    remainingFat: Int,
    remainingCarbs: Int,
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

@Composable
fun AppBackground(content: @Composable () -> Unit) {
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
                .padding(20.dp)
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
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = if (isNumber) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        }
    )
}

@Composable
fun SummaryCard(
    planName: String,
    targetCalories: Int,
    eatenCalories: Int,
    remainingCalories: Int
) {
    val progress = nutritionProgress(eatenCalories, targetCalories)
    val percentText = if (targetCalories > 0) "${eatenCalories * 100 / targetCalories}%" else "0%"
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
                text = if (remainingCalories >= 0) "$remainingCalories kcal" else "超出 ${kotlin.math.abs(remainingCalories)} kcal",
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
                    eatenCalories == 0 -> "还没有摄入记录，点击下方按钮添加第一餐。"
                    else -> "今日仍有热量空间，可结合营养素缺口安排饮食。"
                },
                fontSize = 13.sp,
                color = Color(0xFF6F7785)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryItem(label = "目标", value = "$targetCalories")
                SummaryItem(label = "已摄入", value = "$eatenCalories")
                SummaryItem(label = "完成", value = percentText)
            }
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
        Text(text = label, fontSize = 13.sp, color = Color(0xFF8A92A1))
    }
}

@Composable
fun WaterSummaryCard(
    waterTargetMl: Int,
    drunkWaterMl: Int,
    waterRecords: List<WaterRecord>,
    onAddWater: (Int) -> Unit,
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
                    "$drunkWaterMl / $waterTargetMl ml（剩余 $remainingWaterMl ml）"
                } else {
                    "$drunkWaterMl / $waterTargetMl ml（超出 ${kotlin.math.abs(remainingWaterMl)} ml）"
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
                    drunkWaterMl == 0 -> "今天还没有饮水记录，可以从少量多次开始。"
                    remainingWaterMl > 0 -> "继续保持，建议分多次补足剩余饮水。"
                    remainingWaterMl == 0 -> "刚好完成今日饮水目标。"
                    else -> "今日饮水已超过目标，保持舒适节奏即可。"
                },
                fontSize = 13.sp,
                color = Color(0xFF8A92A1)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { onAddWater(100) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(text = "+100ml", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { onAddWater(250) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text(text = "+250ml", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { onAddWater(500) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
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
                            text = "${record.amountMl} ml",
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
    waterRecords: List<WaterRecord>,
    recordDates: List<String>
) {
    val totalCalories = meals.sumOf { it.calories }
    val totalProtein = meals.sumOf { it.protein }
    val totalFat = meals.sumOf { it.fat }
    val totalCarbs = meals.sumOf { it.carbs }
    val totalWaterMl = waterRecords.sumOf { it.amountMl }
    val mealDays = meals.map { it.date }.distinct().size
    val averageCalories = if (mealDays > 0) totalCalories / mealDays else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCCFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "$visibleMonth 月度汇总", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryItem(label = "记录天数", value = "${recordDates.size}")
                SummaryItem(label = "总热量", value = "$totalCalories")
                SummaryItem(label = "日均", value = "$averageCalories")
            }
            Text(
                text = "蛋白${totalProtein}g · 脂肪${totalFat}g · 碳水${totalCarbs}g · 饮水${totalWaterMl}ml",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF303747)
            )
            Text(
                text = if (recordDates.isEmpty()) "本月还没有记录。" else "本月共有 ${recordDates.size} 天存在饮食或饮水记录，其中 $mealDays 天记录了饮食。",
                fontSize = 13.sp,
                color = Color(0xFF8A92A1)
            )
        }
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
    waterMl: Int,
    planStatusText: String,
    targetCalories: Int?,
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
            Text(text = "$calories kcal · 蛋白${protein}g 脂肪${fat}g 碳水${carbs}g · 饮水${waterMl}ml", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
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
                    text = "${food.calories} kcal · ${foodServingText(food)} · 碳水${food.carbs}g 蛋白${food.protein}g 脂肪${food.fat}g",
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
    val previewProtein = food?.let { (it.protein * servingCount).toInt() } ?: 0
    val previewFat = food?.let { (it.fat * servingCount).toInt() } ?: 0
    val previewCarbs = food?.let { (it.carbs * servingCount).toInt() } ?: 0
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
                            text = "${if (item == selectedFood) "✓ " else ""}${item.name} · ${item.calories} kcal/${foodServingText(item)}",
                            fontSize = 12.sp
                        )
                    }
                }
                if (food != null) {
                    AppTextField(
                        label = if (food.servingType == ServingType.PerItem) "食物总量（个）" else "食物总量（克）",
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        isNumber = true
                    )
                    Text(text = "标签", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                    tags.take(6).chunked(3).forEach { rowTags ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTags.forEach { tag ->
                                OutlinedButton(
                                    onClick = {
                                        val existingTags = tagText
                                            .split(";", "；", "\n")
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                        tagText = if (existingTags.contains(tag)) {
                                            existingTags.filter { it != tag }.joinToString(";")
                                        } else {
                                            (existingTags + tag).distinct().joinToString(";")
                                        }
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
                        text = "预计添加：$previewCalories kcal · 碳水${previewCarbs}g 蛋白${previewProtein}g 脂肪${previewFat}g",
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
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBFFFFFFF))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = food.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161A23))
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(text = "${food.category} · ${food.calories} kcal · ${foodServingText(food)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF5350))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(14.dp)) {
                        Text(text = "编辑", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(14.dp)) {
                        Text(text = "删除", fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            NutritionRow(label = "碳水", value = "${food.carbs} g", color = Color(0xFF66BB6A))
            NutritionRow(label = "蛋白质", value = "${food.protein} g", color = Color(0xFF5B8DEF))
            NutritionRow(label = "脂肪", value = "${food.fat} g", color = Color(0xFFFFA726))
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
    val previewProtein = (food.protein * servingCount).toInt()
    val previewFat = (food.fat * servingCount).toInt()
    val previewCarbs = (food.carbs * servingCount).toInt()
    val previewCalories = calculateCalories(previewProtein, previewFat, previewCarbs)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "添加到今日记录", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = food.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161A23))
                Text(
                    text = "${foodServingText(food)}：${food.calories} kcal · 碳水${food.carbs}g 蛋白${food.protein}g 脂肪${food.fat}g",
                    fontSize = 13.sp,
                    color = Color(0xFF6F7785)
                )
                AppTextField(
                    label = if (food.servingType == ServingType.PerItem) "食物总量（个）" else "食物总量（克）",
                    value = quantityText,
                    onValueChange = onQuantityChange,
                    isNumber = true
                )
                Text(text = "标签", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF303747))
                if (tags.isNotEmpty()) {
                    tags.take(6).chunked(3).forEach { rowTags ->
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
                                            if (existingTags.contains(tag)) {
                                                existingTags.filter { it != tag }.joinToString(";")
                                            } else {
                                                (existingTags + tag).distinct().joinToString(";")
                                            }
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
                    text = "预计添加：$previewCalories kcal · 碳水${previewCarbs}g 蛋白${previewProtein}g 脂肪${previewFat}g",
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
    var carbs by remember { mutableStateOf(initialFood.carbs.toString()) }
    var protein by remember { mutableStateOf(initialFood.protein.toString()) }
    var fat by remember { mutableStateOf(initialFood.fat.toString()) }
    var servingType by remember { mutableStateOf(initialFood.servingType) }
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
                if (category == "自定义") {
                    AppTextField(
                        label = "自定义分类名称",
                        value = customCategory,
                        onValueChange = { customCategory = it }
                    )
                }
                AppTextField(label = "${servingTypeLabel(servingType)}碳水 g", value = carbs, onValueChange = { carbs = it }, isNumber = true)
                AppTextField(label = "${servingTypeLabel(servingType)}蛋白质 g", value = protein, onValueChange = { protein = it }, isNumber = true)
                AppTextField(label = "${servingTypeLabel(servingType)}脂肪 g", value = fat, onValueChange = { fat = it }, isNumber = true)
                errorText?.let {
                    Text(text = it, color = Color(0xFFEF5350), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedCarbs = carbs.toIntOrNull()
                    val parsedProtein = protein.toIntOrNull()
                    val parsedFat = fat.toIntOrNull()

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
                            category = finalCategory
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

fun calculateCalories(protein: Int, fat: Int, carbs: Int): Int {
    return protein * 4 + fat * 9 + carbs * 4
}

fun defaultFoodCategories(): List<String> {
    return listOf("主食", "肉蛋奶", "蔬菜", "水果", "饮品", "零食", "调味品", "自定义", "AI 识别")
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
        val candidates = parseAiCandidatesFromText(content, foodName, servingType, proteinText.toIntOrNull() ?: 0, fatText.toIntOrNull() ?: 0, carbsText.toIntOrNull() ?: 0)
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
    waterTargetMl: Int,
    drunkWaterMl: Int
): String {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        withTransientAiRetry {
        val prompt = buildAiNutritionReportPrompt(selectedDate, plan, meals, waterTargetMl, drunkWaterMl)
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
        parseAiCandidatesFromText(content, fallbackName, fallbackServingType, 0, 0, 0)
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
    waterTargetMl: Int,
    drunkWaterMl: Int
): String {
    val consumedCalories = meals.sumOf { it.calories }
    val consumedProtein = meals.sumOf { it.protein }
    val consumedFat = meals.sumOf { it.fat }
    val consumedCarbs = meals.sumOf { it.carbs }
    val mealLines = meals.joinToString("\n") { meal ->
        "- ${meal.time} ${meal.name} ${meal.calories}kcal 碳水${meal.carbs}g 蛋白${meal.protein}g 脂肪${meal.fat}g 标签:${meal.tags.joinToString("/")}"
    }.ifBlank { "暂无饮食记录" }
    return """
你是一个谨慎的营养建议助手。请根据当天记录生成中文营养报告。
要求：
1. 不要输出 Markdown 表格。
2. 分为：今日判断、接下来怎么吃、饮水建议、注意事项。
3. 建议要具体到食物类型，例如优先补充什么、少吃什么。
4. 不要给医疗诊断，只做日常饮食建议。
5. 控制在 250 字以内。

日期：$selectedDate
计划：${plan?.name ?: "未选择计划"}
目标：热量${plan?.targetCalories ?: 0}kcal 蛋白${plan?.targetProtein ?: 0}g 脂肪${plan?.targetFat ?: 0}g 碳水${plan?.targetCarbs ?: 0}g 饮水${waterTargetMl}ml
已摄入：热量${consumedCalories}kcal 蛋白${consumedProtein}g 脂肪${consumedFat}g 碳水${consumedCarbs}g 饮水${drunkWaterMl}ml
饮食记录：
$mealLines
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
每份/每100g营养：热量${candidate.food.calories}kcal 蛋白${candidate.food.protein}g 脂肪${candidate.food.fat}g 碳水${candidate.food.carbs}g
置信度：${candidate.confidence}
说明：${candidate.note}
标签：${candidate.tags.joinToString("/")}
""".trimIndent()
    }.joinToString("\n\n")
    return """
你是一个谨慎的营养记录汇总助手。下面是多个图片识别 AI 对同一张食物图片给出的候选结果。
请综合这些结果，生成 1 个最终建议结果。不要机械平均明显离谱的数值，要解释你采用哪些结果、忽略哪些不确定点。
只返回 JSON 数组，数组里只放 1 个对象，不要返回 Markdown。
字段：name, category, servingType, quantity, protein, fat, carbs, confidence, note, tags。
servingType 只能是 PerItem 或 Per100g。
protein/fat/carbs 表示每个或每100g的克数。
quantity 必须估算这道菜可食用部分的总量；如果 servingType 是 Per100g，quantity 用克数；不要在没有依据时默认 100g。
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
    val sourceText = if (hasImage) {
        if (imageType == RecognitionImageType.NutritionLabel) {
            "请优先根据用户上传的营养成分表直接读取营养素数值，按表格原文提取，不要按食物照片去估算。"
        } else {
            "请优先根据用户上传的食物图片识别食物类型、可见份量和营养素；用户填写的菜名和补充描述是强线索，可用于避免瞎猜。"
        }
    } else {
        "请根据用户手动输入的食物信息生成候选饮食记录。"
    }
    return """
你是一个营养记录助手。$sourceText
生成 1 到 3 个候选饮食记录。
只返回 JSON 数组，不要返回 Markdown。
字段：name, category, servingType, quantity, protein, fat, carbs, confidence, note, tags。
category 只能尽量使用：主食、肉蛋奶、蔬菜、水果、饮品、零食、调味品、自定义、AI 识别。
servingType 只能是 PerItem 或 Per100g。
营养素 protein/fat/carbs 表示每个或每100g的克数。
quantity 必须估算这道菜可食用部分的总量：如果 servingType 是 Per100g，quantity 用克数；如果 servingType 是 PerItem，quantity 用个数。
如果用户没有填写总量，也要根据图片餐盘、容器、食物形态和用户描述估算，不要默认 100g。
如果来自营养成分表，请优先输出表里原本写明的每份或每100g数值；若两者都有，以更适合记录的标准项为准。
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

fun buildOpenAiCompatiblePayload(settings: AiSettings, prompt: String, imageDataUrl: String? = null): String {
    val userContent = if (imageDataUrl.isNullOrBlank()) {
        "\"${prompt.escapeJson()}\""
    } else {
        """
[
      {"type": "text", "text": "${prompt.escapeJson()}"},
      {"type": "image_url", "image_url": {"url": "${imageDataUrl.escapeJson()}"}}
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
    {"role": "system", "content": "你是专业但谨慎的营养记录助手，必须只输出 JSON。"},
    {"role": "user", "content": $userContent}
  ]
}
""".trimIndent()
}

fun buildZhipuPayload(settings: AiSettings, prompt: String, imageDataUrl: String? = null): String {
    val userContent = if (imageDataUrl.isNullOrBlank()) {
        "\"${prompt.escapeJson()}\""
    } else {
        val imagePayload = imageDataUrl.substringAfter("base64,", imageDataUrl)
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

fun buildAnthropicPayload(settings: AiSettings, prompt: String, imageDataUrl: String? = null): String {
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
  "system": "你是专业但谨慎的营养记录助手。",
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
    fallbackProtein: Int,
    fallbackFat: Int,
    fallbackCarbs: Int
): List<RecognizedFoodCandidate> {
    val cleanedText = cleanAiJsonText(text)
    return extractCandidateJsonObjects(cleanedText).mapNotNull { json ->
        val name = extractJsonStringAny(json, "name", "foodName", "food", "title", "食物", "名称", "食物名称")
            ?: fallbackName.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val proteinValue = extractJsonNumberAny(json, "protein", "protein_g", "proteinGram", "蛋白", "蛋白质")
        val fatValue = extractJsonNumberAny(json, "fat", "fat_g", "fatGram", "脂肪")
        val carbsValue = extractJsonNumberAny(json, "carbs", "carbohydrate", "carbohydrates", "carbs_g", "碳水", "碳水化合物")
        if (proteinValue == null && fatValue == null && carbsValue == null && fallbackProtein == 0 && fallbackFat == 0 && fallbackCarbs == 0) {
            return@mapNotNull null
        }
        val category = extractJsonStringAny(json, "category", "分类", "类别") ?: suggestFoodCategory(name)
        val servingTypeText = extractJsonStringAny(json, "servingType", "serving_type", "记录方式", "计量方式")
        val type = parseServingType(servingTypeText) ?: fallbackServingType
        val quantity = extractJsonNumberAny(json, "quantity", "totalQuantity", "estimatedQuantity", "amount", "weight", "grams", "totalWeight", "estimatedWeight", "建议数量", "数量", "总量", "重量", "估算重量") ?: if (type == ServingType.PerItem) 1.0 else 100.0
        val protein = proteinValue?.toInt() ?: fallbackProtein
        val fat = fatValue?.toInt() ?: fallbackFat
        val carbs = carbsValue?.toInt() ?: fallbackCarbs
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
        value.contains("peritem") || value.contains("item") || value.contains("个") || value.contains("份") -> ServingType.PerItem
        value.contains("per100g") || value.contains("100g") || value.contains("每100") || value.contains("克") -> ServingType.Per100g
        else -> null
    }
}

fun averageRecognizedFoodCandidate(candidates: List<RecognizedFoodCandidate>): RecognizedFoodCandidate {
    val first = candidates.first()
    val averageProtein = candidates.map { it.food.protein }.average().toInt()
    val averageFat = candidates.map { it.food.fat }.average().toInt()
    val averageCarbs = candidates.map { it.food.carbs }.average().toInt()
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
    return Regex("\\\"$key\\\"\\s*:\\s*\\\"?(-?\\d+(?:\\.\\d+)?)\\\"?").find(json)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
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

fun imageDataUrlFromUri(context: Context, uri: Uri): String? {
    val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream)
    } ?: return null
    return bitmapToJpegDataUrl(bitmap)
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

fun mockImageRecognitionCandidates(foodName: String): List<RecognizedFoodCandidate> {
    if (foodName.isNotBlank()) {
        val category = suggestFoodCategory(foodName)
        val baseFood = FoodItem(
            name = foodName.trim(),
            protein = if (category == "肉蛋奶") 18 else 6,
            fat = if (category == "零食") 16 else 5,
            carbs = if (category == "主食") 28 else 12,
            category = category
        )
        return listOf(
            RecognizedFoodCandidate(
                food = baseFood,
                quantity = 150.0,
                tags = suggestMealTags(baseFood.name, baseFood.calories, baseFood.protein, baseFood.fat, baseFood.carbs),
                confidence = 0.52,
                note = "本地规则根据食物名称生成，真实图片识别需启用 AI 设置"
            )
        )
    }
    return listOf(
        RecognizedFoodCandidate(
            food = FoodItem(name = "米饭配鸡胸肉", protein = 12, fat = 3, carbs = 25, category = "AI 识别"),
            quantity = 250.0,
            tags = listOf("正餐", "高蛋白"),
            confidence = 0.46,
            note = "本地示例候选，启用真实 AI 后会根据照片内容识别"
        ),
        RecognizedFoodCandidate(
            food = FoodItem(name = "蔬菜沙拉", protein = 2, fat = 4, carbs = 8, category = "AI 识别"),
            quantity = 180.0,
            tags = listOf("加餐", "低脂"),
            confidence = 0.42,
            note = "本地示例候选，请按实际照片确认"
        ),
        RecognizedFoodCandidate(
            food = FoodItem(name = "拿铁咖啡", protein = 8, fat = 7, carbs = 14, servingType = ServingType.PerItem, category = "饮品"),
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
    protein: Int,
    fat: Int,
    carbs: Int
): List<RecognizedFoodCandidate> {
    val normalizedName = foodName.trim().ifBlank { "待识别食物" }
    val category = suggestFoodCategory(normalizedName)
    val baseFood = FoodItem(
        name = normalizedName,
        protein = protein.coerceAtLeast(0),
        fat = fat.coerceAtLeast(0),
        carbs = carbs.coerceAtLeast(0),
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
    calories: Int,
    protein: Int,
    fat: Int,
    carbs: Int
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

fun mealAnalysisRiskText(
    foodName: String,
    quantity: Double,
    calories: Int,
    protein: Int,
    fat: Int,
    carbs: Int,
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
    val tagOrder = listOf("早餐", "午餐", "晚餐", "零食", "加餐")
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

fun nutritionProgress(value: Int, target: Int): Float {
    if (target <= 0) return 0f
    return (value.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

fun calorieStatusText(calories: Int, targetCalories: Int?): String {
    if (targetCalories == null || targetCalories <= 0) return "未设置热量目标"
    if (calories == 0) return "未记录饮食"
    val ratio = calories.toFloat() / targetCalories.toFloat()
    return when {
        ratio < 0.6f -> "摄入不足"
        ratio <= 1.0f -> "接近目标"
        else -> "超出目标"
    }
}

fun calorieStatusColor(calories: Int, targetCalories: Int?): Color {
    if (targetCalories == null || targetCalories <= 0) return Color(0xFF8A92A1)
    if (calories == 0) return Color(0xFF8A92A1)
    val ratio = calories.toFloat() / targetCalories.toFloat()
    return when {
        ratio < 0.6f -> Color(0xFF29B6F6)
        ratio <= 1.0f -> Color(0xFF35C759)
        else -> Color(0xFFEF5350)
    }
}

fun waterProgress(value: Int, target: Int): Float {
    if (target <= 0) return 0f
    return (value.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

fun loadAppState(context: Context): AppState {
    val prefs = context.getSharedPreferences("calorie_free_state", Context.MODE_PRIVATE)
    val targetCalories = prefs.getInt("targetCalories", 2000)
    val targetProtein = prefs.getInt("targetProtein", 120)
    val targetFat = prefs.getInt("targetFat", 65)
    val targetCarbs = prefs.getInt("targetCarbs", 250)
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
    val tagsText = prefs.getString("tags", "早餐\n午餐\n晚餐\n零食").orEmpty()
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
        meals = meals,
        foods = foods,
        tags = tags,
        plans = plans,
        dailyPlanSelections = dailyPlanSelections,
        waterRecords = waterRecords,
        aiSettings = aiSettings,
        aiSettingsList = aiSettingsList
    )
}

fun saveAppState(
    context: Context,
    targetCalories: Int,
    targetProtein: Int,
    targetFat: Int,
    targetCarbs: Int,
    meals: List<MealRecord>,
    foods: List<FoodItem>,
    tags: List<String>,
    plans: List<NutritionPlan> = emptyList(),
    dailyPlanSelections: List<DailyPlanSelection> = emptyList(),
    waterRecords: List<WaterRecord> = emptyList(),
    aiSettings: AiSettings = AiSettings(),
    aiSettingsList: List<AiSettings> = emptyList()
) {
    val mealsText = meals.joinToString("\n") { encodeMealRecord(it) }
    val foodsText = foods.joinToString("\n") { encodeFoodItem(it) }
    val tagsText = tags.joinToString("\n")
    val plansText = plans.joinToString("\n") { encodeNutritionPlan(it) }
    val dailyPlanSelectionsText = dailyPlanSelections.joinToString("\n") { encodeDailyPlanSelection(it) }
    val waterRecordsText = waterRecords.joinToString("\n") { encodeWaterRecord(it) }
    val aiSettingsText = encodeAiSettings(aiSettings)
    val aiSettingsListText = aiSettingsList.joinToString("\n") { encodeAiSettings(it) }
    context.getSharedPreferences("calorie_free_state", Context.MODE_PRIVATE)
        .edit()
        .putInt("targetCalories", targetCalories)
        .putInt("targetProtein", targetProtein)
        .putInt("targetFat", targetFat)
        .putInt("targetCarbs", targetCarbs)
        .putString("meals", mealsText)
        .putString("foods", foodsText)
        .putString("tags", tagsText)
        .putString("plans", plansText)
        .putString("dailyPlanSelections", dailyPlanSelectionsText)
        .putString("waterRecords", waterRecordsText)
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
        settings.verifiedSignature.encodeStorageField()
    ).joinToString("|")
}

fun decodeAiSettings(line: String): AiSettings? {
    if (line.isBlank()) return null
    val parts = line.split("|")
    return when {
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
            amountMl = parts[2].toIntOrNull() ?: return null
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
        plan.isDefault.toString(),
        plan.note.encodeStorageField()
    ).joinToString("|")
}

fun decodeNutritionPlan(line: String): NutritionPlan? {
    val parts = line.split("|")
    return if (parts.size >= 9) {
        NutritionPlan(
            id = parts[0].toLongOrNull() ?: return null,
            name = parts[1].decodeStorageField(),
            targetCalories = parts[2].toIntOrNull() ?: return null,
            targetProtein = parts[3].toIntOrNull() ?: return null,
            targetFat = parts[4].toIntOrNull() ?: return null,
            targetCarbs = parts[5].toIntOrNull() ?: return null,
            waterTargetMl = parts[6].toIntOrNull() ?: 2000,
            isDefault = parts[7].toBooleanStrictOrNull() ?: false,
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
        meal.tags.joinToString(",") { it.encodeStorageField() }.encodeStorageField()
    ).joinToString("|")
}

fun decodeMealRecord(line: String): MealRecord? {
    val parts = line.split("|")
    return when (parts.size) {
        6 -> MealRecord(
            name = parts[0].decodeStorageField(),
            calories = parts[1].toIntOrNull() ?: return null,
            protein = parts[2].toIntOrNull() ?: return null,
            fat = parts[3].toIntOrNull() ?: return null,
            carbs = parts[4].toIntOrNull() ?: return null,
            time = parts[5].decodeStorageField()
        )
        10, 11 -> {
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
                listOf("早餐")
            }
            MealRecord(
                name = parts[0].decodeStorageField(),
                calories = parts[1].toIntOrNull() ?: return null,
                protein = parts[2].toIntOrNull() ?: return null,
                fat = parts[3].toIntOrNull() ?: return null,
                carbs = parts[4].toIntOrNull() ?: return null,
                time = parts[5].decodeStorageField(),
                date = parts[6].decodeStorageField(),
                tags = storedTags,
                servingType = type,
                quantity = quantity,
                servingCount = servingCountFromQuantity(quantity, type)
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
        food.category.encodeStorageField()
    ).joinToString("|")
}

fun decodeFoodItem(line: String): FoodItem? {
    val parts = line.split("|")
    return when (parts.size) {
        4 -> FoodItem(
            name = parts[0].decodeStorageField(),
            protein = parts[1].toIntOrNull() ?: return null,
            fat = parts[2].toIntOrNull() ?: return null,
            carbs = parts[3].toIntOrNull() ?: return null
        )
        5, 6 -> FoodItem(
            name = parts[0].decodeStorageField(),
            protein = parts[1].toIntOrNull() ?: return null,
            fat = parts[2].toIntOrNull() ?: return null,
            carbs = parts[3].toIntOrNull() ?: return null,
            servingType = runCatching { ServingType.valueOf(parts[4]) }.getOrDefault(ServingType.Per100g),
            category = parts.getOrNull(5)?.decodeStorageField()?.takeIf { it.isNotBlank() } ?: "自定义"
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

fun foodServingText(food: FoodItem): String {
    return if (food.servingType == ServingType.PerItem) {
        "每个"
    } else {
        "每100g"
    }
}

fun mealAmountText(meal: MealRecord): String {
    return if (meal.servingType == ServingType.PerItem) {
        "${formatServingCount(meal.quantity)} 个"
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

fun servingTypeLabel(servingType: ServingType): String {
    return if (servingType == ServingType.PerItem) "每个" else "每100g"
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

fun currentTimeText(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    CalorieFreeApp()
}
