#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <vector>
#include <cstring>
#include "image_processor.h"

#define LOG_TAG "AlphaVisionNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

static size_t g_buffer_pool_bytes = 64 * 1024 * 1024; // default 64 MB
static size_t g_current_usage     = 0;

// ─── Helper: lock bitmap, run lambda, unlock ─────────────────────────────────
template<typename Fn>
static jboolean with_bitmap_info(JNIEnv* env, jobject bmp, Fn fn) {
    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bmp, &info) < 0) {
        LOGE("AndroidBitmap_getInfo failed");
        return JNI_FALSE;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Unsupported bitmap format: %d (expected RGBA_8888)", info.format);
        return JNI_FALSE;
    }
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bmp, &pixels) < 0) {
        LOGE("AndroidBitmap_lockPixels failed");
        return JNI_FALSE;
    }
    fn(info, static_cast<uint8_t*>(pixels));
    AndroidBitmap_unlockPixels(env, bmp);
    return JNI_TRUE;
}

// ─── JNI: processImage ───────────────────────────────────────────────────────
extern "C"
JNIEXPORT jobject JNICALL
Java_com_alpha_vision_pro_gallery_data_nativelib_NativeImageProcessor_processImage(
    JNIEnv* env, jobject /*thiz*/,
    jobject srcBitmap,
    jfloat exposure, jfloat contrast, jfloat saturation,
    jfloat warmth, jfloat highlights, jfloat shadows, jfloat sharpness)
{
    // 1. Get source bitmap info
    AndroidBitmapInfo srcInfo{};
    if (AndroidBitmap_getInfo(env, srcBitmap, &srcInfo) < 0) {
        LOGE("Cannot get src bitmap info");
        return nullptr;
    }

    // 2. Create output Bitmap via Android Bitmap.createBitmap(w, h, ARGB_8888)
    jclass bmpClass   = env->FindClass("android/graphics/Bitmap");
    jclass configClass= env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argbFid  = env->GetStaticFieldID(configClass, "ARGB_8888",
                                               "Landroid/graphics/Bitmap$Config;");
    jobject argbConfig= env->GetStaticObjectField(configClass, argbFid);
    jmethodID createMid = env->GetStaticMethodID(bmpClass, "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject dstBitmap = env->CallStaticObjectMethod(bmpClass, createMid,
        (jint)srcInfo.width, (jint)srcInfo.height, argbConfig);
    if (!dstBitmap) {
        LOGE("Failed to create dst bitmap");
        return nullptr;
    }

    // 3. Lock src & dst, process, unlock
    void* srcPixels = nullptr;
    void* dstPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, srcBitmap, &srcPixels) < 0 ||
        AndroidBitmap_lockPixels(env, dstBitmap, &dstPixels) < 0) {
        LOGE("Pixel lock failed");
        return nullptr;
    }

    EditParams params = { exposure, contrast, saturation, warmth, highlights, shadows, sharpness };
    g_current_usage = srcInfo.width * srcInfo.height * 4 * 2; // src + dst

    alpha_process_image(
        static_cast<const uint8_t*>(srcPixels),
        static_cast<uint8_t*>(dstPixels),
        static_cast<int>(srcInfo.width),
        static_cast<int>(srcInfo.height),
        &params
    );

    AndroidBitmap_unlockPixels(env, srcBitmap);
    AndroidBitmap_unlockPixels(env, dstBitmap);

    LOGI("processImage done: %dx%d, buffer=%.1f MB",
         srcInfo.width, srcInfo.height,
         g_current_usage / (1024.0 * 1024.0));

    return dstBitmap;
}

// ─── JNI: stripExifNative ────────────────────────────────────────────────────
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_alpha_vision_pro_gallery_data_nativelib_NativeImageProcessor_stripExifNative(
    JNIEnv* env, jobject /*thiz*/, jbyteArray jpegBytes)
{
    // For full EXIF strip, we zero-out all APP1 (0xFFE1) markers in JPEG stream.
    jsize len = env->GetArrayLength(jpegBytes);
    std::vector<uint8_t> buf(len);
    env->GetByteArrayRegion(jpegBytes, 0, len,
                             reinterpret_cast<jbyte*>(buf.data()));

    int i = 0;
    while (i < len - 3) {
        if (buf[i] == 0xFF && buf[i+1] == 0xE1) {
            // APP1 marker: next 2 bytes = length (big-endian, includes itself)
            int seg_len = (buf[i+2] << 8) | buf[i+3];
            // Zero out the segment (replace with 0xFF 0xFE = COM marker with 0 len)
            buf[i+1] = 0xFE;
            buf[i+2] = 0x00; buf[i+3] = 0x02;
            // Blank the payload
            if (i + 4 + seg_len - 2 <= len)
                std::memset(&buf[i+4], 0, seg_len - 2);
            i += 2 + seg_len;
        } else {
            ++i;
        }
    }

    jbyteArray result = env->NewByteArray(len);
    env->SetByteArrayRegion(result, 0, len,
                             reinterpret_cast<const jbyte*>(buf.data()));
    return result;
}

// ─── JNI: getNativeMemoryUsage ───────────────────────────────────────────────
extern "C"
JNIEXPORT jlong JNICALL
Java_com_alpha_vision_pro_gallery_data_nativelib_NativeImageProcessor_getNativeMemoryUsage(
    JNIEnv* /*env*/, jobject /*thiz*/)
{
    return static_cast<jlong>(g_current_usage);
}

// ─── JNI: setBufferPoolSizeMb ────────────────────────────────────────────────
extern "C"
JNIEXPORT void JNICALL
Java_com_alpha_vision_pro_gallery_data_nativelib_NativeImageProcessor_setBufferPoolSizeMb(
    JNIEnv* /*env*/, jobject /*thiz*/, jint sizeMb)
{
    g_buffer_pool_bytes = static_cast<size_t>(sizeMb) * 1024 * 1024;
    LOGI("Buffer pool set to %d MB", sizeMb);
}
