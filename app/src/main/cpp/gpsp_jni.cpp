// gPSP JNI adapter sketch. Compile this only after adding upstream gPSP headers/sources.
// Implement setSaveDirectory(path) before loadRom(path): native saves must be written to that
// persistent app-private directory as .sav/.srm files and reloaded from it on each ROM launch.
// Expose loadRom(), setButton(), runFrame(), saveState(), loadState(), and stop() from GpspCore.kt.
// The renderer uploads runFrame's RGB565 240x160 output directly with glTexImage2D.
