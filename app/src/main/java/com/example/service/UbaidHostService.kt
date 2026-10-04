package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.repository.DeviceRepository
import com.example.data.repository.HostConfigRepository
import com.example.network.HostState
import com.example.network.LocalSignalingServer
import com.example.network.SessionManager
import com.example.network.SignalingClient
import com.example.security.KeystoreHelper
import com.example.system.DeviceTelemetryProvider
import com.example.system.HostFileManager
import com.example.system.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UbaidHostService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var telemetryJob: Job? = null

    lateinit var sessionManager: SessionManager
        private set

    private var localServer: LocalSignalingServer? = null
    private var signalingClient: SignalingClient? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            scope.launch {
                sessionManager.evaluateInitialState()
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            scope.launch {
                sessionManager.setHostState(HostState.RECONNECTING)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val db = AppDatabase.getInstance(this)
        val devRepo = DeviceRepository(db.authorizedDeviceDao(), db.sessionLogDao())
        val configRepo = HostConfigRepository(db.hostConfigDao())
        val keystoreHelper = KeystoreHelper()
        val permManager = PermissionManager(this)
        val telemetry = DeviceTelemetryProvider(this)
        val fileManager = HostFileManager(this)

        sessionManager = SessionManager(
            context = this,
            deviceRepository = devRepo,
            hostConfigRepository = configRepo,
            keystoreHelper = keystoreHelper,
            permissionManager = permManager,
            telemetryProvider = telemetry,
            fileManager = fileManager
        )

        // Setup Local embedded signaling server
        localServer = LocalSignalingServer(port = 8999) { rawJson ->
            sessionManager.handleIncomingMessage(rawJson)
        }
        localServer?.start()

        // Setup WAN Relay Client
        signalingClient = SignalingClient { rawJson ->
            sessionManager.handleIncomingMessage(rawJson)
        }

        // Bridge outgoing messages to both local and relay
        sessionManager.sendMessageCallback = { json ->
            localServer?.broadcast(json)
            signalingClient?.send(json)
        }

        // Connect WAN relay if configured
        scope.launch(Dispatchers.IO) {
            val config = configRepo.getOrCreateConfig()
            signalingClient?.connect(config.signalingServerUrl)
        }

        // Register network callback
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        cm?.registerDefaultNetworkCallback(networkCallback)

        // Acquire WakeLock to prevent deep sleep while phone is at home
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Ubaid::HostWakeLock")?.apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // 24 hours max safe timeout
        }

        createNotificationChannel()
        startAsForeground()

        // Observe session state and update notification
        scope.launch {
            sessionManager.hostState.collectLatest { state ->
                updateNotification(state)
            }
        }

        // Periodic telemetry broadcast every 10 seconds if session is active
        telemetryJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(10000)
                if (sessionManager.hostState.value == HostState.ACTIVE_SESSION) {
                    sessionManager.sendDeviceStatus()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun startAsForeground() {
        val notification = buildNotification(HostState.ONLINE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // If projection is active, add mediaProjection type
                if (ScreenCaptureManager.isProjectionActive) {
                    fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: HostState) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: HostState): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, UbaidHostService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when (state) {
            HostState.ACTIVE_SESSION -> "Active Remote Session • Salim Connected"
            HostState.ONLINE -> "Ubaid Host Online • Phone Protected"
            HostState.PAIRING -> "Ubaid Host • Waiting for Pairing"
            HostState.SETUP_REQUIRED -> "Ubaid Host • Setup Required"
            else -> "Ubaid Host • ${state.label}"
        }

        val text = when (state) {
            HostState.ACTIVE_SESSION -> "Screen streaming and input control active"
            HostState.ONLINE -> "Listening for authorized controller on WAN and Local LAN (Port 8999)"
            else -> "Running in background"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(0, "Stop Host", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        telemetryJob?.cancel()
        localServer?.stop()
        signalingClient?.disconnect()

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        try {
            cm?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        instance = null
    }

    companion object {
        const val CHANNEL_ID = "ubaid_host_service_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_HOST_SERVICE"

        @Volatile
        var instance: UbaidHostService? = null
            private set

        fun isRunning(): Boolean = instance != null

        fun start(context: Context) {
            val intent = Intent(context, UbaidHostService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, UbaidHostService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
