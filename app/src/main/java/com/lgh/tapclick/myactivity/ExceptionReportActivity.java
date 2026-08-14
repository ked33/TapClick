package com.lgh.tapclick.myactivity;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.lgh.tapclick.BuildConfig;
import com.lgh.tapclick.databinding.ActivityExceptionReportBinding;
import com.lgh.tapclick.myclass.ExportFileManager;
import com.lgh.tapclick.myclass.MyApplication;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class ExceptionReportActivity extends BaseActivity {
    private ActivityExceptionReportBinding exceptionReportBinding;
    private File exceptionFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        exceptionReportBinding = ActivityExceptionReportBinding.inflate(getLayoutInflater());
        setContentView(exceptionReportBinding.getRoot());
        exceptionFile = new File(getFilesDir(), "exception.txt");
        exceptionReportBinding.export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportException();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        MyApplication.executeIo(new Runnable() {
            @Override
            public void run() {
                try {
                    String message = FileUtils.readFileToString(exceptionFile, StandardCharsets.UTF_8);
                    MyApplication.postToMain(() -> exceptionReportBinding.exception.setText(message));
                } catch (Exception e) {
                    MyApplication.postToMain(() -> Toast.makeText(ExceptionReportActivity.this,
                            "未找到可用的异常报告", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void exportException() {
        MyApplication.executeIo(new Runnable() {
            @Override
            public void run() {
                try {
                    File exported = ExportFileManager.copy(getApplicationContext(), exceptionFile, "exception.txt");
                    MyApplication.postToMain(() -> shareFile(exported));
                } catch (Exception e) {
                    MyApplication.postToMain(() -> Toast.makeText(ExceptionReportActivity.this,
                            "导出异常报告失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void shareFile(File file) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setDataAndType(uri, "text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newUri(getContentResolver(), "exception", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "导出"));
    }
}
