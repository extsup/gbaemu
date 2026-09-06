content = open('jni/gpsp/android.c').read()
old = '    libhandle = dlopen(path, RTLD_NOLOAD | RTLD_LAZY);\n    if (!libhandle) { libhandle = dlopen(path, RTLD_LAZY | RTLD_GLOBAL); }'
new = '    libhandle = RTLD_DEFAULT;'
print('Found!' if old in content else 'NOT FOUND')
open('jni/gpsp/android.c', 'w').write(content.replace(old, new))
