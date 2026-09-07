package com.emu.gba

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.io.File

class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMISSION     = 1
        private const val REQ_MANAGE_STORAGE = 2
        private const val REQ_PICK_FOLDER    = 3
        private const val PREFS_NAME         = "GBAemuPrefs"
        private const val KEY_FOLDER_URI     = "folder_uri"
        private val DEFAULT_ROM_DIR =
            "${Environment.getExternalStorageDirectory()}/GBAemu/roms/"
    }

    private lateinit var prefs: SharedPreferences
    private var folderUri: Uri? = null
    private var romFiles: List<DocumentFile> = emptyList()
    private lateinit var romList: ListView
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        romList   = findViewById(R.id.romList)
        emptyText = findViewById(R.id.emptyText)
        prefs     = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        File(DEFAULT_ROM_DIR).mkdirs()

        findViewById<ImageButton>(R.id.btnPickFolder).setOnClickListener { pickFolder() }

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
            loadRoms()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, results: IntArray
    ) {
        if (requestCode == REQ_PERMISSION
            && results.isNotEmpty()
            && results[0] == PackageManager.PERMISSION_GRANTED) {
            loadRoms()
        } else {
            Toast.makeText(this, "Izin storage ditolak!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_MANAGE_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && Environment.isExternalStorageManager()) {
                    loadRoms()
                }
            }
            REQ_PICK_FOLDER -> {
                if (resultCode == RESULT_OK && data != null) {
                    data.data?.let { uri ->
                        // Minta READ + WRITE agar kita bisa push save file kembali ke folder ini
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
                        folderUri = uri
                        scanFolder(uri)
                    }
                }
            }
        }
    }

    private fun loadRoms() {
        val savedUri = prefs.getString(KEY_FOLDER_URI, null)
        if (savedUri != null) {
            try {
                val uri = Uri.parse(savedUri)
                folderUri = uri
                scanFolder(uri)
                return
            } catch (e: Exception) {
                prefs.edit().remove(KEY_FOLDER_URI).apply()
                folderUri = null
            }
        }

        // Fallback ke folder default (tanpa SAF)
        val defaultDir   = File(DEFAULT_ROM_DIR)
        val defaultFiles = defaultDir.listFiles()?.filter {
            it.isFile && (it.name.lowercase().endsWith(".gba")
                       || it.name.lowercase().endsWith(".zip"))
        } ?: emptyList()

        if (defaultFiles.isNotEmpty()) {
            // Tidak ada folderUri → kirim null ke GameActivity
            showRomList(defaultFiles.map { it.absolutePath to it.name }, folderUriStr = null)
        } else {
            showEmpty()
        }
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, REQ_PICK_FOLDER)
    }

    private fun scanFolder(uri: Uri) {
        val treeUri = DocumentsContract.buildDocumentUriUsingTree(
            uri, DocumentsContract.getTreeDocumentId(uri)
        )
        val root = DocumentFile.fromTreeUri(this, treeUri)
        if (root == null || !root.exists()) {
            Toast.makeText(this, "Folder tidak valid", Toast.LENGTH_SHORT).show()
            pickFolder()
            return
        }
        romFiles = findRomFiles(root)
        if (romFiles.isEmpty()) {
            showEmpty()
        } else {
            // Kirim URI SAF tree ke GameActivity supaya save bisa ditulis balik ke sini
            showRomList(
                romFiles.map { it.uri.toString() to (it.name ?: "Unknown") },
                folderUriStr = uri.toString()
            )
        }
    }

    private fun findRomFiles(doc: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        doc.listFiles().forEach { child ->
            if (child.isDirectory) result.addAll(findRomFiles(child))
            else {
                val name = child.name?.lowercase() ?: ""
                if (name.endsWith(".gba") || name.endsWith(".zip")) result.add(child)
            }
        }
        return result
    }

    private fun showEmpty() {
        romList.visibility   = View.GONE
        emptyText.visibility = View.VISIBLE
    }

    private fun showRomList(files: List<Pair<String, String>>, folderUriStr: String?) {
        romList.visibility   = View.VISIBLE
        emptyText.visibility = View.GONE
        romList.adapter      = RomAdapter(files)
        romList.setOnItemClickListener { _, _, position, _ ->
            launchGame(files[position].first, folderUriStr)
        }
    }

    private fun launchGame(romPathOrUri: String, folderUriStr: String?) {
        startActivity(Intent(this, GameActivity::class.java).apply {
            putExtra("rom_path", romPathOrUri)
            // Kirim SAF tree URI hanya kalau ROM berasal dari folder picker SAF.
            // Kalau null (path biasa), GameActivity akan pakai folder ROM langsung.
            if (folderUriStr != null) {
                putExtra("folder_uri", folderUriStr)
            }
        })
    }

    inner class RomAdapter(private val files: List<Pair<String, String>>) : BaseAdapter() {
        override fun getCount()              = files.size
        override fun getItem(pos: Int)       = files[pos]
        override fun getItemId(pos: Int)     = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.item_rom, parent, false)
            val name = files[pos].second
            view.findViewById<TextView>(R.id.romName).text = name.substringBeforeLast(".")
            view.findViewById<TextView>(R.id.romExt).text  = name.substringAfterLast(".").uppercase()
            return view
        }
    }
}
