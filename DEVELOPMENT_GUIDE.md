# CalorieFree 开发指南

> 本文档基于 `reference.md` 与既定技术方案编写，用于指导 CalorieFree 安卓应用从 0 到 1 的产品设计、技术架构、UI 美术规划、前端开发、后端/AI 接入、本地数据建模、测试与发布。

---

## 1. 项目定位

CalorieFree 是一款用于监测用户每日卡路里与营养素摄入的 Android 应用。

核心目标：

1. 用户可以通过拍照识别食物热量和营养素。
2. 用户可以维护自己的食物热量资料库。
3. 用户可以制定多个热量/营养计划，并选择每日计划。
4. 应用可以自动计算当日剩余可摄入热量和营养素。
5. 用户可以通过日历查看历史摄入情况。
6. 应用可以借助大语言模型 API 生成营养分析报告。
7. UI 风格采用暗色/浅色模式兼容的毛玻璃、磨砂、半透明视觉风格，整体氛围参考网易云音乐式高级感。

---

## 2. 推荐技术栈总览

### 2.1 Android 客户端

| 模块 | 技术 |
|---|---|
| 开发语言 | Kotlin |
| UI 框架 | Jetpack Compose |
| 设计系统 | Material 3 |
| 架构模式 | MVVM + Repository + UseCase |
| 本地数据库 | Room + SQLite |
| 设置存储 | Jetpack DataStore |
| 安全存储 | Android Keystore / EncryptedSharedPreferences |
| 依赖注入 | Hilt |
| 网络请求 | Retrofit + OkHttp |
| JSON 序列化 | Kotlinx Serialization |
| 异步 | Kotlin Coroutines + Flow |
| 拍照 | CameraX |
| 图片加载 | Coil |
| 后台任务 | WorkManager |
| 日历 | Kizitonwose Calendar 或 Compose 自定义日历 |
| 图表 | Vico Charts 或 Compose Canvas 自绘 |
| 测试 | JUnit + MockK + Turbine + Compose UI Test |

### 2.2 AI 与后端

| 模块 | 技术/方案 |
|---|---|
| 食物识别 | 多 Vision LLM Provider Adapter |
| 营养报告 | Text LLM Provider |
| API 调用方式 | 客户端直连或后端代理 |
| 图片处理 | 本地压缩、Base64 或 Multipart 上传 |
| 结果融合 | 多模型投票、中位数、置信度排序 |
| 敏感信息保护 | API Key 加密存储或后端代理隐藏 |

---

## 3. 产品功能规划

### 3.1 功能一：拍照识别食物热量

优先级：重要。

用户流程：

```text
首页点击拍照
↓
CameraX 打开相机
↓
用户拍摄食物图片
↓
本地压缩图片
↓
调用一个或多个大语言模型 Vision API
↓
获取识别结果
↓
展示候选食物、重量、热量、营养素、置信度
↓
用户确认或修改
↓
写入今日饮食记录
↓
可选择写入食物资料库
```

关键要求：

1. 识别结果必须允许用户确认，不应直接写入。
2. AI 结果需要保留原始响应，便于调试和后期优化。
3. 当多个模型结果差异较大时，提示用户手动校正。
4. 优先匹配本地食物资料库，减少 AI 估算误差。
5. 支持失败重试。

---

### 3.2 功能二：日历查看历史

优先级：次要。

用户流程：

```text
进入日历页
↓
显示月视图
↓
每日显示热量完成状态
↓
点击某一天
↓
进入当天详情
↓
查看热量、宏量营养素、饮食记录、AI 分析
```

日历状态建议：

| 状态 | UI 表现 |
|---|---|
| 未记录 | 无标记或灰色小点 |
| 摄入不足 | 蓝色/青色小点 |
| 接近目标 | 绿色小点 |
| 超出目标 | 红色/橙色小点 |
| 有 AI 报告 | 小星标或渐变描边 |

---

### 3.3 功能三：食物热量资料库

优先级：重要。

功能内容：

1. 内置默认食物数据。
2. 用户可以新增、编辑、删除自定义食物。
3. AI 识别结果可以一键转为食物库条目。
4. 饮食记录优先关联食物库。
5. 支持搜索、分类、收藏、最近使用。

建议分类：

```text
主食
肉蛋奶
蔬菜
水果
饮品
零食
调味品
自定义
AI 识别
```

