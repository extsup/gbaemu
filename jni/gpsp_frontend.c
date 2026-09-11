#include <jni.h>
#include <dlfcn.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include "../include/libretro.h"

/* Typedef fallback (kalau libretro.h tidak mendefinisikan) */
#ifndef RETRO_SERIALIZE_TYPEDEFS_DEFINED
typedef size_t (*retro_serialize_size_t)(void);
typedef bool   (*retro_serialize_t)(void *data, size_t size);
typedef bool   (*retro_unserialize_t)(const void *data, size_t size);
#define RETRO_SERIALIZE_TYPEDEFS_DEFINED 1
#endif
#define W 240
#define H 160
#define FRAME_BYTES (W*H*2)
#define AUDIO_FRAMES 65536
static void *core;
static char errbuf[512];
static char system_dir[1024], save_dir[1024];
static uint8_t frame_buf[FRAME_BYTES] __attribute__((aligned(4)));
static int loaded;
static volatile uint16_t buttons;

/* SAVE_OK_MARKER - flag agar save tidak concurrent */
static volatile int g_saving = 0;

/* Save state function pointers (optional — core mungkin tidak punya) */
static retro_serialize_size_t    g_serialize_size_fn;
static retro_serialize_t         g_serialize_fn;
static retro_unserialize_t       g_unserialize_fn;

/* Cek dan buat folder induk dari sebuah file path.
   Return 0 kalau folder ada/berhasil dibuat, <0 kalau error. */
