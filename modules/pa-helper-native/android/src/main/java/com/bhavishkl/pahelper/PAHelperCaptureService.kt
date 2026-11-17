package com.bhavishkl.pahelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class PAHelperCaptureService : Service() {
  companion object {
    const val PREFS = "pa_helper_capture"
    const val KEY_TEXT = "latest_text"
    const val KEY_TIME = "latest_time"
    const val KEY_STATUS = "latest_status"
    const val EXTRA_RESULT_CODE = "projection_result_code"
    const val EXTRA_RESULT_DATA = "projection_result_data"
    private const val CHANNEL_ID = "pa_helper_capture_channel"
    private const val NOTIFICATION_ID = 7402
  }

  private var projection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var windowManager: WindowManager? = null
  private var floatingButton: TextView? = null
  private var captureHandler: Handler? = null
  private var captureThread: HandlerThread? = null
  @Volatile private var capturing = false

  override fun onCreate() {
    super.onCreate()
    captureThread = HandlerThread("PAHelperScreenOCR").also { it.start() }
    captureHandler = Handler(captureThread!!.looper)
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
    val data = if (Build.VERSION.SDK_INT >= 33) {
      intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent?.getParcelableExtra(EXTRA_RESULT_DATA)
    }

    if (resultCode == 0 || data == null || projection != null) {
      stopSelf()
      return START_NOT_STICKY
    }

    try {
      val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      projection = manager.getMediaProjection(resultCode, data)

      val notification = buildNotification()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }

      projection?.registerCallback(object : MediaProjection.Callback() {
        override fun onStop() {
          stopSelf()
        }
      }, captureHandler)

      prepareVirtualDisplay()
      showFloatingButton()
    } catch (error: Exception) {
      saveCapture("", "capture_error: ${error.javaClass.simpleName}")
      stopSelf()
    }
    return START_NOT_STICKY
  }

  private fun prepareVirtualDisplay() {
    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    @Suppress("DEPRECATION")
    windowManager?.defaultDisplay?.getRealMetrics(metrics)
    val width = metrics.widthPixels
    val height = metrics.heightPixels
    val density = metrics.densityDpi
    if (width <= 0 || height <= 0) {
      saveCapture("", "display_unavailable")
      stopSelf()
      return
    }

    imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
    imageReader?.setOnImageAvailableListener({ reader ->
      val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
      if (!capturing) {
        image.close()
        return@setOnImageAvailableListener
      }
      capturing = false
      runOCR(image)
    }, captureHandler)

    virtualDisplay = projection?.createVirtualDisplay(
      "PAHelperScreenCapture",
      width,
      height,
      density,
      DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
      imageReader?.surface,
      null,
      captureHandler
    )
  }

  private fun showFloatingButton() {
    if (Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(this)) {
      saveCapture("", "overlay_permission_missing")
      stopSelf()
      return
    }

    val manager = getSystemService(WINDOW_SERVICE) as WindowManager
    windowManager = manager
    val button = TextView(this).apply {
      text = "PA"
      textSize = 15f
      setTextColor(0xFFFFFFFF.toInt())
      setBackgroundColor(0xFF243B70.toInt())
      gravity = Gravity.CENTER
      elevation = 12f
      setPadding(18, 14, 18, 14)
      contentDescription = "Capture screen with PA Helper"
      setOnClickListener { captureScreen() }
    }

    val type = if (Build.VERSION.SDK_INT >= 26) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    }
    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      type,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.END or Gravity.CENTER_VERTICAL
      x = 12
    }

    try {
      manager.addView(button, params)
      floatingButton = button
    } catch (error: Exception) {
      saveCapture("", "overlay_failed")
    }
  }

  private fun captureScreen() {
    if (projection == null || imageReader == null || capturing) {
      saveCapture("", if (projection == null) "capture_not_ready" else "capture_in_progress")
      return
    }

    capturing = true
    floatingButton?.visibility = View.INVISIBLE

    // The tap hides the overlay. The next composed display frame is sent to ImageReader.
    captureHandler?.postDelayed({
      if (capturing) {
        capturing = false
        floatingButton?.post { floatingButton?.visibility = View.VISIBLE }
        saveCapture("", "no_frame_received")
      }
    }, 3000)
  }

  private fun runOCR(image: Image) {
    try {
      val bitmap = imageToBitmap(image)
      image.close()
      floatingButton?.post { floatingButton?.visibility = View.VISIBLE }
      val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
      recognizer.process(InputImage.fromBitmap(bitmap, 0))
        .addOnSuccessListener { result ->
          val text = result.text.trim()
          saveCapture(text, if (text.isBlank()) "no_text_found" else "success")
          recognizer.close()
          bitmap.recycle()
        }
        .addOnFailureListener {
          saveCapture("", "ocr_failed")
          recognizer.close()
          bitmap.recycle()
        }
    } catch (error: Exception) {
      try { image.close() } catch (_: Exception) {}
      floatingButton?.post { floatingButton?.visibility = View.VISIBLE }
      saveCapture("", "image_processing_failed")
    }
  }

  private fun imageToBitmap(image: Image): Bitmap {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * image.width
    val padded = Bitmap.createBitmap(
      image.width + rowPadding / pixelStride,
      image.height,
      Bitmap.Config.ARGB_8888
    )
    padded.copyPixelsFromBuffer(buffer)
    val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
    padded.recycle()
    return cropped
  }

  private fun saveCapture(text: String, status: String) {
    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
      .putString(KEY_TEXT, text)
      .putLong(KEY_TIME, System.currentTimeMillis())
      .putString(KEY_STATUS, status)
      .apply()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= 26) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "PA Helper floating assistant",
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
        .setContentTitle("PA Helper is active")
        .setContentText("Tap the floating PA button to capture screen text")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .build()
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
        .setContentTitle("PA Helper is active")
        .setContentText("Tap the floating PA button to capture screen text")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setOngoing(true)
        .build()
    }
  }

  override fun onDestroy() {
    try {
      floatingButton?.let { windowManager?.removeView(it) }
    } catch (_: Exception) {}
    virtualDisplay?.release()
    imageReader?.close()
    projection?.stop()
    captureThread?.quitSafely()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
