package com.lgh.tapclick.myclass;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;

import com.lgh.tapclick.BuildConfig;
import com.lgh.tapclick.R;
import com.lgh.tapclick.myactivity.ExceptionReportActivity;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public final class MyUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static volatile MyUncaughtExceptionHandler instance;

    private final Context context;
    private Thread.UncaughtExceptionHandler previousHandler;

    private MyUncaughtExceptionHandler(Context context) {
        this.context = context.getApplicationContext();
    }

    public static MyUncaughtExceptionHandler getInstance(Context context) {
        if (instance == null) {
            synchronized (MyUncaughtExceptionHandler.class) {
                if (instance == null) {
                    instance = new MyUncaughtExceptionHandler(context);
                }
            }
        }
        return instance;
    }

    public synchronized void install() {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current == this) {
            return;
        }
        previousHandler = current;
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
        try {
            createExceptionNotification(throwable);
        } catch (Throwable ignored) {
        }
        if (previousHandler != null) {
            previousHandler.uncaughtException(thread, throwable);
            return;
        }
        Process.killProcess(Process.myPid());
        System.exit(10);
    }

    private synchronized void createExceptionNotification(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String message = Build.FINGERPRINT + "\n"
                + "BuildCommit: " + BuildConfig.BUILD_COMMIT + "\n"
                + "VersionName: " + BuildConfig.VERSION_NAME + "\n"
                + "VersionCode: " + BuildConfig.VERSION_CODE + "\n"
                + stringWriter;
        try {
            File file = new File(context.getFilesDir(), "exception.txt");
            FileUtils.writeStringToFile(file, message, StandardCharsets.UTF_8, false);
        } catch (IOException ignored) {
        }

        try {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            Intent intent = new Intent(context, ExceptionReportActivity.class);
            Notification.Builder builder = new Notification.Builder(context)
                    .setContentIntent(PendingIntent.getActivity(context, 0x01, intent,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                    .setAutoCancel(true)
                    .setSmallIcon(R.drawable.app)
                    .setContentTitle(context.getText(R.string.appName) + "发生异常")
                    .setContentText(throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String channelId = context.getPackageName() + ".crash";
                builder.setChannelId(channelId);
                NotificationChannel channel = new NotificationChannel(
                        channelId, context.getString(R.string.appName) + "异常报告", NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
            }
            notificationManager.notify(context.getPackageName() + ".crash", 0x01, builder.build());
        } catch (RuntimeException ignored) {
        }
    }
}
