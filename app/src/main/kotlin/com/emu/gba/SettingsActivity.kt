package com.emu.gba

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("GBAemuPrefs", MODE_PRIVATE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(android.graphics.Color.parseColor("#1a1a2e"))
        }

        // Judul
        val title = TextView(this).apply {
            text = "Pengaturan"
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 0, 48)
        }
        layout.addView(title)

        // Toggle Orientasi
        val orientSwitch = Switch(this).apply {
            text = "Landscape"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            isChecked = prefs.getBoolean("landscape", false)
        }
        orientSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("landscape", isChecked).apply()
        }
        layout.addView(orientSwitch)

        // Divider
        layout.addView(View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#333355"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 32, 0, 32) }
        })

        // Toggle Filter Bilinear
        val filterSwitch = Switch(this).apply {
            text = "Filter Bilinear"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            isChecked = prefs.getBoolean("bilinear", true)
        }
        filterSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("bilinear", isChecked).apply()
        }
        layout.addView(filterSwitch)

        setContentView(layout)
    }

    override fun onBackPressed() {
        finish()
    }
}
