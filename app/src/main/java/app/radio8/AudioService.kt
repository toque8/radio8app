package app.radio8

import app.radio8.R

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat

class AudioService : Service(), MediaPlayer.OnCompletionListener {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var notificationManager: NotificationManager
    private var isPaused = false
    private var currentPosition = 0
    private var currentIndex = 0

    private val totalTracks = 30
    private var shuffledPlaylist = mutableListOf<Int>()

    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "radio8_channel",
                "Radio8 Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for Radio8 playback"
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        shuffledPlaylist.clear()
        shuffledPlaylist.addAll(1..totalTracks)
        shuffledPlaylist.shuffle()
    }

    private fun ensurePlaylist() {
        if (shuffledPlaylist.isEmpty()) {
            shuffledPlaylist.clear()
            shuffledPlaylist.addAll(1..totalTracks)
            shuffledPlaylist.shuffle()
            currentIndex = 0
        }
    }

    fun playRandomTrack() {
        ensurePlaylist()
        playTrack(shuffledPlaylist[currentIndex])
    }

    private fun playTrack(trackNumber: Int) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                val resourceName = "track_$trackNumber"
                val trackResId = resources.getIdentifier(resourceName, "raw", packageName)

                if (trackResId != 0) {
                    val afd = resources.openRawResourceFd(trackResId)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()

                    setOnCompletionListener(this@AudioService)
                    prepare()
                    start()
                    this@AudioService.isPaused = false
                    this@AudioService.currentPosition = 0
                    showNotification(trackNumber, false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopPlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.reset()
        }
        isPaused = false
        currentPosition = 0
        notificationManager.cancel(1)
        stopSelf()
    }

    fun pauseResumePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                currentPosition = it.currentPosition
                it.pause()
                isPaused = true
                showNotification(shuffledPlaylist[currentIndex], true)
            } else {
                it.seekTo(currentPosition)
                it.start()
                isPaused = false
                showNotification(shuffledPlaylist[currentIndex], false)
            }
        }
    }

    fun nextTrack() {
        ensurePlaylist()
        currentIndex++
        if (currentIndex >= shuffledPlaylist.size) {
            currentIndex = 0
            shuffledPlaylist.shuffle()
        }
        playTrack(shuffledPlaylist[currentIndex])
    }

    private fun showNotification(trackNumber: Int, isPaused: Boolean): Notification {
        val pauseResumeIntent = Intent(this, AudioService::class.java).apply {
            action = "ACTION_PAUSE_RESUME"
        }

        val nextIntent = Intent(this, AudioService::class.java).apply {
            action = "ACTION_NEXT"
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val deleteIntent = Intent(this, AudioService::class.java).apply {
            action = "ACTION_STOP"
        }

        val pauseResumeIcon = if (isPaused) {
            R.drawable.ic_media_play
        } else {
            R.drawable.ic_media_pause
        }

        val pauseResumeText = if (isPaused) "Play" else "Pause"

        val builder = NotificationCompat.Builder(this, "radio8_channel")
            .setContentTitle("Radio8")
            .setContentText("Track $trackNumber")
            .setSmallIcon(R.drawable.ic_media_play)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                pauseResumeIcon,
                pauseResumeText,
                PendingIntent.getService(
                    this,
                    1,
                    pauseResumeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                R.drawable.ic_media_next,
                "Next",
                PendingIntent.getService(
                    this,
                    2,
                    nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setDeleteIntent(
                PendingIntent.getService(
                    this,
                    0,
                    deleteIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setColor(Color.WHITE)
            .setColorized(true)

        val style = MediaNotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1)

        builder.setStyle(style)

        val notification = builder.build()
        notificationManager.notify(1, notification)
        return notification
    }

    override fun onCompletion(mp: MediaPlayer?) {
        nextTrack()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_PAUSE_RESUME" -> pauseResumePlayback()
            "ACTION_NEXT" -> nextTrack()
            "ACTION_STOP" -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder() 

    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }
}
