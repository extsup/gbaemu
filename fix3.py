content = open('jni/gpsp/android.c').read()
old = '    if (libhandle) { dlclose(libhandle); libhandle = NULL; }'
new = '    libhandle = NULL;'
print('Found!' if old in content else 'NOT FOUND')
open('jni/gpsp/android.c', 'w').write(content.replace(old, new))
