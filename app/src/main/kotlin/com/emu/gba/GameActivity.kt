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

        // ── 5. Setup views ────────────────────────────────────────────────────
        gbaView    = GBAView(this)
        controller = VirtualController(this)
        audio      = GBAAudio()
        audio.start()

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        root.addView(gbaView, android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f))
        root.addView(controller, android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f))
        setContentView(root)
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
        // Push save saat app di-minimize – core gpsp sudah flush .srm ke internalDir
        saveSram()
        pushSaves()
    }

    override fun onResume() {
        super.onResume()
        if (::gbaView.isInitialized) gbaView.resume()
        if (::audio.isInitialized) audio.start()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
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
