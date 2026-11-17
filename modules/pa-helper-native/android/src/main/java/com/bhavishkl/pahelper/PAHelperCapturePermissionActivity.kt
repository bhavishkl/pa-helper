package com.bhavishkl.pahelper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle

class PAHelperCapturePermissionActivity : Activity() {
  private val captureRequestCode = 7401

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    startActivityForResult(manager.createScreenCaptureIntent(), captureRequestCode)
  }

  @Deprecated("Android activity result compatibility")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == captureRequestCode && resultCode == RESULT_OK && data != null) {
      val serviceIntent = Intent(this, PAHelperCaptureService::class.java).apply {
        putExtra(PAHelperCaptureService.EXTRA_RESULT_CODE, resultCode)
        putExtra(PAHelperCaptureService.EXTRA_RESULT_DATA, data)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(serviceIntent)
      } else {
        startService(serviceIntent)
      }
    }
    finish()
  }
}
