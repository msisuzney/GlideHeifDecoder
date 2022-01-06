package com.bumptech.glide.integration.heif;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.bumptech.glide.load.ImageHeaderParser;
import com.bumptech.glide.load.data.DataRewinder;
import com.bumptech.glide.load.data.InputStreamRewinder;
import com.bumptech.glide.load.data.ParcelFileDescriptorRewinder;
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool;
import com.bumptech.glide.load.resource.bitmap.Downsampler;
import com.bumptech.glide.util.Preconditions;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This is a helper class for {@link Downsampler} that abstracts out image operations from the input
 * type wrapped into a {@link DataRewinder}.
 */
interface ImageReader {
    @Nullable
    Bitmap decodeBitmap(BitmapFactory.Options options) throws IOException;

    HeifImageHeaderParser.HeifImageType getImageType() throws IOException;

    int getImageOrientation() throws IOException;

    void stopGrowingBuffers();

    final class InputStreamImageReader implements ImageReader {
        private final InputStreamRewinder dataRewinder;
        private final ArrayPool byteArrayPool;

        InputStreamImageReader(
                InputStream is,ArrayPool byteArrayPool) {
            this.byteArrayPool = Preconditions.checkNotNull(byteArrayPool);
            dataRewinder = new InputStreamRewinder(is, byteArrayPool);
        }

        @Nullable
        @Override
        public Bitmap decodeBitmap(BitmapFactory.Options options) throws IOException {
            return HeifBitmapFactory.decodeStream(dataRewinder.rewindAndGet(), null, options);
        }

        @Override
        public HeifImageHeaderParser.HeifImageType getImageType() throws IOException {
            return HeifImageHeaderParser.getType(dataRewinder.rewindAndGet(), byteArrayPool);
        }

        @Override
        public int getImageOrientation() throws IOException {
            return ImageHeaderParser.UNKNOWN_ORIENTATION;
        }

        @Override
        public void stopGrowingBuffers() {
            dataRewinder.fixMarkLimits();
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    final class ParcelFileDescriptorImageReader implements ImageReader {
        private final ArrayPool byteArrayPool;
        private final ParcelFileDescriptorRewinder dataRewinder;

        ParcelFileDescriptorImageReader(
                ParcelFileDescriptor parcelFileDescriptor,
                ArrayPool byteArrayPool) {
            this.byteArrayPool = Preconditions.checkNotNull(byteArrayPool);
            dataRewinder = new ParcelFileDescriptorRewinder(parcelFileDescriptor);
        }

        @Nullable
        @Override
        public Bitmap decodeBitmap(BitmapFactory.Options options) throws IOException {
            return HeifBitmapFactory.decodeFileDescriptor(
                    dataRewinder.rewindAndGet().getFileDescriptor(), null, options);
        }

        @Override
        public HeifImageHeaderParser.HeifImageType getImageType() throws IOException {
            InputStream is = null;
            try {
                is = new FileInputStream(dataRewinder.rewindAndGet().getFileDescriptor());
                return HeifImageHeaderParser.getType(is, byteArrayPool);
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                } catch (IOException e) {
                    // Ignored.
                }
                dataRewinder.rewindAndGet();
            }
        }

        @Override
        public int getImageOrientation() throws IOException {
            return ImageHeaderParser.UNKNOWN_ORIENTATION;
        }

        @Override
        public void stopGrowingBuffers() {
            // Nothing to do here.
        }
    }
}
