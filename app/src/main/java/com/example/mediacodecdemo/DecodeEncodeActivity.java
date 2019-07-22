package com.example.mediacodecdemo;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mediacodecdemo.codec.CodecVideo;
import com.example.mediacodecdemo.codec.ReverseShortVideo;
import com.example.mediacodecdemo.codec.ReverseVideo;

import java.io.File;

/**
 * Created by liangbo.su@ubnt on 2019-07-12
 */
public class DecodeEncodeActivity extends AppCompatActivity {

    /**
     * Params for paths
     */
    private String SDCARD_PATH = Environment.getExternalStorageDirectory().getPath();
    private String INPUT_VIDEO_FILE = SDCARD_PATH + File.separator + "input.mp4";
    private String OUTPUT_VIDEO_FILE = SDCARD_PATH + File.separator + "output.mp4";


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_codec);
        findViewById(R.id.mediaCodec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
//                            new EncodeDecodeTest().testEncodeDecodeVideoFromBufferToBuffer720p();
                            new CodecVideo(INPUT_VIDEO_FILE, OUTPUT_VIDEO_FILE, new CodecVideo.CodecListener() {
                                @Override
                                public void onFinish() {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(DecodeEncodeActivity.this, " Codec is Finish", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });


        findViewById(R.id.longMediaReverseCodec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        new ReverseVideo(INPUT_VIDEO_FILE, OUTPUT_VIDEO_FILE);
                    }
                });
            }
        });
    }


}
