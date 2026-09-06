package com.emu.gba

import android.content.Context
import android.util.Log

object GBAEngine {

    private var corePath: String = ""
    private const val TAG = "GBAEngine"

    const val KEY_A      = (1 shl 0)
    const val KEY_B      = (1 shl 1)
    const val KEY_SELECT = (1 shl 2)
    const val KEY_START  = (1 shl 3)
    const val KEY_RIGHT  = (1 shl 4)
    const val KEY_LEFT   = (1 shl 5)
    const val KEY_UP     = (1 shl 6)
    const val KEY_DOWN   = (1 shl 7)
    const val KEY_R      = (1 shl 8)
    const val KEY_L      = (1 shl 9)

    private var currentKeys = 0

    init {
        System.loadLibrary("gbaemu")
        try {
            System.loadLibrary("gpsp_libretro")
            Log.d(TAG, "gpsp_libretro loaded via System.loadLibrary")
        } catch (e: UnsatisfiedLinkError) {
            Log.d(TAG, "gpsp_libretro not found as system lib, will use dlopen")
        }
    }

    fun initCore(context: Context): Boolean {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val srcFile = java.io.File(nativeDir, "libgpsp_libretro.so")
        val destFile = java.io.File(context.filesDir, "libgpsp_libretro.so")

        if (srcFile.exists()) {
            srcFile.copyTo(destFile, overwrite = true)
            Log.d(TAG, "Core copied to: ${destFile.absolutePath}")
        } else {
            Log.e(TAG, "Core not found in nativeLibraryDir: ${srcFile.absolutePath}")
        }

        if (destFile.exists()) {
            return nativeInit(destFile.absolutePath)
        } else {
            Log.e(TAG, "Core not found: ${destFile.absolutePath}")
            return false
        }
    }

    external fun nativeInit(soPath: String): Boolean
    external fun nativeLoadRom(romPath: String): Boolean
    external fun nativeRunFrame()
    external fun nativeSetInput(keys: Int)
    external fun nativeGetFramebuffer(): IntArray?
    external fun nativeCleanup()

    fun pressKey(key: Int) {
        currentKeys = currentKeys or key
        nativeSetInput(currentKeys)
    }

    fun releaseKey(key: Int) {
        currentKeys = currentKeys and key.inv()
        nativeSetInput(currentKeys)
    }

    fun resetKeys() {
        currentKeys = 0
        nativeSetInput(0)
    }
}