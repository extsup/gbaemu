package com.emu.gba

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class VirtualController(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttons = mutableMapOf<String, RectF>()
    private val pointerMap = mutableMapOf<Int, String>()

    companion object {
        private const val COLOR_DPAD    = 0x99444444.toInt()
        private const val COLOR_A       = 0x99CC0000.toInt()
        private const val COLOR_B       = 0x99004499.toInt()
        private const val COLOR_LR      = 0x99226622.toInt()
        private const val COLOR_SS      = 0x99666666.toInt()
        private const val COLOR_PRESSED = 0xFFFFFFAA.toInt()
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupButtons(w, h)
    }

    private fun setupButtons(w: Int, h: Int) {
        val bw = w * 0.12f
        val bh = h * 0.18f
        val pad = w * 0.02f

        val dx = pad
        val dy = h - (bh * 3) - pad
        buttons["UP"]    = RectF(dx + bw, dy,        dx + bw*2, dy + bh)
        buttons["DOWN"]  = RectF(dx + bw, dy + bh*2, dx + bw*2, dy + bh*3)
        buttons["LEFT"]  = RectF(dx,      dy + bh,   dx + bw,   dy + bh*2)
        buttons["RIGHT"] = RectF(dx + bw*2, dy + bh, dx + bw*3, dy + bh*2)

        val rx = w - (bw * 3) - pad
        val ry = h - (bh * 2) - pad
        buttons["A"] = RectF(rx + bw*2, ry,      rx + bw*3, ry + bh)
        buttons["B"] = RectF(rx + bw,   ry + bh, rx + bw*2, ry + bh*2)

        buttons["L"] = RectF(pad,              pad, pad + bw*1.5f,   pad + bh*0.6f)
        buttons["R"] = RectF(w - pad - bw*1.5f, pad, w - pad,        pad + bh*0.6f)

        val mx = w / 2f
        val my = h - bh*0.8f - pad
        buttons["SELECT"] = RectF(mx - bw*1.2f, my, mx - pad/2,   my + bh*0.6f)
        buttons["START"]  = RectF(mx + pad/2,   my, mx + bw*1.2f, my + bh*0.6f)
    }

    override fun onDraw(canvas: Canvas) {
        buttons.forEach { (name, rect) ->
            val pressed = pointerMap.containsValue(name)
            paint.color = if (pressed) COLOR_PRESSED else when (name) {
                "A"              -> COLOR_A
                "B"              -> COLOR_B
                "L", "R"         -> COLOR_LR
                "SELECT","START" -> COLOR_SS
                else             -> COLOR_DPAD
            }
            canvas.drawRoundRect(rect, 12f, 12f, paint)

            paint.color = Color.WHITE
            paint.textSize = rect.height() * 0.35f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(name, rect.centerX(), rect.centerY() + paint.textSize / 3, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val idx = event.actionIndex
        val pid = event.getPointerId(idx)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                getButtonAt(event.getX(idx), event.getY(idx))?.let {
                    pointerMap[pid] = it
                    GBAEngine.pressKey(keyCode(it))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                pointerMap.remove(pid)?.let { GBAEngine.releaseKey(keyCode(it)) }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val p = event.getPointerId(i)
                    val newBtn = getButtonAt(event.getX(i), event.getY(i))
                    val oldBtn = pointerMap[p]
                    if (newBtn != oldBtn) {
                        oldBtn?.let { GBAEngine.releaseKey(keyCode(it)) }
                        if (newBtn != null) {
                            pointerMap[p] = newBtn
                            GBAEngine.pressKey(keyCode(newBtn))
                        } else {
                            pointerMap.remove(p)
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerMap.values.forEach { GBAEngine.releaseKey(keyCode(it)) }
                pointerMap.clear()
            }
        }
        invalidate()
        return true
    }

    private fun getButtonAt(x: Float, y: Float) =
        buttons.entries.firstOrNull { it.value.contains(x, y) }?.key

    private fun keyCode(btn: String) = when (btn) {
        "A"      -> GBAEngine.KEY_A
        "B"      -> GBAEngine.KEY_B
        "SELECT" -> GBAEngine.KEY_SELECT
        "START"  -> GBAEngine.KEY_START
        "RIGHT"  -> GBAEngine.KEY_RIGHT
        "LEFT"   -> GBAEngine.KEY_LEFT
        "UP"     -> GBAEngine.KEY_UP
        "DOWN"   -> GBAEngine.KEY_DOWN
        "R"      -> GBAEngine.KEY_R
        "L"      -> GBAEngine.KEY_L
        else     -> 0
    }
}
