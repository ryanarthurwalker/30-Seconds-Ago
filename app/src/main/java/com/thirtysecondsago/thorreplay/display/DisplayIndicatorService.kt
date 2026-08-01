package com.thirtysecondsago.thorreplay.display

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.thirtysecondsago.thorreplay.util.LogTags
import com.thirtysecondsago.thorreplay.util.NotificationHelper

class DisplayIndicatorService : Service() {
    private var overlayView: View? = null
    private var overlayWindowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> show(intent.getIntExtra(EXTRA_DISPLAY_ID, DEFAULT_DISPLAY_ID))
            ACTION_SAVED -> showSavedPopup(
                intent.getIntExtra(EXTRA_DISPLAY_ID, DEFAULT_DISPLAY_ID),
                intent.getStringExtra(EXTRA_MESSAGE) ?: "Replay saved",
            )
            ACTION_HIDE -> hideAndStop()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hide()
        super.onDestroy()
    }

    private fun show(displayId: Int) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(LogTags.SERVICE, "Cannot show display indicator without overlay permission")
            stopSelf()
            return
        }

        val displayContext = displayContextFor(displayId) ?: return
        val windowManager = displayContext.getSystemService(WindowManager::class.java)
        val view = RedBorderView(displayContext)
        val params = fullScreenOverlayParams("Thor Replay Display Indicator")

        runCatching {
            windowManager.addView(view, params)
            overlayWindowManager = windowManager
            overlayView = view
            Log.i(LogTags.SERVICE, "Showing display indicator on display=$displayId")
        }.onFailure { error ->
            Log.e(LogTags.SERVICE, "Failed to show display indicator", error)
            stopSelf()
        }
    }

    private fun showSavedPopup(displayId: Int, message: String) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(LogTags.SERVICE, "Cannot show save popup without overlay permission")
            return
        }

        val displayContext = displayContextFor(displayId) ?: return
        val windowManager = displayContext.getSystemService(WindowManager::class.java)
        val view = SavedPopupView(displayContext, message)
        val params = fullScreenOverlayParams("Thor Replay Saved Popup")

        runCatching {
            windowManager.addView(view, params)
            overlayWindowManager = windowManager
            overlayView = view
            mainHandler.postDelayed({ hideAndStop() }, 1_800L)
            Log.i(LogTags.SERVICE, "Showing save popup on display=$displayId")
        }.onFailure { error ->
            Log.e(LogTags.SERVICE, "Failed to show saved popup", error)
            stopSelf()
        }
    }

    private fun displayContextFor(displayId: Int): Context? {
        hide()
        val display = getSystemService(DisplayManager::class.java)
            .displays
            .firstOrNull { it.displayId == displayId }
        if (display == null) {
            Log.w(LogTags.SERVICE, "Display $displayId not found for overlay")
            stopSelf()
            return null
        }
        return createDisplayContext(display)
    }

    private fun fullScreenOverlayParams(title: String): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.title = title
        }
    }

    private fun hideAndStop() {
        hide()
        stopSelf()
    }

    private fun hide() {
        mainHandler.removeCallbacksAndMessages(null)
        val view = overlayView
        if (view != null) {
            runCatching { overlayWindowManager?.removeView(view) }
        }
        overlayView = null
        overlayWindowManager = null
    }

    private class RedBorderView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val inset = paint.strokeWidth / 2f
            canvas.drawRect(inset, inset, width - inset, height - inset, paint)
        }
    }

    private class SavedPopupView(context: Context, private val message: String) : View(context) {
        private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 24, 28, 34)
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 46f
            isFakeBoldText = true
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(225, 230, 235)
            textAlign = Paint.Align.CENTER
            textSize = 26f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val panelWidth = width * 0.72f
            val panelHeight = 180f
            val left = (width - panelWidth) / 2f
            val top = (height - panelHeight) / 2f
            val rect = RectF(left, top, left + panelWidth, top + panelHeight)
            canvas.drawRoundRect(rect, 28f, 28f, panelPaint)
            canvas.drawRoundRect(rect, 28f, 28f, strokePaint)
            canvas.drawText("Replay saved", width / 2f, top + 72f, titlePaint)
            canvas.drawText(message.take(34), width / 2f, top + 122f, bodyPaint)
        }
    }

    companion object {
        private const val DEFAULT_DISPLAY_ID = 0
        private const val ACTION_SHOW = "com.thirtysecondsago.thorreplay.action.SHOW_DISPLAY_INDICATOR"
        private const val ACTION_SAVED = "com.thirtysecondsago.thorreplay.action.SHOW_SAVED_POPUP"
        private const val ACTION_HIDE = "com.thirtysecondsago.thorreplay.action.HIDE_DISPLAY_INDICATOR"
        private const val EXTRA_DISPLAY_ID = "display_id"
        private const val EXTRA_MESSAGE = "message"

        fun showIntent(context: Context, displayId: Int): Intent {
            return Intent(context, DisplayIndicatorService::class.java)
                .setAction(ACTION_SHOW)
                .putExtra(EXTRA_DISPLAY_ID, displayId)
        }

        fun hideIntent(context: Context): Intent {
            return Intent(context, DisplayIndicatorService::class.java).setAction(ACTION_HIDE)
        }

        fun savedPopupIntent(context: Context, displayId: Int, message: String): Intent {
            return Intent(context, DisplayIndicatorService::class.java)
                .setAction(ACTION_SAVED)
                .putExtra(EXTRA_DISPLAY_ID, displayId)
                .putExtra(EXTRA_MESSAGE, message)
        }
    }
}
