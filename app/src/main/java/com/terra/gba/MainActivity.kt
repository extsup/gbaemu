package com.terra.gba

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.terra.gba.core.GpspCore
import com.terra.gba.ui.EmulatorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent {
            EmulatorScreen(
                setLandscape = { landscape ->
                    requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                },
                exitGame = {
                    if (GpspCore.available) GpspCore.stop()
                    finishAndRemoveTask()
                },
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemBars() }

    @Suppress("DEPRECATION") private fun hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

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
