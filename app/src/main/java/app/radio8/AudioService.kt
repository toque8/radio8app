package app.radio8

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
    
    private val totalTracks = 30
    private var shuffledPlaylist = mutableListOf<Int>()
    private var currentIndex = 0
    
    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }
    
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "radio8_channel",
                "Radio8 Playback",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for Radio8 playback"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        shuffledPlaylist = (1..totalTracks).toMutableList()
        shuffledPlaylist.shuffle()
    }
    
    private fun ensurePlaylist() {
        if (shuffledPlaylist.isEmpty()) {
            shuffledPlaylist = (1..totalTracks).toMutableList()
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
                    startForeground(1, createNotification(trackNumber))
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
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }
    
    fun nextTrack() {
        ensurePlaylist()
        
        currentIndex++
        if (currentIndex >= shuffledPlaylist.size) {
            shuffledPlaylist.shuffle()
            currentIndex = 0
        }
        
        playTrack(shuffledPlaylist[currentIndex])
    }
    
    private fun createNotification(trackNumber: Int): Notification {
        val stopIntent = Intent(this, AudioService::class.java).apply {
            action = "ACTION_STOP"
        }
        
        val nextIntent = Intent(this, AudioService::class.java).apply {
            action = "ACTION_NEXT"
        }
        
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val builder = NotificationCompat.Builder(this, "radio8_channel")
            .setContentTitle("Radio8")
            .setContentText("Track$trackNumber")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                PendingIntent.getService(
                    this,
                    1,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                PendingIntent.getService(
                    this,
                    2,
                    nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
        
        val style = MediaNotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1)
        
        builder.setStyle(style)
        
        return builder.build()
    }
    
    override fun onCompletion(mp: MediaPlayer?) {
        nextTrack()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_STOP" -> stopPlayback()
            "ACTION_NEXT" -> nextTrack()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder = LocalBinder()
    
    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }
}