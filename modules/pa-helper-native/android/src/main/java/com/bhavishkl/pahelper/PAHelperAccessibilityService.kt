package com.bhavishkl.pahelper

import android.accessibilityservice.AccessibilityService
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
  }

  @Volatile
  private var captureInProgress = false

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
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
    // A screenshot is requested only by an explicit tap on the floating PA button.
  }

  override fun onInterrupt() = Unit

  override fun onDestroy() {
    if (instance === this) instance = null
    super.onDestroy()
  }
}
