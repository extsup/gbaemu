package com.emu.gba

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class VirtualController(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawables = mutableMapOf<String, Drawable?>()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("GBAemuPrefs", Context.MODE_PRIVATE)

    var editMode = false

    companion object {
        private const val COLOR_DPAD    = 0x99444444.toInt()
        private const val COLOR_A       = 0x99CC0000.toInt()
        private const val COLOR_B       = 0x990044AA.toInt()
        private const val COLOR_LR      = 0x99226622.toInt()
        private const val COLOR_SS      = 0x99666666.toInt()
        private const val COLOR_PRESSED = 0xFFFFFFAA.toInt()
        private const val COLOR_EDIT    = 0xAAFFAA00.toInt()

        val BUTTON_NAMES = listOf("UP","DOWN","LEFT","RIGHT","A","B","L","R","SELECT","START")
    }

    // posisi center tiap tombol (relative 0.0-1.0)
    private val btnCX = mutableMapOf<String, Float>()
    private val btnCY = mutableMapOf<String, Float>()
    private val btnW  = mutableMapOf<String, Float>()
    private val btnH  = mutableMapOf<String, Float>()
    private val rects = mutableMapOf<String, RectF>()

    private val pressedButtons = mutableSetOf<String>()
    private val pointerMap     = mutableMapOf<Int, String>()

    // drag state
    private var dragPtr: Int = -1
    private var dragBtn: String? = null
    private var dragOffX = 0f
    private var dragOffY = 0f

    init {
        drawables["UP"]     = ContextCompat.getDrawable(context, R.drawable.ic_dpad_up)
        drawables["DOWN"]   = ContextCompat.getDrawable(context, R.drawable.ic_dpad_down)
        drawables["LEFT"]   = ContextCompat.getDrawable(context, R.drawable.ic_dpad_left)
        drawables["RIGHT"]  = ContextCompat.getDrawable(context, R.drawable.ic_dpad_right)
        drawables["A"]      = ContextCompat.getDrawable(context, R.drawable.ic_btn_a)
        drawables["B"]      = ContextCompat.getDrawable(context, R.drawable.ic_btn_b)
        drawables["L"]      = ContextCompat.getDrawable(context, R.drawable.ic_btn_l)
        drawables["R"]      = ContextCompat.getDrawable(context, R.drawable.ic_btn_r)
        drawables["SELECT"] = ContextCompat.getDrawable(context, R.drawable.ic_btn_select)
        drawables["START"]  = ContextCompat.getDrawable(context, R.drawable.ic_btn_start)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initDefaultSizes(w, h)
        loadPositions(w, h)
        buildRects(w, h)
    }

    private fun initDefaultSizes(w: Int, h: Int) {
        val bw = w * 0.13f
        val bh = h * 0.20f
        val lbw = w * 0.20f
        val lbh = h * 0.12f
        val sbw = w * 0.14f
        val sbh = h * 0.10f

        for (name in BUTTON_NAMES) {
            if (btnW[name] == null) {
                btnW[name] = when (name) {
                    "L","R"          -> lbw
                    "SELECT","START" -> sbw
                    else             -> bw
                }
                btnH[name] = when (name) {
                    "L","R"          -> lbh
                    "SELECT","START" -> sbh
                    else             -> bh
                }
            }
        }
    }

    private fun defaultCX(name: String, w: Int, h: Int): Float {
        val bw = w * 0.13f
        val pad = w * 0.03f
        return when (name) {
            "LEFT"   -> pad + bw * 0.5f
            "RIGHT"  -> pad + bw * 2.5f
            "UP","DOWN" -> pad + bw * 1.5f
            "A"      -> w - pad - bw * 0.5f
            "B"      -> w - pad - bw * 1.5f
            "L"      -> pad + w * 0.10f
            "R"      -> w - pad - w * 0.10f
            "SELECT" -> w * 0.42f
            "START"  -> w * 0.58f
            else     -> w / 2f
        }
    }

    private fun defaultCY(name: String, w: Int, h: Int): Float {
        val bh = h * 0.20f
        val pad = h * 0.03f
        return when (name) {
            "UP"     -> h - bh * 2.5f - pad
            "DOWN"   -> h - bh * 0.5f - pad
            "LEFT","RIGHT" -> h - bh * 1.5f - pad
            "A"      -> h - bh * 1.5f - pad
            "B"      -> h - bh * 0.5f - pad
            "L","R"  -> pad + h * 0.06f
            "SELECT","START" -> h - h * 0.08f
            else     -> h / 2f
        }
    }

    private fun loadPositions(w: Int, h: Int) {
        for (name in BUTTON_NAMES) {
            btnCX[name] = prefs.getFloat("btn_cx_$name", defaultCX(name, w, h))
            btnCY[name] = prefs.getFloat("btn_cy_$name", defaultCY(name, w, h))
        }
    }

    private fun savePosition(name: String) {
        prefs.edit()
            .putFloat("btn_cx_$name", btnCX[name] ?: 0f)
            .putFloat("btn_cy_$name", btnCY[name] ?: 0f)
            .apply()
    }

    private fun buildRects(w: Int, h: Int) {
        for (name in BUTTON_NAMES) {
            val cx = btnCX[name] ?: defaultCX(name, w, h)
            val cy = btnCY[name] ?: defaultCY(name, w, h)
            val hw = (btnW[name] ?: w * 0.13f) / 2f
            val hh = (btnH[name] ?: h * 0.20f) / 2f
            rects[name] = RectF(cx - hw, cy - hh, cx + hw, cy + hh)
        }
    }

    override fun onDraw(canvas: Canvas) {
        for (name in BUTTON_NAMES) {
            val rect = rects[name] ?: continue
            val pressed = pressedButtons.contains(name)

            if (editMode) {
                paint.color = COLOR_EDIT
                canvas.drawRoundRect(rect, 16f, 16f, paint)
                paint.color = 0xAAFFFFFF.toInt()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawRoundRect(rect, 16f, 16f, paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.BLACK
                paint.textSize = rect.height() * 0.35f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(name, rect.centerX(), rect.centerY() + paint.textSize / 3f, paint)
            } else {
                val d = drawables[name]
                if (d != null) {
                    val alpha = if (pressed) 255 else 180
                    d.alpha = alpha
                    d.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
                    d.draw(canvas)
                } else {
                    paint.color = if (pressed) COLOR_PRESSED else COLOR_DPAD
                    canvas.drawRoundRect(rect, 16f, 16f, paint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (editMode) return handleEditTouch(event)
        return handleGameTouch(event)
    }

    private fun handleGameTouch(event: MotionEvent): Boolean {
        val idx = event.actionIndex
        val pid = event.getPointerId(idx)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                getButtonAt(event.getX(idx), event.getY(idx))?.let {
                    pointerMap[pid] = it
                    pressedButtons.add(it)
                    GBAEngine.pressKey(keyCode(it))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                pointerMap.remove(pid)?.let {
                    pressedButtons.remove(it)
                    GBAEngine.releaseKey(keyCode(it))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val p = event.getPointerId(i)
                    val newBtn = getButtonAt(event.getX(i), event.getY(i))
                    val oldBtn = pointerMap[p]
                    if (newBtn != oldBtn) {
                        oldBtn?.let {
                            pressedButtons.remove(it)
                            GBAEngine.releaseKey(keyCode(it))
                        }
                        if (newBtn != null) {
                            pointerMap[p] = newBtn
                            pressedButtons.add(newBtn)
                            GBAEngine.pressKey(keyCode(newBtn))
                        } else {
                            pointerMap.remove(p)
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedButtons.forEach { GBAEngine.releaseKey(keyCode(it)) }
                pressedButtons.clear()
                pointerMap.clear()
            }
        }
        invalidate()
        return true
    }

    private fun handleEditTouch(event: MotionEvent): Boolean {
        val idx = event.actionIndex
        val pid = event.getPointerId(idx)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (dragBtn == null) {
                    val x = event.getX(idx)
                    val y = event.getY(idx)
                    val btn = getButtonAt(x, y)
                    if (btn != null) {
                        dragBtn = btn
                        dragPtr = pid
                        dragOffX = x - (btnCX[btn] ?: x)
                        dragOffY = y - (btnCY[btn] ?: y)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val db = dragBtn ?: return true
                for (i in 0 until event.pointerCount) {
                    if (event.getPointerId(i) == dragPtr) {
                        val nx = (event.getX(i) - dragOffX).coerceIn(
                            (btnW[db] ?: 0f) / 2f, width - (btnW[db] ?: 0f) / 2f)
                        val ny = (event.getY(i) - dragOffY).coerceIn(
                            (btnH[db] ?: 0f) / 2f, height - (btnH[db] ?: 0f) / 2f)
                        btnCX[db] = nx
                        btnCY[db] = ny
                        buildRects(width, height)
                        invalidate()
                        break
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (pid == dragPtr) {
                    dragBtn?.let { savePosition(it) }
                    dragBtn = null
                    dragPtr = -1
                }
            }
        }
        return true
    }

    fun resetPositions() {
        val editor = prefs.edit()
        for (name in BUTTON_NAMES) {
            editor.remove("btn_cx_$name").remove("btn_cy_$name")
        }
        editor.apply()
        loadPositions(width, height)
        buildRects(width, height)
        invalidate()
    }

    private fun getButtonAt(x: Float, y: Float) =
        BUTTON_NAMES.firstOrNull { rects[it]?.contains(x, y) == true }

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
