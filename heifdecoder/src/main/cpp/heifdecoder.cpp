//
// Created by chenxin on 2021/12/28.
//
#include <cstring>
#include <android/bitmap.h>
#include <libheif/heif_cxx.h>
#include "heifdecoder.h"
#include "rect.h"
#include "row_convert.h"

#define RETURN_NULL_IF_EXCEPTION(env) \
  if ((env)->ExceptionOccurred()) {\
    return {};\
  }

static jclass avifBitmapFactoryClass;
static jclass runtimeExceptionClass;
static jmethodID createBitmapMethod;
static jmethodID setOutDimensionsMethod;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if ((*vm).GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR; // JNI version not supported.
    }
    jclass clazz;
    clazz = (*env).FindClass(
            "com/bumptech/glide/integration/heif/HeifBitmapFactory");
    avifBitmapFactoryClass = (jclass) env->NewGlobalRef(clazz);
    if (!avifBitmapFactoryClass) {
        return JNI_ERR;
    }

    createBitmapMethod = env->GetStaticMethodID(avifBitmapFactoryClass, "createBitmap",
                                                "(IILandroid/graphics/BitmapFactory$Options;)Landroid/graphics/Bitmap;");
    if (!createBitmapMethod) {
        return JNI_ERR;
    }
    setOutDimensionsMethod = env->GetStaticMethodID(avifBitmapFactoryClass, "setOutDimensions",
                                                    "(Landroid/graphics/BitmapFactory$Options;II)Z");
    if (!setOutDimensionsMethod) {
        return JNI_ERR;
    }
    env->DeleteLocalRef(clazz);
    return JNI_VERSION_1_6;
}

jboolean setOutDimensions(JNIEnv *env, jobject bitmapOptions, int image_width, int image_height) {
    jboolean hadDecodeBounds = env->CallStaticBooleanMethod(avifBitmapFactoryClass,
                                                            setOutDimensionsMethod, bitmapOptions,
                                                            image_width, image_height);
    return hadDecodeBounds;
}


jobject createBitmap(JNIEnv *env, jint image_width, jint image_height, jobject bitmapOptions) {
    jobject bitmap = env->CallStaticObjectMethod(avifBitmapFactoryClass, createBitmapMethod,
                                                 image_width, image_height, bitmapOptions);
    return bitmap;
}

Rect parseWH(uint8_t *encoded_image, uint32_t encoded_image_length) {
    auto ctx = heif::Context();
    ctx.read_from_memory_without_copy(encoded_image, encoded_image_length);
    auto handle = ctx.get_primary_image_handle();
    uint32_t imageWidth = handle.get_width();
    uint32_t imageHeight = handle.get_height();
    Rect bounds = {.x = 0, .y = 0, .width = imageWidth, .height = imageHeight};
    return bounds;
}

void
decode(uint8_t *encoded_image, uint32_t encoded_image_length, uint8_t *outPixels, Rect imageRect,
       Rect bitmapRect, bool rgb565,
       uint32_t sampleSize) {
    heif::Image img;
    uint32_t imageWidth;
    try {
        auto ctx = heif::Context();
        ctx.read_from_memory_without_copy(encoded_image, encoded_image_length);
        auto handle = ctx.get_primary_image_handle();
        auto chroma = rgb565 ? heif_chroma_interleaved_RGB : heif_chroma_interleaved_RGBA;
        imageWidth = handle.get_width();
        img = handle.decode_image(heif_colorspace_RGB, chroma);
    } catch (heif::Error &error) {
        throw std::runtime_error(error.get_message());
    }

    int stride;
    const uint8_t *inPixels = img.get_plane(heif_channel_interleaved, &stride);

// Calculate stride of the decoded image with the requested region
    uint32_t inStride = stride;
    uint32_t inStrideOffset = imageRect.x * (stride / imageWidth);
    auto inPixelsPos = (uint8_t *) inPixels + inStride * imageRect.y;

// Calculate output stride
    uint32_t outStride = bitmapRect.width * (rgb565 ? 2 : 4);
    uint8_t *outPixelsPos = outPixels;

// Set row conversion function
    auto rowFn = rgb565 ? &RGB888_to_RGB565_row : &RGBA8888_to_RGBA8888_row;

    if (sampleSize == 1) {
        for (uint32_t i = 0; i < bitmapRect.height; i++) {
// Apply row conversion function to the following row
            rowFn(outPixelsPos, inPixelsPos + inStrideOffset, nullptr, bitmapRect.width, 1);

// Shift row to read and write
            inPixelsPos += inStride;
            outPixelsPos += outStride;
        }
    } else {
// Calculate the number of rows to discard
        uint32_t skipStart = (sampleSize - 2) / 2;
        uint32_t skipEnd = sampleSize - 2 - skipStart;

        for (uint32_t i = 0; i < bitmapRect.height; ++i) {
// Skip starting rows
            inPixelsPos += inStride * skipStart;

// Apply row conversion function to the following two rows
            rowFn(outPixelsPos, inPixelsPos + inStrideOffset,
                  inPixelsPos + inStride + inStrideOffset, bitmapRect.width, sampleSize);

// Shift row to read to the next 2 rows (the ones we've just read) + the skipped end rows
            inPixelsPos += inStride * (2 + skipEnd);

// Shift row to write
            outPixelsPos += outStride;
        }
    }
}


