package com.emu.gba

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.SurfaceHolder
import android.view.SurfaceView

class GBAView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val frameBitmap = Bitmap.createBitmap(GBA_W, GBA_H, Bitmap.Config.ARGB_8888)
    private var renderThread: RenderThread? = null

    companion object {
        const val GBA_W = 240
        const val GBA_H = 160
    }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        renderThread = RenderThread(holder).also {
            it.running = true
            it.start()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = pause()
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}

    fun pause() {
        renderThread?.let {
            it.running = false
            try { it.join() } catch (e: InterruptedException) {}
        }
        renderThread = null
    }

    fun resume() {
        if (renderThread == null) {
            renderThread = RenderThread(holder).also {
                it.running = true
                it.start()
            }
        }
    }

    inner class RenderThread(private val holder: SurfaceHolder) : Thread() {
        @Volatile var running = false
        private val FRAME_TIME = 1000L / 60

        override fun run() {
            while (running) {
                val start = System.currentTimeMillis()

                GBAEngine.nativeRunFrame()

                GBAEngine.nativeGetFramebuffer()?.let { pixels ->
                    frameBitmap.setPixels(pixels, 0, GBA_W, 0, 0, GBA_W, GBA_H)
                }

                val canvas: Canvas? = holder.lockCanvas()
                canvas?.let {
                    val dst = Rect(0, 0, it.width, it.height)
                    it.drawBitmap(frameBitmap, null, dst, null)
                    holder.unlockCanvasAndPost(it)
                }

                val sleep = FRAME_TIME - (System.currentTimeMillis() - start)
                if (sleep > 0) try { Thread.sleep(sleep) } catch (e: InterruptedException) {}
            }
        }
    }
}