---

### 3.4 功能四：热量计划

优先级：最重要。

功能内容：

1. 用户可以创建多个计划。
2. 每个计划包含每日热量目标和营养素目标。
3. 用户可以选择当天使用哪个计划。
4. 应用根据当天摄入自动计算剩余热量和营养素。
5. 首页重点展示今日计划完成进度。

计划字段建议：

```text
计划名称
每日热量目标 kcal
蛋白质目标 g
脂肪目标 g
碳水目标 g
膳食纤维目标 g
饮水目标 ml
是否默认计划
备注
```

计算逻辑：

```text
剩余热量 = 每日热量目标 - 今日已摄入热量
剩余蛋白质 = 蛋白质目标 - 今日已摄入蛋白质
剩余脂肪 = 脂肪目标 - 今日已摄入脂肪
剩余碳水 = 碳水目标 - 今日已摄入碳水
剩余饮水 = 饮水目标 - 今日已饮水量
```

当剩余值小于 0 时，应显示超标状态。

---

### 3.5 功能五：AI 营养分析报告

优先级：次要。

报告内容：

1. 今天吃得是否合理。
2. 还可以补充什么食物。
3. 饮水建议。
4. 蛋白质、脂肪、碳水是否均衡。
5. 膳食纤维、矿物质摄入建议。
6. 晚餐/加餐建议。
7. 注意事项。

报告生成流程：

```text
读取今日或指定日期饮食记录
↓
读取对应热量计划
↓
本地计算营养素差值
↓
组装结构化 JSON Prompt
↓
调用 LLM 文本接口
↓
返回自然语言报告
↓
缓存到本地数据库
```

---

## 4. 整体架构设计

### 4.1 推荐架构

```text
UI Layer
- Compose Screen
- ViewModel
- UiState
- UiEvent

Domain Layer
- UseCase
- Domain Model
- NutritionCalculator
- AiResultMerger

Data Layer
- Repository Implementation
- Room DAO
- DataStore
- Remote AI DataSource
- Local File Storage
```

### 4.2 推荐目录结构

```text
app/src/main/java/{package}/
├── CalorieFreeApplication.kt
├── MainActivity.kt
├── core/
│   ├── database/
│   │   ├── AppDatabase.kt
│   │   ├── dao/
│   │   └── entity/
│   ├── datastore/
│   ├── network/
│   ├── security/
│   ├── ui/
│   │   ├── component/
│   │   ├── theme/
│   │   └── glass/
│   ├── util/
│   └── worker/
├── domain/
│   ├── model/
│   ├── repository/
│   ├── usecase/
│   └── calculator/
├── data/
│   ├── repository/
│   ├── local/
│   ├── remote/
│   └── mapper/
├── ai/
│   ├── provider/
│   ├── prompt/
│   ├── merger/
│   └── model/
└── feature/
    ├── today/
    ├── camera/
    ├── recognition/
    ├── fooddb/
    ├── plan/
    ├── calendar/
    ├── report/
    └── settings/
```

---

## 5. 前端 UI 规划

### 5.1 视觉关键词

```text
毛玻璃
磨砂透明
圆角卡片
渐变背景
轻量阴影
柔和高光
暗色模式
浅色模式
沉浸式状态栏
高级感
```

### 5.2 主色方案

浅色模式：

```text
背景：#F7F8FA
主色：#FF4D6D 或 #FF5A7A
辅助色：#6C63FF
成功色：#35C759
警告色：#FFB020
错误色：#FF453A
文字主色：#1C1C1E
文字次色：#6E6E73
卡片背景：rgba(255,255,255,0.55)
```

暗色模式：

```text
背景：#0F1014
主色：#FF5A7A
辅助色：#8A7CFF
成功色：#30D158
警告色：#FFD60A
错误色：#FF453A
文字主色：#F5F5F7
文字次色：#A1A1AA
卡片背景：rgba(30,30,36,0.55)
```

### 5.3 背景设计

推荐使用渐变光斑背景：

```text
左上：粉红/红色模糊光斑
右上：紫色模糊光斑
底部：深蓝/黑色渐变
中间：半透明玻璃卡片
```

Compose 实现思路：

