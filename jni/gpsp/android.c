#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#define TAG "GBAemu"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define RETRO_DEVICE_JOYPAD             1
#define RETRO_DEVICE_ID_JOYPAD_B        0
#define RETRO_DEVICE_ID_JOYPAD_A        8
#define RETRO_DEVICE_ID_JOYPAD_SELECT   2
#define RETRO_DEVICE_ID_JOYPAD_START    3
#define RETRO_DEVICE_ID_JOYPAD_UP       4
#define RETRO_DEVICE_ID_JOYPAD_DOWN     5
#define RETRO_DEVICE_ID_JOYPAD_LEFT     6
#define RETRO_DEVICE_ID_JOYPAD_RIGHT    7
#define RETRO_DEVICE_ID_JOYPAD_L        9
#define RETRO_DEVICE_ID_JOYPAD_R        10
#define RETRO_ENVIRONMENT_SET_PIXEL_FORMAT 10
#define RETRO_PIXEL_FORMAT_RGB565       1

struct retro_game_info { const char *path; const void *data; size_t size; const char *meta; };
struct retro_system_av_info {
    struct { unsigned width, height, base_width, base_height; float aspect_ratio; } geometry;
    struct { double fps, sample_rate; } timing;
};
struct retro_system_info {
    const char *library_name, *library_version, *valid_extensions;
    int need_fullpath, block_extract;
};

typedef void     (*retro_video_refresh_t)(const void*, unsigned, unsigned, size_t);
typedef void     (*retro_audio_sample_t)(int16_t, int16_t);
typedef size_t   (*retro_audio_sample_batch_t)(const int16_t*, size_t);
typedef void     (*retro_input_poll_t)(void);
typedef int16_t  (*retro_input_state_t)(unsigned, unsigned, unsigned, unsigned);
typedef int      (*retro_environment_t)(unsigned, void*);

static void *libhandle = NULL;
static void (*p_retro_init)(void);
static void (*p_retro_deinit)(void);
static int  (*p_retro_load_game)(const struct retro_game_info*);
static void (*p_retro_run)(void);
static void (*p_retro_unload_game)(void);
static void (*p_retro_set_environment)(retro_environment_t);
static void (*p_retro_set_video_refresh)(retro_video_refresh_t);
static void (*p_retro_set_audio_sample)(retro_audio_sample_t);
static void (*p_retro_set_audio_sample_batch)(retro_audio_sample_batch_t);
static void (*p_retro_set_input_poll)(retro_input_poll_t);
static void (*p_retro_set_input_state)(retro_input_state_t);

static uint32_t *framebuffer = NULL;
static unsigned fb_width = 240, fb_height = 160;
static uint32_t input_state = 0;

// OpenSL ES Audio Engine variables
static SLObjectItf engineObject = NULL;
static SLEngineItf engineEngine = NULL;
static SLObjectItf outputMixObject = NULL;
static SLObjectItf bqPlayerObject = NULL;
static SLPlayItf bqPlayerPlay = NULL;
static SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue = NULL;

#define AUDIO_RING_SIZE (16384)
static int16_t audio_ring[AUDIO_RING_SIZE];
static volatile int ring_head = 0;
static volatile int ring_tail = 0;

static void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
    int16_t block[1024];
    int samples_to_read = 512; // 256 stereo frames
    int count = 0;
    
    while (count < samples_to_read && ring_head != ring_tail) {
        block[count++] = audio_ring[ring_tail];
        ring_tail = (ring_tail + 1) % AUDIO_RING_SIZE;
    }
    
    if (count > 0) {
        (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, block, count * sizeof(int16_t));
    }
}

static void init_opensles() {
    slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
    (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);

    (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 0, NULL, NULL);
    (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);

    SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};
    SLDataFormat_PCM format_pcm = {
        SL_DATAFORMAT_PCM, 2, SL_SAMPLINGRATE_32,
        SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
        SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, SL_BYTEORDER_LITTLEENDIAN
    };
    SLDataSource audioSrc = {&loc_bufq, &format_pcm};

    SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    SLDataSink audioSnk = {&loc_outmix, NULL};

    const SLInterfaceID ids[1] = {SL_IID_BUFFERQUEUE};
    const SLboolean req[1] = {SL_BOOLEAN_TRUE};

    (*engineEngine)->CreateAudioPlayer(engineEngine, &bqPlayerObject, &audioSrc, &audioSnk, 1, ids, req);
    (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE);
    (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay);
    (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE, &bqPlayerBufferQueue);

    (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, bqPlayerCallback, NULL);
    (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);

    int16_t silence[512] = {0};
    (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, silence, sizeof(silence));
}

