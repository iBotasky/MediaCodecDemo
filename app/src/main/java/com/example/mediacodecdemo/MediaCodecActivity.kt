package com.example.mediacodecdemo

import android.media.*
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_media_codec.*
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference


/**
 * Created by liangbo.su@ubnt on 2019-07-10
 */
class MediaCodecActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MediaCodec"
        /**
         * Params for paths
         */
        private val SDCARD_PATH: String = Environment.getExternalStorageDirectory().path
        private val INPUT_FILE = SDCARD_PATH + File.separator + "input.mp4"
        private val OUTPUT_VIDEO_FILE = SDCARD_PATH + File.separator + "output.mp4"
        private val OUTPUT_AUDIO_FILE = SDCARD_PATH + File.separator + "output"

        /**
         * MediaCodec params
         */
        const val TIMEOUT_USEC = 1000L //how long to wait for the next buffer to become available
        /**
         * Params for the video encoder
         */
        const val OUTPUT_VIDEO_MIME_TYPE = "video/avc"  // H.264 Advanced Video Coding
        const val OUTPUT_VIDEO_BIT_RATE = 1024 * 1024 * 10    // 512 kbps maybe better
        const val OUTPUT_VIDEO_FRAME_RATE = 30          // 25 fps
        const val OUTPUT_VIDEO_IFRAME_INTERVAL = 10     // 10 seconds between I-Frames
        const val OUTPUT_VIDEO_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible // MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar is deprecated

    }

    /**
     * Whether to copy the video from the test video.
     */
    private var mCopyVideo = true

    /**
     * Whether to copy the audio from the test audio.
     */
    private var mCopyAudio = false
    /**
     * Width of the output frames.
     */
    private var mWidth = -1
    /**
     * Height of the output frames.
     */
    private var mHeight = -1
    /**
     * The raw resource used as the input file.
     */
    private var mBaseFileRoot = ""
    /**
     * The raw resource used as the input file.
     */
    private var mBaseFile = INPUT_FILE
    /**
     * The destination file for the encoded output.
     */
    private var mOutputFile = OUTPUT_VIDEO_FILE

    private var interrupted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_codec)
        mediaCodec.setOnClickListener {
            AsyncTask.THREAD_POOL_EXECUTOR.execute {
                extractorDecoderEncoderMux()
            }
        }
    }


    @Throws(Exception::class)
    private fun extractorDecoderEncoderMux() {
        // Create video extractor from file
        val videoExtractor = createExtractor()
        // Get video track index
        val videoInputTrack = getAndSelectVideoTrackIndex(videoExtractor)
        videoExtractor.selectTrack(videoInputTrack)
        Log.e(TAG, "video track index:$videoInputTrack")
        // Get video format, and make sure decode buffer size equals encode buffer size
        val videoInputFormat = videoExtractor.getTrackFormat(videoInputTrack)
//        Log.e("ColorFormat", " in track:${videoInputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)}")

        Log.e(TAG, "video track fromat:$videoInputFormat")
        videoInputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, OUTPUT_VIDEO_COLOR_FORMAT)
        if (mWidth != -1) {
            videoInputFormat.setInteger(MediaFormat.KEY_WIDTH, mWidth)
        } else {
            mWidth = videoInputFormat.getInteger(MediaFormat.KEY_WIDTH)
        }
        if (mHeight != -1) {
            videoInputFormat.setInteger(MediaFormat.KEY_HEIGHT, mHeight)
        } else {
            mHeight = videoInputFormat.getInteger(MediaFormat.KEY_HEIGHT)
        }
        Log.e(TAG, "video match input format:$videoInputFormat")

        val videoCodecInfo = selectCodec(OUTPUT_VIDEO_MIME_TYPE)
        if (videoCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for :$OUTPUT_VIDEO_MIME_TYPE")
            return
        }

        Log.e(TAG,"mWidth:$mWidth mHeight:$mHeight size:${mWidth * mHeight * 4}")