1. 使用 Box 作为根容器。
2. 背景绘制多个 RadialGradient。
3. 内容区域使用半透明卡片叠加。
4. 卡片使用 blur、alpha、border、shadow 营造玻璃感。

### 5.4 通用组件

建议封装：

```text
GlassCard
GlassScaffold
NutritionProgressRing
MacroProgressBar
BlurredBackground
GradientIconButton
FoodImageCard
AiLoadingDialog
PlanSelectorSheet
DateNutritionSummaryCard
```

### 5.5 底部导航

推荐 5 个主 Tab：

```text
今日
拍照
资料库
日历
我的
```

其中拍照可以作为中间凸起按钮。

### 5.6 首页布局

首页是最重要页面，应优先开发。

布局建议：

```text
顶部：问候语 + 今日日期 + 设置入口
↓
今日计划选择卡片
↓
今日剩余热量大卡片
↓
蛋白质/脂肪/碳水进度条
↓
快速操作：拍照识别、手动添加、喝水记录
↓
今日饮食记录列表
↓
AI 分析报告入口
```

首页关键 UI：

```text
剩余热量：大字号
目标热量：小字号
完成进度：环形进度
超标时：颜色变为橙红
```

### 5.7 拍照识别页面

状态流转：

```text
CameraPreview
↓
拍照成功
↓
图片确认
↓
AI 识别中
↓
候选结果页
↓
用户确认/编辑
↓
写入记录
```

识别中 UI：

1. 显示图片预览。
2. 显示模型调用状态。
3. 如果多模型识别，显示每个模型的进度。
4. 提供取消按钮。

### 5.8 食物资料库页面

布局：

```text
搜索框
分类 Chips
最近使用
默认食物
自定义食物
AI 识别食物
```

支持操作：

1. 添加食物。
2. 编辑食物。
3. 删除用户自定义食物。
4. 选择食物添加到今日记录。
5. 查看每 100g 营养信息。

### 5.9 计划页面

布局：

```text
计划列表
当前默认计划标识
新建计划按钮
计划详情编辑页
```

计划卡片显示：

```text
计划名称
每日 kcal
蛋白质/脂肪/碳水目标
是否默认
今日是否使用
```

### 5.10 日历页面

布局：

```text
月份切换
月历网格
每日完成度标记
点击日期后底部弹窗展示当天详情
```

### 5.11 报告页面

报告页面展示：

```text
今日总结
营养缺口
推荐食物
饮水建议
矿物质/膳食纤维建议
注意事项
```

应提供：

1. 重新生成报告。
2. 复制报告。
3. 分享报告。
4. 查看 Prompt 输入摘要。

---

## 6. 数据库设计

### 6.1 FoodEntity

用于存储默认食物、用户自定义食物、AI 识别后保存的食物。

字段建议：

```text
id: Long
name: String
category: String
brand: String?
caloriesPer100g: Double
proteinPer100g: Double
fatPer100g: Double
carbsPer100g: Double
fiberPer100g: Double?
sugarPer100g: Double?
sodiumPer100g: Double?
sourceType: String // default, user, ai
imageUri: String?
isFavorite: Boolean
createdAt: Long
updatedAt: Long
```

### 6.2 MealRecordEntity

用于存储每日饮食记录。

```text
id: Long
date: String // yyyy-MM-dd
mealType: String // breakfast, lunch, dinner, snack
foodId: Long?
foodNameSnapshot: String
amountGram: Double
calories: Double
protein: Double
fat: Double
carbs: Double
fiber: Double?
sodium: Double?
imageUri: String?
createdAt: Long
updatedAt: Long
```

说明：

`foodNameSnapshot` 是必要字段，因为食物资料库条目未来可能被修改，历史记录应保留当时名称。

### 6.3 NutritionPlanEntity

用于存储热量计划。

```text
id: Long
name: String
dailyCaloriesTarget: Double
proteinTarget: Double
fatTarget: Double
carbsTarget: Double
fiberTarget: Double?
waterTargetMl: Int
isDefault: Boolean
note: String?
createdAt: Long
updatedAt: Long
```

### 6.4 DailyPlanSelectionEntity

用于记录某一天选择了哪个计划。

```text
date: String // yyyy-MM-dd
planId: Long
createdAt: Long
updatedAt: Long
```

### 6.5 AiRecognitionEntity

用于保存 AI 识别记录。

