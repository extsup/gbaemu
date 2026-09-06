package com.emu.gba

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class GameActivity : Activity() {

    private lateinit var gbaView: GBAView
    private lateinit var controller: VirtualController
    private lateinit var audio: GBAAudio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val romInput = intent.getStringExtra("rom_path") ?: run {
            Toast.makeText(this, "ROM tidak ditemukan!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Inisialisasi core terlebih dahulu
        if (!GBAEngine.initCore(this)) {
            Toast.makeText(this, "Gagal load core!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val romPath = resolveRomPath(romInput)
        if (romPath == null) {
            Toast.makeText(this, "Gagal mengakses ROM", Toast.LENGTH_SHORT).show()
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
        audio = GBAAudio()
        audio.start()

        val frame = FrameLayout(this)
        frame.addView(gbaView)
        frame.addView(controller)
        setContentView(frame)
    }

    private fun resolveRomPath(input: String): String? {
        if (!input.startsWith("content://")) {
            val file = File(input)
            if (file.exists() && file.canRead()) {
                return copyToCache(file)
            }
            return null
        }

        try {
            val uri = Uri.parse(input)
            val pfd = contentResolver.openFileDescriptor(uri, "r")
            if (pfd == null) {
                Toast.makeText(this, "Tidak dapat membuka file", Toast.LENGTH_SHORT).show()
                return null
            }
            val cacheFile = File(cacheDir, "temp_rom.gba")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(cacheFile).use { output ->
                    inputStream.copyTo(output)
                }
            }
            if (cacheFile.exists() && cacheFile.canRead()) {
                return cacheFile.absolutePath
            } else {
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun copyToCache(file: File): String? {
        val cacheFile = File(cacheDir, "temp_rom.gba")
        try {
            file.inputStream().use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return cacheFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun onPause() {
        super.onPause()
        if (::gbaView.isInitialized) gbaView.pause()
        if (::audio.isInitialized) audio.stop()
    }

    override fun onResume() {
        super.onResume()
        if (::gbaView.isInitialized) gbaView.resume()
        if (::audio.isInitialized) audio.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::gbaView.isInitialized) gbaView.pause()
        if (::audio.isInitialized) audio.release()
        GBAEngine.nativeCleanup()
    }
}
