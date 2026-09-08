package com.terra.gba.core

/** Contract implemented by the optional gPSP JNI library. No frames travel through a CPU Bitmap. */
object GpspCore {
    const val A = 0; const val B = 1; const val L = 2; const val R = 3
    const val START = 4; const val SELECT = 5; const val UP = 6; const val DOWN = 7
    const val LEFT = 8; const val RIGHT = 9

    val available: Boolean by lazy {
        runCatching { System.loadLibrary("gpsp_jni") }.isSuccess
    }
    external fun loadRom(path: String): Boolean
    external fun setButton(button: Int, pressed: Boolean)
    /** Runs one gPSP frame and exposes its RGB565 pixel buffer to the GL renderer. */
    external fun runFrame(): java.nio.ByteBuffer?
}
