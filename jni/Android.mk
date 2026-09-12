LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := gpsp_frontend

# === Sumber utama frontend ===
LOCAL_SRC_FILES := gpsp_frontend.c

# === rcheevos — file yang di-include (exclude external framework) ===
LOCAL_SRC_FILES += \
    rcheevos/src/rc_client.c \
    rcheevos/src/rc_compat.c \
    rcheevos/src/rc_util.c \
    rcheevos/src/rc_version.c \
    rcheevos/src/rapi/rc_api_common.c \
    rcheevos/src/rapi/rc_api_editor.c \
    rcheevos/src/rapi/rc_api_info.c \
    rcheevos/src/rapi/rc_api_runtime.c \
    rcheevos/src/rapi/rc_api_user.c \
    rcheevos/src/rcheevos/alloc.c \
    rcheevos/src/rcheevos/condition.c \
    rcheevos/src/rcheevos/condset.c \
    rcheevos/src/rcheevos/consoleinfo.c \
    rcheevos/src/rcheevos/format.c \
    rcheevos/src/rcheevos/lboard.c \
    rcheevos/src/rcheevos/memref.c \
    rcheevos/src/rcheevos/operand.c \
    rcheevos/src/rcheevos/richpresence.c \
    rcheevos/src/rcheevos/runtime.c \
    rcheevos/src/rcheevos/runtime_progress.c \
    rcheevos/src/rcheevos/trigger.c \
    rcheevos/src/rcheevos/value.c \
    rcheevos/src/rhash/hash.c \
    rcheevos/src/rhash/hash_rom.c \
    rcheevos/src/rhash/md5.c

# === File yang di-EXCLUDE (tidak dipakai) ===
# - rc_libretro.c         : butuh simbol core libretro (bukan frontend)
# - rc_client_external.c  : butuh external client framework
# - rc_client_raintegration.c : Windows RetroArch integration
# - rcheevos/rc_validate.c: dev validator
# - rhash/cdreader.c, hash_disc.c, hash_encrypted.c, hash_zip.c, aes.c
#   : butuh CD/DVD/AES/ZIP reader, tidak dipakai untuk GBA ROM simple

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/../include \
    $(LOCAL_PATH)/rcheevos/include

LOCAL_LDLIBS := -ldl
LOCAL_CFLAGS := -O2 -fvisibility=hidden -std=c99 \
    -DRC_DISABLE_LUA=1 \
    -DRC_HASH_NO_ENCRYPTED=1 \
    -DRC_HASH_NO_ZIP=1

include $(BUILD_SHARED_LIBRARY)