static int ensure_parent_dir(const char *filepath) {
    char dir[1100];
    size_t n = strlen(filepath);
    if (n == 0 || n >= sizeof(dir)) return -1;
    memcpy(dir, filepath, n + 1);
    char *slash = strrchr(dir, '/');
    if (!slash) return -1;
    *slash = '\0';
    if (dir[0] == '\0') return 0; /* root */
    struct stat st;
    if (stat(dir, &st) == 0) {
        return S_ISDIR(st.st_mode) ? 0 : -1;
    }
    /* mkdir single-level; parent diasumsikan sudah ada */
    if (mkdir(dir, 0755) == 0) return 0;
    /* Kalau gagal, coba cek sekali lagi (mungkin race dengan thread lain) */
    if (stat(dir, &st) == 0 && S_ISDIR(st.st_mode)) return 0;
    return -1;
}
static int16_t *audio_ring;
static volatile unsigned audio_r, audio_w;
static retro_set_environment_t set_environment;
static retro_set_video_refresh_t set_video;
static retro_set_audio_sample_batch_t set_audio_batch;
static retro_set_input_poll_t set_input_poll;
static retro_set_input_state_t set_input_state;
static retro_api_version_t api_version;
static retro_init_t retro_init_fn;
static retro_deinit_t retro_deinit_fn;
static retro_load_game_t load_game_fn;
static retro_unload_game_t unload_game_fn;
static retro_run_t run_fn;
static retro_get_memory_data_t get_mem_data_fn;
static retro_get_memory_size_t get_mem_size_fn;
static void seterr(const char *s){snprintf(errbuf,sizeof(errbuf),"%s",s?s:"unknown");}
static void *getsym(const char *n){void *p=dlsym(core,n);if(!p){const char *e=dlerror();snprintf(errbuf,sizeof(errbuf),"%s: %s",n,e?e:"symbol not found");}return p;}
static void video_cb(const void *data,unsigned w,unsigned h,size_t pitch){if(!data||w!=W||h!=H)return;for(unsigned y=0;y<H;y++)memcpy(frame_buf+y*W*2,(const uint8_t*)data+y*pitch,W*2);}
static size_t audio_cb(const int16_t *data,size_t frames){if(!audio_ring)return frames;for(size_t i=0;i<frames;i++){unsigned n=(audio_w+1)%AUDIO_FRAMES;if(n==audio_r)break;audio_ring[audio_w*2]=data[i*2];audio_ring[audio_w*2+1]=data[i*2+1];__sync_synchronize();audio_w=n;}return frames;}
static void poll_cb(void){}
static int16_t state_cb(unsigned port,unsigned device,unsigned index,unsigned id){if(port||device!=RETRO_DEVICE_JOYPAD||index||id>11)return 0;return(buttons&(1u<<id))?1:0;}
static bool env_cb(unsigned cmd,void *data){switch(cmd){case RETRO_ENVIRONMENT_GET_CAN_DUPE:if(data)*(bool*)data=true;return true;case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:return data&&*(const int*)data==RETRO_PIXEL_FORMAT_RGB565;case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:if(data)*(const char**)data=system_dir;return true;case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:if(data)*(const char**)data=save_dir;return true;case RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY:if(data)*(const char**)data=system_dir;return true;case RETRO_ENVIRONMENT_GET_VARIABLE:if(data){struct retro_variable*v=(struct retro_variable*)data;if(v->key&&strcmp(v->key,"gpsp_sound_rate")==0){v->value="65536";return true;}}return false;case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:if(data)*(bool*)data=false;return true;case RETRO_ENVIRONMENT_GET_MESSAGE_INTERFACE_VERSION:if(data)*(unsigned*)data=0;return true;case RETRO_ENVIRONMENT_SET_MESSAGE:return true;default:return false;}}
static int resolve(void){core=dlopen("libgpsp_libretro_android.so",RTLD_NOW|RTLD_GLOBAL);if(!core){seterr(dlerror());return -1;}
#define R(x,n) do{*(void**)&x=getsym(n);if(!x)return -2;}while(0)
R(set_environment,"retro_set_environment");R(set_video,"retro_set_video_refresh");R(set_audio_batch,"retro_set_audio_sample_batch");R(set_input_poll,"retro_set_input_poll");R(set_input_state,"retro_set_input_state");R(api_version,"retro_api_version");R(retro_init_fn,"retro_init");R(retro_deinit_fn,"retro_deinit");R(load_game_fn,"retro_load_game");R(unload_game_fn,"retro_unload_game");R(run_fn,"retro_run");
get_mem_data_fn = dlsym(core, "retro_get_memory_data");
/* Save state symbols (opsional, tidak wajib ada di core) */
g_serialize_size_fn = (retro_serialize_size_t)  dlsym(core, "retro_serialize_size");
g_serialize_fn      = (retro_serialize_t)       dlsym(core, "retro_serialize");
g_unserialize_fn    = (retro_unserialize_t)     dlsym(core, "retro_unserialize");
get_mem_size_fn = dlsym(core, "retro_get_memory_size");
#undef R
return 0;}
JNIEXPORT jstring JNICALL Java_com_example_gpsp_NativeBridge_getLastError(JNIEnv*e,jclass c){(void)c;return(*e)->NewStringUTF(e,errbuf);}
JNIEXPORT jint JNICALL Java_com_example_gpsp_NativeBridge_init(JNIEnv*e,jclass c,jstring base){(void)c;const char*b=(*e)->GetStringUTFChars(e,base,0);if(!b)return-1;snprintf(system_dir,sizeof(system_dir),"%s/system",b);snprintf(save_dir,sizeof(save_dir),"%s/save",b);(*e)->ReleaseStringUTFChars(e,base,b);if(resolve())return-2;char cmd[2200];snprintf(cmd,sizeof(cmd),"mkdir -p '%s' '%s'",system_dir,save_dir);system(cmd);audio_ring=(int16_t*)calloc(AUDIO_FRAMES*2,sizeof(int16_t));if(!audio_ring){seterr("audio allocation failed");return-3;}set_environment(env_cb);set_video(video_cb);set_audio_batch(audio_cb);set_input_poll(poll_cb);set_input_state(state_cb);if(api_version&&api_version()!=1){seterr("libretro API version != 1");return-4;}retro_init_fn();return 0;}
JNIEXPORT jint JNICALL Java_com_example_gpsp_NativeBridge_loadGame(JNIEnv*e,jclass c,jstring jp){(void)c;if(!load_game_fn){seterr("core not initialized");return-1;}const char*p=(*e)->GetStringUTFChars(e,jp,0);if(!p)return-2;struct retro_game_info g;memset(&g,0,sizeof(g));g.path=p;bool ok=load_game_fn(&g);(*e)->ReleaseStringUTFChars(e,jp,p);if(!ok){seterr("retro_load_game returned false");return-3;}loaded=1;return 0;}
JNIEXPORT void JNICALL Java_com_example_gpsp_NativeBridge_unloadGame(JNIEnv*e,jclass c){(void)e;(void)c;if(loaded&&unload_game_fn)unload_game_fn();loaded=0;}
JNIEXPORT void JNICALL Java_com_example_gpsp_NativeBridge_runFrame(JNIEnv*e,jclass c){(void)e;(void)c;if(loaded&&run_fn)run_fn();}
JNIEXPORT jobject JNICALL Java_com_example_gpsp_NativeBridge_getVideoBuffer(JNIEnv*e,jclass c){(void)c;return(*e)->NewDirectByteBuffer(e,frame_buf,FRAME_BYTES);}
JNIEXPORT jint JNICALL Java_com_example_gpsp_NativeBridge_getVideoWidth(JNIEnv*e,jclass c){(void)e;(void)c;return W;}
JNIEXPORT jint JNICALL Java_com_example_gpsp_NativeBridge_getVideoHeight(JNIEnv*e,jclass c){(void)e;(void)c;return H;}
JNIEXPORT jint JNICALL Java_com_example_gpsp_NativeBridge_getSampleRate(JNIEnv*e,jclass c){(void)e;(void)c;return 65536;}
JNIEXPORT jint JNICALL Java_com_example_gpsp_NativeBridge_readAudio(JNIEnv*e,jclass c,jobject out,jint max){(void)c;if(!out||max<=0||!audio_ring)return 0;int16_t*d=(int16_t*)(*e)->GetDirectBufferAddress(e,out);if(!d)return 0;unsigned n=0;while(n<(unsigned)max&&audio_r!=audio_w){d[n*2]=audio_ring[audio_r*2];d[n*2+1]=audio_ring[audio_r*2+1];__sync_synchronize();audio_r=(audio_r+1)%AUDIO_FRAMES;n++;}return(jint)n;}
JNIEXPORT void JNICALL Java_com_example_gpsp_NativeBridge_setButton(JNIEnv*e,jclass c,jint id,jboolean down){(void)e;(void)c;if(id<0||id>11)return;uint16_t m=(uint16_t)(1u<<id);if(down)buttons|=m;else buttons&=(uint16_t)~m;}
JNIEXPORT jboolean JNICALL Java_com_example_gpsp_NativeBridge_isCoreLoaded(JNIEnv*e,jclass c){(void)e;(void)c;return loaded?JNI_TRUE:JNI_FALSE;}

