package com.salat.gmediahud.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.salat.gmediahud.R

class NotificationSoundUtil(context: Context) {
    private var soundPool: SoundPool
    private var soundId: Int = 0
    private var soundLoaded = false
    private var volume = 0f

    init {
        // Create AudioAttributes for notifications to ensure sound is handled correctly
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Initialize SoundPool with a maximum of one audio stream
        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load a custom audio file from resources (notification1.mp3 in res/raw folder)
        soundId = soundPool.load(context, R.raw.notification1, 1)

        // Track when the sound is fully loaded to play it without delays
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == soundId) {
                soundLoaded = true
            }
        }
    }

    fun setVolume(volume: Float) {
        this.volume = volume
    }

    // Method to play the notification sound
    fun playSound() {
        if (soundLoaded) {
            // Parameters: soundId, left/right channel (1.0f for max volume), priority,
            // number of repeats (0 for no repeat), and playback speed (1.0f for normal)
            soundPool.play(soundId, volume, volume, 1, 0, 1.0f)
        }
    }

    // Release resources when done
    fun release() {
        soundPool.release()
    }
}
