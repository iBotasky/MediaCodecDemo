package com.example.mediacodecdemo

import android.media.MediaCodecInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_media_codec.*

/**
 * Created by liangbo.su@ubnt on 2019-07-10
 */
class MediaCodecActivity : AppCompatActivity() {
    companion object {
        /**
         * MediaCodec params
         */
        const val TIMEOUT_USEC = 1000 //how long to wait for the next buffer to become available
        /**
         * Params for the video encoder
         */
        const val OUTPUT_VIDEO_MIME_TYPE = "video/acv"  // H.264 Advanced Video Coding
        const val OUTPUT_VIDEO_BIT_RATE = 512 * 1024    // 512 kbps maybe better
        const val OUTPUT_VIDEO_FRAME_RATE = 25          // 25 fps
        const val OUTPUT_VIDEO_IFRAME_INTERVAL = 10     // 10 seconds between I-Frames
        const val OUTPUT_VIDEO_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible // MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar is deprecated
        /**
         * Params for the audio encoder
         */
        const val OUTPUT_AUDIO_TYPE = "audio/mp4a-latm" // Advanced Audio  Coding
        const val OUTPUT_AUDIO_BIT_RATE = 64 * 1024     // 64 kbps
        const val OUTPUT_AUDIO_AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC // better then AACObjectHE?
        /**
         * Params for the audio encoder config from input stream
         */
        const val OUTPUT_AUDIO_CHANNEL_COUNT = 1       // Must match the input stream. can not config
        const val OUTPUT_AUDIO_SAMPLE_RATE = 48000      // Must match the imput stream. can not config
    }

    /**
     * Whether to copy the video from the test video.
     */
    private var mCopyVideo = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_codec)
        mediaCodec.setOnClickListener {

        }
    }
}