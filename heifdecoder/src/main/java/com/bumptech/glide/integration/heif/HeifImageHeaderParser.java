package com.bumptech.glide.integration.heif;

import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool;
import com.bumptech.glide.load.resource.bitmap.RecyclableBufferedInputStream;
import com.bumptech.glide.util.Preconditions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * author: msisuzney
 * date: 2021/12/30
 */
public class HeifImageHeaderParser {
    private static final int MARK_READ_LIMIT = 5 * 1024 * 1024;
    // "ftyp"
    private static final int FTYP_HEADER = 0x66747970;
    // "avif"
    private static final int AVIF_BRAND = 0x61766966;
    // "avis"
    private static final int AVIS_BRAND = 0x61766973;
    // "heic"
    private static final int HEIC_BRAND = 0x68656963;
    // "mif1"
    private static final int MIF1_BRAND = 0x6d696631;

    static HeifImageType getType(InputStream is, ArrayPool byteArrayPool) throws IOException {
        if (!is.markSupported()) {
            is = new RecyclableBufferedInputStream(is, byteArrayPool);
        }
        is.mark(MARK_READ_LIMIT);
        try {
            return getType(new StreamReader(Preconditions.checkNotNull(is)));
        } finally {
            is.reset();
        }
    }

    static HeifImageType getType(ByteBuffer byteBuffer) throws IOException {
        if (byteBuffer == null) {
            return HeifImageType.NONE_HEIF;
        }
        ByteBufferReader byteBufferReader = new ByteBufferReader(Preconditions.checkNotNull(byteBuffer));
        return getType(byteBufferReader);
    }

    private static HeifImageType getType(Reader reader) throws IOException {
        final int firstFourBytes = reader.getUInt32();
        return sniffHeif(reader, firstFourBytes);
    }

    /*
     avif File Type Box:
     00000000: 0000 0020 6674 7970 6176 6966 0000 0000  ... ftypavif....
     00000010: 6176 6966 6d69 6631 6d69 6166 4d41 3141  avifmif1miafMA1A

       the first 4 bytes means the Box size，in the example,
       the Box size is 32 bytes including the first 4 bytes.
     */

    /*
     * heic File Type Box:
     * 00000000: 0000 0028 6674 7970 6865 6963 0000 0000  ...(ftypheic....
     * 00000010: 6d69 6631 4d69 4845 4d69 5072 6d69 6166  mif1MiHEMiPrmiaf
     * 00000020: 4d69 4842 6865 6963 0000 0e7d 6d65 7461  MiHBheic...}meta
     */

    /**
     * modify the code from https://github.com/bumptech/glide/blob/master/library/src/main/java/com/bumptech/glide/load/resource/bitmap/DefaultImageHeaderParser.java
     * <p>
     * Check if the bits look like an AVIF Image. AVIF Specification:
     * https://aomediacodec.github.io/av1-avif/
     *
     * @return true if the first few bytes looks like it could be an AVIF Image, false otherwise.
     */
    private static HeifImageType sniffHeif(Reader reader, int boxSize) throws IOException {
        int chunkType = (reader.getUInt16() << 16) | reader.getUInt16();
        if (chunkType != FTYP_HEADER) {
            return HeifImageType.NONE_HEIF;
        }
        // majorBrand.
        int brand = (reader.getUInt16() << 16) | reader.getUInt16();
        if (brand == AVIF_BRAND || brand == AVIS_BRAND) {
            return HeifImageType.AVIF;
        }
        if (brand == HEIC_BRAND || brand == MIF1_BRAND) {
            return HeifImageType.HEIF;
        }
        // Skip the minor version.
        reader.skip(4);
        // Check the first five minor brands. While there could theoretically be more than five minor
        // brands, it is rare in practice. This way we stop the loop from running several times on a
        // blob that just happened to look like an ftyp box.
        int sizeRemaining = boxSize - 16;
        if (sizeRemaining % 4 != 0) {
            return HeifImageType.NONE_HEIF;
        }
        for (int i = 0; i < 5 && sizeRemaining > 0; ++i, sizeRemaining -= 4) {
            brand = (reader.getUInt16() << 16) | reader.getUInt16();
            if (brand == AVIF_BRAND || brand == AVIS_BRAND) {
                return HeifImageType.AVIF;
            }
            if (brand == HEIC_BRAND || brand == MIF1_BRAND) {
                return HeifImageType.HEIF;
            }
        }
        return HeifImageType.NONE_HEIF;
    }

