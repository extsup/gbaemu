# TerraGBA

A modern, landscape-first Android shell for the **gPSP** Game Boy Advance core, written in Kotlin.

## Architecture
- Kotlin + Material 3 Compose UI with an accessible virtual gamepad and optional physical-controller input.
- `GlesGameSurface` owns the emulation surface. gPSP output is uploaded into an OpenGL ES texture; the display path is GPU-based, not a CPU `Bitmap`/`Canvas` blit.
- `GpspCore` is the single Kotlin-to-native boundary. The native module is deliberately thin: it translates buttons and frame buffers and leaves emulation to upstream gPSP.

## Adding the gPSP core
This repository does not redistribute gPSP. Put a compatible gPSP source checkout at `app/src/main/cpp/gpsp`, then wire its source files into `CMakeLists.txt`. The included JNI adapter documents the three callbacks it expects (`gpsp_load_rom`, `gpsp_run_frame`, `gpsp_set_button`). Review gPSP's license and ship its notices with your build.

## Build
```sh
gradle :app:assembleDebug
```

Until gPSP is linked, the app shows a GPU checkerboard preview, so UI work can be tested independently.

## Continuous integration
GitHub Actions installs Gradle 8.10.2 and builds the debug APK on every push and pull request using JDK 17. Download the `terragba-debug-apk` artifact from the workflow run.
