package com.bhavishkl.pahelper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.accessibilityservice.AccessibilityServiceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.hardware.HardwareBuffer
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class PAHelperAccessibilityService : AccessibilityService() {
  companion object {
    const val PREFS = "pa_helper_capture"
    const val KEY_TEXT = "latest_text"
    const val KEY_TIME = "latest_time"
    const val KEY_STATUS = "latest_status"
    const val KEY_METHOD = "latest_method"

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
      val accessibilityText = captureAccessibilityText()
      if (isUsefulAccessibilityText(accessibilityText)) {
        saveCapture(accessibilityText, "success", "Accessibility")
        openPAHelperHome()
      } else {
        captureOneScreenshot { openPAHelperHome() }
      }
    }
    return false
  }


  private fun captureAccessibilityText(): String {
    val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return ""

    data class PositionedText(val text: String, val left: Int, val top: Int, val bottom: Int, val centerY: Double)
    val items = mutableListOf<PositionedText>()

    fun visit(node: AccessibilityNodeInfo?, depth: Int) {
      if (node == null || depth > 30 || items.size > 1200) return
      try {
        val nodeText = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        val value = if (nodeText.isNotEmpty()) nodeText else description
        val hasTextChild = (0 until node.childCount).any { index ->
          try {
            val child = node.getChild(index)
            val hasText = child != null && (!child.text.isNullOrBlank() || !child.contentDescription.isNullOrBlank())
            child?.recycle()
            hasText
          } catch (_: Exception) { false }
        }
        if (value.isNotEmpty() && !hasTextChild) {
          val bounds = Rect()
          node.getBoundsInScreen(bounds)
          items.add(PositionedText(value.replace(Regex("\\s+"), " "), bounds.left, bounds.top, bounds.bottom, (bounds.top + bounds.bottom) / 2.0))
        }
        for (index in 0 until node.childCount) {
          val child = try { node.getChild(index) } catch (_: Exception) { null }
          if (child != null) {
            visit(child, depth + 1)
            child.recycle()
          }
        }
      } catch (_: Exception) {
        // Ignore stale or inaccessible nodes.
      }
    }

    try { visit(root, 0) } finally { root.recycle() }
    if (items.isEmpty()) return ""

    data class Row(val elements: MutableList<PositionedText>, var top: Int, var bottom: Int) {
      val centerY: Double get() = (top + bottom) / 2.0
    }
    val rows = mutableListOf<Row>()
    for (item in items.sortedBy { it.centerY }) {
      val row = rows.filter { existing ->
        val overlap = minOf(existing.bottom, item.bottom) - maxOf(existing.top, item.top)
        val minHeight = minOf(existing.bottom - existing.top, item.bottom - item.top).coerceAtLeast(1)
        overlap.toDouble() / minHeight >= 0.30 || kotlin.math.abs(existing.centerY - item.centerY) <= minHeight * 0.45
      }.minByOrNull { kotlin.math.abs(it.centerY - item.centerY) }
      if (row == null) rows.add(Row(mutableListOf(item), item.top, item.bottom))
      else {
        row.elements.add(item)
        row.top = minOf(row.top, item.top)
        row.bottom = maxOf(row.bottom, item.bottom)
      }
    }
    return rows.sortedBy { it.centerY }.joinToString("\n") { row ->
      row.elements.sortedBy { it.left }.joinToString("  ") { it.text }
    }.trim()
  }

  private fun isUsefulAccessibilityText(text: String): Boolean {
    if (text.length < 24) return false
    val paymentContext = Regex("transaction successful|received from|paid to|sent to|debited from|credited to|utr|phonepe", RegexOption.IGNORE_CASE).containsMatchIn(text)
    return paymentContext || text.lines().count { it.isNotBlank() } >= 4
  }

  fun captureOneScreenshot(onComplete: () -> Unit) {
    if (captureInProgress) {
      saveCapture("", "capture_in_progress", "OCR")
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
            finishCapture("", "image_processing_failed", onComplete, "OCR")
            return
          }

          val capturedBitmap = bitmap
          val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
          recognizer.process(InputImage.fromBitmap(capturedBitmap, 0))
            .addOnSuccessListener { result ->
              val text = buildReadingOrderText(result)
              finishCapture(
                text,
                if (text.isBlank()) "no_text_found" else "success",
                onComplete,
                "OCR"
              )
              recognizer.close()
              capturedBitmap.recycle()
            }
            .addOnFailureListener {
              finishCapture("", "ocr_failed", onComplete, "OCR")
              recognizer.close()
              capturedBitmap.recycle()
            }
        }

        override fun onFailure(errorCode: Int) {
          finishCapture("", "screenshot_failed_$errorCode", onComplete, "OCR")
        }
      })
    } catch (_: Exception) {
      finishCapture("", "screenshot_request_failed", onComplete, "OCR")
    }
  }

  /**
   * ML Kit's result.text can concatenate separate text blocks in an order that
   * does not match the screen layout. Rebuild the text from line bounding boxes:
   * group lines on the same visual row, then read each row from left to right.
   */
  private fun buildReadingOrderText(result: Text): String {
    data class PositionedLine(
      val text: String,
      val left: Int,
      val top: Int,
      val bottom: Int,
      val centerY: Double
    )

    val positioned = result.textBlocks
      .flatMap { block -> block.lines }
      .mapNotNull { line ->
        val bounds = line.boundingBox ?: return@mapNotNull null
        PositionedLine(
          text = line.text.trim(),
          left = bounds.left,
          top = bounds.top,
          bottom = bounds.bottom,
          centerY = (bounds.top + bounds.bottom) / 2.0
        )
      }
      .filter { it.text.isNotEmpty() }
      .sortedBy { it.centerY }

    if (positioned.isEmpty()) return result.text.trim()

    data class VisualRow(
      val lines: MutableList<PositionedLine>,
      var top: Int,
      var bottom: Int
    ) {
      val centerY: Double get() = (top + bottom) / 2.0
    }

    val rows = mutableListOf<VisualRow>()
    for (line in positioned) {
      val matchingRow = rows
        .filter { row ->
          val overlap = minOf(row.bottom, line.bottom) - maxOf(row.top, line.top)
          val minHeight = minOf(row.bottom - row.top, line.bottom - line.top).coerceAtLeast(1)
          val centerDistance = kotlin.math.abs(row.centerY - line.centerY)
          overlap.toDouble() / minHeight >= 0.30 || centerDistance <= minHeight * 0.45
        }
        .minByOrNull { kotlin.math.abs(it.centerY - line.centerY) }

      if (matchingRow == null) {
        rows.add(VisualRow(mutableListOf(line), line.top, line.bottom))
      } else {
        matchingRow.lines.add(line)
        matchingRow.top = minOf(matchingRow.top, line.top)
        matchingRow.bottom = maxOf(matchingRow.bottom, line.bottom)
      }
    }

    return rows
      .sortedBy { it.centerY }
      .joinToString("\n") { row ->
        row.lines
          .sortedBy { it.left }
          .joinToString(" ") { it.text }
      }
      .trim()
  }

  private fun finishCapture(text: String, status: String, onComplete: () -> Unit, method: String) {
    saveCapture(text, status, method)
    captureInProgress = false
    onComplete()
  }

  private fun openPAHelperHome() {
    try {
      val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
      launchIntent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TOP or
          Intent.FLAG_ACTIVITY_SINGLE_TOP
      )
      startActivity(launchIntent)
    } catch (_: Exception) {
      // Capture is saved already; a launch failure must not affect volume handling.
    }
  }

  private fun saveCapture(text: String, status: String, method: String) {
    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
      .putString(KEY_TEXT, text)
      .putLong(KEY_TIME, System.currentTimeMillis())
      .putString(KEY_STATUS, status)
      .putString(KEY_METHOD, method)
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
