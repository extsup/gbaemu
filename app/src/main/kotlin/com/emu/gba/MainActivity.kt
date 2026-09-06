package com.emu.gba

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMISSION = 1
        private const val REQ_FILE_PICKER = 2
        private const val REQ_MANAGE_STORAGE = 3
        private val ROM_DIR = "${Environment.getExternalStorageDirectory()}/GBAemu/roms/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        File(ROM_DIR).mkdirs()

        // Untuk Android 11+, minta izin MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }.let { startActivityForResult(it, REQ_MANAGE_STORAGE) }
                return
            }
        }

        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                REQ_PERMISSION
            )
        } else {
            showRomPicker()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, results: IntArray
    ) {
        if (requestCode == REQ_PERMISSION &&
            results.isNotEmpty() &&
            results[0] == PackageManager.PERMISSION_GRANTED) {
            showRomPicker()
        } else {
            Toast.makeText(this, "Permission storage ditolak!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_MANAGE_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        showRomPicker()
                    } else {
                        Toast.makeText(this, "Izin manajemen storage diperlukan!", Toast.LENGTH_LONG).show()
                    }
                }
            }
            REQ_FILE_PICKER -> {
                if (resultCode == RESULT_OK && data != null) {
                    val uri = data.data
                    if (uri != null) {
                        val path = getRealPathFromUri(uri)
                        if (path != null) {
                            launchGame(path)
                        } else {
                            Toast.makeText(this, "Gagal mendapatkan path file", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        if (uri.scheme == "content") {
            try {
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    return "/proc/self/fd/" + pfd.fd
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun showRomPicker() {
        val dir = File(ROM_DIR)
        val files = dir.listFiles()?.filter {
            it.isFile && it.name.lowercase().endsWith(".gba")
        } ?: emptyList()

        if (files.isNotEmpty()) {
            val names = files.map { it.name }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Pilih ROM")
                .setItems(names) { _, which ->
                    launchGame(files[which].absolutePath)
                }
                .show()
        } else {
            // Tidak ada ROM, tawarkan pilih manual
            AlertDialog.Builder(this)
                .setTitle("GBAemu")
                .setMessage("Tidak ada ROM ditemukan di folder:\n$ROM_DIR\n\nPilih ROM dari penyimpanan?")
                .setPositiveButton("Pilih ROM") { _, _ ->
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "*/*"
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    startActivityForResult(intent, REQ_FILE_PICKER)
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun launchGame(romPath: String) {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("rom_path", romPath)
        startActivity(intent)
    }
}
