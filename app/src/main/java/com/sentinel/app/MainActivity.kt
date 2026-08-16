package com.sentinel.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.app.ai.AIAnswerEngine
import com.sentinel.app.data.*
import com.sentinel.app.service.OverlayService
import com.sentinel.app.service.SpeechRecognitionService
import com.sentinel.app.ui.theme.SentinelTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var isServiceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "需要麦克风权限才能使用语音识别", Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SentinelTheme {
                SentinelApp(
                    onStartListening = { subject, mode ->
                        startListeningService(subject, mode)
                    },
                    onStopListening = { stopListeningService() },
                    onRequestPermissions = { requestPermissions() },
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onOpenSettings = { openAppSettings() }
                )
            }
        }
    }

    private fun startListeningService(subject: SubjectConfig, mode: SentinelMode) {
        if (!checkPermissions()) {
            Toast.makeText(this, "请先授予麦克风权限", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, SpeechRecognitionService::class.java).apply {
            action = SpeechRecognitionService.ACTION_START
            putExtra(SpeechRecognitionService.EXTRA_SUBJECT, subject.name)
            putExtra(SpeechRecognitionService.EXTRA_MODE, mode.name)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // 启动悬浮窗
        if (Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(this, OverlayService::class.java)
            startService(overlayIntent)
        }

        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        isServiceBound = true
    }

    private fun stopListeningService() {
        val intent = Intent(this, SpeechRecognitionService::class.java).apply {
            action = SpeechRecognitionService.ACTION_STOP
        }
        stopService(intent)

        val overlayIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_HIDE
        }
        startService(overlayIntent)

        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        return permissions.isEmpty()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }
}

