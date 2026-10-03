#include <ctype.h>
#include <dlfcn.h>
#include <jni.h>
#include <locale.h>

/*
 * Bionic on Android 4.4 lacks POSIX *_l locale helpers that Flutter's libc++
 * expects at runtime. Provide fallbacks and export them globally before
 * libflutter.so is loaded.
 */

#define KITKAT_LOCALE_STUB(name, call) \
    int name(int c, locale_t locale) { \
        (void)locale; \
        return call(c); \
    }

KITKAT_LOCALE_STUB(isxdigit_l, isxdigit)
KITKAT_LOCALE_STUB(isalpha_l, isalpha)
KITKAT_LOCALE_STUB(isdigit_l, isdigit)
KITKAT_LOCALE_STUB(isalnum_l, isalnum)
KITKAT_LOCALE_STUB(isspace_l, isspace)

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)vm;
    (void)reserved;
    dlopen("libkitkat_shim.so", RTLD_NOW | RTLD_GLOBAL);
    return JNI_VERSION_1_6;
}
