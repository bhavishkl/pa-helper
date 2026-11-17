package com.bhavishkl.pahelper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class PAHelperAccessibilityService : AccessibilityService() {
  companion object {
    const val PREFS = "pa_helper_capture"
    const val KEY_TEXT = "latest_text"
    const val KEY_TIME = "latest_time"
    const val KEY_STATUS = "latest_status"

    @Volatile
    var instance: PAHelperAccessibilityService? = null
      private set

    private const val VOLUME_SHORTCUT_WINDOW_MS = 1200L
    private const val VOLUME_SHORTCUT_PRESS_COUNT = 2
  }

  @Volatile
  private var captureInProgress = false

  private val keyHandler = Handler(Looper.getMainLooper())
  private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
  private var volumeUpPresses = 0
  private var volumeShortcutRunnable: Runnable? = null

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    serviceInfo = serviceInfo.apply {
      flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
    }
  }

  /**
   * Every Volume Up press passes through immediately. A second press within
   * 1.2 seconds independently requests one screenshot.
   */
  override fun onKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP) return false
    if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false

    if (volumeUpPresses == 0) {
      volumeShortcutRunnable = Runnable {
        volumeUpPresses = 0
        volumeShortcutRunnable = null
      }
      keyHandler.postDelayed(volumeShortcutRunnable!!, VOLUME_SHORTCUT_WINDOW_MS)
    }

    volumeUpPresses += 1
    if (volumeUpPresses >= VOLUME_SHORTCUT_PRESS_COUNT) {
      volumeShortcutRunnable?.let { keyHandler.removeCallbacks(it) }
      volumeShortcutRunnable = null
      volumeUpPresses = 0
      captureOneScreenshot {}
    }
    return false
  }

  fun captureOneScreenshot(onComplete: () -> Unit) {
    if (captureInProgress) {
      saveCapture("", "capture_in_progress")
      onComplete()
      return
    }

    captureInProgress = true
    try {
      takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
        override fun onSuccess(screenshot: ScreenshotResult) {
          var bitmap: Bitmap? = null
          val hardwareBuffer: HardwareBuffer = screenshot.hardwareBuffer
          try {
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(
              hardwareBuffer,
              screenshot.colorSpace
            )
            bitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
            hardwareBitmap?.recycle()
          } catch (_: Exception) {
            bitmap = null
          } finally {
            hardwareBuffer.close()
          }

          if (bitmap == null) {
            finishCapture("", "image_processing_failed", onComplete)
            return
          }

          val capturedBitmap = bitmap
          val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
          recognizer.process(InputImage.fromBitmap(capturedBitmap, 0))
            .addOnSuccessListener { result ->
              val text = result.text.trim()
              finishCapture(
                text,
                if (text.isBlank()) "no_text_found" else "success",
                onComplete
              )
              recognizer.close()
              capturedBitmap.recycle()
            }
            .addOnFailureListener {
              finishCapture("", "ocr_failed", onComplete)
              recognizer.close()
              capturedBitmap.recycle()
            }
        }

        override fun onFailure(errorCode: Int) {
          finishCapture("", "screenshot_failed_$errorCode", onComplete)
        }
      })
    } catch (_: Exception) {
      finishCapture("", "screenshot_request_failed", onComplete)
    }
  }

  private fun finishCapture(text: String, status: String, onComplete: () -> Unit) {
    saveCapture(text, status)
    captureInProgress = false
    onComplete()
  }

  private fun saveCapture(text: String, status: String) {
    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
      .putString(KEY_TEXT, text)
      .putLong(KEY_TIME, System.currentTimeMillis())
      .putString(KEY_STATUS, status)
      .apply()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // Intentionally do not inspect or process accessibility events.
    // A screenshot is requested only by the explicit Volume Up double-press shortcut.
  }

  override fun onInterrupt() = Unit

  override fun onDestroy() {
    volumeShortcutRunnable?.let { keyHandler.removeCallbacks(it) }
    keyHandler.removeCallbacksAndMessages(null)
    if (instance === this) instance = null
    super.onDestroy()
  }
}