jobject doDecode(
        JNIEnv *env,
        uint8_t *encoded_image,
        unsigned int encoded_image_length,
        jobject bitmapOptions,
        jint sampleSize) {

    auto imageRect = parseWH(encoded_image, encoded_image_length);

    if ((bitmapOptions != nullptr) &&
        (setOutDimensions(env, bitmapOptions, imageRect.width, imageRect.height))) {
        return {};
    }
    if (sampleSize < 1) {
        sampleSize = 1;
    }
    uint32_t bitmapWidth = imageRect.width / sampleSize;
    uint32_t bitmapHeight = imageRect.height / sampleSize;
    Rect bitmapRect = {0, 0, bitmapWidth, bitmapHeight};
    auto *bitmap = createBitmap(env, bitmapWidth, bitmapHeight, bitmapOptions);
    AndroidBitmapInfo bitmapInfo;
    int rc = AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);
    if (rc != ANDROID_BITMAP_RESULT_SUCCESS) {
        env->ThrowNew(runtimeExceptionClass, "Decode error get bitmap info");
        return JNI_FALSE;
    }

    uint8_t *raw_pixels;
    rc = AndroidBitmap_lockPixels(env, bitmap, (void **) &raw_pixels);
    if (rc != ANDROID_BITMAP_RESULT_SUCCESS) {
        env->ThrowNew(runtimeExceptionClass, "Decode error locking pixels");
        return JNI_FALSE;
    }
    decode(encoded_image, encoded_image_length, raw_pixels, imageRect, bitmapRect, false,
           sampleSize);

    rc = AndroidBitmap_unlockPixels(env, bitmap);
    if (rc != ANDROID_BITMAP_RESULT_SUCCESS) {
        env->ThrowNew(runtimeExceptionClass, "Decode error unlocking pixels");
        return {};
    }

    if (bitmapOptions != nullptr) {
        setOutDimensions(env, bitmapOptions, bitmapRect.width, bitmapRect.height);
    }

    return bitmap;
}


std::vector<uint8_t> readStreamFully(JNIEnv *env, jobject is, jbyteArray inTempStorage) {
    // read start
    std::vector<uint8_t> read_buffer;

    jclass inputStreamJClass = env->FindClass("java/io/InputStream");
    jmethodID readMethodId = env->GetMethodID(inputStreamJClass, "read", "([B)I");

    while (true) {

        const int chunk_size = env->CallIntMethod(is, readMethodId, inTempStorage);

        if (chunk_size < 0) {
            return read_buffer;
        }

        if (chunk_size > 0) {
            jbyte *data = env->GetByteArrayElements(inTempStorage, nullptr);
            RETURN_NULL_IF_EXCEPTION(env);
            read_buffer.insert(read_buffer.end(), data, data + chunk_size);
            env->ReleaseByteArrayElements(inTempStorage, data, JNI_ABORT);
            RETURN_NULL_IF_EXCEPTION(env);
        }
    }
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_bumptech_glide_integration_heif_HeifBitmapFactory_nativeDecodeStream(JNIEnv *env,
                                                                              jclass clazz,
                                                                              jobject is,
                                                                              jobject opts,
                                                                              jint sampleSize,
                                                                              jbyteArray inTempStorage) {

    auto encoded_image = readStreamFully(env, is, inTempStorage);
    if (!encoded_image.empty()) {
        return doDecode(env, encoded_image.data(), encoded_image.size(), opts, sampleSize);
    }
    return {};


}
extern "C"
JNIEXPORT jobject JNICALL
Java_com_bumptech_glide_integration_heif_HeifBitmapFactory_nativeDecodeByteArray(JNIEnv *env,
                                                                                 jclass clazz,
                                                                                 jbyteArray array,
                                                                                 jint offset,
                                                                                 jint length,
                                                                                 jobject opts,
                                                                                 jint sampleSize,
                                                                                 jbyteArray inTempStorage) {
    // get image into decoded heap
    jbyte *data = env->GetByteArrayElements(array, nullptr);
    if (env->ExceptionCheck() == JNI_TRUE) {
        env->ReleaseByteArrayElements(inTempStorage, data, JNI_ABORT);
        RETURN_NULL_IF_EXCEPTION(env);
    }
    if (data == nullptr || offset + length > env->GetArrayLength(array)) {
        env->ReleaseByteArrayElements(array, data, JNI_ABORT);
        RETURN_NULL_IF_EXCEPTION(env);
    }
    jobject bitmap = doDecode(env, reinterpret_cast<uint8_t *>(data) + offset, length, opts,
                              sampleSize);
    env->ReleaseByteArrayElements(array, data, JNI_ABORT);
    RETURN_NULL_IF_EXCEPTION(env);

    return bitmap;

}