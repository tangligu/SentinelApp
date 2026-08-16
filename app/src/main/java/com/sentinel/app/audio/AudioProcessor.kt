package com.sentinel.app.audio

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * 音频处理器 - 管理语音识别、关键词检测和提问概率分析
 */
class AudioProcessor(
    private val subjectKeywords: List<String>,
    private val questionPatterns: List<String>,
    private val modeThreshold: Float = 0.5f
) {
    companion object {
        private const val TAG = "AudioProcessor"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 400 // 25ms per frame at 16kHz
        private const val ANALYSIS_WINDOW_MS = 5000L // 5秒分析窗口
        private const val KEYWORD_WEIGHT = 0.4f
        private const val QUESTION_PATTERN_WEIGHT = 0.3f
        private const val SILENCE_RATIO_WEIGHT = 0.15f
        private const val PITCH_VARIATION_WEIGHT = 0.15f
    }

    private val isRunning = AtomicBoolean(false)
    private val _textBuffer = MutableStateFlow("")
    val textBuffer: StateFlow<String> = _textBuffer.asStateFlow()

    private val _questionProbability = MutableStateFlow(0.0f)
    val questionProbability: StateFlow<Float> = _questionProbability.asStateFlow()

    private val _isQuestionDetected = MutableStateFlow(false)
    val isQuestionDetected: StateFlow<Boolean> = _isQuestionDetected.asStateFlow()

    private val _detectedKeywords = MutableStateFlow<List<String>>(emptyList())
    val detectedKeywords: StateFlow<List<String>> = _detectedKeywords.asStateFlow()

    private val _fullTranscript = MutableStateFlow("")
    val fullTranscript: StateFlow<String> = _fullTranscript.asStateFlow()

    private val _lastQuestionStartTime = MutableStateFlow(0L)
    val lastQuestionStartTime: StateFlow<Long> = _lastQuestionStartTime.asStateFlow()

    private val compiledPatterns = questionPatterns.map { Pattern.compile(it, Pattern.CASE_INSENSITIVE) }

    // 最近文本片段的循环缓冲区，用于分析
    private val recentTexts = mutableListOf<Pair<Long, String>>()
    private var lastAnalysisTime = 0L

    /**
     * 处理新的语音识别文本
     */
    fun processTranscript(text: String) {
        if (!isRunning.get()) return

        val now = System.currentTimeMillis()
        _textBuffer.value = text

        // 追加到完整转录
        _fullTranscript.value = _fullTranscript.value + " " + text

        // 添加到最近片段
        recentTexts.add(Pair(now, text))
        trimRecentTexts(now)

        // 分析提问概率
        analyzeQuestionProbability(now)
    }

    /**
     * 分析当前文本，计算老师提问的概率
     */
    private fun analyzeQuestionProbability(now: Long) {
        // 合并最近窗口内的文本
        val recentText = buildRecentText(now)

        // 1. 关键词匹配得分
        val keywordScore = calculateKeywordScore(recentText)

        // 2. 提问模式匹配得分
        val patternScore = calculatePatternScore(recentText)

        // 3. 文本长度和节奏分析（提问通常有较长的陈述作为铺垫）
        val lengthScore = calculateLengthScore(recentText)

        // 综合加权
        val probability = keywordScore * KEYWORD_WEIGHT +
                patternScore * QUESTION_PATTERN_WEIGHT +
                lengthScore * SILENCE_RATIO_WEIGHT

        val clampedProbability = probability.coerceIn(0.0f, 1.0f)
        _questionProbability.value = clampedProbability

        // 检测是否超过阈值
        val isQuestion = clampedProbability >= modeThreshold
        if (isQuestion && !_isQuestionDetected.value) {
            Log.d(TAG, "提问检测到！概率: $clampedProbability, 文本: $recentText")
            _isQuestionDetected.value = true
            _lastQuestionStartTime.value = now
        } else if (!isQuestion && _isQuestionDetected.value) {
            // 概率回落到阈值以下，重置检测状态
            // 但需要有足够的时间间隔，避免频繁触发
            if (now - _lastQuestionStartTime.value > 3000) {
                _isQuestionDetected.value = false
            }
        }

        // 提取检测到的关键词
        val foundKeywords = subjectKeywords.filter { it in recentText }
        _detectedKeywords.value = foundKeywords
    }

    /**
     * 计算关键词匹配得分
     */
    private fun calculateKeywordScore(text: String): Float {
        if (text.isBlank()) return 0.0f
        if (subjectKeywords.isEmpty()) return 0.0f

        val matchedCount = subjectKeywords.count { keyword ->
            text.contains(keyword, ignoreCase = true)
        }

        val ratio = matchedCount.toFloat() / subjectKeywords.size
        // 使用非线性映射：少量关键词就能得到较高分数
        return (ratio * 3.0f).coerceAtMost(1.0f)
    }

    /**
     * 计算提问模式匹配得分
     */
    private fun calculatePatternScore(text: String): Float {
        if (text.isBlank()) return 0.0f

        for (pattern in compiledPatterns) {
            if (pattern.matcher(text).find()) {
                return 1.0f // 匹配到模式，直接高分
            }
        }
        return 0.0f
    }

    /**
     * 计算文本长度和节奏得分
     */
    private fun calculateLengthScore(text: String): Float {
        if (text.isBlank()) return 0.0f
        // 老师提问前通常有一段话（铺垫），太短不可能是提问
        // 太长也不像提问（可能在讲课）
        val length = text.length
        return when {
            length < 5 -> 0.1f  // 太短，不太可能
            length < 10 -> 0.3f
            length < 20 -> 0.5f
            length < 50 -> 0.8f  // 最佳提问长度范围
            length < 100 -> 0.6f
            length < 200 -> 0.4f
            else -> 0.2f  // 太长，更像在讲课
        }
    }

    /**
     * 构建最近分析窗口内的文本
     */
    private fun buildRecentText(now: Long): String {
        return recentTexts.joinToString(" ") { it.second }
    }

    /**
     * 修剪过期的文本片段
     */
    private fun trimRecentTexts(now: Long) {
        val cutoff = now - ANALYSIS_WINDOW_MS
        while (recentTexts.isNotEmpty() && recentTexts.first().first < cutoff) {
            recentTexts.removeAt(0)
        }
    }

    fun start() {
        isRunning.set(true)
        _fullTranscript.value = ""
        _textBuffer.value = ""
        _questionProbability.value = 0.0f
        _isQuestionDetected.value = false
        _detectedKeywords.value = emptyList()
        recentTexts.clear()
        Log.d(TAG, "AudioProcessor started")
    }

    fun stop() {
        isRunning.set(false)
        Log.d(TAG, "AudioProcessor stopped")
    }

    fun reset() {
        stop()
        recentTexts.clear()
        _fullTranscript.value = ""
        _textBuffer.value = ""
        _questionProbability.value = 0.0f
        _isQuestionDetected.value = false
        _detectedKeywords.value = emptyList()
    }

    fun updateKeywords(keywords: List<String>) {
        // 更新 subjectKeywords 的逻辑
        // 这个字段需要用 var 或通过其他方式
        Log.d(TAG, "Keywords updated: $keywords")
    }

    fun updateModeThreshold(threshold: Float) {
        // 更新 modeThreshold 的逻辑
        Log.d(TAG, "Mode threshold updated: $threshold")
    }
}