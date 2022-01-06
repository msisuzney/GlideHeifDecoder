package com.bumptech.glide.integration.heif;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapDrawableDecoder;
import com.bumptech.glide.module.LibraryGlideModule;

import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * author: msisuzney
 * date: 2021/12/30
 */
@GlideModule
public class HeifLibraryGlideModule extends LibraryGlideModule {

    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        final Resources resources = context.getResources();
        final BitmapPool bitmapPool = glide.getBitmapPool();
        final ArrayPool arrayPool = glide.getArrayPool();
        HeifDownsampler downsampler = new HeifDownsampler(resources.getDisplayMetrics(), bitmapPool, arrayPool);
        StreamBitmapHeifDecoder streamBitmapDecoder = new StreamBitmapHeifDecoder(downsampler, arrayPool);
        ByteBufferHeifBitmapDecoder byteBufferBitmapDecoder = new ByteBufferHeifBitmapDecoder(downsampler, bitmapPool);
        registry
                /* Bitmaps for still heif images */
                .prepend(Registry.BUCKET_BITMAP, ByteBuffer.class, Bitmap.class, byteBufferBitmapDecoder)
                .prepend(Registry.BUCKET_BITMAP, InputStream.class, Bitmap.class, streamBitmapDecoder)
                /* BitmapDrawables for still heif images */
                .prepend(
                        Registry.BUCKET_BITMAP_DRAWABLE,
                        ByteBuffer.class,
                        BitmapDrawable.class,
                        new BitmapDrawableDecoder<>(resources, byteBufferBitmapDecoder))
                .prepend(
                        Registry.BUCKET_BITMAP_DRAWABLE,
                        InputStream.class,
                        BitmapDrawable.class,
                        new BitmapDrawableDecoder<>(resources, streamBitmapDecoder));
    }
}
