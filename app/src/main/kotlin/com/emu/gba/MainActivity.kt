package com.emu.gba

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.io.File

class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMISSION = 1
        private const val REQ_MANAGE_STORAGE = 2
        private const val REQ_PICK_FOLDER = 3
        private const val PREFS_NAME = "GBAemuPrefs"
        private const val KEY_FOLDER_URI = "folder_uri"
        private val DEFAULT_ROM_DIR = "${Environment.getExternalStorageDirectory()}/GBAemu/roms/"
    }

    private lateinit var prefs: SharedPreferences
    private var folderUri: Uri? = null
    private var romFiles: List<DocumentFile> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        File(DEFAULT_ROM_DIR).mkdirs()

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
            showMainDialog()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, results: IntArray
    ) {
        if (requestCode == REQ_PERMISSION &&
            results.isNotEmpty() &&
            results[0] == PackageManager.PERMISSION_GRANTED) {
            showMainDialog()
        } else {
            Toast.makeText(this, "Izin storage ditolak!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_MANAGE_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        showMainDialog()
                    } else {
                        Toast.makeText(this, "Izin manajemen storage diperlukan!", Toast.LENGTH_LONG).show()
                    }
                }
            }
            REQ_PICK_FOLDER -> {
                if (resultCode == RESULT_OK && data != null) {
                    data.data?.let { uri ->
                        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
                        folderUri = uri
                        scanFolder(uri)
                    }
                }
            }
        }
    }

    private fun showMainDialog() {
        val savedUri = prefs.getString(KEY_FOLDER_URI, null)
        if (savedUri != null) {
            try {
                val uri = Uri.parse(savedUri)
                folderUri = uri
                scanFolder(uri)
                return
            } catch (e: Exception) {
                prefs.edit().remove(KEY_FOLDER_URI).apply()
            }
        }

        val defaultDir = File(DEFAULT_ROM_DIR)
        val defaultFiles = defaultDir.listFiles()?.filter {
            it.isFile && (it.name.lowercase().endsWith(".gba") || it.name.lowercase().endsWith(".zip"))
        } ?: emptyList()

        if (defaultFiles.isNotEmpty()) {
            showRomList(defaultFiles.map { it.absolutePath to it.name })
        } else {
            AlertDialog.Builder(this)
                .setTitle("GBAemu")
                .setMessage("Tidak ada ROM ditemukan di folder default.\n\nPilih folder yang berisi ROM (.gba atau .zip)?")
                .setPositiveButton("Pilih Folder") { _, _ ->
                    pickFolder()
                }
                .setNegativeButton("Keluar") { _, _ -> finish() }
                .show()
        }
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivityForResult(intent, REQ_PICK_FOLDER)
    }

    private fun scanFolder(uri: Uri) {
        val treeUri = DocumentsContract.buildDocumentUriUsingTree(uri,
            DocumentsContract.getTreeDocumentId(uri))
        val root = DocumentFile.fromTreeUri(this, treeUri)
        if (root == null || !root.exists()) {
            Toast.makeText(this, "Folder tidak valid", Toast.LENGTH_SHORT).show()
            pickFolder()
            return
        }

        romFiles = findRomFiles(root)
        if (romFiles.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Tidak ada ROM")
                .setMessage("Folder tidak mengandung file .gba atau .zip.\nPilih folder lain?")
                .setPositiveButton("Pilih Folder") { _, _ -> pickFolder() }
                .setNegativeButton("Batal") { _, _ -> finish() }
                .show()
        } else {
            val names = romFiles.map { it.name ?: "Unknown" }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Pilih ROM")
                .setItems(names) { _, which ->
                    val selected = romFiles[which]
                    launchGame(selected.uri.toString())
                }
                .show()
        }
    }

    private fun findRomFiles(doc: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        doc.listFiles().forEach { child ->
            if (child.isDirectory) {
                result.addAll(findRomFiles(child))
            } else {
                val name = child.name?.lowercase() ?: ""
                if (name.endsWith(".gba") || name.endsWith(".zip")) {
                    result.add(child)
                }
            }
        }
        return result
    }

    private fun showRomList(files: List<Pair<String, String>>) {
        val names = files.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pilih ROM")
            .setItems(names) { _, which ->
                launchGame(files[which].first)
            }
            .show()
    }

    private fun launchGame(romPathOrUri: String) {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("rom_path", romPathOrUri)
        startActivity(intent)
    }
}
