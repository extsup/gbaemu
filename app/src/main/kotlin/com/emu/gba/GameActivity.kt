package com.emu.gba

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast

class GameActivity : Activity() {

    private lateinit var gbaView: GBAView
    private lateinit var controller: VirtualController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val romPath = intent.getStringExtra("rom_path") ?: run {
    // --- PATCH: Salin ROM ke cache internal ---
    val romFile = File(romPath)
    if (!romFile.exists() || !romFile.canRead()) {
        Toast.makeText(this, "ROM tidak dapat diakses!", Toast.LENGTH_SHORT).show()
        finish()
        return
    }
    val cacheRom = File(cacheDir, "temp_rom.gba")
    try {
        romFile.inputStream().use { input ->
            cacheRom.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } catch (e: Exception) {
        Toast.makeText(this, "Gagal menyalin ROM: ${e.message}", Toast.LENGTH_SHORT).show()
        finish()
        return
    }
    val pathToLoad = cacheRom.absolutePath
    // --- AKHIR PATCH ---
            Toast.makeText(this, "ROM tidak ditemukan!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!GBAEngine.nativeInit(GBAEngine.CORE_PATH)) {
            Toast.makeText(this, "Gagal load core!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!GBAEngine.nativeLoadRom(romPath)) {
            Toast.makeText(this, "Gagal load ROM!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        gbaView = GBAView(this)
        controller = VirtualController(this)

        val frame = FrameLayout(this)
        frame.addView(gbaView)
        frame.addView(controller)
        setContentView(frame)
    }

    override fun onPause() {
        super.onPause()
        if (::gbaView.isInitialized) gbaView.pause()
    }

    override fun onResume() {
        super.onResume()
        if (::gbaView.isInitialized) gbaView.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::gbaView.isInitialized) gbaView.pause()
        GBAEngine.nativeCleanup()
    }
}
