package com.emu.gba

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

class GameActivity : Activity() {

    private lateinit var gbaView: GBAView
    private lateinit var controller: VirtualController
    private lateinit var audio: GBAAudio

    // ROM cache sementara (hanya ada kalau ROM diakses via SAF content://)
    private var tempRomFile: File? = null

    // Save sync state – diisi di onCreate, dipakai di onPause & onDestroy
    private var internalSaveDir: File? = null
    private var folderUriStr: String? = null   // SAF tree URI (kalau ROM dari SAF)
    private var romDir: File? = null
    private var romName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gbaPrefs = getSharedPreferences("GBAemuPrefs", MODE_PRIVATE)
        applyOrientation(gbaPrefs.getBoolean("landscape", false))

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        applyImmersive()

        val romInput = intent.getStringExtra("rom_path") ?: run {
            Toast.makeText(this, "ROM tidak ditemukan!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // folder_uri hanya ada kalau ROM dipilih lewat SAF folder picker
        folderUriStr = intent.getStringExtra("folder_uri")

        // ── 1. Resolve ROM ke path nyata di filesystem ────────────────────────
        //    Harus dilakukan duluan supaya kita tahu romName sebelum set save dir.
        val romPath = resolveRomPath(romInput) ?: run {
            Toast.makeText(this, "Gagal mengakses ROM", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        romName       = romPath.substringAfterLast("/").substringBeforeLast(".")
        val saveDir       = File(filesDir, "saves/$romName").also { it.mkdirs() }
        internalSaveDir   = saveDir

        // ── 2. Pull save yang sudah ada dari lokasi asal ke internal dir ──────
        //    Dilakukan SEBELUM core load ROM, supaya file .srm sudah tersedia
        //    di internalDir saat core mencarinya.
        val fUriStr = folderUriStr
        if (fUriStr != null) {
            // ROM dari SAF – pull dari root SAF tree
            SaveSyncer.pullFromSaf(this, Uri.parse(fUriStr), romName, saveDir)
        } else {
            // ROM dari path biasa – pull dari folder yang sama
            val rDir = File(romPath).parentFile
            romDir = rDir
            if (rDir != null) SaveSyncer.pullFromDir(rDir, romName, saveDir)
        }

        // ── 3. Init core & set save dir ───────────────────────────────────────
        if (!GBAEngine.initCore(this)) {
            Toast.makeText(this, "Gagal load core!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Native save dir = internalDir (pasti writable, tidak perlu SAF API)
        GBAEngine.nativeSetSaveDir(saveDir.absolutePath)

        // ── 4. Load ROM ───────────────────────────────────────────────────────
        if (!GBAEngine.nativeLoadRom(romPath)) {
            Toast.makeText(this, "Gagal load ROM!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        GBANotification.show(this, romName)
        loadSram()
        getSharedPreferences("GBAemuPrefs", MODE_PRIVATE).edit().putBoolean("game_running", true).apply()

        // ── 5. Setup views ────────────────────────────────────────────────────
        gbaView    = GBAView(this)
        controller = VirtualController(this)
        audio      = GBAAudio()
        audio.start()

        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        // In portrait the game belongs at the top.  The lower part of the
        // screen is deliberately left for controls instead of centering a
        // landscape-sized surface in the middle of the display.
        val display = resources.displayMetrics
        val gameHeight = if (display.heightPixels > display.widthPixels) {
            (display.widthPixels * 160f / 240f).toInt()
        } else {
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        }
        val gbaParams = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            gameHeight,
            android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
        )
        root.addView(gbaView, gbaParams)
        root.addView(controller, android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
        controller.bringToFront()
    }

    // ── Save sync ─────────────────────────────────────────────────────────────

    /**
     * Push save dari internal dir ke lokasi asal ROM.
     * Dipanggil setelah emulator berhenti (onPause / setelah nativeCleanup).
     */
    private fun loadSram() {
        val size = GBAEngine.nativeGetSramSize()
        if (size <= 0) return
        val srmFile = File(internalSaveDir, "$romName.srm")
        if (!srmFile.exists()) return
        val bytes = srmFile.readBytes()
        GBAEngine.nativeSetSram(bytes)
    }

    private fun saveSram() {
        val size = GBAEngine.nativeGetSramSize()
        if (size <= 0) return
        val buf = ByteArray(size)
        if (!GBAEngine.nativeGetSram(buf)) return
        File(internalSaveDir, "$romName.srm").writeBytes(buf)
    }

    private fun startEditLayout() {
        controller.editMode = true
        controller.invalidate()

        // Tampilkan toolbar edit di atas layar
        val overlay = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding(16, 16, 16, 16)
            tag = "edit_overlay"
        }
        val btnDone = android.widget.Button(this).apply {
            text = "✓ Selesai"
            setOnClickListener { stopEditLayout() }
        }
        val btnReset = android.widget.Button(this).apply {
            text = "↺ Reset"
            setOnClickListener {
                controller.resetPositions()
            }
        }
        overlay.addView(btnDone, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        overlay.addView(btnReset, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val root = findViewById<android.widget.FrameLayout>(android.R.id.content)
            .getChildAt(0) as android.widget.FrameLayout
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP
        )
        root.addView(overlay, params)
    }

    private fun stopEditLayout() {
        controller.editMode = false
        controller.invalidate()
        val root = findViewById<android.widget.FrameLayout>(android.R.id.content)
            .getChildAt(0) as android.widget.FrameLayout
        val overlay = root.findViewWithTag<android.view.View>("edit_overlay")
        overlay?.let { root.removeView(it) }
        if (::gbaView.isInitialized) gbaView.resume()
        if (::audio.isInitialized) audio.start()
    }

    private fun showSettingsMenu() {
        startActivityForResult(
            android.content.Intent(this, SettingsActivity::class.java), 1001
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            val prefs = getSharedPreferences("GBAemuPrefs", MODE_PRIVATE)
            applyOrientation(prefs.getBoolean("landscape", false))
            if (::gbaView.isInitialized) gbaView.resume()
            if (::audio.isInitialized) audio.start()
        }
    }

    private fun applyOrientation(landscape: Boolean) {
        requestedOrientation = if (landscape)
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun pushSaves() {
        val saveDir = internalSaveDir ?: return
        val fUriStr = folderUriStr
        if (fUriStr != null) {
            SaveSyncer.pushToSaf(this, Uri.parse(fUriStr), saveDir)
        } else {
            val rDir = romDir
            if (rDir != null) SaveSyncer.pushToDir(rDir, saveDir)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        if (::gbaView.isInitialized) gbaView.pause()
        if (::audio.isInitialized) audio.stop()
        Thread {
            saveSram()
            pushSaves()
        }.start()
    }

    override fun onStop() {
        super.onStop()
        // Jangan reset flag kalau hanya buka SettingsActivity
        if (!isFinishing) return
        getSharedPreferences("GBAemuPrefs", MODE_PRIVATE).edit().putBoolean("game_running", false).apply()
    }

    override fun onResume() {
        super.onResume()
        getSharedPreferences("GBAemuPrefs", MODE_PRIVATE).edit().putBoolean("game_running", true).apply()
        if (::gbaView.isInitialized) gbaView.resume()
        if (::audio.isInitialized) audio.start()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    override fun onBackPressed() {
        if (::gbaView.isInitialized) gbaView.pause()
        if (::audio.isInitialized) audio.stop()

        val menuItems = arrayOf("Pengaturan", "Custom layout tombol", "Exit")
        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_NoActionBar)
            .setTitle("Menu")
            .setItems(menuItems) { _, which ->
                when (which) {
                    0 -> {
                        showSettingsMenu()
                    }
                    1 -> {
                        startEditLayout()
                    }
                    2 -> {
                        finish()
                    }
                }
            }
            .setOnCancelListener {
                if (::gbaView.isInitialized) gbaView.resume()
                if (::audio.isInitialized) audio.start()
            }
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF15171D.toInt()))
        dialog.listView?.apply {
            divider = android.graphics.drawable.ColorDrawable(0xFF30333A.toInt())
            dividerHeight = 1
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::gbaView.isInitialized) gbaView.pause()
        if (::audio.isInitialized) audio.release()
        GBANotification.hide(this)

        // nativeCleanup → retro_unload_game → core flush save ke internalDir
        saveSram()
        GBAEngine.nativeCleanup()

        // Push final SETELAH cleanup, supaya dapat save yang paling lengkap
        pushSaves()

        // Hapus ROM temp dari cache (hanya ada kalau ROM dari SAF)
        tempRomFile?.delete()
        tempRomFile = null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Mengubah romInput (bisa content:// URI atau path biasa) jadi path
     * filesystem yang bisa dibaca oleh native code.
     *
     * Kalau content:// → copy ke cacheDir dulu (native JNI tidak bisa baca URI).
     * Kalau path biasa → langsung return kalau file ada & bisa dibaca.
     */
    private fun resolveRomPath(input: String): String? {
        if (!input.startsWith("content://")) {
            val file = File(input)
            return if (file.exists() && file.canRead()) file.absolutePath else null
        }
        return try {
            val uri      = Uri.parse(input)
            val fileName = DocumentFile.fromSingleUri(this, uri)?.name ?: "rom.gba"
            val temp     = File(cacheDir, fileName)
            contentResolver.openInputStream(uri)?.use { stream ->
                FileOutputStream(temp).use { out -> stream.copyTo(out) }
            }
            if (temp.exists()) {
                tempRomFile = temp
                temp.absolutePath
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun applyImmersive() {
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }
}
