
package dev.omkartenkale.fliptopause

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.quicksettings.TileService
import android.widget.Toast

class FlipMediaToggleService : Service(), SensorEventListener {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "NotificationChannel"
        private const val NOTIFICATION_ID = 1
        const val START_ACTION = "StartAction"
        const val STOP_ACTION = "StopAction"

        fun triggerHaptic(context: Context, durationMs: Long) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (!vibrator.hasVibrator()) return

            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                null
            }

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && effect != null -> {
                    val attrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ACCESSIBILITY)
                        .build()
                    vibrator.vibrate(effect, attrs)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && effect != null -> {
                    vibrator.vibrate(effect)
                }
                else -> {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val am: AudioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val nm: NotificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val sensorManager: SensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private var gravitySensor: Sensor? = null

    private var audioFocusRequest: AudioFocusRequest? = null
    private var isDebounceScheduled = false
    private var reverseObserverUnsubscribe: (() -> Unit)? = null

    private val debounceRunnable = Runnable {
        isDebounceScheduled = false
        if (!am.isMusicActive) return@Runnable
        pauseMediaPlayback()
        FlipState.isPausedByUs.value = true
        updateNotification()
        triggerHaptic(this@FlipMediaToggleService, 50L)
        Toast.makeText(this@FlipMediaToggleService, AppStrings.current.toastPaused, Toast.LENGTH_SHORT).show()
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            if (FlipState.isPausedByUs.value) {
                FlipState.isPausedByUs.value = false
                updateNotification()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        FlipState.init(this)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
        }

        // Keep notification instruction in sync if user flips "Reverse" in Settings
        reverseObserverUnsubscribe = FlipState.isReversed.observe {
            updateNotification()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            intent?.getBooleanExtra(START_ACTION, false) == true -> start()
            intent?.getBooleanExtra(STOP_ACTION, false) == true -> stop()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        FlipState.isServiceRunning.value = true
        val notification = getNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                )
            } catch (_: IllegalArgumentException) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        requestTileSync()

        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun cancelDebounce() {
        if (isDebounceScheduled) {
            mainHandler.removeCallbacks(debounceRunnable)
            isDebounceScheduled = false
        }
    }

    private fun stop() {
        cancelDebounce()
        sensorManager.unregisterListener(this)
        if (FlipState.isPausedByUs.value) {
            resumeMediaPlayback()
            FlipState.isPausedByUs.value = false
        }
        FlipState.isServiceRunning.value = false
        requestTileSync()
        stopSelf()
    }

    override fun onDestroy() {
        reverseObserverUnsubscribe?.invoke()
        reverseObserverUnsubscribe = null
        cancelDebounce()
        mainHandler.removeCallbacksAndMessages(null)
        sensorManager.unregisterListener(this)
        FlipState.isServiceRunning.value = false
        requestTileSync()
        super.onDestroy()
    }

    private fun requestTileSync() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(
                this,
                ComponentName(this, FlipMediaToggleTileService::class.java)
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val z = event.values[2]
        val threshold = FlipState.zThreshold.value
        val isPaused = FlipState.isPausedByUs.value
        val isReversed = FlipState.isReversed.value

        val inPauseZone = if (isReversed) z < -threshold else z > threshold
        val inResumeZone = if (isReversed) z > threshold else z < -threshold

        if (inPauseZone && !isPaused) {
            if (!isDebounceScheduled) {
                val waitMs = (FlipState.minPauseDelaySeconds.value * 1000).toLong()
                if (waitMs > 0L) {
                    isDebounceScheduled = true
                    mainHandler.postDelayed(debounceRunnable, waitMs)
                } else {
                    debounceRunnable.run()
                }
            }
        } else if (!inPauseZone && !isPaused) {
            cancelDebounce()
        }

        if (inResumeZone && isPaused) {
            cancelDebounce()
            resumeMediaPlayback()
            FlipState.isPausedByUs.value = false
            updateNotification()
            triggerHaptic(this@FlipMediaToggleService, 50L)
            mainHandler.post {
                Toast.makeText(this@FlipMediaToggleService, AppStrings.current.toastResumed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun pauseMediaPlayback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun resumeMediaPlayback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusChangeListener)
        }
    }

    private fun updateNotification() {
        if (FlipState.isServiceRunning.value) {
            nm.notify(NOTIFICATION_ID, getNotification())
        }
    }

    private fun getNotification(): Notification {
        createNotificationChannel()
        val strings = AppStrings.current
        val isPaused = FlipState.isPausedByUs.value
        val isReversed = FlipState.isReversed.value

        // Default (isReversed = false): face up pauses, face down resumes.
        // Reversed (isReversed = true): face down pauses, face up resumes.
        val instruction = when {
            isPaused && isReversed -> strings.notifFaceUpToResume
            isPaused -> strings.notifFaceDownToResume
            isReversed -> strings.notifFaceDownToPause
            else -> strings.notifFaceUpToPause
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).apply {
                setPriority(Notification.PRIORITY_LOW)
                setSound(null)
                setVibrate(null)
            }
        }

        val turnOffAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_delete),
                strings.notifTurnOff,
                getStopAction()
            ).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Action.Builder(
                android.R.drawable.ic_delete,
                strings.notifTurnOff,
                getStopAction()
            ).build()
        }

        // No .setContentTitle(...) — only the instruction line is displayed
        return builder
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentText(instruction)
            .setContentIntent(getOpenAppIntent())
            .setOngoing(true)
            .addAction(turnOffAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                AppStrings.current.notifChannelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun getOpenAppIntent(): PendingIntent {
        val openIntent = Intent(this, ViewActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(this, 0, openIntent, flags)
    }

    private fun getStopAction(): PendingIntent {
        val stopIntent = Intent(this, FlipMediaToggleService::class.java).apply {
            putExtra(STOP_ACTION, true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getService(this, 1, stopIntent, flags)
    }
}