JNIEXPORT jint JNICALL Java_com_example_gpsp_NativeBridge_saveSram(
    JNIEnv *e, jclass c, jstring jpath)
{
    (void)c;
    if (!get_mem_data_fn || !get_mem_size_fn) return -1;

    /* Cegah save concurrent */
    if (__sync_lock_test_and_set(&g_saving, 1)) return -10;

    int rc = 0;
    const char *path = NULL;
    char tmp[1100];
    char parent[1100];
    FILE *fp = NULL;
    size_t sz = 0;

    sz = get_mem_size_fn(RETRO_MEMORY_SAVE_RAM);
    if (sz == 0) { rc = 0; goto done; }

    void *data = get_mem_data_fn(RETRO_MEMORY_SAVE_RAM);
    if (!data) { rc = -2; goto done; }

    path = (*e)->GetStringUTFChars(e, jpath, 0);
    if (!path) { rc = -2; goto done; }

    /* Cek panjang path (buffer tmp = 1100, butuh +4 untuk ".tmp") */
    size_t plen = strlen(path);
    if (plen < 1 || plen + 5 >= sizeof(tmp)) { rc = -7; goto done; }

    /* Siapkan folder induk */
    if (ensure_parent_dir(path) != 0) { rc = -8; goto done; }

    /* Simpan parent untuk fsync nanti */
    memcpy(parent, path, plen + 1);
    char *pslash = strrchr(parent, '/');
    if (pslash) *pslash = '\0'; else parent[0] = '\0';

    /* Tulis ke file sementara */
    snprintf(tmp, sizeof(tmp), "%s.tmp", path);
    fp = fopen(tmp, "wb");
    if (!fp) { rc = -3; goto done; }

    size_t written = fwrite(data, 1, sz, fp);
    if (written != sz) {
        rc = -4;  /* write tidak lengkap */
        goto done;
    }

    /* fsync: pastikan data fisik tertulis */
    int fd = fileno(fp);
    if (fd >= 0) fsync(fd);

    if (fclose(fp) != 0) {
        fp = NULL;
        rc = -5;
        goto done;
    }
    fp = NULL;

    /* Rename atomic */
    if (rename(tmp, path) != 0) {
        unlink(tmp);
        rc = -6;
        goto done;
    }

    /* fsync folder supaya entry rename tercatat */
    if (parent[0]) {
        int dfd = open(parent, O_RDONLY);
        if (dfd >= 0) { fsync(dfd); close(dfd); }
    }

done:
    if (fp) { fclose(fp); unlink(tmp); }
    if (path) (*e)->ReleaseStringUTFChars(e, jpath, path);
    __sync_lock_release(&g_saving);
    return rc;
}

