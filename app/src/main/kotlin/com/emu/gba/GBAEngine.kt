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
            System.loadLibrary("gpsp_libretro_android")
            Log.d(TAG, "gpsp_libretro_android loaded via System.loadLibrary")
        } catch (e: UnsatisfiedLinkError) {
            Log.d(TAG, "gpsp_libretro_android not found as system lib, will use dlopen")
        }
    }

    fun initCore(context: Context): Boolean {
        val possiblePaths = listOf(
            context.applicationInfo.nativeLibraryDir + "/gpsp_libretro_android.so",
            "/data/data/${context.packageName}/lib/gpsp_libretro_android.so",
            "/data/app/${context.packageName}*/lib/arm64-v8a/gpsp_libretro_android.so"
        )
        for (p in possiblePaths) {
            Log.d(TAG, "Trying core: $p")
            if (nativeInit(p)) {
                corePath = p
                Log.d(TAG, "Core loaded: $p")
                return true
            } else {
                Log.e(TAG, "Failed to load from: $p")
            }
        }
        try {
            System.loadLibrary("gpsp_libretro_android")
            Log.d(TAG, "Fallback: gpsp_libretro_android loaded via System.loadLibrary")
            return true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Fallback failed", e)
        }
        return false
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
