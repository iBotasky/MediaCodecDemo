package com.example.mediacodecdemo.codec;

import android.graphics.*;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.*;
import java.nio.ByteBuffer;


/**
 * Created by liangbo.su@ubnt on 2019-07-12
 */
public class CodecUtil {
    /**
     * Get the video track index from the extractor
     *
     * @param extractor
     * @return
     */
    public static int getVideoTrackIndex(MediaExtractor extractor) {
        int trackCount = extractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or
     * null if no match was found.
     */
    public static MediaCodecInfo selectCodec(String mimeType) {
        MediaCodecInfo[] codecInfos = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
        for (MediaCodecInfo codecInfo : codecInfos) {
            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equals(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    /**
     * Returns a color format that is supported by the codec and by this test code.  If no
     * match is found, this throws a test failure -- the set of formats known to the test
     * should be expanded for new platforms.
     */
    public static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        return 0;   // not reached
    }

    /**
     * Returns true if this is a color format that this test code understands (i.e. we know how
     * to read and generate frames in this format).
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    public static Bitmap yuvToBitmap(int size, ByteBuffer byteBuffer, int width, int height) {
        byte[] array = new byte[size];
        byteBuffer.get(array);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuvImage = new YuvImage(array, ImageFormat.NV21, width, height, null);
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 50, out);
        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    /**
     * Trans yuv data to a bitmap and save to local.
     *
     * @param size
     * @param byteBuffer
     * @param width
     * @param height
     * @param fileName
     * @param isInput
     */
    public static void saveBitmap(int size, ByteBuffer byteBuffer, int width, int height, String fileName, boolean isInput) {
        byte[] array = new byte[size];
        byteBuffer.get(array);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuvImage = new YuvImage(array, ImageFormat.YUY2, width, height, null);
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 50, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        File dir = isInput ? new File(Environment.getExternalStorageDirectory().getPath() + File.separator + "zCodecInput") :
                new File(Environment.getExternalStorageDirectory().getPath() + File.separator + "zCodecOutput");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName);
        FileOutputStream fOut = null;
        try {
            fOut = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fOut);
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();

        } finally {
            bitmap.recycle();
            Log.e("ReverseShortVideo", " bitmap is save");
        }
    }


    /**
     * YV12 To I420
     *
     * @param yv12bytes
     * @param i420bytes
     * @param width
     * @param height
     */
    public static void swapYV12toI420(byte[] yv12bytes, byte[] i420bytes, int width, int height) {
        System.arraycopy(yv12bytes, 0, i420bytes, 0, width * height);
        System.arraycopy(yv12bytes, width * height + width * height / 4, i420bytes, width * height, width * height / 4);
        System.arraycopy(yv12bytes, width * height, i420bytes, width * height + width * height / 4, width * height / 4);
    }

    /**
     * YV12 To NV12
     *
     * @param yv12bytes
     * @param nv12bytes
     * @param width
     * @param height
     */
    public static void swapYV12toNV12(byte[] yv12bytes, byte[] nv12bytes, int width, int height) {
        int nLenY = width * height;
        int nLenU = nLenY / 4;

        System.arraycopy(yv12bytes, 0, nv12bytes, 0, width * height);
        for (int i = 0; i < nLenU; i++) {
            nv12bytes[nLenY + 2 * i + 1] = yv12bytes[nLenY + i];
            nv12bytes[nLenY + 2 * i] = yv12bytes[nLenY + nLenU + i];
        }
    }

    /**
     * YV12 TO NV21
     *
     * @param input
     * @param output
     * @param width
     * @param height
     */
    public static void swapYV12toNV21(final byte[] input, final byte[] output, final int width, final int height) {
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;
        final int tempFrameSize = frameSize * 5 / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[tempFrameSize + i]; // Cr (V)
        }
    }


    /**
     * NV12 TO yuv420p
     *
     * @param nv12
     * @param yuv420p
     * @param width
     * @param height
     */
    public static void swapNV12ToYuv420P(byte[] nv12, byte[] yuv420p, int width, int height) {

        int ySize = width * height;

        int i, j;

        //y
        for (i = 0; i < ySize; i++) {
            yuv420p[i] = nv12[i];
        }

        //u
        i = 0;
        for (j = 0; j < ySize / 2; j += 2) {
            yuv420p[ySize + i] = nv12[ySize + j];
            i++;
        }

        //v
        i = 0;
        for (j = 1; j < ySize / 2; j += 2) {
            yuv420p[ySize * 5 / 4 + i] = nv12[ySize + j];
            i++;
        }
    }

    /**
     * NV12 TO I420
     *
     * @param nv12bytes
     * @param i420bytes
     * @param width
     * @param height
     */
    public static void swapNV12toI420(byte[] nv12bytes, byte[] i420bytes, int width, int height) {
        int nLenY = width * height;
        int nLenU = nLenY / 4;

        System.arraycopy(nv12bytes, 0, i420bytes, 0, width * height);
        for (int i = 0; i < nLenU; i++) {
            i420bytes[nLenY + i] = nv12bytes[nLenY + 2 * i + 1];
            i420bytes[nLenY + nLenU + i] = nv12bytes[nLenY + 2 * i];
        }
    }


    /**
     * I420 TO NV21
     *
     * @param input
     * @param output
     * @param width
     * @param height
     */
    public static void swapI420ToNV21(final byte[] input, final byte[] output, final int width, final int height) {
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;
        final int tempFrameSize = frameSize * 5 / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[tempFrameSize + i]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
        }
    }

    /**
     * NV21 TO NV12
     *
     * @param nv21
     * @param nv12
     * @param width
     * @param height
     */
    public static void swapNV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) return;

        int framesize = width * height;
        int i = 0, j = 0;

        System.arraycopy(nv21, 0, nv12, 0, framesize);

        for (i = 0; i < framesize; i++) {
            nv12[i] = nv21[i];
        }

        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j - 1] = nv21[j + framesize];
        }

        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j] = nv21[j + framesize - 1];
        }
    }
}
