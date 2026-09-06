content = open('jni/gpsp/android.c').read()
old = '    libhandle = RTLD_DEFAULT;\n    (*env)->ReleaseStringUTFChars(env, soPath, path);\n    if (!libhandle) { LOGE("dlopen failed: %s", dlerror()); return JNI_FALSE; }'
new = '    libhandle = RTLD_DEFAULT;\n    (*env)->ReleaseStringUTFChars(env, soPath, path);'
print('Found!' if old in content else 'NOT FOUND')
open('jni/gpsp/android.c', 'w').write(content.replace(old, new))
