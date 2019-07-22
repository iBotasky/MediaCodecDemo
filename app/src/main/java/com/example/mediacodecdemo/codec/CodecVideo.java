package com.example.mediacodecdemo.codec;

import android.graphics.BitmapFactory;
import android.media.*;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by liangbo.su@ubnt on 2019-07-12
 */
public class CodecVideo {
    private static final String TAG = "CodecVideo";
    /**
     * Const for the video encoder
     */
    private static final int TIMEOUT_USEC = 1000;
    // movie length, in frames
    private static final int NUM_FRAMES = 30;               // two seconds of video
    private static final String MIME_TYPE = "video/avc";  // H.264 Advanced Video Coding
    private static final int OUTPUT_VIDEO_BIT_RATE = 1024 * 1024 * 10;    // 512 kbps maybe better
    private static final int OUTPUT_VIDEO_FRAME_RATE = 30;          // 25 fps
    private static final int OUTPUT_VIDEO_IFRAME_INTERVAL = 10;     // 10 seconds between I-Frames
    private static final int OUTPUT_VIDEO_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar; // MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar is deprecated


    /**
     * For media codec
     */
    private MediaExtractor mExtractor;
    private MediaMuxer mMuxer;
    private MediaCodec mDecoder;
    private MediaCodec mEncoder;
    private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();


    private String inputVideoFile;
    private String outputVideoFile;
    private CodecListener listener;
    private int mWidth;
    private int mHeight;
    private int mColorFormat;

    private int mOutputTrackId = -1;


    private ArrayList<Long> mKeyFramesTime = new ArrayList<>();


    public CodecVideo(String inputVideoFile, String outputVideoFile, CodecListener listener) {
        this.inputVideoFile = inputVideoFile;
        this.outputVideoFile = outputVideoFile;
        this.listener = listener;
        init();
    }

    /**
     * Init the properties
     */
    private void init() {
        Log.e(TAG, " on init");
        // Create MediaExtractor
        try {
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(inputVideoFile);

            int videoTrackIndex = CodecUtil.getVideoTrackIndex(mExtractor);
            mExtractor.selectTrack(videoTrackIndex);
            MediaFormat videoInputFormat = mExtractor.getTrackFormat(videoTrackIndex);

            mWidth = videoInputFormat.getInteger(MediaFormat.KEY_WIDTH);
            mHeight = videoInputFormat.getInteger(MediaFormat.KEY_HEIGHT);

            MediaCodecInfo videoCodecInfo = CodecUtil.selectCodec(MIME_TYPE);
            if (videoCodecInfo == null) {
                // Don't fail CTS if they don't have an AVC codec (not here, anyway).
                Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
                return;
            }
            mColorFormat = CodecUtil.selectColorFormat(videoCodecInfo, MIME_TYPE);
            // We avoid the device-specific limitations on width and height by using values that
            // are multiples of 16, which all tested devices seem to be able to handle.
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
            // Set some properties.  Failing to specify some of these can cause the MediaCodec
            // configure() call to throw an unhelpful exception.
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_VIDEO_BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, OUTPUT_VIDEO_FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mWidth * mHeight * 4);
            Log.e(TAG, "format: " + format);
            // Create Encoder
//            mEncoder = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME));
            mEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mEncoder.start();
            // Create Decoder
            mDecoder = MediaCodec.createDecoderByType(videoInputFormat.getString(MediaFormat.KEY_MIME));
            mDecoder.configure(videoInputFormat, null, null, 0);
            mDecoder.start();

            // Create muxer
            mMuxer = new MediaMuxer(outputVideoFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            doEncoderDecodeVideoFromBuffer();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Log.e(TAG, "onFinally");
            if (mMuxer != null) {
                mMuxer.release();
            }

            if (mDecoder != null) {
                mDecoder.stop();
                mDecoder.release();
            }

            if (mEncoder != null) {
                mEncoder.stop();
                mEncoder.release();
            }
            if (mExtractor != null) {
                mExtractor.release();
            }

            listener.onFinish();
        }
    }

    public static void showByteHexArrayLog(String tag, String log, byte[] data, int lineNumber) {
        StringBuilder logBuffer = new StringBuilder();
        logBuffer.append(log + "\n{");
        for (int i = 0; i < data.length; i++) {
            logBuffer.append(String.format("%02x", data[i]));
            if (i < data.length - 1) {
                logBuffer.append(", ");
            }
            if (i % lineNumber == lineNumber - 1) {
                logBuffer.append("\n");
                if (logBuffer.length() >= 2000) {
                    Log.i(tag, "" + logBuffer.toString());
                    logBuffer = new StringBuilder();
                }
            }

        }
        logBuffer.delete(logBuffer.length(), logBuffer.length());
        Log.i(tag, logBuffer.toString() + "}");
    }

    int count = 0;

