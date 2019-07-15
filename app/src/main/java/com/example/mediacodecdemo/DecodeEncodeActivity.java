package com.example.mediacodecdemo;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mediacodecdemo.codec.CodecVideo;
import com.example.mediacodecdemo.codec.ReverseCodec;

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
                            new CodecVideo(INPUT_VIDEO_FILE, OUTPUT_VIDEO_FILE);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });


        findViewById(R.id.mediaReverseCodec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        new ReverseCodec(INPUT_VIDEO_FILE, OUTPUT_VIDEO_FILE);
                    }
                });
            }
        });
    }




}
