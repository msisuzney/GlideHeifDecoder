package com.bumptech.glide.integration.heif;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool;

import java.io.IOException;
import java.io.InputStream;
/**
 * author: msisuzney
 * date: 2021/12/30
 */
public class StreamBitmapHeifDecoder implements ResourceDecoder<InputStream, Bitmap> {
    private final HeifDownsampler downsampler;
    private final ArrayPool byteArrayPool;

    public StreamBitmapHeifDecoder(HeifDownsampler downsampler, ArrayPool byteArrayPool) {
        this.downsampler = downsampler;
        this.byteArrayPool = byteArrayPool;
    }

    @Override
    public boolean handles(@NonNull InputStream source, @NonNull Options options) throws IOException {
        HeifImageHeaderParser.HeifImageType imageType = HeifImageHeaderParser.getType(source, byteArrayPool);
        return imageType == HeifImageHeaderParser.HeifImageType.AVIF || imageType == HeifImageHeaderParser.HeifImageType.HEIF;
    }

    @Override
    public Resource<Bitmap> decode(@NonNull InputStream source, int width, int height, @NonNull Options options) throws IOException {
        return downsampler.decode(source, width, height, options);
    }
}