JNIEXPORT jint JNICALL Java_com_example_gpsp_NativeBridge_loadSram(
    JNIEnv *e, jclass c, jstring jpath)
{
    (void)c;
    if (!get_mem_data_fn || !get_mem_size_fn) return -1;
    size_t sz = get_mem_size_fn(RETRO_MEMORY_SAVE_RAM);
    if (sz == 0) return 0;
    void *data = get_mem_data_fn(RETRO_MEMORY_SAVE_RAM);
    if (!data) return -2;

    const char *path = (*e)->GetStringUTFChars(e, jpath, 0);
    if (!path) return -2;

    FILE *fp = fopen(path, "rb");
    if (!fp) {
        (*e)->ReleaseStringUTFChars(e, jpath, path);
        return 1;  /* file tidak ada, bukan error */
    }

    size_t got = fread(data, 1, sz, fp);
    fclose(fp);
    (*e)->ReleaseStringUTFChars(e, jpath, path);

    if (got != sz) {
        /* File lebih kecil / korup: kosongkan SRAM agar tidak dipakai setengah */
        memset(data, 0, sz);
        return 2;
    }
    return 0;
}

/* ============ SAVE STATE ============ */

#define STATE_MAGIC  0x50537067   /* "gpSP" little-endian */
#define STATE_VERSION 1

