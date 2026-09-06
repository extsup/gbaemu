#!/usr/bin/env python3
import os
import shutil

FILE_PATH = "./app/src/main/kotlin/com/emu/gba/GameActivity.kt"
BACKUP_PATH = FILE_PATH + ".backup"

INSERT_BLOCK = '''    // --- PATCH: Salin ROM ke cache internal ---
    val romFile = File(romPath)
    if (!romFile.exists() || !romFile.canRead()) {
        Toast.makeText(this, "ROM tidak dapat diakses!", Toast.LENGTH_SHORT).show()
        finish()
        return
    }
    val cacheRom = File(cacheDir, "temp_rom.gba")
    try {
        romFile.inputStream().use { input ->
            cacheRom.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } catch (e: Exception) {
        Toast.makeText(this, "Gagal menyalin ROM: ${e.message}", Toast.LENGTH_SHORT).show()
        finish()
        return
    }
    val pathToLoad = cacheRom.absolutePath
    // --- AKHIR PATCH ---
'''

def apply_patch():
    if not os.path.exists(FILE_PATH):
        print(f"❌ File {FILE_PATH} tidak ditemukan!")
        return

    shutil.copy2(FILE_PATH, BACKUP_PATH)
    print(f"✅ Backup disimpan ke {BACKUP_PATH}")

    with open(FILE_PATH, "r") as f:
        lines = f.readlines()

    target = '        val romPath = intent.getStringExtra("rom_path") ?: run {\n'
    insert_idx = -1
    for i, line in enumerate(lines):
        if line == target:
            insert_idx = i + 1
            break

    if insert_idx == -1:
        print("❌ Target tidak ditemukan. Restore backup.")
        shutil.copy2(BACKUP_PATH, FILE_PATH)
        return

    new_lines = lines[:insert_idx] + [INSERT_BLOCK] + lines[insert_idx:]
    with open(FILE_PATH, "w") as f:
        f.writelines(new_lines)

    print("✅ Patch berhasil diterapkan! Sekarang push ke GitHub.")

if __name__ == "__main__":
    apply_patch()
