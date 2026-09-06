package com.emu.gba

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class GBAAudio {

    private val sampleRate = 32768
    private val bufferSize = maxOf(
        AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2, 4096
    )

    private val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize,
        AudioTrack.MODE_STREAM
    )

    private val buf = ShortArray(bufferSize / 2)
    private var thread: Thread? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        audioTrack.play()
        running = true
        thread = Thread {
            while (running) {
                val read = GBAEngine.nativeReadAudio(buf, buf.size)
                if (read > 0) {
                    audioTrack.write(buf, 0, read)
                } else {
                    Thread.sleep(1)
                }
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join()
        thread = null
        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause()
            audioTrack.flush()
        }
    }

    fun release() {
        stop()
        audioTrack.release()
    }
}
