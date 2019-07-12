package com.example.mediacodecdemo.codec;

import android.media.*;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by liangbo.su@ubnt on 2019-07-12
 */
public class CodecVideo {
    private static final String TAG = "CodecVideo";
    /**
     * Const for the video encoder
     */
    private static final int TIMEOUT_USEC = 10000;
    // movie length, in frames
    private static final int NUM_FRAMES = 30;               // two seconds of video
    private static final String MIME_TYPE = "video/avc";  // H.264 Advanced Video Coding
    private static final int OUTPUT_VIDEO_BIT_RATE = 1024 * 1024 * 10;    // 512 kbps maybe better
    private static final int OUTPUT_VIDEO_FRAME_RATE = 30;          // 25 fps
    private static final int OUTPUT_VIDEO_IFRAME_INTERVAL = 10;     // 10 seconds between I-Frames
    private static final int OUTPUT_VIDEO_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface; // MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar is deprecated


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
    private int mWidth;
    private int mHeight;
    private int mColorFormat;

    private boolean isEndcoderEOS = false;
    private boolean isDecodeDone = false;
    private boolean isEncodeDone = false;
    private long mWrittenPresentationTimeUs = 0;

    private int mOutputTrackId = -1;

    private int mPollIndex = -1;


    public CodecVideo(String inputVideoFile, String outputVideoFile) {
        this.inputVideoFile = inputVideoFile;
        this.outputVideoFile = outputVideoFile;
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
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
            format.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_VIDEO_BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, OUTPUT_VIDEO_FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, OUTPUT_VIDEO_IFRAME_INTERVAL);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mWidth * mHeight * 4);
            Log.e(TAG, "format: " + format);
            // Create Encoder
            mEncoder = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME));
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
        }
    }

    private void doEncoderDecodeVideoFromBuffer() throws IOException {
        while (!isEndcoderEOS) {
            while (drainEncoder()) {
            }
            while (drainDecoder()) {
            }
            while (feedEncoder()) {
            }
            while (drainExtractor()) {
            }
        }

    }

    private boolean drainExtractor() {
        if (isEndcoderEOS) {
            return false;
        }

        int decoderInputIndex = mDecoder.dequeueInputBuffer(TIMEOUT_USEC);
        if (decoderInputIndex <= MediaCodec.INFO_TRY_AGAIN_LATER) {
            return false;
        }

        ByteBuffer decodeInputBuffer = mDecoder.getInputBuffer(decoderInputIndex);
        int size = mExtractor.readSampleData(decodeInputBuffer, 0);
        long presentationTime = mExtractor.getSampleTime();
        if (size > 0) {
            // Feed the buffer to decoder
            mDecoder.queueInputBuffer(decoderInputIndex, 0, size, presentationTime, mExtractor.getSampleFlags());
        }
        isEndcoderEOS = !mExtractor.advance();
        if (isEndcoderEOS) {
            mDecoder.queueInputBuffer(decoderInputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
        return true;
    }


    private boolean drainEncoder() {
        if (isEncodeDone) {
            return false;
        }

        int result = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        switch (result) {
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                return false;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                MediaFormat actualFormat = mEncoder.getOutputFormat();
                mOutputTrackId = mMuxer.addTrack(actualFormat);
                mMuxer.start();
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                return true;
        }
        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            isEncodeDone = true;
            mBufferInfo.set(0, 0, 0, mBufferInfo.flags);
        }
        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            mEncoder.releaseOutputBuffer(result, false);
            return true;
        }
        mMuxer.writeSampleData(mOutputTrackId, mEncoder.getOutputBuffer(result), mBufferInfo);
        mEncoder.releaseOutputBuffer(result, false);
        return true;
    }


    private boolean drainDecoder() {
        if (isDecodeDone) {
            return false;
        }
        int result = mDecoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        switch (result) {
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                return false;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                return true;
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                return true;
        }
        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            isDecodeDone = true;
        }
        if (mBufferInfo.size >= 0) {
            mPollIndex = result;
            Log.e(TAG,"index for drainDecoder" + result);
            return false;
        }
        return true;
    }


    private boolean feedEncoder() {
        if (mPollIndex == -1) {
            return false;
        }
        int encoderInputIndex = mEncoder.dequeueInputBuffer(TIMEOUT_USEC);
        if (encoderInputIndex <= 0) {
            return false;
        }
        ByteBuffer inputBuffer = mEncoder.getInputBuffer(encoderInputIndex);
        int size = mBufferInfo.size;
        long presentationTime = mBufferInfo.presentationTimeUs;

        if (size >= 0) {
            Log.e(TAG,"index for feedEncoder" + mPollIndex);
            ByteBuffer decodedBuffer = mDecoder.getOutputBuffer(mPollIndex).duplicate();
            decodedBuffer.position(mBufferInfo.offset);
            decodedBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

            inputBuffer.clear();
            inputBuffer.put(decodedBuffer);

            mEncoder.queueInputBuffer(encoderInputIndex, 0, size, presentationTime, mBufferInfo.flags);

        }
        mDecoder.releaseOutputBuffer(mPollIndex, false);
        mPollIndex = -1;
        return false;
    }
}