    public enum HeifImageType {
        HEIF,
        AVIF,
        NONE_HEIF
    }

    private interface Reader {
        /**
         * Reads and returns a 8-bit unsigned integer.
         *
         * <p>Throws an {@link Reader.EndOfFileException} if an EOF is reached.
         */
        short getUInt8() throws IOException;

        /**
         * Reads and returns a 16-bit unsigned integer.
         *
         * <p>Throws an {@link Reader.EndOfFileException} if an EOF is reached.
         */
        int getUInt16() throws IOException;

        /**
         * Reads and returns a 32-bit unsigned integer.
         *
         * <p>Throws an {@link Reader.EndOfFileException} if an EOF is reached.
         */
        int getUInt32() throws IOException;

        /**
         * Reads and returns a byte array.
         *
         * <p>Throws an {@link Reader.EndOfFileException} if an EOF is reached before anything was read.
         */
        int read(byte[] buffer, int byteCount) throws IOException;

        long skip(long total) throws IOException;

        // TODO(timurrrr): Stop inheriting from IOException, and make sure all attempts to read from
        //   a Reader correctly handle EOFs.
        final class EndOfFileException extends IOException {
            private static final long serialVersionUID = 1L;

            EndOfFileException() {
                super("Unexpectedly reached end of a file");
            }
        }
    }

    private static final class ByteBufferReader implements Reader {

        private final ByteBuffer byteBuffer;

        ByteBufferReader(ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
        }

        @Override
        public short getUInt8() throws EndOfFileException {
            if (byteBuffer.remaining() < 1) {
                throw new EndOfFileException();
            }
            return (short) (byteBuffer.get() & 0xFF);
        }

        @Override
        public int getUInt16() throws EndOfFileException {
            return ((int) getUInt8() << 8) | getUInt8();
        }

        @Override
        public int getUInt32() throws IOException {
            return getUInt16() << 16 | getUInt16();
        }

        @Override
        public int read(byte[] buffer, int byteCount) {
            int toRead = Math.min(byteCount, byteBuffer.remaining());
            if (toRead == 0) {
                return -1;
            }
            byteBuffer.get(buffer, 0 /*dstOffset*/, toRead);
            return toRead;
        }

        @Override
        public long skip(long total) {
            int toSkip = (int) Math.min(byteBuffer.remaining(), total);
            byteBuffer.position(byteBuffer.position() + toSkip);
            return toSkip;
        }
    }

    private static final class StreamReader implements Reader {
        private final InputStream is;

        // Motorola / big endian byte order.
        StreamReader(InputStream is) {
            this.is = is;
        }

        @Override
        public short getUInt8() throws IOException {
            int readResult = is.read();
            if (readResult == -1) {
                throw new EndOfFileException();
            }

            return (short) readResult;
        }

        @Override
        public int getUInt16() throws IOException {
            return ((int) getUInt8() << 8) | getUInt8();
        }

        @Override
        public int getUInt32() throws IOException {
            return getUInt16() << 16 | getUInt16();
        }

        @Override
        public int read(byte[] buffer, int byteCount) throws IOException {
            int numBytesRead = 0;
            int lastReadResult = 0;
            while (numBytesRead < byteCount
                    && ((lastReadResult = is.read(buffer, numBytesRead, byteCount - numBytesRead)) != -1)) {
                numBytesRead += lastReadResult;
            }

            if (numBytesRead == 0 && lastReadResult == -1) {
                throw new EndOfFileException();
            }

            return numBytesRead;
        }

        @Override
        public long skip(long total) throws IOException {
            if (total < 0) {
                return 0;
            }

            long toSkip = total;
            while (toSkip > 0) {
                long skipped = is.skip(toSkip);
                if (skipped > 0) {
                    toSkip -= skipped;
                } else {
                    // Skip has no specific contract as to what happens when you reach the end of
                    // the stream. To differentiate between temporarily not having more data and
                    // having finished the stream, we read a single byte when we fail to skip any
                    // amount of data.
                    int testEofByte = is.read();
                    if (testEofByte == -1) {
                        break;
                    } else {
                        toSkip--;
                    }
                }
            }
            return total - toSkip;
        }
    }
}
