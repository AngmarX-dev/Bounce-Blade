package com.angmarx.bounceblade.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Add sound asset loading and sound-id registration when audio files are ready. */
class SoundManager {
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    fun loadResource(context: Context, resourceId: Int): Int =
        soundPool.load(context.applicationContext, resourceId, 1)

    fun play(soundId: Int, volume: Float = 1f) {
        val safeVolume = volume.coerceIn(0f, 1f)
        soundPool.play(soundId, safeVolume, safeVolume, 1, 0, 1f)
    }

    fun release() = soundPool.release()
}
