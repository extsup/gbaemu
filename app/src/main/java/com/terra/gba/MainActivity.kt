package com.terra.gba

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.terra.gba.core.GpspCore
import com.terra.gba.ui.EmulatorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { EmulatorScreen() } }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val button = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> GpspCore.A; KeyEvent.KEYCODE_BUTTON_B -> GpspCore.B
            KeyEvent.KEYCODE_BUTTON_L1 -> GpspCore.L; KeyEvent.KEYCODE_BUTTON_R1 -> GpspCore.R
            KeyEvent.KEYCODE_BUTTON_START -> GpspCore.START; KeyEvent.KEYCODE_BUTTON_SELECT -> GpspCore.SELECT
            KeyEvent.KEYCODE_DPAD_UP -> GpspCore.UP; KeyEvent.KEYCODE_DPAD_DOWN -> GpspCore.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> GpspCore.LEFT; KeyEvent.KEYCODE_DPAD_RIGHT -> GpspCore.RIGHT
            else -> return super.dispatchKeyEvent(event)
        }
        if (GpspCore.available) GpspCore.setButton(button, event.action == KeyEvent.ACTION_DOWN)
        return true
    }
}
