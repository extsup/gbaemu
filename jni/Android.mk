LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := gbaemu
LOCAL_SRC_FILES := gpsp/android.c
LOCAL_LDLIBS    := -llog -ldl -landroid
include $(BUILD_SHARED_LIBRARY)
