package com.example.mediacodecdemo.codec;

import android.media.*;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Stack;

/**
 * Created by liangbo.su@ubnt on 2019-07-17
 */
public class ReverseVideo {
    private static final String TAG = "ReverseShortVideo";
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

    private long mVideoDuration = 0;


    private int mOutputTrackId = -1;

    private ArrayList<Long> mKeyFramesTime = new ArrayList<>();

    public ReverseVideo(String inputVideoFile, String outputVideoFile) {
        this.inputVideoFile = inputVideoFile;
        this.outputVideoFile = outputVideoFile;
        init();
    }

    private void init() {
        Log.e(TAG, " on init");
        // Create MediaExtractor
        try {
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(inputVideoFile);


            int videoTrackIndex = CodecUtil.getVideoTrackIndex(mExtractor);
            mExtractor.selectTrack(videoTrackIndex);
            MediaFormat videoInputFormat = mExtractor.getTrackFormat(videoTrackIndex);
            mVideoDuration = videoInputFormat.getLong(MediaFormat.KEY_DURATION);
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

//            doExtractorKeyFramesTime();
//            mExtractor.unselectTrack(videoTrackIndex);
//            mExtractor.selectTrack(videoTrackIndex);

            doReverse();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "OnException");
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


    /**
     * 翻转最后一个KeyFrame到结束
     */
    private void doReverse() {
        Stack<byte[]> byteBuffers = new Stack<>();
        Stack<MediaCodec.BufferInfo> bufferInfos = new Stack<>();


        boolean isAllDone = false;
        boolean isExtractorDone = false;
        boolean isDecodeDone = false;
        boolean isEncodeDone = false;

        int m = 0;
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
                // We need to calculate the time for reverse
                long presentationTime = mExtractor.getSampleTime();
                if (size > 0) {
                    Log.e(TAG,"decoder input time " + mVideoDuration + " sampleTime " + mExtractor.getSampleTime() + " presentTime " + presentationTime);
                    Log.e(TAG, "decoder input is valid flag " + mExtractor.getSampleFlags() + " size " + size + " time " + presentationTime);
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
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int result = mDecoder.dequeueOutputBuffer(info, TIMEOUT_USEC);

                if (result == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.e(TAG, "decoder output no buffer valid");
                    break;
                } else if (result == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.e(TAG, "decoder output change " + mDecoder.getOutputFormat());
                    continue;
                } else if (result == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    break;
                }

                // TODO 这里不能>=0 ,不然后面导致Encoder的input一直-1
                if (info.size > 0 && result >= 0) {
                    Log.e(TAG, "save the decode buffer time " + info.presentationTimeUs + " flag " + info.flags + " size " + info.size);
                    // save the decode buffer, Here we use the array to save, not the buffer.duplicate(), because it's just point to the them address
                    ByteBuffer decodedOutputBuffer = mDecoder.getOutputBuffer(result);
                    info.presentationTimeUs = mVideoDuration - info.presentationTimeUs;
                    Log.e(TAG, "save the decoder buffer new Time " + info.presentationTimeUs);
                    byte[] array = new byte[decodedOutputBuffer.limit()];
                    decodedOutputBuffer.get(array);
                    byteBuffers.push(array);
                    bufferInfos.push(info);
                }
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.e(TAG, "video decoder done");
                    isDecodeDone = true;
                    isAllDone = true;
                }
                mDecoder.releaseOutputBuffer(result, false);
            }
        }


        // Encode the data
        boolean isReverseDone = false;
        boolean isFeedDone = false;
        int frameCount = byteBuffers.size();

        while (!isReverseDone) {
            while (!isFeedDone) {
                if (isFeedDone) {
                    break;
                }
                int encoderInputIndex = mEncoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (encoderInputIndex <= MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.e(TAG, "encoder input is not valid");
                    break;
                }
                byte[] decodedOutput = byteBuffers.pop();
                MediaCodec.BufferInfo info = bufferInfos.pop();
                Log.e(TAG,"classname: "  + decodedOutput + " hashcode " + decodedOutput.hashCode());
                ByteBuffer encoderInputBuffer = mEncoder.getInputBuffer(encoderInputIndex);
                int size = info.size;
                long presentationTime = info.presentationTimeUs;
                Log.e(TAG, "encoder input buffer at:" + encoderInputIndex + "  size " + size + " time " + presentationTime + " flags " + info.flags);

                encoderInputBuffer.clear();

                encoderInputBuffer.put(decodedOutput);

                mEncoder.queueInputBuffer(encoderInputIndex, 0, size, presentationTime, info.flags);

                if (byteBuffers.isEmpty() || bufferInfos.isEmpty()) {
                    Log.e(TAG, "Feed encoder done");
                    isFeedDone = true;
                }
            }

            // Encode
            while (!isEncodeDone) {
                if (isEncodeDone) {
                    break;
                }

                int encoderOutputIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                Log.e(TAG, "encoder input buffer index " + encoderOutputIndex);
                if (encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.e(TAG, "encoder output is not valid");
                    break;
                } else if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat realForamt = mEncoder.getOutputFormat();
                    Log.e(TAG, "encoder output change:" + realForamt);
                    mOutputTrackId = mMuxer.addTrack(realForamt);
                    mMuxer.start();
                    continue;
                } else if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    break;
                }

                ByteBuffer encoderOutputBuffer = mEncoder.getOutputBuffer(encoderOutputIndex);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    mEncoder.releaseOutputBuffer(encoderOutputIndex, false);
                    break;
                }

                Log.e(TAG, "mBuffer flags " + mBufferInfo.flags + " size " + mBufferInfo.size + " time " + mBufferInfo.presentationTimeUs);
                mMuxer.writeSampleData(mOutputTrackId, encoderOutputBuffer, mBufferInfo);
                mEncoder.releaseOutputBuffer(encoderOutputIndex, false);
                frameCount--;
                if (frameCount <= 0){
                    isEncodeDone = true;
                    isReverseDone = true;
                }
            }
        }
    }


    /**
     * 获取关键帧位置
     */
    private void doExtractorKeyFramesTime() {
        boolean isGetKeyFrameTimesEnd = false;
        while (!isGetKeyFrameTimesEnd) {
            if ((mExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                Log.e(TAG, "This is an key frame");
                if (mExtractor.getSampleTime() != -1) {
                    mKeyFramesTime.add(mExtractor.getSampleTime());
                }
            } else {
                Log.e(TAG, "This is not an key frame");
            }
            isGetKeyFrameTimesEnd = !mExtractor.advance();
        }
        mKeyFramesTime.add(mVideoDuration);
        Collections.reverse(mKeyFramesTime);
        for (Long i : mKeyFramesTime) {
            Log.e(TAG, " time is " + i);
        }
    }
}
