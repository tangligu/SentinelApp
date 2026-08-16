package com.sentinel.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.sentinel.app.R
import com.sentinel.app.data.SentinelMode
import com.sentinel.app.data.threshold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 悬浮窗服务 - 在屏幕上显示实时识别信息和提问提示
 * 类似游戏中的"小地图"悬浮窗，显示当前状态
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"

        @Volatile
        var isOverlayShowing = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var collapsedView: View? = null
    private var expandedView: View? = null

    // 窗口参数
    private var params: WindowManager.LayoutParams? = null
    private var isExpanded = true

    // 拖拽偏移
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            hideOverlay()
            return START_NOT_STICKY
        }

        showOverlay()
        startObserving()

        return START_STICKY
    }

    private fun showOverlay() {
        if (isOverlayShowing) return

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_sentinel, null)

        collapsedView = overlayView?.findViewById(R.id.overlay_collapsed)
        expandedView = overlayView?.findViewById(R.id.overlay_expanded)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        windowManager.addView(overlayView, params)
        isOverlayShowing = true

        setupTouchListener()
        setupClickListeners()
    }

    private fun setupTouchListener() {
        overlayView?.setOnTouchListener { view, event ->
            params?.let { p ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = p.x
                        initialY = p.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        p.x = initialX + (event.rawX - initialTouchX).toInt()
                        p.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, p)
                        true
                    }
                    else -> false
                }
            } ?: false
        }
    }

    private fun setupClickListeners() {
        // 折叠/展开切换
        overlayView?.findViewById<View>(R.id.overlay_toggle)?.setOnClickListener {
            toggleExpand()
        }

        // 关闭按钮
        overlayView?.findViewById<View>(R.id.overlay_close)?.setOnClickListener {
            hideOverlay()
        }

        // 点按折叠区域展开
        collapsedView?.setOnClickListener {
            if (!isExpanded) toggleExpand()
        }
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        collapsedView?.visibility = if (isExpanded) View.GONE else View.VISIBLE
        expandedView?.visibility = if (isExpanded) View.VISIBLE else View.GONE

        // 调整窗口大小
        overlayView?.let { view ->
            params?.let { p ->
                p.width = if (isExpanded) WindowManager.LayoutParams.WRAP_CONTENT
                else WindowManager.LayoutParams.WRAP_CONTENT
                windowManager.updateViewLayout(view, p)
            }
        }
    }

    private fun startObserving() {
        serviceScope.launch {
            SpeechRecognitionService.currentQuestionProbability.collectLatest { prob ->
                updateProbabilityUI(prob)
            }
        }

        serviceScope.launch {
            SpeechRecognitionService.isQuestionDetected.collectLatest { detected ->
                if (detected) {
                    flashQuestionAlert()
                }
            }
        }

        serviceScope.launch {
            SpeechRecognitionService.currentTranscript.collectLatest { text ->
                updateTranscriptUI(text)
            }
        }

        serviceScope.launch {
            SpeechRecognitionService.detectedKeywords.collectLatest { keywords ->
                updateKeywordsUI(keywords)
            }
        }
    }

    private fun updateProbabilityUI(probability: Float) {
        val probText = overlayView?.findViewById<TextView>(R.id.overlay_probability)
        val probBar = overlayView?.findViewById<View>(R.id.overlay_probability_bar)

        probText?.text = "${(probability * 100).toInt()}%"
        probBar?.let { bar ->
            val newWidth = (probability * 200).toInt().coerceAtLeast(2)
            bar.layoutParams?.width = newWidth
            bar.requestLayout()
        }

        // 根据概率更新颜色
        val color = when {
            probability >= 0.7f -> 0xFFFF4444.toInt()  // 红色
            probability >= 0.5f -> 0xFFFF8800.toInt()  // 橙色
            probability >= 0.3f -> 0xFF66BB6A.toInt()  // 浅绿
            else -> 0xFF4CAF50.toInt()  // 绿色
        }
        probBar?.setBackgroundColor(color)
    }

    private fun updateTranscriptUI(text: String) {
        val transcriptView = overlayView?.findViewById<TextView>(R.id.overlay_transcript)
        if (text.isNotBlank()) {
            transcriptView?.text = text.take(60)
            transcriptView?.visibility = View.VISIBLE
        }
    }

    private fun updateKeywordsUI(keywords: List<String>) {
        val keywordView = overlayView?.findViewById<TextView>(R.id.overlay_keywords)
        if (keywords.isNotEmpty()) {
            keywordView?.text = "关键词: ${keywords.joinToString(", ")}"
            keywordView?.visibility = View.VISIBLE
        } else {
            keywordView?.visibility = View.GONE
        }
    }

    private fun flashQuestionAlert() {
        // 闪烁提示检测到提问
        val alertView = overlayView?.findViewById<View>(R.id.overlay_question_alert)
        alertView?.visibility = View.VISIBLE
        alertView?.postDelayed({
            alertView?.visibility = View.GONE
        }, 3000)
    }

    private fun hideOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                // 视图可能已经被移除
            }
        }
        overlayView = null
        isOverlayShowing = false
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_HIDE = "com.sentinel.app.action.HIDE_OVERLAY"
    }
}