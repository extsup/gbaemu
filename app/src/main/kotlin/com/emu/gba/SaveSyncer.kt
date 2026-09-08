package com.emu.gba

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Mengelola sinkronisasi file save antara internal save dir (yang dipakai native/JNI)
 * dengan lokasi asal ROM (baik SAF tree maupun folder biasa di filesystem).
 *
 * Flow:
 *   1. pullFrom*()  → sebelum game start, copy save dari luar ke internal
 *   2. pushTo*()    → setelah game pause/destroy, copy save dari internal ke luar
 *
 * Native code hanya baca/tulis ke internalDir. Semua sync ke/dari lokasi
 * asal ROM ditangani di sini, di sisi Kotlin.
 */
object SaveSyncer {

    private const val TAG = "SaveSyncer"

    // Ekstensi file yang mungkin ditulis gpsp libretro
    private val SAVE_EXTENSIONS = setOf(
        "srm",   // SRAM – paling umum
        "sav",   // alternatif SRAM
        "rtc",   // Real-Time Clock
        "state", "st0", "st1", "st2", "st3", "st4",
        "st5", "st6", "st7", "st8", "st9"
    )

    // ─── SAF ──────────────────────────────────────────────────────────────────

    /**
     * Copy save files yang cocok dengan [romName] dari root SAF tree ke [internalDir].
     * Hanya meng-overwrite jika file di SAF lebih baru.
     */
    fun pullFromSaf(context: Context, treeUri: Uri, romName: String, internalDir: File) {
        internalDir.mkdirs()
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: run {
            Log.w(TAG, "pullFromSaf: tree URI tidak valid")
            return
        }
        root.listFiles().forEach { doc ->
            val name = doc.name ?: return@forEach
            if (!isSaveFile(name, romName)) return@forEach
            val dest = File(internalDir, name)
            if (dest.exists() && doc.lastModified() <= dest.lastModified()) return@forEach
            try {
                context.contentResolver.openInputStream(doc.uri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                }
                Log.d(TAG, "Pull SAF → internal: $name")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal pull $name dari SAF: ${e.message}")
            }
        }
    }

    /**
     * Copy semua file dari [internalDir] ke root SAF tree.
     * File yang sudah ada di SAF akan dihapus dulu lalu dibuat ulang (hindari
     * masalah mode "wt" yang tidak konsisten antar provider).
     */
    fun pushToSaf(context: Context, treeUri: Uri, internalDir: File) {
        if (!internalDir.exists()) return
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: run {
            Log.w(TAG, "pushToSaf: tree URI tidak valid")
            return
        }
        internalDir.listFiles()?.filter { it.isFile }?.forEach { localFile ->
            try {
                // Hapus versi lama di SAF agar tidak ada isu overwrite
                root.findFile(localFile.name)?.delete()
                val docFile = root.createFile("application/octet-stream", localFile.name)
                    ?: return@forEach
                context.contentResolver.openOutputStream(docFile.uri)?.use { out ->
                    localFile.inputStream().use { input -> input.copyTo(out) }
                }
                Log.d(TAG, "Push internal → SAF: ${localFile.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal push ${localFile.name} ke SAF: ${e.message}")
            }
        }
    }

    // ─── Filesystem biasa ─────────────────────────────────────────────────────

    /**
     * Copy save files dari [romDir] (folder yang sama dengan ROM) ke [internalDir].
     */
    fun pullFromDir(romDir: File, romName: String, internalDir: File) {
        internalDir.mkdirs()
        romDir.listFiles()?.forEach { file ->
            if (!isSaveFile(file.name, romName)) return@forEach
            val dest = File(internalDir, file.name)
            if (dest.exists() && file.lastModified() <= dest.lastModified()) return@forEach
            try {
                file.copyTo(dest, overwrite = true)
                Log.d(TAG, "Pull dir → internal: ${file.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal pull ${file.name} dari dir: ${e.message}")
            }
        }
    }

    /**
     * Copy semua file dari [internalDir] ke [romDir].
     */
    fun pushToDir(romDir: File, internalDir: File) {
        if (!internalDir.exists()) return
        internalDir.listFiles()?.filter { it.isFile }?.forEach { localFile ->
            val dest = File(romDir, localFile.name)
            if (dest.exists() && localFile.lastModified() <= dest.lastModified()) return@forEach
            try {
                localFile.copyTo(dest, overwrite = true)
                Log.d(TAG, "Push internal → dir: ${localFile.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal push ${localFile.name} ke dir: ${e.message}")
            }
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private fun isSaveFile(fileName: String, romName: String): Boolean {
        val base = fileName.substringBeforeLast(".")
        val ext  = fileName.substringAfterLast(".").lowercase()
        return base == romName && ext in SAVE_EXTENSIONS
    }
}