```text
id: Long
imageUri: String
providers: String
rawResponse: String
mergedResultJson: String
selectedFoodName: String?
estimatedWeightGram: Double?
estimatedCalories: Double?
confidence: Double?
status: String // success, failed, cancelled
createdAt: Long
```

### 6.6 AiReportEntity

用于缓存 AI 营养报告。

```text
id: Long
date: String
planId: Long?
inputJson: String
reportText: String
provider: String
createdAt: Long
updatedAt: Long
```

### 6.7 WaterRecordEntity

用于饮水记录。

```text
id: Long
date: String
amountMl: Int
createdAt: Long
```

---

## 7. 核心计算逻辑

### 7.1 食物按克数换算

```text
实际热量 = 每 100g 热量 * 摄入克数 / 100
实际蛋白质 = 每 100g 蛋白质 * 摄入克数 / 100
实际脂肪 = 每 100g 脂肪 * 摄入克数 / 100
实际碳水 = 每 100g 碳水 * 摄入克数 / 100
```

### 7.2 今日汇总

```text
今日总热量 = sum(MealRecord.calories)
今日总蛋白质 = sum(MealRecord.protein)
今日总脂肪 = sum(MealRecord.fat)
今日总碳水 = sum(MealRecord.carbs)
```

### 7.3 计划完成度

```text
热量完成度 = 今日总热量 / 每日热量目标
蛋白质完成度 = 今日蛋白质 / 蛋白质目标
脂肪完成度 = 今日脂肪 / 脂肪目标
碳水完成度 = 今日碳水 / 碳水目标
```

UI 中完成度建议限制显示范围：

```text
progress = min(value / target, 1.0)
```

但如果超标，应额外显示超标数值。

---

## 8. AI 识别设计

### 8.1 Provider 抽象

设计统一接口：

```kotlin
interface FoodVisionProvider {
    val providerName: String
    suspend fun recognizeFood(imageBytes: ByteArray): FoodRecognitionResult
}
```

统一结果模型：

```kotlin
data class FoodRecognitionResult(
    val provider: String,
    val foods: List<RecognizedFood>,
    val rawResponse: String,
    val confidence: Double?
)

data class RecognizedFood(
    val name: String,
    val estimatedWeightGram: Double?,
    val calories: Double?,
    val protein: Double?,
    val fat: Double?,
    val carbs: Double?,
    val confidence: Double?
)
```

### 8.2 多模型融合策略

当启用多个模型时：

```text
1. 并发请求多个 Vision Provider。
2. 将结果统一转换为 FoodRecognitionResult。
3. 对食物名称进行相似度归并。
4. 对热量、重量、营养素取中位数。
5. 根据 provider 权重和 confidence 排序。
6. 输出一个推荐结果和多个候选结果。
```

建议：

```text
如果模型差异小：直接给出推荐结果。
如果模型差异大：提示“识别结果存在差异，请手动确认”。
如果全部失败：展示失败原因和重试按钮。
```

### 8.3 Prompt 设计原则

Vision 模型 Prompt 应要求返回结构化 JSON。

示例要求：

```text
请识别图片中的所有食物，并估算每种食物的重量、热量和主要营养素。
请只返回 JSON，不要返回 Markdown。
字段包括：name, estimatedWeightGram, calories, protein, fat, carbs, confidence, note。
如果无法确定，请给出较低 confidence。
```

### 8.4 AI 识别注意事项

1. AI 对重量估算天然不准，必须允许用户编辑。
2. 同一食物不同烹饪方式热量差异很大，应显示“估算值”。
3. 不应把 AI 结果当作医学建议。
4. 保存原始响应用于排查。
5. 应限制图片大小，避免 API 费用过高。

---

## 9. AI 营养报告设计

### 9.1 输入结构

```json
{
  "date": "2026-01-01",
  "plan": {
    "calories": 2000,
    "protein": 120,
    "fat": 60,
    "carbs": 220,
    "waterMl": 2000
  },
  "consumed": {
    "calories": 1450,
    "protein": 70,
    "fat": 50,
    "carbs": 160,
    "waterMl": 900
  },
  "remaining": {
    "calories": 550,
    "protein": 50,
    "fat": 10,
    "carbs": 60,
    "waterMl": 1100
  },
  "meals": []
}
```

### 9.2 输出要求

AI 应输出：

