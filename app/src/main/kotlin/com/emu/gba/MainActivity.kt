package com.emu.gba

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMISSION = 1
        private val ROM_DIR = "${Environment.getExternalStorageDirectory()}/GBAemu/roms/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        File(ROM_DIR).mkdirs()

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

    private fun showRomPicker() {
        val dir = File(ROM_DIR)
        val files = dir.listFiles()?.filter {
            it.name.lowercase().endsWith(".gba")
        } ?: emptyList()

        if (files.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("GBAemu")
                .setMessage("Tidak ada ROM ditemukan.\nTaruh file .gba di:\n$ROM_DIR")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val names = files.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pilih ROM")
            .setItems(names) { _, which ->
                launchGame(files[which].absolutePath)
            }
            .show()
    }

    private fun launchGame(romPath: String) {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("rom_path", romPath)
        startActivity(intent)
    }
}
