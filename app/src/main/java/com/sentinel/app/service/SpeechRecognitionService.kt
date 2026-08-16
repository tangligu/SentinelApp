package com.sentinel.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sentinel.app.R
import com.sentinel.app.audio.AudioProcessor
import com.sentinel.app.data.SentinelMode
import com.sentinel.app.data.SubjectConfig
import com.sentinel.app.data.threshold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 语音识别前台服务 - 在后台持续监听麦克风
 */
class SpeechRecognitionService : Service() {

    companion object {
        private const val TAG = "SpeechRecognitionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sentinel_speech_channel"

        val isRunning = MutableStateFlow(false)
        val currentTranscript = MutableStateFlow("")
        val currentQuestionProbability = MutableStateFlow(0.0f)
        val isQuestionDetected = MutableStateFlow(false)
        val detectedKeywords = MutableStateFlow<List<String>>(emptyList())
        val fullTranscript = MutableStateFlow("")

        const val ACTION_START = "com.sentinel.app.action.START"
        const val ACTION_STOP = "com.sentinel.app.action.STOP"
        const val ACTION_UPDATE_SUBJECT = "com.sentinel.app.action.UPDATE_SUBJECT"
        const val ACTION_UPDATE_MODE = "com.sentinel.app.action.UPDATE_MODE"

        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_MODE = "extra_mode"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var audioProcessor: AudioProcessor
    private var subjectConfig: SubjectConfig = SubjectConfig.GENERAL
    private var sentinelMode: SentinelMode = SentinelMode.BALANCED
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service created")

        audioProcessor = AudioProcessor(
            subjectKeywords = emptyList(),
            questionPatterns = emptyList(),
            modeThreshold = 0.5f
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                val subjectName = intent.getStringExtra(EXTRA_SUBJECT) ?: "GENERAL"
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: "BALANCED"

                subjectConfig = SubjectConfig.fromName(subjectName)
                sentinelMode = try { SentinelMode.valueOf(modeName) } catch (e: Exception) { SentinelMode.BALANCED }

                startForeground(NOTIFICATION_ID, createNotification())
                startListening()
            }
            ACTION_STOP -> {
                stopListening()
                stopSelf()
            }
            ACTION_UPDATE_SUBJECT -> {
                val subjectName = intent.getStringExtra(EXTRA_SUBJECT) ?: "GENERAL"
                subjectConfig = SubjectConfig.fromName(subjectName)
                updateProcessorConfig()
            }
            ACTION_UPDATE_MODE -> {
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: "BALANCED"
                sentinelMode = try { SentinelMode.valueOf(modeName) } catch (e: Exception) { SentinelMode.BALANCED }
                updateProcessorConfig()
            }
        }

        return START_STICKY
    }

    private fun startListening() {
        isRunning.value = true
        audioProcessor = AudioProcessor(
            subjectKeywords = subjectConfig.keywords,
            questionPatterns = subjectConfig.questionPatterns,
            modeThreshold = sentinelMode.threshold()
        )
        audioProcessor.start()
        Log.d(TAG, "Started listening. Subject: ${subjectConfig.displayName}, Mode: ${sentinelMode.displayName}")

        startGoogleSpeechRecognition()

        serviceScope.launch {
            audioProcessor.questionProbability.collect { prob ->
                currentQuestionProbability.value = prob
                currentTranscript.value = audioProcessor.textBuffer.value
                isQuestionDetected.value = audioProcessor.isQuestionDetected.value
                detectedKeywords.value = audioProcessor.detectedKeywords.value
                fullTranscript.value = audioProcessor.fullTranscript.value
            }
        }
    }

    private fun stopListening() {
        isRunning.value = false
        audioProcessor.stop()
        speechRecognizer?.destroy()
        speechRecognizer = null
        Log.d(TAG, "Stopped listening")
    }

    private fun startGoogleSpeechRecognition() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (isRunning.value) {
                        speechRecognizer?.startListening(intent)
                    }
                }

                override fun onError(error: Int) {
                    Log.e(TAG, "Speech recognition error: $error")
                    if (isRunning.value) {
                        speechRecognizer?.startListening(intent)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    texts?.firstOrNull()?.let { text ->
                        audioProcessor.processTranscript(text)
                    }
                    if (isRunning.value) {
                        speechRecognizer?.startListening(intent)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    texts?.firstOrNull()?.let { text ->
                        audioProcessor.processTranscript(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition: ${e.message}")
        }
    }

    private fun updateProcessorConfig() {
        audioProcessor.updateKeywords(subjectConfig.keywords)
        audioProcessor.updateModeThreshold(sentinelMode.threshold())
        Log.d(TAG, "Processor config updated: ${subjectConfig.displayName}")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "哨兵语音识别",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "哨兵正在监听课堂语音"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("哨兵 · ${subjectConfig.displayName}")
            .setContentText("监听中 | 模式: ${sentinelMode.displayName}")
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = Intent(this, SpeechRecognitionService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_SUBJECT, subjectConfig.name)
            putExtra(EXTRA_MODE, sentinelMode.name)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            android.app.PendingIntent.FLAG_ONE_SHOT
        }
        val pendingIntent = android.app.PendingIntent.getService(this, 0, restartIntent, flags)
        val alarmManager = getSystemService(android.app.AlarmManager::class.java)
        alarmManager.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 1000, pendingIntent)
    }
}