JNIEXPORT jint JNICALL Java_com_example_gpsp_NativeBridge_saveState(
    JNIEnv *e, jclass c, jstring jpath)
{
    (void)c;
    if (!g_serialize_size_fn || !g_serialize_fn) return -20;  /* core tidak support */
    if (__sync_lock_test_and_set(&g_saving, 1)) return -10;

    int rc = 0;
    const char *path = NULL;
    char tmp[1100];
    char parent[1100];
    FILE *fp = NULL;
    void *buf = NULL;
    size_t sz = 0;

    sz = g_serialize_size_fn();
    if (sz == 0) { rc = -21; goto done; }

    buf = malloc(sz);
    if (!buf) { rc = -22; goto done; }

    if (!g_serialize_fn(buf, sz)) { rc = -23; goto done; }

    path = (*e)->GetStringUTFChars(e, jpath, 0);
    if (!path) { rc = -2; goto done; }

    size_t plen = strlen(path);
    if (plen < 1 || plen + 5 >= sizeof(tmp)) { rc = -7; goto done; }

    if (ensure_parent_dir(path) != 0) { rc = -8; goto done; }

    memcpy(parent, path, plen + 1);
    char *pslash = strrchr(parent, '/');
    if (pslash) *pslash = '\0'; else parent[0] = '\0';

    snprintf(tmp, sizeof(tmp), "%s.tmp", path);
    fp = fopen(tmp, "wb");
    if (!fp) { rc = -3; goto done; }

    /* Header: magic (4B) + version (4B) + size (4B, LE) */
    uint32_t magic   = STATE_MAGIC;
    uint32_t version = STATE_VERSION;
    uint32_t size32  = (uint32_t)sz;
    if (fwrite(&magic,   4, 1, fp) != 1) { rc = -4; goto done; }
    if (fwrite(&version, 4, 1, fp) != 1) { rc = -4; goto done; }
    if (fwrite(&size32,  4, 1, fp) != 1) { rc = -4; goto done; }

    if (fwrite(buf, 1, sz, fp) != sz) { rc = -4; goto done; }

    int fd = fileno(fp);
    if (fd >= 0) fsync(fd);

    if (fclose(fp) != 0) { fp = NULL; rc = -5; goto done; }
    fp = NULL;

    if (rename(tmp, path) != 0) { unlink(tmp); rc = -6; goto done; }

    if (parent[0]) {
        int dfd = open(parent, O_RDONLY);
        if (dfd >= 0) { fsync(dfd); close(dfd); }
    }

done:
    if (fp) { fclose(fp); unlink(tmp); }
    if (buf) free(buf);
    if (path) (*e)->ReleaseStringUTFChars(e, jpath, path);
    __sync_lock_release(&g_saving);
    return rc;
}

JNIEXPORT jint JNICALL Java_com_example_gpsp_NativeBridge_loadState(
    JNIEnv *e, jclass c, jstring jpath)
{
    (void)c;
    if (!g_unserialize_fn || !g_serialize_size_fn) return -20;

    const char *path = (*e)->GetStringUTFChars(e, jpath, 0);
    if (!path) return -2;

    FILE *fp = fopen(path, "rb");
    if (!fp) {
        (*e)->ReleaseStringUTFChars(e, jpath, path);
        return 1;  /* file tidak ada */
    }

    /* Baca header */
    uint32_t magic = 0, version = 0, size32 = 0;
    if (fread(&magic,   4, 1, fp) != 1 ||
        fread(&version, 4, 1, fp) != 1 ||
        fread(&size32,  4, 1, fp) != 1) {
        fclose(fp);
        (*e)->ReleaseStringUTFChars(e, jpath, path);
        return -24;  /* header korup */
    }

    if (magic != STATE_MAGIC || version != STATE_VERSION) {
        fclose(fp);
        (*e)->ReleaseStringUTFChars(e, jpath, path);
        return -25;  /* bukan file state valid */
    }

    size_t expect = g_serialize_size_fn();
    if (expect != size32) {
        fclose(fp);
        (*e)->ReleaseStringUTFChars(e, jpath, path);
        return -26;  /* ukuran tidak cocok (beda versi ROM/core) */
    }

    void *buf = malloc(expect);
    if (!buf) {
        fclose(fp);
        (*e)->ReleaseStringUTFChars(e, jpath, path);
        return -22;
    }

    size_t got = fread(buf, 1, expect, fp);
    fclose(fp);
    (*e)->ReleaseStringUTFChars(e, jpath, path);

    if (got != expect) {
        free(buf);
        return -27;  /* data tidak lengkap */
    }

    bool ok = g_unserialize_fn(buf, expect);
    free(buf);
    return ok ? 0 : -28;
}

jint JNI_OnLoad(JavaVM*vm,void*r){(void)vm;(void)r;return JNI_VERSION_1_4;}