    private void doEncoderDecodeVideoFromBuffer() throws IOException {
        boolean isAllDone = false;
        boolean isExtractorDone = false;
        boolean isDecodeDone = false;
        boolean isEncodeDone = false;
        while (!isAllDone) {
            // Extractor
            while (!isExtractorDone) {
                if (isExtractorDone) {
                    break;
                }

                int decoderInputIndex = mDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (decoderInputIndex <= MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.e(TAG, "decoder input is not valid buffer");
                    break;
                }

                ByteBuffer decodeInputBuffer = mDecoder.getInputBuffer(decoderInputIndex);
                int size = mExtractor.readSampleData(decodeInputBuffer, 0);
                long presentationTime = mExtractor.getSampleTime();
                if (size != -1 && presentationTime != -1) {
                    Log.e(TAG, "decoder input is valid " + size + " time " + presentationTime);
                    mDecoder.queueInputBuffer(decoderInputIndex, 0, size, presentationTime, mExtractor.getSampleFlags());
                }
                isExtractorDone = !mExtractor.advance();
                if (isExtractorDone) {
                    Log.e(TAG, "extractor is done");
                    mDecoder.queueInputBuffer(decoderInputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            }

            // Decoder
            while (!isDecodeDone) {
                if (isDecodeDone) {
                    break;
                }
                int result = mDecoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);

                if (result == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.e(TAG, "decoder output no buffer valid");
                    break;
                } else if (result == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.e(TAG, "decoder output change " + mDecoder.getOutputFormat());
                    continue;
                } else if (result == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    mDecoder.getOutputBuffers();
                    break;
                }

                Log.e(TAG, "encoder feed with result " + result);
                if (mBufferInfo.size >= 0 && result >= 0) {
                    //feed the encoder
                    int encoderInputBufferIndex = mEncoder.dequeueInputBuffer(TIMEOUT_USEC);

                    if (encoderInputBufferIndex <= MediaCodec.INFO_TRY_AGAIN_LATER) {
                        Log.e(TAG, "encoder input is not valid");
                        break;
                    }
                    ByteBuffer encoderInputBuffer = mEncoder.getInputBuffer(encoderInputBufferIndex);
                    int size = mBufferInfo.size;
                    long presentationTime = mBufferInfo.presentationTimeUs;
                    Log.e(TAG, "encoder input buffer size " + size + " time " + presentationTime + " flags " + mBufferInfo.flags);
                    ByteBuffer decoderOutputBuffer = mDecoder.getOutputBuffer(result).duplicate();

                    /* Save decoder image.
                     */
                    Image image = mDecoder.getOutputImage(result);
                    String fileDir = Environment.getExternalStorageDirectory().getPath() + File.separator + "ZDecoder";
                    File theDir = new File(fileDir);
                    if (!theDir.exists()) {
                        theDir.mkdirs();
                    } else if (!theDir.isDirectory()) {
                        throw new IOException("Not a directory");
                    }
                    String fileName = fileDir + File.separator + String.format("%03d.jpg", count);
                    CodecUtil.compressToJpeg(fileName, image);

                    byte[] array = ColorUtil.Companion.getNV12(mWidth, mHeight, BitmapFactory.decodeFile(fileName));
                    count++;



                    decoderOutputBuffer.position(mBufferInfo.offset);
                    decoderOutputBuffer.limit(mBufferInfo.offset + size);
                    encoderInputBuffer.clear();
                    encoderInputBuffer.put(array);
                    mEncoder.queueInputBuffer(
                            encoderInputBufferIndex,
                            0,
                            size,
                            presentationTime,
                            mBufferInfo.flags
                    );
                }
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.e(TAG, "video decoder done");
                    isDecodeDone = true;
                }
                mDecoder.releaseOutputBuffer(result, false);
            }
            // Encoder
            while (!isEncodeDone) {
                if (isEncodeDone) {
                    break;
                }
                int encoderOutputIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                if (encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.e(TAG, "encoder output is not valid");
                    break;
                } else if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat acturalMeidaFormat = mEncoder.getOutputFormat();
                    Log.e(TAG, "encoder output format change " + acturalMeidaFormat);
                    mOutputTrackId = mMuxer.addTrack(acturalMeidaFormat);
                    mMuxer.start();
                    continue;
                } else if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    mEncoder.getOutputBuffers();
                    break;
                }

                ByteBuffer encoderOutputBuffer = mEncoder.getOutputBuffer(encoderOutputIndex);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    mEncoder.releaseOutputBuffer(encoderOutputIndex, false);
                    break;
                }

                if (mBufferInfo.size >= 0) {
                    mMuxer.writeSampleData(mOutputTrackId, encoderOutputBuffer, mBufferInfo);
                }
                Log.e(TAG, " mBuffer flags " + mBufferInfo.flags + " size " + mBufferInfo.size + " time " + mBufferInfo.presentationTimeUs);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isEncodeDone = true;
                    isAllDone = true;
                }
                mEncoder.releaseOutputBuffer(encoderOutputIndex, false);
            }
        }
    }

    public interface CodecListener {
        void onFinish();
    }
}
