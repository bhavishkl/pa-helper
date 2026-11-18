package com.bhavishkl.pahelper

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings\nimport android.Manifest\nimport android.content.pm.PackageManager\nimport android.os.Build
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class PAHelperModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("PAHelperNative")


    AsyncFunction("getOtpForwardingSettings") {
      val context = appContext.reactContext ?: throw IllegalStateException("React context is not available")
      val prefs = context.getSharedPreferences(PAHelperOtpNotificationListener.PREFS, 0)
      mapOf(
        "enabled" to prefs.getBoolean("enabled", false),
        "destination" to (prefs.getString("destination", "+91843100179") ?: "+91843100179")
      )
    }

    AsyncFunction("saveOtpForwardingSettings") { enabled: Boolean, destination: String ->
      val context = appContext.reactContext ?: throw IllegalStateException("React context is not available")
      context.getSharedPreferences(PAHelperOtpNotificationListener.PREFS, 0).edit()
        .putBoolean("enabled", enabled)
        .putString("destination", destination)
        .apply()
      mapOf("status" to "saved", "enabled" to enabled)
    }

    AsyncFunction("getOtpListenerStatus") {
      val context = appContext.reactContext ?: throw IllegalStateException("React context is not available")
      val component = ComponentName(context, PAHelperOtpNotificationListener::class.java)
      val enabledListeners = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
      ).orEmpty().split(':')
      mapOf(
        "notificationAccess" to enabledListeners.any { ComponentName.unflattenFromString(it) == component },
        "smsPermission" to (context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
      )
    }

    AsyncFunction("openOtpNotificationSettings") {
      val context = appContext.reactContext ?: throw IllegalStateException("React context is not available")
      context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      mapOf("status" to "settings_opened")
    }

    AsyncFunction("getAccounts") {
      val context = appContext.reactContext
        ?: throw IllegalStateException("React context is not available")
      context.getSharedPreferences("pa_helper_accounts", 0)
        .getString("accounts_json", "[]") ?: "[]"
    }

    AsyncFunction("saveAccounts") { accountsJson: String ->
      val context = appContext.reactContext
        ?: throw IllegalStateException("React context is not available")
      context.getSharedPreferences("pa_helper_accounts", 0)
        .edit()
        .putString("accounts_json", accountsJson)
        .apply()
      mapOf("status" to "saved")
    }

    AsyncFunction("enableVolumeShortcut") {
      val context = appContext.reactContext
        ?: throw IllegalStateException("React context is not available")

      val expected = ComponentName(context, PAHelperAccessibilityService::class.java)
      val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
      ).orEmpty().split(':')

      val accessibilityEnabled = enabledServices.any {
        ComponentName.unflattenFromString(it) == expected
      }

      if (!accessibilityEnabled) {
        context.startActivity(
          Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return@AsyncFunction mapOf("status" to "accessibility_permission_required")
      }

      mapOf("status" to "shortcut_ready")
    }

    AsyncFunction("startFloatingAssistant") {
      val context = appContext.reactContext
        ?: throw IllegalStateException("React context is not available")

      if (!Settings.canDrawOverlays(context)) {
        context.startActivity(
          Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .setData(android.net.Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return@AsyncFunction mapOf("status" to "overlay_permission_required")
      }

      val expected = ComponentName(context, PAHelperAccessibilityService::class.java)
      val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
      ).orEmpty().split(':')

      val accessibilityEnabled = enabledServices.any {
        ComponentName.unflattenFromString(it) == expected
      }

      if (!accessibilityEnabled) {
        context.startActivity(
          Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return@AsyncFunction mapOf("status" to "accessibility_permission_required")
      }

      val serviceIntent = Intent(context, PAHelperOverlayService::class.java)
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
      } else {
        context.startService(serviceIntent)
      }
      mapOf("status" to "overlay_started")
    }

    AsyncFunction("getCaptureHistory") {
      val context = appContext.reactContext
        ?: throw IllegalStateException("React context is not available")
      context.getSharedPreferences("pa_helper_capture_history", 0)
        .getString("history_json", "[]") ?: "[]"
    }

    AsyncFunction("saveCaptureHistory") { historyJson: String ->
      val context = appContext.reactContext
        ?: throw IllegalStateException("React context is not available")
      context.getSharedPreferences("pa_helper_capture_history", 0)
        .edit().putString("history_json", historyJson).apply()
      mapOf("status" to "saved")
    }

    AsyncFunction("copyText") { value: String ->
      val context = appContext.reactContext
        ?: throw IllegalStateException("React context is not available")
      val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
      clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PA Helper capture", value))
      mapOf("status" to "copied")
    }

    AsyncFunction("getLatestCapture") {
      val context = appContext.reactContext
        ?: throw IllegalStateException("React context is not available")
      val prefs = context.getSharedPreferences(PAHelperAccessibilityService.PREFS, 0)
      mapOf(
        "text" to (prefs.getString(PAHelperAccessibilityService.KEY_TEXT, "") ?: ""),
        "capturedAt" to prefs.getLong(PAHelperAccessibilityService.KEY_TIME, 0L),
        "status" to (prefs.getString(PAHelperAccessibilityService.KEY_STATUS, "empty") ?: "empty"),
        "method" to (prefs.getString(PAHelperAccessibilityService.KEY_METHOD, "OCR") ?: "OCR")
      )
    }

    AsyncFunction("stopFloatingAssistant") {
      val context = appContext.reactContext
        ?: throw IllegalStateException("React context is not available")
      context.stopService(Intent(context, PAHelperOverlayService::class.java))
      mapOf("status" to "stopped")
    }
  }
}
