content = open('jni/gpsp/android.c').read()

# Tambah variable save dir dan define
old = '#define RETRO_ENVIRONMENT_SET_PIXEL_FORMAT 10'
new = '''#define RETRO_ENVIRONMENT_SET_PIXEL_FORMAT 10
#define RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY 31
#define RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY 9

static char save_dir[512] = {0};'''

print('Found!' if old in content else 'NOT FOUND')
content = content.replace(old, new)

# Update environment_cb
old2 = '''static int environment_cb(unsigned cmd, void *data) {
    if (cmd == RETRO_ENVIRONMENT_SET_PIXEL_FORMAT) { *(int*)data = RETRO_PIXEL_FORMAT_RGB565; return 1; }
    return 0;
}'''
new2 = '''static int environment_cb(unsigned cmd, void *data) {
    if (cmd == RETRO_ENVIRONMENT_SET_PIXEL_FORMAT) { *(int*)data = RETRO_PIXEL_FORMAT_RGB565; return 1; }
    if (cmd == RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY) { *(const char**)data = save_dir; return 1; }
    if (cmd == RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY) { *(const char**)data = save_dir; return 1; }
    return 0;
}'''
print('Found2!' if old2 in content else 'NOT FOUND2')
content = content.replace(old2, new2)

# Tambah fungsi nativeSetSaveDir sebelum nativeInit
old3 = 'JNIEXPORT jboolean JNICALL\nJava_com_emu_gba_GBAEngine_nativeInit'
new3 = '''JNIEXPORT void JNICALL
Java_com_emu_gba_GBAEngine_nativeSetSaveDir(JNIEnv *env, jobject obj, jstring path) {
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    strncpy(save_dir, p, sizeof(save_dir) - 1);
    (*env)->ReleaseStringUTFChars(env, path, p);
    LOGI("Save dir set to: %s", save_dir);
}

JNIEXPORT jboolean JNICALL
Java_com_emu_gba_GBAEngine_nativeInit'''
print('Found3!' if old3 in content else 'NOT FOUND3')
content = content.replace(old3, new3)

open('jni/gpsp/android.c', 'w').write(content)
print('Done!')
