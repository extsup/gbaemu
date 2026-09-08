# GBAemu
Emulator GBA Android berbasis gpSP (libretro), ditulis dengan Kotlin.

## Cara Build via GitHub Ahctions
1. Push repo ini ke GitHub
2. GitHub Actions mengunduh coreb gpSP arm64 terbaru dari buildbot Libretro saat build.
   File binary core **tidak disimpan di repository atau pull request**.
3. GitHub Actions otomatis build APK setiap push ke `main`
4. Download APK dari tab **Actions → Artifacts**

## Core gpSP
CI mengunduh `gpsp_libretro_android.so` dari buildbot Libretro dan mengemasnya ke APK
sebagai `libgpsp_libretro.so`. Untuk build lokal, letakkan core tersebut di
`app/src/main/jniLibs/arm64-v8a/libgpsp_libretro.so` (folder ini diabaikan Git).

## ROM & BIOS
- ROM `.gba` taruh di `/storage/emulated/0/GBAemu/roms/`
- BIOS `gba_bios.bin` taruh di `/storage/emulated/0/GBAemu/`

## Struktur Project
```
GBAemu/
├── .github/workflows/build.yml   # GitHub Actions
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── kotlin/com/emu/gba/
│   │   ├── GBAEngine.kt          # JNI bridge
│   │   ├── GBAView.kt            # Render frame
│   │   ├── GameActivity.kt       # Game screen
│   │   ├── MainActivity.kt       # Pilih ROM
│   │   └── VirtualController.kt  # Tombol virtual
│   └── res/
├── jni/
│   ├── Android.mk
│   ├── Application.mk
│   └── gpsp/android.c            # Libretro wrapper
├── build.gradle
└── settings.gradle
```
