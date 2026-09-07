f = open('app/src/main/kotlin/com/emu/gba/GameActivity.kt')
c = f.read()
f.close()

old = '     // Inisialisasi core terlebih dahulu\n        if (!GBAEngine.initCore(this)) {'
new = '        val saveDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "GBAemu/saves")\n        saveDir.mkdirs()\n        GBAEngine.nativeSetSaveDir(saveDir.absolutePath)\n\n        // Inisialisasi core terlebih dahulu\n        if (!GBAEngine.initCore(this)) {'

print('Found!' if old in c else 'NOT FOUND')
open('app/src/main/kotlin/com/emu/gba/GameActivity.kt', 'w').write(c.replace(old, new))
