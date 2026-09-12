package com.terra.gba.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.util.zip.ZipInputStream

data class RomEntry(val uri: Uri, val name: String)

/** Persists SAF folder access and materializes ROMs in app storage for native cores that require paths. */
class RomLibrary(private val context: Context) {
    private val prefs = context.getSharedPreferences("rom-library", Context.MODE_PRIVATE)
    fun folders(): List<Uri> = prefs.getStringSet("folders", emptySet()).orEmpty().map(Uri::parse)
    fun addFolder(uri: Uri) { prefs.edit().putStringSet("folders", folders().map { it.toString() }.plus(uri.toString()).toSet()).apply() }
    fun games(): List<RomEntry> = folders().flatMap(::gamesIn).sortedBy { it.name.lowercase() }
    private fun gamesIn(folder: Uri): List<RomEntry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        return context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) { val id = cursor.getString(0); val name = cursor.getString(1); if (name.endsWith(".gba", true) || name.endsWith(".zip", true)) add(RomEntry(DocumentsContract.buildDocumentUriUsingTree(folder, id), name)) } }
        }.orEmpty()
    }
    fun materialize(entry: RomEntry): File {
        val romDir = File(context.filesDir, "roms").apply { mkdirs() }
        val target = File(romDir, entry.name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        context.contentResolver.openInputStream(entry.uri)!!.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        if (!entry.name.endsWith(".zip", true)) return target
        ZipInputStream(target.inputStream()).use { zip ->
            var item = zip.nextEntry
            while (item != null) { if (!item.isDirectory && item.name.endsWith(".gba", true)) { val extracted = File(romDir, target.nameWithoutExtension + ".gba"); extracted.outputStream().use { output -> zip.copyTo(output) }; return extracted }; item = zip.nextEntry }
        }
        error("ZIP tidak berisi ROM .gba")
    }
    fun saveDirectory(): File = File(context.filesDir, "saves").apply { mkdirs() }
}
