package com.rvp.rrp

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var isPlaying = false
    private lateinit var playButton: Button
    private lateinit var logoImage: ImageView
    private lateinit var trackNameView: TextView
    private var rotationAnimator: ValueAnimator? = null
    private var startTime = 0L
    private var isServiceRunning = false

    // BroadcastReceiver для получения обновлений названия трека
    private val trackUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("RRP", "MainActivity получил broadcast: ${intent?.action}")
            if (intent?.action == MusicService.ACTION_TRACK_UPDATE) {
                val trackName = intent.getStringExtra(MusicService.EXTRA_TRACK_NAME) ?: "Загрузка..."
                Log.d("RRP", "MainActivity обновляет название трека: $trackName")
                updateTrackName(trackName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("RRP", "MainActivity: onCreate вызван")
        setContentView(R.layout.activity_main)

        // Инициализация элементов интерфейса
        playButton = findViewById(R.id.playButton)
        logoImage = findViewById(R.id.logoImage)
        trackNameView = findViewById(R.id.trackName)
        Log.d("RRP", "MainActivity: Элементы UI инициализированы")

        // Стиль кнопки
        playButton.setTextColor(Color.WHITE)
        playButton.setTypeface(null, android.graphics.Typeface.BOLD)

        // Настройка ссылок
        setupLinks()

        // Настройка кнопки воспроизведения
        setupPlayButton()
    }

    private fun setupLinks() {
        // Клик по "Расписание программ"
        val linkSchedule = findViewById<TextView>(R.id.linkSchedule)
        linkSchedule.setOnClickListener {
            val url = "https://radiorever.com/schedule"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        // Клик по "Ревер-чарт"
        val linkChart = findViewById<TextView>(R.id.linkChart)
        linkChart.setOnClickListener {
            val url = "https://radiorever.com/chart"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        // Клик по основной ссылке
        val linkMain = findViewById<TextView>(R.id.linkMain)
        linkMain.setOnClickListener {
            val url = "https://radiorever.com"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }

    private fun setupPlayButton() {
        // Переключение воспроизведения через сервис
        val togglePlayback = {
            val intent = Intent(this, MusicService::class.java)
            if (isPlaying) {
                intent.action = MusicService.ACTION_PAUSE
                playButton.text = "Play"
                stopRotation()
            } else {
                intent.action = MusicService.ACTION_PLAY
                playButton.text = "Pause"
                startRotation()
                // Запускаем сервис впервые
                if (!isServiceRunning) {
                    startService(intent)
                    isServiceRunning = true
                }
            }
            startService(intent)
            isPlaying = !isPlaying
        }

        playButton.setOnClickListener { togglePlayback() }
        logoImage.setOnClickListener { togglePlayback() }
    }

    private fun startRotation() {
        stopRotation()
        val period = 60_000 / 16L // Период вращения
        startTime = System.currentTimeMillis()

        rotationAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Long.MAX_VALUE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val elapsed = System.currentTimeMillis() - startTime
                val angle = (elapsed % period) * 360f / period
                logoImage.rotation = angle
            }
            start()
        }
    }

    private fun stopRotation() {
        rotationAnimator?.cancel()
        logoImage.rotation = 0f
    }

    // Метод для обновления названия трека из сервиса
    fun updateTrackName(trackName: String) {
        Log.d("RRP", "updateTrackName вызван с: $trackName")
        runOnUiThread {
            Log.d("RRP", "Обновляем UI: trackNameView.text = $trackName")
            trackNameView.text = trackName
        }
    }

    override fun onResume() {
        super.onResume()
        // Регистрируем BroadcastReceiver для получения обновлений трека
        val filter = IntentFilter(MusicService.ACTION_TRACK_UPDATE)
        Log.d("RRP", "MainActivity: Регистрируем BroadcastReceiver для ${MusicService.ACTION_TRACK_UPDATE}")

        // Для Android 13+ (API 33+) требуется явно указать флаг экспорта
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(trackUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            Log.d("RRP", "MainActivity: BroadcastReceiver зарегистрирован с RECEIVER_NOT_EXPORTED")
        } else {
            registerReceiver(trackUpdateReceiver, filter)
            Log.d("RRP", "MainActivity: BroadcastReceiver зарегистрирован (legacy)")
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("RRP", "MainActivity: onPause вызван")
        // Разрегистрируем BroadcastReceiver
        try {
            unregisterReceiver(trackUpdateReceiver)
            Log.d("RRP", "MainActivity: BroadcastReceiver разрегистрирован")
        } catch (e: IllegalArgumentException) {
            Log.d("RRP", "MainActivity: BroadcastReceiver уже был разрегистрирован")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("RRP", "MainActivity: onDestroy вызван")
        stopRotation()
    }
}