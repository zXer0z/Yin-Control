LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := v60direct
LOCAL_SRC_FILES := direct.c
LOCAL_CFLAGS := -O2 -fvisibility=hidden
LOCAL_LDLIBS := -ldl -llog
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)
