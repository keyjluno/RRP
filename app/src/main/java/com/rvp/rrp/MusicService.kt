package com.rvp.rrp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.DefaultLoadControl
import org.json.JSONObject
import java.net.URL

class MusicService : Service() {

    private lateinit var player: ExoPlayer
    private var isPlaying = false
    private var trackUpdateJob: Thread? = null
    private var isUpdating = false
    private var currentTrackName = "Загрузка..."

    companion object {
        const val CHANNEL_ID = "RRPChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_TRACK_UPDATE = "com.rvp.rrp.TRACK_UPDATE"
        const val EXTRA_TRACK_NAME = "track_name"

        // Адреса потока и API из вашего исходного кода
        private const val STREAM_URL = "https://myradio24.org/radiorever"
        private const val TRACK_INFO_URL = "https://myradio24.com/users/radiorever/status.json"
    }

    override fun onCreate() {
        super.onCreate()

        // Создаём канал уведомлений (для Android 8+)
        createNotificationChannel()

        // Настройка буферизации для стабильного воспроизведения
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(5000, 30000, 2500, 5000)
            .build()

        // Создание ExoPlayer
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()

        // Настройка потока
        val mediaItem = MediaItem.fromUri(STREAM_URL)
        player.setMediaItem(mediaItem)
        player.prepare()

        // Запускаем обновление информации о треке
        startTrackUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                player.play()
                isPlaying = true
                updateNotification()
                Log.d("RRP", "Воспроизведение запущено")
            }
            ACTION_PAUSE -> {
                player.pause()
                isPlaying = false
                updateNotification()
                Log.d("RRP", "Воспроизведение приостановлено")
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RRP Music",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getNotification(): Notification {
        val playIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY }
        val pauseIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PAUSE }

        val pendingPlay = PendingIntent.getService(
            this, 0, playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pendingPause = PendingIntent.getService(
            this, 1, pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RRP Radio Player")
            .setContentText(currentTrackName)
            .setSmallIcon(android.R.drawable.ic_media_play) // Временная иконка
            .setOngoing(true)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) pendingPause else pendingPlay
            )
            .build()
    }

    private fun updateNotification() {
        startForeground(NOTIFICATION_ID, getNotification())
    }

    private fun startTrackUpdates() {
        isUpdating = true
        trackUpdateJob = Thread {
            while (isUpdating) {
                try {
                    // Получаем информацию о текущем треке
                    val url = URL(TRACK_INFO_URL)
                    val jsonText = url.readText()
                    val json = JSONObject(jsonText)
                    val song = json.optString("song", "Постпанк в эфире...")

                    // Обновляем название трека
                    currentTrackName = song

                    // Обновляем уведомление с новым названием
                    runOnUiThread {
                        if (isPlaying) {
                            updateNotification()
                        }
                        // Здесь можно добавить обновление UI в MainActivity
                        updateMainActivity(song)
                    }

                    Log.d("RRP", "Трек обновлён: $song")

                    // Ожидаем 10 секунд до следующего обновления
                    Thread.sleep(10_000)
                } catch (e: Exception) {
                    Log.e("RRP", "Ошибка загрузки трека", e)
                    Thread.sleep(5_000) // При ошибке ждём меньше
                }
            }
        }
        trackUpdateJob?.start()
    }

    private fun updateMainActivity(trackName: String) {
        // Отправляем explicit broadcast с названием трека в MainActivity
        val intent = Intent(ACTION_TRACK_UPDATE).apply {
            putExtra(EXTRA_TRACK_NAME, trackName)
            // Explicit broadcast - указываем конкретный пакет
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d("RRP", "Отправляем explicit broadcast в MainActivity: $trackName")
    }

    private fun runOnUiThread(block: () -> Unit) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post(block)
    }

    override fun onDestroy() {
        super.onDestroy()
        isUpdating = false
        trackUpdateJob?.interrupt()
        player.release()
        stopForeground(true)
        Log.d("RRP", "Сервис остановлен")
    }
}