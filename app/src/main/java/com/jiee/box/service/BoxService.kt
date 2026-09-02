package com.jiee.box.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.jiee.box.MainActivity
import com.jiee.box.JieeBoxApplication
import com.jiee.box.network.NetworkUtils
import com.jiee.box.server.JieeHttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BoxServerState(
    val isRunning: Boolean = false,
    val address: String? = null,
    val connectedDevices: Int = 0,
    val devices: List<JieeHttpServer.ConnectedDevice> = emptyList(),
    val activeTransfers: Int = 0,
    val error: String? = null
)

/**
 * Foreground service so the HTTP server keeps running while the app is
 * backgrounded or the screen is off — Android will otherwise throttle or
 * kill background network sockets, which would break section 19's core
 * scenario ("un autre téléphone se connecte... télécharge... sans problème").
 */
class BoxService : Service() {

    companion object {
        const val ACTION_START = "com.jiee.box.action.START"
        const val ACTION_STOP = "com.jiee.box.action.STOP"
        const val PORT = 8080
        private const val CHANNEL_ID = "jiee_box_channel"
        private const val NOTIFICATION_ID = 1

        private val _state = MutableStateFlow(BoxServerState())
        val state: StateFlow<BoxServerState> = _state.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, BoxService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BoxService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private var server: JieeHttpServer? = null
    private var pollHandle: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> startServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        if (server != null) return

        val repository = (application as JieeBoxApplication).fileRepository
        val receivedRepository = (application as JieeBoxApplication).receivedFileRepository
        val transferLog = (application as JieeBoxApplication).transferLogRepository
        val settings = (application as JieeBoxApplication).settingsRepository.get()
        repository.refreshAvailability()

        val ip = NetworkUtils.findLocalIPv4()
        if (ip == null) {
            _state.value = BoxServerState(
                isRunning = false,
                error = "Aucune adresse Wi-Fi locale détectée. Activez le hotspot puis réessayez."
            )
            stopSelf()
            return
        }

        try {
            val newServer = JieeHttpServer(
                PORT, applicationContext, repository, receivedRepository, transferLog, settings.boxName, settings.password
            )
            newServer.start(NanoHTTPDTimeout, true)
            server = newServer

            // Many Android OEMs (Samsung, Xiaomi, etc.) throttle CPU/network for
            // apps once the screen is off, even with a foreground service — this
            // is what stalls or drops an in-progress upload the moment the host
            // stops actively looking at the app. A partial wake lock keeps the
            // CPU awake for as long as the box is running so transfers can
            // actually finish in the background.
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "JieeBox::ServerWakeLock"
            ).apply { acquire(12 * 60 * 60 * 1000L /* 12h safety cap */) }

            val address = "http://$ip:$PORT"
            _state.value = BoxServerState(isRunning = true, address = address, connectedDevices = 0)

            startForeground(NOTIFICATION_ID, buildNotification(address, settings.boxName))
            startPresencePolling()
        } catch (e: Exception) {
            _state.value = BoxServerState(isRunning = false, error = "Impossible de démarrer le serveur: ${e.message}")
            stopSelf()
        }
    }

    private fun stopServer() {
        pollHandle?.let { handler.removeCallbacks(it) }
        server?.stop()
        server = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        _state.value = BoxServerState(isRunning = false)
    }

    /** Periodically refresh the connected-devices list and active-transfer
     *  count shown in the UI. */
    private fun startPresencePolling() {
        val runnable = object : Runnable {
            override fun run() {
                val s = server
                if (s != null) {
                    _state.value = _state.value.copy(
                        connectedDevices = s.connectedDeviceCount,
                        devices = s.connectedDevices,
                        activeTransfers = s.activeTransferCount
                    )
                    updateNotification()
                    handler.postDelayed(this, 5_000)
                }
            }
        }
        pollHandle = runnable
        handler.postDelayed(runnable, 5_000)
    }

    private fun buildNotification(address: String, boxName: String): Notification {
        createChannelIfNeeded()

        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, BoxService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$boxName active")
            .setContentText(address)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Arrêter", stopIntent)
            .build()
    }

    private fun updateNotification() {
        val addr = _state.value.address ?: return
        val boxName = (application as JieeBoxApplication).settingsRepository.get().boxName
        val manager = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$boxName — ${_state.value.connectedDevices} appareil(s)")
            .setContentText(addr)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
        manager.notify(NOTIFICATION_ID, notif)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "JIEE BOX", NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}

// NanoHTTPD's start() takes a socket-read timeout in ms. Bumped well above the
// default 60s: under CPU throttling (screen off, Doze) a slow-but-alive
// connection can go quiet for longer than that between reads, and a timeout
// this short was closing the socket mid-upload — exactly the "network error"
// the user hit. The wake lock above is the primary fix; this is a safety margin.
private const val NanoHTTPDTimeout = 10 * 60 * 1000 // 10 minutes
