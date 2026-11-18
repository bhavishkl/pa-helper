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

  override fun onListenerConnected() {
    super.onListenerConnected()
    record("listener_connected", "Android notification listener connected")
  }

  private fun record(event: String, detail: String, source: String = "") {
    val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
    prefs.edit()
      .putString("last_event", event)
      .putString("last_detail", detail.take(180))
      .putString("last_source", source.take(100))
      .putLong("last_event_at", System.currentTimeMillis())
      .apply()
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null) return
    if (sbn.packageName !in SMS_PACKAGES) return
    val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
    val source = sbn.packageName
    prefs.edit().putInt("sms_notifications_seen", prefs.getInt("sms_notifications_seen", 0) + 1).apply()
    if (!prefs.getBoolean("enabled", false)) {
      record("notification_ignored", "Supported SMS app notification received while forwarding is off", source)
      return
    }
    if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
      record("permission_missing", "SEND_SMS permission missing; OTP not forwarded", source)
      Log.w(TAG, "SEND_SMS permission missing; OTP not forwarded")
      return
    }

    val extras = sbn.notification.extras ?: run {
      record("empty_notification", "Notification has no extras", source)
      return
    }
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text = buildString {
      append(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
      append("\n")
      append(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty())
      val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
      lines?.forEach { append("\n"); append(it?.toString().orEmpty()) }
    }.trim()
    if (text.isBlank()) {
      record("empty_notification", "SMS notification text is empty or hidden by the messaging app", source)
      return
    }

    val otp = extractOtp(text) ?: run {
      record("no_otp_match", "SMS notification received, but no 4–8 digit OTP pattern matched", source)
      return
    }
    prefs.edit().putLong("last_otp_detected_at", System.currentTimeMillis()).apply()
    val destination = prefs.getString("destination", "").orEmpty()
    if (!destination.matches(Regex("^\\+?[1-9]\\d{7,14}$"))) {
      record("invalid_destination", "Configured forwarding number is invalid", source)
      Log.w(TAG, "Invalid OTP forwarding destination")
      return
    }

    val signature = sha256("${sbn.packageName}|$title|$text|$otp")
    val sent = prefs.getStringSet("sent_signatures", emptySet())?.toMutableSet() ?: mutableSetOf()
    if (signature in sent) {
      record("duplicate_ignored", "This exact OTP notification was already forwarded", source)
      return
    }

    try {
      prefs.edit().putInt("forward_attempts", prefs.getInt("forward_attempts", 0) + 1).apply()
      record("forward_attempt", "OTP matched; attempting to send SMS", source)
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
      prefs.edit().putInt("forward_accepted", prefs.getInt("forward_accepted", 0) + 1).apply()
      record("forward_accepted", "Android accepted the SMS send request; carrier delivery is not confirmed", source)
      Log.i(TAG, "OTP notification forwarded from an SMS app")
    } catch (error: Exception) {
      prefs.edit().putInt("forward_errors", prefs.getInt("forward_errors", 0) + 1).apply()
      record("forward_error", (error.javaClass.simpleName + ": " + (error.message ?: "SMS send failed")), source)
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
