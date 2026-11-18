package com.bhavishkl.pahelper

import android.Manifest
import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.app.role.RoleManager
import android.os.Bundle
import android.os.Parcelable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.SmsManager
import android.util.Log
import java.security.MessageDigest

/**
 * OTP forwarding is opt-in. This listener only examines notifications from
 * the user's default SMS app plus known SMS clients. It does not inspect unrelated apps.
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
      .putBoolean("listener_connected", if (event == "listener_connected") true else prefs.getBoolean("listener_connected", false))
      .putString("last_event", event)
      .putString("last_detail", detail.take(180))
      .putString("last_source", source.take(100))
      .putLong("last_event_at", System.currentTimeMillis())
      .apply()
  }

  override fun onListenerDisconnected() {
    super.onListenerDisconnected()
    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("listener_connected", false).apply()
    record("listener_disconnected", "Android disconnected the notification listener")
  }

  private fun isSmsPackage(packageName: String): Boolean {
    if (packageName in SMS_PACKAGES) return true
    val roleManager = getSystemService(RoleManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
      roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true &&
      packageName in roleManager.getRoleHolders(RoleManager.ROLE_SMS)) return true
    val name = packageName.lowercase()
    return name.contains("sms") || name.contains("mms") ||
      name.contains("messaging") || name.endsWith(".messages")
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null) return
    if (!isSmsPackage(sbn.packageName)) {
      // Keep a privacy-safe count of non-SMS notifications, not their contents.
      return
    }
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
    val title = listOf(
      Notification.EXTRA_TITLE,
      Notification.EXTRA_TITLE_BIG,
      Notification.EXTRA_SUB_TEXT,
      Notification.EXTRA_SUMMARY_TEXT,
      Notification.EXTRA_INFO_TEXT
    ).mapNotNull { key -> extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() } }
      .distinct().joinToString(" | ")

    // SMS clients differ: some use EXTRA_TEXT, others use MessagingStyle bundles.
    val messageParts = mutableListOf<String>()
    listOf(Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_TEXT_LINES).forEach { key ->
      when (val value = extras.get(key)) {
        is CharSequence -> if (value.isNotBlank()) messageParts.add(value.toString())
        is Array<*> -> value.filterIsInstance<CharSequence>().forEach { if (it.isNotBlank()) messageParts.add(it.toString()) }
      }
    }
    listOf(Notification.EXTRA_MESSAGES, Notification.EXTRA_HISTORIC_MESSAGES).forEach { key ->
      val messages = extras.getParcelableArray(key)
      messages?.forEach { item ->
        val bundle = item as? Bundle
        val body = bundle?.getCharSequence("text")?.toString()
        if (!body.isNullOrBlank()) messageParts.add(body)
      }
    }
    val text = (messageParts + title).distinct().joinToString("\\n").trim()
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
    // Support common SMS wording, with the code either before or after its label.
    // Do not forward arbitrary 4–8 digit numbers unless the surrounding text
    // identifies them as an OTP, verification code, PIN, or login code.
    val patterns = listOf(
      Regex("(?i)\\\\b(?:otp|one[- ]time password|verification code|security code|login code|passcode|pin|code)\\\\D{0,40}(\\\\d{4,8})\\\\b"),
      Regex("(?i)\\\\b(\\\\d{4,8})\\\\D{0,40}(?:is your|as your|your otp|otp|verification code|security code|login code|passcode|pin|code)\\\\b"),
      Regex("(?i)\\\\b(?:use|enter|type|submit)\\\\s+(\\\\d{4,8})\\\\s+(?:to|for)\\\\b")
    )
    return patterns.firstNotNullOfOrNull { it.find(message)?.groupValues?.getOrNull(1) }
  }

  private fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
  }
}
