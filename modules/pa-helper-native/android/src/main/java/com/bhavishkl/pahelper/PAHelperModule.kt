package com.bhavishkl.pahelper

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class PAHelperModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("PAHelperNative")

    AsyncFunction("startFloatingAssistant") {
      val context = appContext.reactContext
        ?: throw IllegalStateException("React context is not available")

      if (!Settings.canDrawOverlays(context)) {
        val settingsIntent = Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(settingsIntent)
        return@AsyncFunction mapOf("status" to "overlay_permission_required")
      }

      val permissionIntent = Intent(context, PAHelperCapturePermissionActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(permissionIntent)
      mapOf("status" to "screen_capture_permission_requested")
    }

    AsyncFunction("getLatestCapture") {
      val context = appContext.reactContext
        ?: throw IllegalStateException("React context is not available")
      val prefs = context.getSharedPreferences(PAHelperCaptureService.PREFS, 0)
      mapOf(
        "text" to (prefs.getString(PAHelperCaptureService.KEY_TEXT, "") ?: ""),
        "capturedAt" to prefs.getLong(PAHelperCaptureService.KEY_TIME, 0L),
        "status" to (prefs.getString(PAHelperCaptureService.KEY_STATUS, "empty") ?: "empty")
      )
    }

    AsyncFunction("stopFloatingAssistant") {
      val context = appContext.reactContext
        ?: throw IllegalStateException("React context is not available")
      context.stopService(Intent(context, PAHelperCaptureService::class.java))
      mapOf("status" to "stopped")
    }
  }
}
