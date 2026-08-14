package com.lgh.tapclick.myactivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.lgh.tapclick.R;
import com.lgh.tapclick.databinding.ActivityAuthorizationBinding;
import com.lgh.tapclick.myfunction.MyUtils;

public class AuthorizationActivity extends BaseActivity {
    private static final int REQUEST_POST_NOTIFICATIONS = 0x01;

    private ActivityAuthorizationBinding authorizationBinding;
    private Context context;
    private PackageManager packageManager;
    private NotificationManager notificationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authorizationBinding = ActivityAuthorizationBinding.inflate(getLayoutInflater());
        setContentView(authorizationBinding.getRoot());
        context = getApplicationContext();
        packageManager = getPackageManager();
        notificationManager = getSystemService(NotificationManager.class);

        View.OnClickListener onOffClickListener = new View.OnClickListener() {
            @SuppressLint("NonConstantResourceId")
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.accessibility_on_off:
                        openAccessibilitySettings();
                        break;
                    case R.id.notification_on_off:
                        toggleNotificationKeepAlive();
                        break;
                    case R.id.floating_window_on_off:
                        boolean enabled = !MyUtils.getKeepAliveByFloatingWindow();
                        MyUtils.setKeepAliveByFloatingWindow(enabled);
                        MyUtils.requestUpdateKeepAliveByFloatingWindow(enabled);
                        Toast.makeText(context, enabled ? "已开启" : "已关闭", Toast.LENGTH_SHORT).show();
                        refreshStates();
                        break;
                }
            }
        };
        authorizationBinding.accessibilityOnOff.setOnClickListener(onOffClickListener);
        authorizationBinding.notificationOnOff.setOnClickListener(onOffClickListener);
        authorizationBinding.floatingWindowOnOff.setOnClickListener(onOffClickListener);
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(context, "授权窗口打开失败，请手动打开", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleNotificationKeepAlive() {
        if (!notificationManager.areNotificationsEnabled()) {
            MyUtils.setKeepAliveByNotification(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
            } else {
                openNotificationSettings();
            }
            return;
        }
        boolean enabled = !MyUtils.getKeepAliveByNotification();
        MyUtils.setKeepAliveByNotification(enabled);
        MyUtils.requestUpdateKeepAliveByNotification(enabled);
        Toast.makeText(context, enabled ? "已开启" : "已关闭", Toast.LENGTH_SHORT).show();
        refreshStates();
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(context, "授权窗口打开失败，请手动打开", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_POST_NOTIFICATIONS) {
            return;
        }
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        MyUtils.setKeepAliveByNotification(granted);
        MyUtils.requestUpdateKeepAliveByNotification(granted);
        Toast.makeText(context, granted ? "已开启" : "通知权限未授予", Toast.LENGTH_SHORT).show();
        refreshStates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStates();
    }

    private void refreshStates() {
        authorizationBinding.accessibilityOnOffImg.setImageResource(MyUtils.isServiceRunning() ? R.drawable.ic_ok : R.drawable.ic_error);
        authorizationBinding.notificationOnOffImg.setImageResource(
                notificationManager.areNotificationsEnabled() && MyUtils.getKeepAliveByNotification()
                        ? R.drawable.ic_ok : R.drawable.ic_error);
        authorizationBinding.floatingWindowOnOffImg.setImageResource(
                MyUtils.getKeepAliveByFloatingWindow() ? R.drawable.ic_ok : R.drawable.ic_error);
    }
}
