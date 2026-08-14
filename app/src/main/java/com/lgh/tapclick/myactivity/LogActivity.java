package com.lgh.tapclick.myactivity;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.lgh.tapclick.BuildConfig;
import com.lgh.tapclick.databinding.ActivityLogBinding;
import com.lgh.tapclick.myclass.ExportFileManager;
import com.lgh.tapclick.myclass.MyApplication;
import com.lgh.tapclick.myfunction.MyUtils;

import java.io.File;

public class LogActivity extends BaseActivity {
    private ActivityLogBinding logBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logBinding = ActivityLogBinding.inflate(getLayoutInflater());
        setContentView(logBinding.getRoot());
        logBinding.export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String log = logBinding.log.getText().toString();
                MyApplication.executeIo(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            File file = ExportFileManager.writeText(getApplicationContext(), "log.txt", log);
                            MyApplication.postToMain(() -> shareFile(file));
                        } catch (Exception e) {
                            MyApplication.postToMain(() -> Toast.makeText(getApplicationContext(),
                                    "生成日志文件时发生错误", Toast.LENGTH_SHORT).show());
                        }
                    }
                });
            }
        });
    }

    private void shareFile(File file) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", file);
        intent.setDataAndType(uri, "text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newUri(getContentResolver(), "log", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "导出"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        logBinding.log.setText(MyUtils.getLog());
        logBinding.scroll.post(() -> logBinding.scroll.fullScroll(View.FOCUS_DOWN));
    }
}
