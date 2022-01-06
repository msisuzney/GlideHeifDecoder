package com.bumptech.glide.integration.heif;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.util.ByteBufferUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * author: msisuzney
 * date: 2021/12/30
 */
public class ByteBufferHeifBitmapDecoder implements ResourceDecoder<ByteBuffer, Bitmap> {
    private final HeifDownsampler downsampler;
    private final BitmapPool bitmapPool;

    public ByteBufferHeifBitmapDecoder(HeifDownsampler downsampler, BitmapPool bitmapPool) {
        this.downsampler = downsampler;
        this.bitmapPool = bitmapPool;
    }

    @Override
    public boolean handles(@NonNull ByteBuffer source, @NonNull Options options) throws IOException {
        HeifImageHeaderParser.HeifImageType imageType = HeifImageHeaderParser.getType(source);
        return imageType == HeifImageHeaderParser.HeifImageType.AVIF || imageType == HeifImageHeaderParser.HeifImageType.HEIF;
    }


    @Override
    public Resource<Bitmap> decode(@NonNull ByteBuffer source, int width, int height, @NonNull Options options) throws IOException {
        InputStream is = ByteBufferUtil.toStream(source);
        return downsampler.decode(is, width, height, options);
    }
}
