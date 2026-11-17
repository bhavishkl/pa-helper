package com.bhavishkl.pahelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class PAHelperOverlayService : Service() {
  companion object {
    private const val CHANNEL_ID = "pa_helper_overlay_channel"
    private const val NOTIFICATION_ID = 7403
  }

  private var windowManager: WindowManager? = null
  private var floatingButton: TextView? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  private var capturePending = false

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    val notification = buildNotification()
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
    showFloatingButton()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (floatingButton == null) showFloatingButton()
    return START_STICKY
  }

  private fun showFloatingButton() {
    if (!Settings.canDrawOverlays(this)) {
      stopSelf()
      return
    }

    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    val button = TextView(this).apply {
      text = "PA"
      textSize = 15f
      setTextColor(0xFFFFFFFF.toInt())
      gravity = Gravity.CENTER
      setPadding(19, 15, 19, 15)
      elevation = 16f
      contentDescription = "Tap once to capture this screen"
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xFF243B70.toInt())
        setStroke(2, 0xFFFFFFFF.toInt())
      }
      setOnClickListener { captureOneFrame() }
    }

    val overlayType = if (Build.VERSION.SDK_INT >= 26) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    }

    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      overlayType,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.END or Gravity.CENTER_VERTICAL
      x = 18
    }

    try {
      windowManager?.addView(button, params)
      floatingButton = button
    } catch (_: Exception) {
      saveStatus("overlay_failed")
      stopSelf()
    }
  }

  private fun captureOneFrame() {
    if (capturePending) return
    capturePending = true
    floatingButton?.visibility = android.view.View.INVISIBLE

    // Hide the bubble before requesting the one-shot screenshot.
    mainHandler.postDelayed({
      val accessibility = PAHelperAccessibilityService.instance
      if (accessibility == null) {
        saveStatus("accessibility_service_unavailable")
        floatingButton?.visibility = android.view.View.VISIBLE
        capturePending = false
        return@postDelayed
      }

      accessibility.captureOneScreenshot {
        floatingButton?.visibility = android.view.View.VISIBLE
        capturePending = false
      }
    }, 250)
  }

  private fun saveStatus(status: String) {
    getSharedPreferences(PAHelperAccessibilityService.PREFS, Context.MODE_PRIVATE)
      .edit()
      .putString(PAHelperAccessibilityService.KEY_TEXT, "")
      .putLong(PAHelperAccessibilityService.KEY_TIME, System.currentTimeMillis())
      .putString(PAHelperAccessibilityService.KEY_STATUS, status)
      .apply()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= 26) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "PA Helper floating button",
        NotificationManager.IMPORTANCE_LOW
      )
      (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        .createNotificationChannel(channel)
    }
  }

  private fun buildNotification(): Notification {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
    val pendingIntent = launchIntent?.let {
      PendingIntent.getActivity(
        this, 0, it,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    }
    return if (Build.VERSION.SDK_INT >= 26) {
      Notification.Builder(this, CHANNEL_ID)
        .setContentTitle("PA Helper floating button is active")
        .setContentText("Screen capture happens only when you tap PA")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .build()
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
        .setContentTitle("PA Helper floating button is active")
        .setContentText("Screen capture happens only when you tap PA")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setOngoing(true)
        .build()
    }
  }

  override fun onDestroy() {
    mainHandler.removeCallbacksAndMessages(null)
    try {
      floatingButton?.let { windowManager?.removeView(it) }
    } catch (_: Exception) {}
    floatingButton = null
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
