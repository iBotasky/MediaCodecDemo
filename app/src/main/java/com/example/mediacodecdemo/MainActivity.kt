package com.example.mediacodecdemo

import android.Manifest
import android.media.MediaExtractor
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.ByteBuffer


class MainActivity : AppCompatActivity() {
    companion object {
        val SDCARD_PATH: String = Environment.getExternalStorageDirectory().path
        const val MP4_FILE: String = "ss.mp4"
        const val OUTPUT_FILE: String = "output.mp4"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val checkWriteExternalPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val checkReadExternalPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (checkWriteExternalPermission != PackageManager.PERMISSION_GRANTED || checkReadExternalPermission != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                0
            )
        }

        btnStart.setOnClickListener {
            AsyncTask.THREAD_POOL_EXECUTOR.execute {
                val mediaExtractor = MediaExtractor()
                mediaExtractor.setDataSource(SDCARD_PATH + File.separator + MP4_FILE)
                var videoMediaMuxer: MediaMuxer? = null
                var mVideoTrackIndex = -1
                var frameRate = 0

                for (i in 0 until mediaExtractor.trackCount) {
                    val format: MediaFormat = mediaExtractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (!mime.startsWith("video/"))
                        continue
                    videoMediaMuxer =
                        MediaMuxer(
                            SDCARD_PATH + File.separator + OUTPUT_FILE,
                            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                        )
                    frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE)
                    mVideoTrackIndex = videoMediaMuxer.addTrack(format)
                    mediaExtractor.selectTrack(i)
                }

                Log.e("OnProcess", "frameRate:$frameRate mVideoIndex:$mVideoTrackIndex")
                if (videoMediaMuxer == null) {
                    return@execute
                }
                videoMediaMuxer.start()
                val info = MediaCodec.BufferInfo()
                info.presentationTimeUs = 0
                val buffer = ByteBuffer.allocate(500 * 1024)
                var sampleSize = 0

                while (true) {
                    sampleSize = mediaExtractor.readSampleData(buffer, 0)
                    if (sampleSize <= 0) break
                    Log.e("OnProcess", "sampleSize:$sampleSize")
                    info.offset = 0
                    info.size = sampleSize
                    info.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME
                    info.presentationTimeUs += 1000 * 1000 / frameRate
                    videoMediaMuxer.writeSampleData(mVideoTrackIndex, buffer, info)
                    mediaExtractor.advance()
                }
                mediaExtractor.release()
                videoMediaMuxer.stop()
                videoMediaMuxer.release()
            }
        }
    }
}
