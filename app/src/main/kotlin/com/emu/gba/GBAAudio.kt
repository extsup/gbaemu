package com.emu.gba

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

class GBAAudio {

    private val sampleRate = 44100
    private val bufferSize = maxOf(
        AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2, 4096
    )

    private var audioTrack: AudioTrack? = null
    private val buf = ShortArray(bufferSize / 2)
    private var thread: Thread? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        try {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            audioTrack?.play()
            running = true
            thread = Thread {
                try {
                    while (running) {
                        val read = GBAEngine.nativeReadAudio(buf, buf.size)
                        if (read > 0) {
                            audioTrack?.write(buf, 0, read)
                        } else {
                            Thread.sleep(1)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GBAAudio", "Audio thread error: ${e.message}")
                }
            }.also { it.start() }
        } catch (e: Exception) {
            Log.e("GBAAudio", "Failed to start audio: ${e.message}")
        }
    }

    fun stop() {
        running = false
        thread?.join()
        thread = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {}
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }
}
