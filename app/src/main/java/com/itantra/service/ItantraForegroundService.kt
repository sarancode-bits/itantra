package com.itantra.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.itantra.MainActivity
import com.itantra.core.alert.SosAlertPlayer
import com.itantra.core.transport.ConnectionState
import com.itantra.core.transport.P2pTransport
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ItantraForegroundService : Service() {

    @Inject lateinit var transport: P2pTransport
    @Inject lateinit var sosAlertPlayer: SosAlertPlayer

    private val scope = CoroutineScope(Dispatchers.Main)
    private var serviceJob: Job? = null

    companion object {
        const val CHANNEL_ID = "itantra_p2p_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_SERVICE"

        fun startService(context: Context) {
            try {
                val intent = Intent(context, ItantraForegroundService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, ItantraForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = buildNotification("iTantra Active", "Searching or connected over P2P network")
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                observeConnectionState()
            }
        }
        return START_STICKY
    }

    private fun observeConnectionState() {
        serviceJob?.cancel()
        serviceJob = scope.launch {
            transport.connectionState.collect { state ->
                val title = "iTantra Emergency Link"
                val body = when (state) {
                    is ConnectionState.Advertising -> "Host mode active — waiting for peer"
                    is ConnectionState.Discovering -> "Scanning for nearby emergency hosts..."
                    is ConnectionState.Connecting -> "Connecting to ${state.peerName}..."
                    is ConnectionState.Connected -> "Connected to ${state.peerName}"
                    is ConnectionState.Disconnected -> "Disconnected"
                    else -> "Offline"
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification(title, body))
            }
        }
    }

    private fun buildNotification(title: String, body: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "iTantra Peer-to-Peer Link",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active emergency radio connection status"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob?.cancel()
        super.onDestroy()
    }
}