//        val videoOutputFormat = MediaFormat.createVideoFormat(OUTPUT_VIDEO_MIME_TYPE, mWidth, mHeight)
//        videoOutputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, videoInputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT))
//        videoOutputFormat.setInteger(MediaFormat.KEY_BIT_RATE, mWidth * mHeight * videoInputFormat.getInteger(MediaFormat.KEY_FRAME_RATE) / 8)
//        videoOutputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, videoInputFormat.getInteger(MediaFormat.KEY_FRAME_RATE))
//        videoOutputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, OUTPUT_VIDEO_IFRAME_INTERVAL)
//        videoOutputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mWidth * mHeight * 4)

        val videoOutputFormat = MediaFormat.createVideoFormat(OUTPUT_VIDEO_MIME_TYPE, mWidth, mHeight)
        videoOutputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, OUTPUT_VIDEO_COLOR_FORMAT)
        videoOutputFormat.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_VIDEO_BIT_RATE)
        videoOutputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, OUTPUT_VIDEO_FRAME_RATE)
        videoOutputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, OUTPUT_VIDEO_IFRAME_INTERVAL)
        videoOutputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mWidth * mHeight * 4)
        Log.e(TAG, "video encoder format:$videoOutputFormat")

        // Create encoder by videoOutpuFormat and create decoder by videoInputFormat
        val videoEncoder = createVideoEncoder(videoCodecInfo, videoOutputFormat, null)
        val videoDecoder = createVideoDecoder(videoInputFormat, null)

        // Create muxer
        val muxer = createMuxer()

        doExtractorDecodeEncodeMux(videoExtractor, videoDecoder, videoEncoder, muxer)

    }

    /**
     * Does the actual work for extracting, decoding, encoding and muxing.
     * Here we just codec the video
     */
    private fun doExtractorDecodeEncodeMux(
        videoExtractor: MediaExtractor,
        videoDecoder: MediaCodec,
        videoEncoder: MediaCodec,
        muxer: MediaMuxer
    ) {
        var videoDecoderInputBuffers = videoDecoder.inputBuffers
        var videoDecoderOutputBuffers = videoDecoder.outputBuffers

        val videoEncoderInputBuffers = videoEncoder.inputBuffers
        var videoEncoderOutputBuffers = videoEncoder.outputBuffers

        val videoDecoderOutputBufferInfo = MediaCodec.BufferInfo()
        val videoEncoderOutputBufferInfo = MediaCodec.BufferInfo()

        // We will get these from the decoders when notified a format change.
        var decoderOutputVideoFormat: MediaFormat? = null
        var encoderOutputVideoFormat: MediaFormat? = null
        // We will determine these once we have the output format.
        var outputVideoTrack = -1
        // Whether things are done on the video side
        var videoExtractorDone = false
        var videoDecoderDone = false
        var videoEncoderDone = false
        // The video decoder output buffer to process, -1 if none.
        var pendingVideoDecoderOutputBufferIndex = -1

        var muxing = false

        var videoExtractedFrameCount = 0
        var videoDecodedFrameCount = 0
        var videoEncodedFrameCount = 0

        var mVideoConfig = false
        var mainVideoFrame = false
        var mLastVideoSampleTime = 0L
        var mVideoSampleTime = 0L


        while (!interrupted && !videoEncoderDone) {
            /**
             * Extractor video from file and feed to decoder
             * Do not extractor video if we have determined the output format
             * but we are not yet ready to mux the frames
             */
            while (!videoExtractorDone && (encoderOutputVideoFormat == null || muxing)) {
                // 获取空闲缓冲区位置
                val decoderInputBufferIndex = videoDecoder.dequeueInputBuffer(TIMEOUT_USEC)
                if (decoderInputBufferIndex <= MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.e(TAG, "no video decoder input buffer:$decoderInputBufferIndex")
                    break
                }
                // 获取空闲缓冲区Buffer
                Log.e(TAG, "video decoder dequeueInputBuffer returned input buffer:$decoderInputBufferIndex")
                val decoderInputBuffer = videoDecoder.getInputBuffer(decoderInputBufferIndex)
                // 往空闲缓冲区塞Video的数据
                val size = videoExtractor.readSampleData(decoderInputBuffer, 0)
                if (videoExtractor.sampleFlags == MediaExtractor.SAMPLE_FLAG_SYNC) {
                    Log.e(TAG, " video decoder SAMPLE_FLAG_SYNC ")
                }
                val presentationTime = videoExtractor.sampleTime
                Log.e(
                    TAG,
                    "video extractor returned buffer of size:$size , time:$presentationTime flags:${videoExtractor.sampleFlags}"
                )
                //  video的size正确，就解码该index的缓冲区的视频数据
                if (size > 0) {
                    videoDecoder.queueInputBuffer(
                        decoderInputBufferIndex,
                        0,
                        size,
                        presentationTime,
                        videoExtractor.sampleFlags
                    )
                }

                // 定位到下一帧，如果是结束帧，就添加结束帧数据
                videoExtractorDone = !videoExtractor.advance()
                if (videoExtractorDone) {
                    Log.e(TAG, "video extractor: EOS")
                    videoDecoder.queueInputBuffer(
                        decoderInputBufferIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
                // We extracted a frame, let's try something else next.
                videoExtractedFrameCount++
            }

            /**
             * Poll output frames from the video decoder and feed the encoder.
             */
            while (!videoDecoderDone && pendingVideoDecoderOutputBufferIndex == -1 && (encoderOutputVideoFormat == null || muxing)) {
                // 获取Decoder解码出来的数据在缓存区的位置, 解码数据存入videoDecoderOutputbufferInfo里面, 解码的buffer缓冲区在Decoder的decoderOutputBufferIndex中，
                val decoderOutputBufferIndex =
                    videoDecoder.dequeueOutputBuffer(videoDecoderOutputBufferInfo, TIMEOUT_USEC)

                if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.e(TAG, "no video decoder output buffer")
                    break
                } else if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    decoderOutputVideoFormat = videoDecoder.outputFormat

                    Log.e("ColorFormat", " in track:${decoderOutputVideoFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)}")
                    Log.e(TAG, "video decoder: output fromat changed:$decoderOutputVideoFormat")
                    break
                } else if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.e(TAG, "video decoder: output buffers changed")
                    videoDecoderOutputBuffers = videoDecoder.getOutputBuffers()
                    break
                }

                Log.e(
                    TAG,
                    "video decoder returned output buffer:$decoderOutputBufferIndex size:${videoDecoderOutputBufferInfo.size}"
                )
                Log.e(TAG, "videoDecoderOutputBuffer.flags:${videoDecoderOutputBufferInfo.flags}")
                if ((videoDecoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.e(TAG, "video decoder: codec config buffer")
                    videoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false)
                    break
                }
                Log.e(TAG, "video decoder: returned buffer for time:${videoDecoderOutputBufferInfo.presentationTimeUs}")

                //赋值decoder解码缓冲区index到pendingVideoDecoderOutputBufferIndex
                pendingVideoDecoderOutputBufferIndex = decoderOutputBufferIndex
                videoDecodedFrameCount++
            }

            /**
             * Feed the pending decode video buffer to the video encoder.
             */
            while (pendingVideoDecoderOutputBufferIndex != -1) {
                //获取Encoder空闲的缓冲区Index，encoderInputBufferIndex
                Log.e(TAG, "video decoder: attempting to process pending buffer:$pendingVideoDecoderOutputBufferIndex")

                val encoderInputBufferIndex = videoEncoder.dequeueInputBuffer(TIMEOUT_USEC)
                if (encoderInputBufferIndex <= MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.e(TAG, "no video encoder input buffer:$encoderInputBufferIndex")
                    break
                }
                Log.e(TAG, "video encoder returned input buffer:$encoderInputBufferIndex")
                // 获取Encoder空闲的缓冲区index指向的缓冲区
                val encoderInputBuffer = videoEncoderInputBuffers[encoderInputBufferIndex]
                // 通过videoDecoderOutputBuffer获取size，跟presentationTime
                val size = videoDecoderOutputBufferInfo.size
                val presentationTime = videoDecoderOutputBufferInfo.presentationTimeUs
                Log.e(
                    TAG,
                    "video decoder processing pending buffer:$pendingVideoDecoderOutputBufferIndex size:$size time:$presentationTime"
                )
                // 如果数据正确，就把当前的decoderOutputBuffer 放入对应的空闲的EncoderInputBufferindex的编码缓冲区
                if (size >= 0) {
                    val decoderOutputBuffer =
                        videoDecoder.getOutputBuffer(pendingVideoDecoderOutputBufferIndex)!!.duplicate()
                    decoderOutputBuffer.position(videoDecoderOutputBufferInfo.offset)
                    decoderOutputBuffer.limit(videoDecoderOutputBufferInfo.offset + size)
                    Log.e("BufferOver", " decoderPosition:${decoderOutputBuffer.position()}  limit:${decoderOutputBuffer.limit()} encoderInput:${encoderInputBuffer.limit()}")
                    encoderInputBuffer.clear()
                    // endcoderInputbuffer 存入VideoDecoder解码完的buffer数据，
                    encoderInputBuffer.put(decoderOutputBuffer)
                    // 开始往编码器塞入要编码的已经解完码的buffer数据，进行编码
                    videoEncoder.queueInputBuffer(
                        encoderInputBufferIndex,
                        0,
                        size,
                        presentationTime,
                        videoDecoderOutputBufferInfo.flags
                    )
                }
                // 释放decoder的缓冲区
                videoDecoder.releaseOutputBuffer(pendingVideoDecoderOutputBufferIndex, false)
                pendingVideoDecoderOutputBufferIndex = -1
                if ((videoDecoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.e(TAG, "video decoder: EOS")
                    videoDecoderDone = true
                }
                break
            }
            /**
             * Poll frames from the video encoder and send them to muxer.
             */
            while (!videoEncoderDone && (encoderOutputVideoFormat == null || muxing)) {
                // 获取Encoder中编码完的缓冲区index
                val encoderOutputBufferIndex =
                    videoEncoder.dequeueOutputBuffer(videoEncoderOutputBufferInfo, TIMEOUT_USEC)
                if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.e(TAG, "no video encoder output buffer")
                    break
                } else if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.e(TAG, "video encoder: output format changed")
                    if (outputVideoTrack >= 0) {
                        Log.e(TAG, "video encoder changed its output format again")
                    }
                    encoderOutputVideoFormat = videoEncoder.outputFormat
                    if (encoderOutputVideoFormat.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
                        Log.e(
                            "ColorFormat",
                            " format:${encoderOutputVideoFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)}"
                        )
                    }
                    outputVideoTrack = muxer.addTrack(encoderOutputVideoFormat)
                    muxer.start()
                    muxing = true
                    break
                } else if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.e(TAG, "video encoder: output buffers changed")
                    videoEncoderOutputBuffers = videoEncoder.getOutputBuffers()
                    break
                }

                Log.e(
                    TAG,
                    "video encoder return output buffer:$encoderOutputBufferIndex size:${videoEncoderOutputBufferInfo.size}"
                )
                // 获取编码完的缓冲区的index的buffer数据
                val encoderOutputBuffer = videoEncoder.getOutputBuffer(encoderOutputBufferIndex)!!
                Log.e(TAG, "videoEncoderOutputBufferInfoFlags: ${videoEncoderOutputBufferInfo.flags}")
                if ((videoEncoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.e(TAG, "video encoder: codec config buffer")

                    mVideoConfig = true
                    videoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
                    break
                }

                Log.e(TAG, "video encoder returned buffer for time:${videoEncoderOutputBufferInfo.presentationTimeUs}")

                if (mVideoConfig) {
                    if (!mainVideoFrame) {
                        mLastVideoSampleTime = videoEncoderOutputBufferInfo.presentationTimeUs
                        mainVideoFrame = true
                    } else {
                        if (mVideoSampleTime == 0L) {
                            mVideoSampleTime = videoEncoderOutputBufferInfo.presentationTimeUs - mLastVideoSampleTime
                        }
                    }
                }
                videoEncoderOutputBufferInfo.presentationTimeUs = mLastVideoSampleTime + mVideoSampleTime
                // 往Muxer中塞入编码玩得EncoderOutputBuffer进行合成
                if (videoEncoderOutputBufferInfo.size != 0) {
                    muxer.writeSampleData(
                        outputVideoTrack,
                        encoderOutputBuffer, videoEncoderOutputBufferInfo
                    )
                    mLastVideoSampleTime = videoEncoderOutputBufferInfo.presentationTimeUs
                }

                if ((videoEncoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.e(TAG, "video encoder: EOS")
                    videoEncoderDone = true
                }
                // 释放缓冲区
                videoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
                videoEncodedFrameCount++
                // We enqueued an encoded frame, let's try something else next.
                break
            }

            /**
             * Start muxing video
             */

            if (!muxing && (encoderOutputVideoFormat != null)) {
                Log.e(TAG, "muxer: adding video track.")
//                outputVideoTrack = muxer.addTrack(encoderOutputVideoFormat)

                Log.d(TAG, "muxer: starting")

            }
        }

    }


    /**
     * Creates an extractor that reads its frames from [.mSourceResId].
     */
    @Throws(IOException::class)
    private fun createExtractor(): MediaExtractor {
        // net source
        val extractor = MediaExtractor()
        if (mBaseFile.contains(":")) {
            extractor.setDataSource(mBaseFile)
        } else {
            val mFile = File(mBaseFile)
            extractor.setDataSource(mFile.toString())
        }
        return extractor
    }

    /**
     * Get the extractor's video track index
     */
    private fun getAndSelectVideoTrackIndex(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                return index
            }
        }
        return -1
    }


    /**
     * Returns the first codec capable of encoding the specified MIME type, or
     * null if no match was found.
     */
    private fun selectCodec(mimeType: String): MediaCodecInfo? {
        val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        for (i in 0 until codecInfos.size) {
            val codecInfo = codecInfos[i]
            if (!codecInfo.isEncoder) {
                continue
            }

            val types = codecInfo.supportedTypes
            for (j in types.indices) {
                if (types[j].equals(mimeType, ignoreCase = true)) {
                    return codecInfo
                }
            }
        }
        return null
    }

    /**
     * Creates a decoder for the given format, which outputs to the given
     * surface.
     *
     * @param inputFormat
     * the format of the stream to decode
     * @param surface
     * into which to decode the frames
     */
    private fun createVideoDecoder(
        inputFormat: MediaFormat,
        surface: Surface?
    ): MediaCodec {
        val decoder = MediaCodec
            .createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME))
        decoder.configure(inputFormat, surface, null, 0)
        decoder.start()
        return decoder
    }

    /**
     * Creates an encoder for the given format using the specified codec, taking
     * input from a surface.
     *
     *
     *
     * The surface to use as input is stored in the given reference.
     *
     * @param codecInfo
     * of the codec to use
     * @param format
     * of the stream to be produced
     * @param surfaceReference
     * to store the surface to use as input
     */
    private fun createVideoEncoder(
        codecInfo: MediaCodecInfo,
        format: MediaFormat, surfaceReference: AtomicReference<Surface>?
    ): MediaCodec {
        val encoder = MediaCodec.createByCodecName(codecInfo.name)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        // Must be called before start() is.
        surfaceReference?.set(encoder.createInputSurface())
        encoder.start()
        return encoder
    }

    /**
     * Creates a muxer to write the encoded frames. The muxer is not started as
     * it needs to be started only after all streams have been added.
     */
    @Throws(IOException::class)
    private fun createMuxer(): MediaMuxer {
        return MediaMuxer(mOutputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }
}