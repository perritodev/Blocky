package com.omargarcia.blocky

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.omargarcia.blocky.data.SettingsManager
import kotlinx.coroutines.*

class SoundManager(private val context: Context) {
    private val settingsManager = SettingsManager(context)
    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = 0
    private var hitSoundId: Int = 0
    private var alienMediaPlayer: MediaPlayer? = null
    private var alienFadeJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var fadeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        try {
            clickSoundId = soundPool?.load(context, R.raw.sfx_button_click, 1) ?: 0
            hitSoundId = soundPool?.load(context, R.raw.sfx_hit, 1) ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playClick() {
        if (!settingsManager.isSoundEnabled) return
        try {
            if (clickSoundId != 0) {
                soundPool?.play(clickSoundId, 0.20f, 0.20f, 1, 0, 1.0f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playHit() {
        if (!settingsManager.isSoundEnabled) return
        try {
            if (hitSoundId != 0) {
                soundPool?.play(hitSoundId, 0.40f, 0.40f, 1, 0, 1.0f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var alertMediaPlayer: MediaPlayer? = null

    fun updateAlertPreviewVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        val gain = if (clamped <= 0.02f) 0.0f else (clamped * clamped)

        try {
            if (alertMediaPlayer == null) {
                if (gain > 0f) {
                    alertMediaPlayer = MediaPlayer.create(context, R.raw.sfx_hit)?.apply {
                        isLooping = true
                        setVolume(gain, gain)
                        start()
                    }
                }
            } else {
                alertMediaPlayer?.let { player ->
                    player.setVolume(gain, gain)
                    if (gain <= 0f) {
                        if (player.isPlaying) {
                            player.pause()
                        }
                    } else {
                        if (!player.isPlaying) {
                            player.start()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAlertPreview() {
        try {
            alertMediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            alertMediaPlayer = null
        }
    }

    fun playAlienSound() {
        if (!settingsManager.isSoundEnabled) return
        stopAlienSound()

        try {
            alienMediaPlayer = MediaPlayer.create(context, R.raw.alien_sound)?.apply {
                isLooping = false
                setVolume(0.10f, 0.10f)
                start()
            }

            // Start fade out at second 3
            alienFadeJob = scope.launch {
                delay(3000L) // Wait 3 seconds
                val fadeDurationMs = 1500L // 1.5-second smooth fade out
                val steps = 20
                val stepDelay = fadeDurationMs / steps
                val initialVol = 0.10f

                for (i in 1..steps) {
                    delay(stepDelay)
                    val factor = (steps - i).toFloat() / steps.toFloat()
                    val currentVol = initialVol * factor
                    try {
                        alienMediaPlayer?.setVolume(currentVol, currentVol)
                    } catch (_: Exception) {
                        break
                    }
                }
                stopAlienSound()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAlienSound() {
        alienFadeJob?.cancel()
        alienFadeJob = null
        try {
            if (alienMediaPlayer?.isPlaying == true) {
                alienMediaPlayer?.stop()
            }
            alienMediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            alienMediaPlayer = null
        }
    }

    fun playOnboardingTheme() {
        if (!settingsManager.isSoundEnabled) return
        stopMusic()

        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.music_welcome)?.apply {
                isLooping = false
                setVolume(0.40f, 0.40f)
                start()
            }

            // Gradually fade out starting at second 15
            fadeJob = scope.launch {
                delay(15_000L) // Wait 15 seconds
                val fadeDurationMs = 5000L // 5-second fade down to 0
                val steps = 25
                val stepDelay = fadeDurationMs / steps
                val initialVol = 0.40f

                for (i in 1..steps) {
                    delay(stepDelay)
                    val factor = (steps - i).toFloat() / steps.toFloat()
                    val currentVol = initialVol * factor
                    try {
                        mediaPlayer?.setVolume(currentVol, currentVol)
                    } catch (_: Exception) {
                        break
                    }
                }
                stopMusic()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopMusic() {
        fadeJob?.cancel()
        fadeJob = null
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }
    }

    fun release() {
        stopMusic()
        stopAlienSound()
        stopAlertPreview()
        scope.cancel()
        soundPool?.release()
        soundPool = null
    }
}