```text
今日总结
营养素缺口
建议补充食物
饮水建议
晚餐/加餐建议
注意事项
```

### 9.3 报告限制

1. 报告应标注为参考建议。
2. 不应给出疾病诊断。
3. 对孕妇、慢病患者、运动员等特殊人群应建议咨询专业人士。

---

## 10. 网络层设计

### 10.1 Retrofit/OkHttp 配置

建议配置：

```text
连接超时：30 秒
读取超时：120 秒
写入超时：120 秒
AI 请求重试：最多 2 次
日志：Debug 环境开启，Release 关闭敏感信息
```

### 10.2 API Key 管理

推荐两种方案：

#### 方案 A：客户端直连

适合个人项目或离线优先项目。

要求：

1. API Key 使用 Android Keystore 加密。
2. 不要写死在代码里。
3. 设置页允许用户填写自己的 API Key。

#### 方案 B：后端代理

适合正式上线。

优势：

1. 隐藏 API Key。
2. 可以统一做限流。
3. 可以统一做日志和错误分析。
4. 可以降低滥用风险。

后端代理接口建议：

```text
POST /api/ai/recognize-food
POST /api/ai/nutrition-report
GET  /api/health
```

---

## 11. 后端规划

当前项目可以先不强依赖后端，但如果要正式产品化，推荐加入轻量后端代理。

### 11.1 后端职责

```text
AI API Key 保护
请求限流
用户鉴权
AI Provider 路由
日志审计
错误监控
远程配置
未来云同步
```

### 11.2 推荐后端技术

可选方案：

```text
Ktor + Kotlin
或
Node.js + NestJS
或
Spring Boot
```

如果希望与 Android 技术栈统一，推荐：

```text
Ktor + Kotlin
```

### 11.3 后端接口设计

#### 11.3.1 食物图片识别

```text
POST /api/ai/recognize-food
Content-Type: multipart/form-data
```

请求：

```text
image: File
providers: String[]
locale: zh-CN
```

响应：

```json
{
  "success": true,
  "result": {
    "foods": [
      {
        "name": "米饭",
        "estimatedWeightGram": 150,
        "calories": 174,
        "protein": 3.9,
        "fat": 0.45,
        "carbs": 38.7,
        "confidence": 0.82
      }
    ],
    "confidence": 0.82,
    "rawProviderResults": []
  }
}
```

#### 11.3.2 营养报告

```text
POST /api/ai/nutrition-report
Content-Type: application/json
```

请求：

```json
{
  "date": "2026-01-01",
  "plan": {},
  "consumed": {},
  "remaining": {},
  "meals": []
}
```

响应：

```json
{
  "success": true,
  "reportText": "..."
}
```

### 11.4 后端安全

1. 所有接口必须 HTTPS。
2. 对图片大小做限制。
3. 对单用户请求频率做限制。
4. 不记录用户敏感图片，除非用户明确授权。
5. 日志中不要输出 API Key。
6. 对 AI 返回内容做基础过滤。

---

## 12. 状态管理设计

### 12.1 UiState

每个页面使用稳定的 UiState。

示例：首页状态：

```kotlin
data class TodayUiState(
    val isLoading: Boolean = false,
    val selectedDate: LocalDate,
    val selectedPlan: NutritionPlan? = null,
    val consumedCalories: Double = 0.0,
    val remainingCalories: Double = 0.0,
    val consumedProtein: Double = 0.0,
    val consumedFat: Double = 0.0,
    val consumedCarbs: Double = 0.0,
    val mealRecords: List<MealRecord> = emptyList(),
    val errorMessage: String? = null
)
```

### 12.2 UiEvent

用户行为使用 UiEvent：

```kotlin
sealed interface TodayUiEvent {
    data object OnCameraClick : TodayUiEvent
    data object OnAddMealClick : TodayUiEvent
    data class OnDateChange(val date: LocalDate) : TodayUiEvent
    data class OnDeleteMeal(val id: Long) : TodayUiEvent
}
```

---

## 13. 权限设计

需要申请的权限：

```text
CAMERA：拍照识别
READ_MEDIA_IMAGES：选择本地图片，Android 13+
READ_EXTERNAL_STORAGE：旧版本兼容，可尽量避免
POST_NOTIFICATIONS：如需要提醒喝水/记录饮食
```

