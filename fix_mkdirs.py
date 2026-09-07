f = open('app/src/main/kotlin/com/emu/gba/GameActivity.kt')
c = f.read()
f.close()
old = '      val saveDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "GBAemu/saves")\n        GBAEngine.nativeSetSaveDir(saveDir.absolutePath)'
new = '        val saveDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "GBAemu/saves")\n        saveDir.mkdirs()\n        GBAEngine.nativeSetSaveDir(saveDir.absolutePath)'
print('Found!' if old in c else 'NOT FOUND')
open('app/src/main/kotlin/com/emu/gba/GameActivity.kt', 'w').write(c.replace(old, new))
