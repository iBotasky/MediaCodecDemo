package com.example.mediacodecdemo

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_media_actions.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.abs


/**
 * Created by liangbo.su@ubnt on 2019-07-04
 */
class MediaActionsActivity : AppCompatActivity() {
    companion object {
        /**
         * Extractor params
         */
        private val SDCARD_PATH: String = Environment.getExternalStorageDirectory().path
        private val INPUT_FILE = SDCARD_PATH + File.separator + "input.mp4"
        private val OUTPUT_VIDEO_FILE = SDCARD_PATH + File.separator + "output.mp4"
        private val OUTPUT_AUDIO_FILE = SDCARD_PATH + File.separator + "output"
    }

    private lateinit var mMediaExtractor: MediaExtractor



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_actions)

        extractorAudioAndVideo.setOnClickListener {
            AsyncTask.THREAD_POOL_EXECUTOR.execute {
                extractorMedia()
            }
        }

        extractorVideo.setOnClickListener {
            AsyncTask.THREAD_POOL_EXECUTOR.execute {
                extractorVideo()
            }
        }

        extractorAudio.setOnClickListener {
            AsyncTask.THREAD_POOL_EXECUTOR.execute {
                extractorAudio()
            }
        }

        combineVideo.setOnClickListener { }

        muxerPlayBackVideo.setOnClickListener {
            AsyncTask.THREAD_POOL_EXECUTOR.execute {
                muxerPlayBackVideo()
            }
        }


    }

    /**
     * 全关键帧视频合成倒放视频
     */
    private fun muxerPlayBackVideo() {
        Log.e("Playback", " on start")
        mMediaExtractor = MediaExtractor()
        mMediaExtractor.setDataSource(INPUT_FILE)
        var originalVideoIndex = -1
        for (i in 0 until mMediaExtractor.trackCount) {
            val mediaFormat = mMediaExtractor.getTrackFormat(i)
            val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
            if (mime.startsWith("video/")) {
                originalVideoIndex = i
                break
            }
        }
        // 设置分离器选择视频轨道
        mMediaExtractor.selectTrack(originalVideoIndex)
        val videoFormat = mMediaExtractor.getTrackFormat(originalVideoIndex)
        val mediaMuxer = MediaMuxer(OUTPUT_VIDEO_FILE, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val muxerVideoIndex = mediaMuxer.addTrack(videoFormat)
        val byteBuffer = ByteBuffer.allocate(500 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()
        val videoDuration = videoFormat.getLong(MediaFormat.KEY_DURATION)

        Log.e("Playback", " duration:$videoDuration")

        val frameTimes = ArrayList<Long>()
        mMediaExtractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        var i = 0
        while (true) {
            frameTimes.add(mMediaExtractor.sampleTime)
            Log.e("PlayBack", " i : $i  time:${mMediaExtractor.sampleTime} flag:${mMediaExtractor.sampleFlags}")
            mMediaExtractor.advance()
            i++
            if (mMediaExtractor.sampleTime < 0) {
                Log.e("Last", " flag :${mMediaExtractor.sampleFlags}")
                break
            }
        }
//        //最后一帧
//        frameTimes.add(videoDuration)
//        i++

        mediaMuxer.start()
        // 先定位到最后一帧
        mMediaExtractor.seekTo(videoDuration, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        i--
        var allTime = 0L
        val size = frameTimes.size
        if (size < 2) {
            return
        }
        while (true) {
            val sampleSize = mMediaExtractor.readSampleData(byteBuffer, 0)
            if (sampleSize < 0) {
                break
            }

            bufferInfo.size = sampleSize
            bufferInfo.offset = 0
            bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
            bufferInfo.presentationTimeUs = allTime
            mediaMuxer.writeSampleData(muxerVideoIndex, byteBuffer, bufferInfo)
//            Log.e(
//                "Playback",
//                "index:$i presentTime:${bufferInfo.presentationTimeUs} sampleTime=${mMediaExtractor.sampleTime}"
//            )

            if (i <= 0) {
                break
            }
            val duration = frameTimes[i] - frameTimes[i - 1]
            allTime += duration
            val nextFrameTime = frameTimes[i - 1]
//            Log.e("PlayBack", " index:$i sampleTime:${mMediaExtractor.sampleTime} seek nextTime :$nextFrameTime")
            mMediaExtractor.seekTo(nextFrameTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var loopTime = 0
            // here we may get wrong frame in some device, so we loop to seek to this frame.
            while (mMediaExtractor.sampleTime != nextFrameTime) {
                Log.e("PlayBack", " loop to same frame, Time:$nextFrameTime")
                mMediaExtractor.seekTo(nextFrameTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                loopTime++
                if (loopTime > 5) {
                    break
                }
            }
            i--

        }

        mediaMuxer.stop()
        mediaMuxer.release()
        mMediaExtractor.release()
    }


    /**
     * 根据MIME提取视频轨道，可以播放
     */
    private fun extractorVideo() {
        mMediaExtractor = MediaExtractor()
        mMediaExtractor.setDataSource(INPUT_FILE)
        var videoTrackIndex = -1 // 原始视频的视频轨道index

        for (i in 0 until mMediaExtractor.trackCount) {
            val mediaFormat = mMediaExtractor.getTrackFormat(i)
            val mime = mediaFormat.getString(MediaFormat.KEY_MIME)

            if (mime.startsWith("video/")) {
                videoTrackIndex = i
                break
            }

        }
        Log.e("ExtractorVideo", " onStart videoIndex:$videoTrackIndex")
        val videoFormat = mMediaExtractor.getTrackFormat(videoTrackIndex)
        val mediaMuxer = MediaMuxer(OUTPUT_VIDEO_FILE, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // 获取添加到要合成MediaMuxer的轨道index
        val trackIndex = mediaMuxer.addTrack(videoFormat)
        val byteBuffer = ByteBuffer.allocate(500 * 1024)

        val bufferInfo = MediaCodec.BufferInfo()
        mMediaExtractor.selectTrack(videoTrackIndex)
        mediaMuxer.start()

        val frameTime = 1000 * 1000 / videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
        Log.e("ExtractorVideo", " frameTime:$frameTime")
        while (true) {

            val sampleSize = mMediaExtractor.readSampleData(byteBuffer, 0)

            Log.e("ExtractorVideo", "SampleSize:$sampleSize")
            if (sampleSize < 0) {
                break
            }

            bufferInfo.size = sampleSize
            bufferInfo.offset = 0
            bufferInfo.flags = mMediaExtractor.sampleFlags
            bufferInfo.presentationTimeUs += frameTime
            mediaMuxer.writeSampleData(trackIndex, byteBuffer, bufferInfo)
            mMediaExtractor.advance()
        }

        mediaMuxer.stop()
        mediaMuxer.release()
        mMediaExtractor.release()
        Log.e("ExtractorVideo", "onFinish")
    }

    /**
     * 根据MIME提取音频轨道可以播放
     */
    private fun extractorAudio() {
        Log.e("ExtractorAudio", " onStart")
        mMediaExtractor = MediaExtractor()
        mMediaExtractor.setDataSource(INPUT_FILE)
        var audioIndex = -1
        for (i in 0 until mMediaExtractor.trackCount) {
            val mediaFormat = mMediaExtractor.getTrackFormat(i)
            val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
            if (mime.startsWith("audio/")) {
                audioIndex = i
                break
            }
        }
        if (audioIndex == -1) {
            return
        }
        val audioMediaFormat = mMediaExtractor.getTrackFormat(audioIndex)
        mMediaExtractor.selectTrack(audioIndex)
        val muxer = MediaMuxer(OUTPUT_AUDIO_FILE, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val writeAudioIndex = muxer.addTrack(audioMediaFormat)
        muxer.start()

        val byteBuffer = ByteBuffer.allocate(500 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        var sampleTime: Long = 0
        mMediaExtractor.readSampleData(byteBuffer, 0)
        if (mMediaExtractor.sampleFlags == MediaExtractor.SAMPLE_FLAG_SYNC) {
            mMediaExtractor.advance()
        }
        mMediaExtractor.readSampleData(byteBuffer, 0)
        val firstTime = mMediaExtractor.sampleTime
        mMediaExtractor.advance()
        val secondeTime = mMediaExtractor.sampleTime
        sampleTime = abs(secondeTime - firstTime)

        //TODO to make time correct then sampleTime
//        val audioTimes = ArrayList<Long>()
//        while (true){
//            val readSampleSize = mMediaExtractor.readSampleData(byteBuffer, 0)
//            if (readSampleSize < 0){
//                break
//            }
//            audioTimes.add(mMediaExtractor.sampleTime)
//            mMediaExtractor.advance()
//        }
//
        mMediaExtractor.unselectTrack(audioIndex)
        mMediaExtractor.selectTrack(audioIndex)
//        var i = 0
        while (true) {
            val readSapmle = mMediaExtractor.readSampleData(byteBuffer, 0)
            if (readSapmle < 0) {
                break
            }
            mMediaExtractor.advance()
            bufferInfo.size = readSapmle
            bufferInfo.flags = mMediaExtractor.sampleFlags
            bufferInfo.offset = 0
//            bufferInfo.presentationTimeUs = audioTimes[i]
            bufferInfo.presentationTimeUs += sampleTime
//            i++
            muxer.writeSampleData(writeAudioIndex, byteBuffer, bufferInfo)
        }

        muxer.stop()
        muxer.release()
        mMediaExtractor.release()
        Log.e("ExtractorAudio", " onFinish")
    }


    /**
     * 根据MIME分离音频轨跟视频轨， 都不能播放
     */
    private fun extractorMedia() {
        mMediaExtractor = MediaExtractor()
        var videoOutputStream: FileOutputStream? = null
        var audioOutputStream: FileOutputStream? = null

        try {
            val videoFile = File(OUTPUT_VIDEO_FILE)
            if (videoFile.exists().not()) {
                videoFile.createNewFile()
            }

            val audioFile = File(OUTPUT_AUDIO_FILE)
            if (audioFile.exists().not()) {
                audioFile.createNewFile()
            }
            videoOutputStream = FileOutputStream(videoFile)
            audioOutputStream = FileOutputStream(audioFile)

            mMediaExtractor.setDataSource(INPUT_FILE)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            //根据mime获取制定的轨道index
            for (track in 0 until mMediaExtractor.trackCount) {
                val format: MediaFormat = mMediaExtractor.getTrackFormat(track)
                val mime = format.getString(MediaFormat.KEY_MIME)

                if (mime.startsWith("video/")) {
                    videoTrackIndex = track
                }
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = track
                }
            }

            //创建缓冲区
            val byteBuffer: ByteBuffer = ByteBuffer.allocate(500 * 1024)
            Log.e("Extractor", " onVideo")
            mMediaExtractor.selectTrack(videoTrackIndex)
            while (true) {
                val readSampleCount = mMediaExtractor.readSampleData(byteBuffer, 0)
                Log.e("Extractor", " onVideo readSampleCount:$readSampleCount")
                if (readSampleCount < 0) {
                    break
                }
                val buffer = ByteArray(readSampleCount)
                byteBuffer.get(buffer)
                videoOutputStream.write(buffer)
                byteBuffer.clear()
                mMediaExtractor.advance()
            }


            Log.e("Extractor", " onAudio")
            mMediaExtractor.selectTrack(audioTrackIndex)
            while (true) {
                val readSampleCount = mMediaExtractor.readSampleData(byteBuffer, 0)
                Log.e("Extractor", " onAudio readSampleCount:$readSampleCount")
                if (readSampleCount < 0) {
                    break
                }
                val buffer = ByteArray(readSampleCount)
                byteBuffer.get(buffer)
                audioOutputStream.write(buffer)
                byteBuffer.clear()
                mMediaExtractor.advance()
            }

        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            Log.e("Extractor", " onFinish")
            mMediaExtractor.release()
            videoOutputStream?.close()
            audioOutputStream?.close()
        }
    }

}