权限原则：

1. 只在需要时申请。
2. 拒绝后提供解释。
3. 用户拒绝相机权限时允许手动录入食物。

---

## 14. 开发阶段规划

### 14.1 第一阶段：项目基础

目标：搭建可运行项目骨架。

任务：

```text
配置 Gradle Kotlin DSL
接入 Compose Material 3
接入 Hilt
接入 Room
接入 DataStore
搭建导航结构
实现暗色/浅色主题
实现基础 GlassCard 组件
```

### 14.2 第二阶段：核心本地功能

目标：不用 AI 也能完整记录热量。

任务：

```text
食物资料库 CRUD
热量计划 CRUD
今日饮食记录 CRUD
首页剩余热量计算
手动添加食物记录
饮水记录
```

### 14.3 第三阶段：拍照识别

目标：接入单个 Vision 模型。

任务：

```text
CameraX 拍照
图片压缩
AI Provider 抽象
单模型识别
识别结果确认页
写入饮食记录
写入食物资料库
```

### 14.4 第四阶段：日历与报告

任务：

```text
日历月视图
日期详情页
AI 营养报告
报告缓存
历史趋势图表
```

### 14.5 第五阶段：增强体验

任务：

```text
多模型融合识别
图表优化
动画优化
性能优化
错误监控
数据导入导出
后端代理可选接入
```

---

## 15. 测试策略

### 15.1 单元测试

重点测试：

```text
营养素换算
今日汇总
计划剩余计算
AI 结果融合
日期处理
Repository 数据流
```

### 15.2 UI 测试

重点测试：

```text
首页是否正确显示剩余热量
添加食物流程
计划切换流程
日历点击流程
识别结果确认流程
```

### 15.3 数据库测试

重点测试：

```text
DAO 插入/查询/删除
日期范围查询
默认食物导入
计划选择查询
```

---

## 16. 性能优化

### 16.1 Compose 性能

1. 避免在 Composable 中直接执行复杂计算。
2. 使用 `remember` 和 `derivedStateOf`。
3. 列表使用稳定 key。
4. ViewModel 中提前聚合数据。
5. 大图使用 Coil 缩略图。

### 16.2 图片优化

1. 上传 AI 前压缩。
2. 限制最长边，例如 1024 或 1280。
3. 本地只保存必要图片。
4. Room 中只存 URI，不存二进制图片。

### 16.3 数据库优化

1. 对 `date` 建索引。
2. 对 `foodName` 建索引。
3. 对 `createdAt` 建索引。
4. 分页加载历史记录。

---

## 17. 隐私与安全

1. 用户饮食记录属于隐私数据。
2. 默认本地存储，不上传云端。
3. 若使用后端代理，必须在隐私政策中说明。
4. API Key 不得明文保存在代码或日志里。
5. 图片上传前应提示用户会发送给 AI 服务商。
6. 用户应可以删除所有本地数据。
7. AI 报告应标注“仅供参考，不构成医疗建议”。

---

## 18. 发布前检查清单

```text
暗色模式正常
浅色模式正常
首页计算准确
食物资料库可用
计划创建与切换正常
相机权限流程正常
AI 失败可重试
无 API Key 明文日志
数据库迁移正常
离线模式可基本使用
隐私提示完整
Release 构建通过
```

---

## 19. 最终推荐 MVP 范围

第一版最小可用产品建议包含：

```text
首页今日热量展示
热量计划系统
食物资料库
手动添加饮食记录
CameraX 拍照
单个 AI Vision 识别
识别结果确认
日历历史查看
暗色/浅色模式
毛玻璃 UI 基础组件
```

多模型识别、复杂图表、AI 报告、后端代理可以作为后续版本迭代。

---

## 20. 总结

CalorieFree 的最佳实现思路是：

```text
以 Room 本地数据库为核心，保证饮食、食物库和计划数据稳定可靠；
以 Jetpack Compose + Material 3 构建现代化毛玻璃 UI；
以 Hilt、Repository、UseCase、Flow 保证代码可维护；
以 CameraX + 多 LLM Provider Adapter 实现拍照识别；
以 AI 报告增强用户体验；
以后端代理作为正式上线后的安全增强方案。
```

该架构既能快速完成 MVP，也能支持后续扩展为更完整的智能饮食管理应用。
