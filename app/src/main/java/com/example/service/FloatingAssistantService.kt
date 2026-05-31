package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.ui.FloatingAssistantUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class FloatingAssistantService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performAttach()
    }

    override val viewModelStore: ViewModelStore = store
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    companion object {
        const val CHANNEL_ID = "floating_assistant_channel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
        var screenCaptureIntent: Intent? = null
        var screenCaptureResultCode: Int = 0
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        isRunning = true
        
        createNotificationChannel()
        startServiceAsForeground()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showFloatingBubble()
    }

    private fun startServiceAsForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun updateToCaptureForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notification = createNotification()
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        }
    }

    private fun showFloatingBubble() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        val composeView = ComposeView(this).apply {
            setContent {
                FloatingAssistantUI(
                    service = this@FloatingAssistantService,
                    onMove = { dx, dy -> updatePosition(dx, dy) },
                    onCaptureResult = { resultCode, data -> 
                        screenCaptureResultCode = resultCode
                        screenCaptureIntent = data
                    }
                )
            }
        }

        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)
        
        floatingView = composeView
        windowManager.addView(floatingView, params)
    }

    fun updateLayoutParams(update: (WindowManager.LayoutParams) -> Unit) {
        params?.let {
            update(it)
            windowManager.updateViewLayout(floatingView, it)
        }
    }

    private fun updatePosition(dx: Int, dy: Int) {
        params?.let {
            it.x += dx
            it.y += dy
            windowManager.updateViewLayout(floatingView, it)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Assistente Flutuante",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IA Float Assistant")
            .setContentText("O assistente está ativo.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        floatingView?.let { windowManager.removeView(it) }
        scope.cancel()
    }
}
