package com.bhavishkl.pahelper

import android.Manifest
import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.SmsManager
import android.util.Log
import java.security.MessageDigest

/**
 * OTP forwarding is opt-in. This listener only examines notifications from
 * known SMS applications; it does not scrape arbitrary app notifications.
 */
class PAHelperOtpNotificationListener : NotificationListenerService() {
  companion object {
    const val PREFS = "pa_helper_otp_forwarding"
    private const val TAG = "PAHelperOtpListener"
    private val SMS_PACKAGES = setOf(
      "com.google.android.apps.messaging",
      "com.android.messaging",
      "com.samsung.android.messaging",
      "com.miui.mms",
      "com.oneplus.mms",
      "com.vivo.messaging",
      "com.oppo.mms",
      "com.coloros.mms",
      "com.transsion.phoenix"
    )
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null || sbn.packageName !in SMS_PACKAGES) return
    val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
    if (!prefs.getBoolean("enabled", false)) return
    if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "SEND_SMS permission missing; OTP not forwarded")
      return
    }

    val extras = sbn.notification.extras ?: return
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text = buildString {
      append(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
      append("\n")
      append(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty())
      val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
      lines?.forEach { append("\n"); append(it?.toString().orEmpty()) }
    }.trim()
    if (text.isBlank()) return

    val otp = extractOtp(text) ?: return
    val destination = prefs.getString("destination", "").orEmpty()
    if (!destination.matches(Regex("^\\+?[1-9]\\d{7,14}$"))) {
      Log.w(TAG, "Invalid OTP forwarding destination")
      return
    }

    val signature = sha256("${sbn.packageName}|$title|$text|$otp")
    val sent = prefs.getStringSet("sent_signatures", emptySet())?.toMutableSet() ?: mutableSetOf()
    if (signature in sent) return

    try {
      val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(SmsManager::class.java)
      } else {
        @Suppress("DEPRECATION")
        SmsManager.getDefault()
      } ?: throw IllegalStateException("SMS service unavailable")
      manager.sendTextMessage(destination, null, "OTP: $otp", null, null)
      sent.add(signature)
      while (sent.size > 100) sent.remove(sent.first())
      prefs.edit().putStringSet("sent_signatures", sent).putLong("last_forwarded_at", System.currentTimeMillis())
        .putString("last_forwarded_source", title.take(80)).apply()
      Log.i(TAG, "OTP notification forwarded from an SMS app")
    } catch (error: Exception) {
      Log.e(TAG, "OTP forwarding failed", error)
    }
  }

  private fun extractOtp(message: String): String? {
    val patterns = listOf(
      Regex("(?i)(?:otp|one[- ]time password|verification code|security code|login code|passcode)\\D{0,32}(\\d{4,8})"),
      Regex("(?i)(\\d{4,8})\\D{0,24}(?:is your|as your|your otp|verification code)")
    )
    return patterns.firstNotNullOfOrNull { it.find(message)?.groupValues?.getOrNull(1) }
  }

  private fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
  }
}
