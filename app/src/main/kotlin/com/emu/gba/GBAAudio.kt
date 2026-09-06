package com.emu.gba

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class GBAAudio {

    private val sampleRate = 32768
    private val channelCount = 2
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 2

    private val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
        )
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private val buf = ShortArray(bufferSize / 2)
    private var thread: Thread? = null
    @Volatile private var running = false

    fun start() {
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
        audioTrack.pause()
        audioTrack.flush()
    }

    fun release() {
        stop()
        audioTrack.release()
    }
}
