# GBAemu
Emulator GBA Android berbasis gpSP (libretro), ditulis dengan Kotlin.

## Cara Build via GitHub Actions
1. Push repo ini ke GitHub
2. Taruh `gpsp_libretro.so` di folder `prebuilt/`
3. GitHub Actions otomatis build APK setiap push ke `main`
4. Download APK dari tab **Actions → Artifacts**

## Cara dapat gpsp_libretro.so
- Install RetroArch di Android
- Download core "Nintendo - Game Boy Advance (gpSP)"
- Copy dari `/data/data/com.retroarch/cores/gpsp_libretro_android.so`
- Rename jadi `gpsp_libretro.so`, taruh di folder `prebuilt/`

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
├── prebuilt/                     # Taruh gpsp_libretro.so di sini
├── build.gradle
└── settings.gradle
```
