package com.bhavishkl.pahelper

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class PAHelperModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("PAHelperNative")

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
