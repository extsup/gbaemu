package com.emu.gba

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
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
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        val romInput = intent.getStringExtra("rom_path") ?: run {
            Toast.makeText(this, "ROM tidak ditemukan!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val saveDir = File(Environment.getExternalStorageDirectory(), "GBAemu/saves")
        saveDir.mkdirs()
        GBAEngine.nativeSetSaveDir(saveDir.absolutePath)

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

        val romName = romPath.substringAfterLast("/").substringBeforeLast(".")
        GBANotification.show(this, romName)

        gbaView = GBAView(this)
        controller = VirtualController(this)
        audio = GBAAudio()
        audio.start()

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val gameParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f
        )
        val ctrlParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f
        )

        root.addView(gbaView, gameParams)
        root.addView(controller, ctrlParams)
        setContentView(root)
    }

    private fun resolveRomPath(input: String): String? {
        val romsDir = File(Environment.getExternalStorageDirectory(), "GBAemu/roms")
        romsDir.mkdirs()

        if (!input.startsWith("content://")) {
            val file = File(input)
            if (file.exists() && file.canRead()) return file.absolutePath
            return null
        }

        return try {
            val uri = Uri.parse(input)
            val fileName = DocumentFile.fromSingleUri(this, uri)?.name ?: "rom.gba"
            val destFile = File(romsDir, fileName)
            if (!destFile.exists()) {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (destFile.exists()) destFile.absolutePath else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
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

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::gbaView.isInitialized) gbaView.pause()
        if (::audio.isInitialized) audio.release()
        GBANotification.hide(this)
        GBAEngine.nativeCleanup()
    }
}