/**
 * 主界面 Composable
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun SentinelApp(
    onStartListening: (SubjectConfig, SentinelMode) -> Unit,
    onStopListening: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 状态
    var selectedSubject by remember { mutableStateOf(SubjectConfig.GENERAL) }
    var selectedMode by remember { mutableStateOf(SentinelMode.BALANCED) }
    var isListening by remember { mutableStateOf(false) }
    var showSubjectSelector by remember { mutableStateOf(false) }
    var showAISettings by remember { mutableStateOf(false) }
    var showModeSelector by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var apiEndpoint by remember { mutableStateOf("https://api.openai.com/v1/chat/completions") }
    var lastAnswer by remember { mutableStateOf("") }
    var showAnswer by remember { mutableStateOf(false) }
    var isAnswering by remember { mutableStateOf(false) }

    // 从服务获取状态
    val isServiceRunning by SpeechRecognitionService.isRunning.collectAsStateWithLifecycle()
    val questionProbability by SpeechRecognitionService.currentQuestionProbability.collectAsStateWithLifecycle()
    val transcript by SpeechRecognitionService.currentTranscript.collectAsStateWithLifecycle()
    val isQuestionDetected by SpeechRecognitionService.isQuestionDetected.collectAsStateWithLifecycle()
    val detectedKeywords by SpeechRecognitionService.detectedKeywords.collectAsStateWithLifecycle()
    val fullTranscript by SpeechRecognitionService.fullTranscript.collectAsStateWithLifecycle()

    // AI引擎
    val aiEngine = remember {
        AIAnswerEngine(
            apiEndpoint = apiEndpoint,
            apiKey = apiKey,
            model = "gpt-3.5-turbo"
        )
    }

    // 科目颜色
    val subjectColor = when (selectedSubject) {
        SubjectConfig.GENERAL -> Color(0xFF00BCD4)
        SubjectConfig.MATHEMATICS -> Color(0xFFE91E63)
        SubjectConfig.PHYSICS -> Color(0xFFFF5722)
        SubjectConfig.CHEMISTRY -> Color(0xFF9C27B0)
        SubjectConfig.ENGLISH -> Color(0xFF3F51B5)
        SubjectConfig.CHINESE -> Color(0xFF4CAF50)
        SubjectConfig.HISTORY -> Color(0xFF795548)
        SubjectConfig.BIOLOGY -> Color(0xFF009688)
    }

    // 模式颜色
    val modeColor = when (selectedMode) {
        SentinelMode.CONSERVATIVE -> Color(0xFF4CAF50)
        SentinelMode.BALANCED -> Color(0xFFFF9800)
        SentinelMode.AGGRESSIVE -> Color(0xFFF44336)
    }

    // 监听提问，自动触发AI解答
    LaunchedEffect(isQuestionDetected) {
        if (isQuestionDetected && isListening && lastAnswer.isEmpty()) {
            val question = transcript
            if (question.isNotBlank() && aiEngine.isConfigured()) {
                isAnswering = true
                aiEngine.setSubject(selectedSubject)
                val result = aiEngine.getAnswer(question, fullTranscript)
                lastAnswer = result.answer
                isAnswering = false
                showAnswer = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "哨兵",
                            fontWeight = FontWeight.Bold
                        )
                        if (isListening) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A237E),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    // 设置按钮
                    IconButton(onClick = { showAISettings = true }) {
                        Icon(Icons.Default.Settings, "AI设置")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = Color(0xFF1A237E),
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 科目选择按钮
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { showSubjectSelector = true }) {
                            Icon(
                                Icons.Default.Book,
                                contentDescription = "选择科目",
                                tint = subjectColor
                            )
                        }
                        Text(
                            selectedSubject.displayName,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    // 模式选择按钮
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { showModeSelector = true }) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = "选择模式",
                                tint = modeColor
                            )
                        }
                        Text(
                            selectedMode.displayName,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    // 开始/停止按钮
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = {
                                if (isListening) {
                                    onStopListening()
                                    isListening = false
                                    lastAnswer = ""
                                } else {
                                    onStartListening(selectedSubject, selectedMode)
                                    isListening = true
                                    aiEngine.setSubject(selectedSubject)
                                }
                            },
                            containerColor = if (isListening) Color(0xFFF44336) else Color(0xFF4CAF50),
                            contentColor = Color.White
                        ) {
                            Icon(
                                if (isListening) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isListening) "停止监听" else "开始监听",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            if (isListening) "停止" else "开始",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    // 悬浮窗权限
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = onRequestOverlayPermission) {
                            Icon(
                                Icons.Default.Widgets,
                                contentDescription = "悬浮窗",
                                tint = Color(0xFF80DEEA)
                            )
                        }
                        Text(
                            "悬浮窗",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    // 历史记录
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { showAnswer = false }) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "历史",
                                tint = Color(0xFF80CBC4)
                            )
                        }
                        Text(
                            "记录",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1A237E),
                            Color(0xFF0D47A1),
                            Color(0xFF1565C0)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 顶部状态卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 盾牌图标 + 状态
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isListening)
                                            Brush.sweepGradient(listOf(Color(0xFF00BCD4), Color(0xFF1A237E)))
                                        else
                                            Color.Gray.copy(alpha = 0.3f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = "哨兵",
                                    tint = if (isListening) Color.White else Color.Gray,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            Column {
                                Text(
                                    if (isListening) "监听中" else "待机中",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "${selectedSubject.icon} ${selectedSubject.displayName} · ${selectedMode.displayName}",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // 提问概率仪表盘
                        if (isListening) {
                            Text(
                                "提问概率",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(8.dp))

                            // 概率条
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                // 动态概率条
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = questionProbability)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                questionProbability >= 0.7f -> Color(0xFFF44336)
                                                questionProbability >= 0.5f -> Color(0xFFFF9800)
                                                questionProbability >= 0.3f -> Color(0xFF66BB6A)
                                                else -> Color(0xFF4CAF50)
                                            }
                                        )
                                )
                                // 阈值标记
                                val threshold = selectedMode.threshold()
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(3.dp)
                                        .offset(x = (threshold * 300).dp)
                                        .background(Color.White)
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                "${(questionProbability * 100).toInt()}%",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    questionProbability >= 0.7f -> Color(0xFFF44336)
                                    questionProbability >= 0.5f -> Color(0xFFFF9800)
                                    questionProbability >= 0.3f -> Color(0xFF66BB6A)
                                    else -> Color(0xFF4CAF50)
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 实时语音识别文本
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "实时语音识别",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (transcript.isNotBlank()) transcript else if (isListening) "等待语音输入..." else "点击「开始」按钮启动监听",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        // 检测到的关键词
                        if (detectedKeywords.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                detectedKeywords.forEach { keyword ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFFF9800).copy(alpha = 0.3f)
                                    ) {
                                        Text(
                                            keyword,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            fontSize = 12.sp,
                                            color = Color(0xFFFF9800)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // AI解答区域
                if (isQuestionDetected && isListening) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1B5E20).copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "🤔 检测到提问！AI解答中...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD54F)
                                )
                            }

                            if (isAnswering) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF4CAF50)
                                )
                            }

                            if (showAnswer && lastAnswer.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    lastAnswer,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 10,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 完整识别记录
                if (fullTranscript.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "完整记录",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                fullTranscript.takeLast(500),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // 科目选择对话框
    if (showSubjectSelector) {
        AlertDialog(
            onDismissRequest = { showSubjectSelector = false },
            title = { Text("选择科目", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn {
                    items(SubjectConfig.entries) { subject ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (subject == selectedSubject)
                                    Color(0xFF1A237E).copy(alpha = 0.2f)
                                else Color.Transparent
                            ),
                            onClick = {
                                selectedSubject = subject
                                showSubjectSelector = false
                                // 如果正在监听，更新科目
                                if (isListening) {
                                    val intent = Intent(context, SpeechRecognitionService::class.java).apply {
                                        action = SpeechRecognitionService.ACTION_UPDATE_SUBJECT
                                        putExtra(SpeechRecognitionService.EXTRA_SUBJECT, subject.name)
                                    }
                                    context.startService(intent)
                                    aiEngine.setSubject(subject)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(subject.icon, fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(subject.displayName, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${subject.keywords.size}个关键词 · ${subject.questionPatterns.size}个提问模式",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                if (subject == selectedSubject) {
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubjectSelector = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // 模式选择对话框
    if (showModeSelector) {
        AlertDialog(
            onDismissRequest = { showModeSelector = false },
            title = { Text("选择模式", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    SentinelMode.entries.forEach { mode ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (mode == selectedMode)
                                    Color(0xFF1A237E).copy(alpha = 0.2f)
                                else Color.Transparent
                            ),
                            onClick = {
                                selectedMode = mode
                                showModeSelector = false
                                if (isListening) {
                                    val intent = Intent(context, SpeechRecognitionService::class.java).apply {
                                        action = SpeechRecognitionService.ACTION_UPDATE_MODE
                                        putExtra(SpeechRecognitionService.EXTRA_MODE, mode.name)
                                    }
                                    context.startService(intent)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val icon = when (mode) {
                                            SentinelMode.CONSERVATIVE -> Icons.Default.Shield
                                            SentinelMode.BALANCED -> Icons.Default.Balance
                                            SentinelMode.AGGRESSIVE -> Icons.Default.FlashOn
                                        }
                                        Icon(
                                            icon,
                                            contentDescription = null,
                                            tint = when (mode) {
                                                SentinelMode.CONSERVATIVE -> Color(0xFF4CAF50)
                                                SentinelMode.BALANCED -> Color(0xFFFF9800)
                                                SentinelMode.AGGRESSIVE -> Color(0xFFF44336)
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(mode.displayName, fontWeight = FontWeight.Medium)
                                    }
                                    Text(
                                        mode.description,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(start = 28.dp)
                                    )
                                    Text(
                                        "阈值: ${(mode.threshold() * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        color = when (mode) {
                                            SentinelMode.CONSERVATIVE -> Color(0xFF4CAF50)
                                            SentinelMode.BALANCED -> Color(0xFFFF9800)
                                            SentinelMode.AGGRESSIVE -> Color(0xFFF44336)
                                        },
                                        modifier = Modifier.padding(start = 28.dp)
                                    )
                                }
                                if (mode == selectedMode) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModeSelector = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // AI设置对话框
    if (showAISettings) {
        AlertDialog(
            onDismissRequest = { showAISettings = false },
            title = { Text("AI解答设置", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("API端点", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = apiEndpoint,
                        onValueChange = { apiEndpoint = it },
                        placeholder = { Text("https://api.openai.com/v1/chat/completions") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("API密钥", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        placeholder = { Text("sk-...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "提示：你可以使用任意兼容OpenAI API的服务（如DeepSeek、通义千问等）",
                        fontSize = 12.sp,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAISettings = false }) {
                    Text("确定")
                }
            }
        )
    }
}