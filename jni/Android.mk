LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := gpsp_frontend
LOCAL_SRC_FILES := gpsp_frontend.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../include
LOCAL_LDLIBS := -ldl
LOCAL_CFLAGS := -O2 -fvisibility=hidden -std=c99
include $(BUILD_SHARED_LIBRARY)
