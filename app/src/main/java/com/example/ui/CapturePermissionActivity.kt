package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.service.FloatingAssistantService

class CapturePermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                FloatingAssistantService.screenCaptureResultCode = result.resultCode
                FloatingAssistantService.screenCaptureIntent = result.data
                // Notifica o serviço ou apenas fecha, o serviço verá os dados estáticos
                sendBroadcast(Intent("com.example.ACTION_CAPTURE_READY"))
            }
            finish()
        }
        
        launcher.launch(mpManager.createScreenCaptureIntent())
    }
}