static void video_refresh_cb(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data || !framebuffer) return;
    fb_width = width; fb_height = height;
    for (unsigned y = 0; y < height; y++) {
        const uint16_t *row = (const uint16_t*)((const uint8_t*)data + y * pitch);
        for (unsigned x = 0; x < width; x++) {
            uint16_t px = row[x];
            int r = ((px >> 11) & 0x1F) << 3;
            int g = ((px >> 5)  & 0x3F) << 2;
            int b = ((px)       & 0x1F) << 3;
            framebuffer[y * width + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }
}

static void audio_sample_cb(int16_t l, int16_t r) {
    int next_head = (ring_head + 1) % AUDIO_RING_SIZE;
    if (next_head != ring_tail) {
        audio_ring[ring_head] = l;
        ring_head = next_head;
    }
    next_head = (ring_head + 1) % AUDIO_RING_SIZE;
    if (next_head != ring_tail) {
        audio_ring[ring_head] = r;
        ring_head = next_head;
    }
}

static size_t audio_sample_batch_cb(const int16_t *data, size_t frames) {
    for (size_t i = 0; i < frames * 2; i++) {
        int next_head = (ring_head + 1) % AUDIO_RING_SIZE;
        if (next_head != ring_tail) {
            audio_ring[ring_head] = data[i];
            ring_head = next_head;
        }
    }
    return frames;
}

static void input_poll_cb(void) {}
static int16_t input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0 || device != RETRO_DEVICE_JOYPAD) return 0;
    switch (id) {
        case RETRO_DEVICE_ID_JOYPAD_A:      return (input_state >> 0) & 1;
        case RETRO_DEVICE_ID_JOYPAD_B:      return (input_state >> 1) & 1;
        case RETRO_DEVICE_ID_JOYPAD_SELECT: return (input_state >> 2) & 1;
        case RETRO_DEVICE_ID_JOYPAD_START:  return (input_state >> 3) & 1;
        case RETRO_DEVICE_ID_JOYPAD_RIGHT:  return (input_state >> 4) & 1;
        case RETRO_DEVICE_ID_JOYPAD_LEFT:   return (input_state >> 5) & 1;
        case RETRO_DEVICE_ID_JOYPAD_UP:     return (input_state >> 6) & 1;
        case RETRO_DEVICE_ID_JOYPAD_DOWN:   return (input_state >> 7) & 1;
        case RETRO_DEVICE_ID_JOYPAD_R:      return (input_state >> 8) & 1;
        case RETRO_DEVICE_ID_JOYPAD_L:      return (input_state >> 9) & 1;
    }
    return 0;
}
static int environment_cb(unsigned cmd, void *data) {
    if (cmd == RETRO_ENVIRONMENT_SET_PIXEL_FORMAT) { *(int*)data = RETRO_PIXEL_FORMAT_RGB565; return 1; }
    return 0;
}

#define LOAD(name) \
    p_##name = dlsym(libhandle, #name); \
    if (!p_##name) { LOGE("Missing symbol: " #name); return JNI_FALSE; }

JNIEXPORT jboolean JNICALL
Java_com_emu_gba_GBAEngine_nativeInit(JNIEnv *env, jobject obj, jstring soPath) {
    const char *path = (*env)->GetStringUTFChars(env, soPath, NULL);
    LOGI("Loading core: %s", path);
    libhandle = dlopen(path, RTLD_LAZY);
    (*env)->ReleaseStringUTFChars(env, soPath, path);
    if (!libhandle) { LOGE("dlopen failed: %s", dlerror()); return JNI_FALSE; }

    LOAD(retro_init) LOAD(retro_deinit) LOAD(retro_load_game) LOAD(retro_run)
    LOAD(retro_unload_game) LOAD(retro_set_environment) LOAD(retro_set_video_refresh)
    LOAD(retro_set_audio_sample) LOAD(retro_set_audio_sample_batch)
    LOAD(retro_set_input_poll) LOAD(retro_set_input_state)

    p_retro_set_environment(environment_cb);
    p_retro_set_video_refresh(video_refresh_cb);
    p_retro_set_audio_sample(audio_sample_cb);
    p_retro_set_audio_sample_batch(audio_sample_batch_cb);
    p_retro_set_input_poll(input_poll_cb);
    p_retro_set_input_state(input_state_cb);
    p_retro_init();

    init_opensles();

    framebuffer = (uint32_t*)malloc(240 * 160 * 4);
    LOGI("Core and Audio initialized.");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_emu_gba_GBAEngine_nativeLoadRom(JNIEnv *env, jobject obj, jstring romPath) {
    const char *path = (*env)->GetStringUTFChars(env, romPath, NULL);
    struct retro_game_info info = { path, NULL, 0, NULL };
    int ok = p_retro_load_game(&info);
    (*env)->ReleaseStringUTFChars(env, romPath, path);
    if (!ok) { LOGE("Failed to load ROM"); return JNI_FALSE; }
    LOGI("ROM loaded.");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_emu_gba_GBAEngine_nativeRunFrame(JNIEnv *env, jobject obj) {
    if (p_retro_run) p_retro_run();
}

JNIEXPORT void JNICALL
Java_com_emu_gba_GBAEngine_nativeSetInput(JNIEnv *env, jobject obj, jint keys) {
    input_state = (uint32_t)keys;
}

JNIEXPORT jintArray JNICALL
Java_com_emu_gba_GBAEngine_nativeGetFramebuffer(JNIEnv *env, jobject obj) {
    if (!framebuffer) return NULL;
    int size = fb_width * fb_height;
    jintArray arr = (*env)->NewIntArray(env, size);
    (*env)->SetIntArrayRegion(env, arr, 0, size, (jint*)framebuffer);
    return arr;
}

JNIEXPORT void JNICALL
Java_com_emu_gba_GBAEngine_nativeCleanup(JNIEnv *env, jobject obj) {
    if (bqPlayerObject) {
        (*bqPlayerObject)->Destroy(bqPlayerObject);
        bqPlayerObject = NULL;
    }
    if (outputMixObject) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = NULL;
    }
    if (engineObject) {
        (*engineObject)->Destroy(engineObject);
        engineObject = NULL;
    }
    if (p_retro_unload_game) p_retro_unload_game();
    if (p_retro_deinit) p_retro_deinit();
    if (framebuffer) { free(framebuffer); framebuffer = NULL; }
    if (libhandle) { dlclose(libhandle); libhandle = NULL; }
}
