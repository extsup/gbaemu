// gPSP JNI adapter sketch. Compile this only after adding upstream gPSP headers/sources.
// Expose loadRom(), setButton(), and runFrame() declared in GpspCore.kt.
// The renderer uploads runFrame's RGB565 240x160 output directly with glTexImage2D.
