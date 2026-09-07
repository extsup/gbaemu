package com.emu.gba

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.SurfaceHolder
import android.view.SurfaceView

class GBAView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val frameBitmap = Bitmap.createBitmap(GBA_W, GBA_H, Bitmap.Config.ARGB_8888)
    private val frameBuffer = IntArray(GBA_W * GBA_H)
    private var renderThread: RenderThread? = null

    companion object {
        const val GBA_W = 240
        const val GBA_H = 160
    }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startRender(holder)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopRender()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}

    fun pause() {
        stopRender()
    }

    fun resume() {
        if (holder.surface.isValid) {
            startRender(holder)
        }
    }

    private fun startRender(holder: SurfaceHolder) {
        stopRender()
        renderThread = RenderThread(holder).also {
            it.running = true
            it.start()
        }
    }

    private fun stopRender() {
        renderThread?.let {
            it.running = false
            try { it.join(500) } catch (e: InterruptedException) {}
        }
        renderThread = null
    }

    inner class RenderThread(private val holder: SurfaceHolder) : Thread() {
        @Volatile var running = false
        private val FRAME_TIME = 1000L / 60

        override fun run() {
            while (running) {
                val start = System.currentTimeMillis()

                GBAEngine.nativeRunFrame()

                if (GBAEngine.nativeGetFramebuffer(frameBuffer)) {
                    frameBitmap.setPixels(frameBuffer, 0, GBA_W, 0, 0, GBA_W, GBA_H)
                }

                val canvas: Canvas? = holder.lockCanvas()
                canvas?.let {
                    val sw = it.width
                    val sh = it.height
                    val gameRatio = GBA_W.toFloat() / GBA_H.toFloat()
                    val screenRatio = sw.toFloat() / sh.toFloat()
                    val dstW: Int
                    val dstH: Int
                    if (screenRatio > gameRatio) {
                        dstH = sh
                        dstW = (sh * gameRatio).toInt()
                    } else {
                        dstW = sw
                        dstH = (sw / gameRatio).toInt()
                    }
                    val left = (sw - dstW) / 2
                    val top = (sh - dstH) / 2
                    it.drawColor(android.graphics.Color.BLACK)
                    val dst = Rect(left, top, left + dstW, top + dstH)
                    val paint = Paint().apply { isFilterBitmap = true }
                    it.drawBitmap(frameBitmap, null, dst, paint)
                    holder.unlockCanvasAndPost(it)
                }

                val sleep = FRAME_TIME - (System.currentTimeMillis() - start)
                if (sleep > 0) try { Thread.sleep(sleep) } catch (e: InterruptedException) {}
            }
        }
    }
}
