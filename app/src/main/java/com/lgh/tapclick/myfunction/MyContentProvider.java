package com.lgh.tapclick.myfunction;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

import cn.hutool.core.collection.ListUtil;

public class MyContentProvider extends ContentProvider {
    private final Handler handler;

    public MyContentProvider() {
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        return -1;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (TextUtils.equals(selection, "isServiceRunning")) {
            boolean isRunning = MyAccessibilityService.mainFunction != null;
            MatrixCursor matrixCursor = new MatrixCursor(new String[]{"isServiceRunning"});
            matrixCursor.addRow(new Object[]{isRunning ? 1 : 0});
            return matrixCursor;
        }
        if (TextUtils.equals(selection, "log")) {
            MatrixCursor matrixCursor = new MatrixCursor(new String[]{"log"});
            if (MyAccessibilityService.mainFunction != null) {
                matrixCursor.addRow(new Object[]{MyAccessibilityService.mainFunction.getLog()});
            } else {
                matrixCursor.addRow(new Object[]{"无障碍服务未开启"});
            }
            return matrixCursor;
        }
        return new MatrixCursor(new String[]{});
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (MyAccessibilityService.mainFunction != null) {
            MainFunction mainFunction = MyAccessibilityService.mainFunction;
            updateData(mainFunction, values);
            updateAllData(mainFunction, values);
            updateKeepAlive(mainFunction, values);
            showDbClickSetting(mainFunction, values);
            showDbClickFloating(mainFunction, values);
            showAddDataWindow(mainFunction, values);
        }
        return 1;
    }

    private void updateData(MainFunction mainFunction, ContentValues values) {
        String updateScope = values.getAsString("updateScope");
        String packageName = values.getAsString("packageName");
        if (TextUtils.isEmpty(updateScope) || TextUtils.isEmpty(packageName)) {
            return;
        }
        if (TextUtils.equals(updateScope, "updateAppDescribe")) {
            mainFunction.refreshAppDescribe(packageName);
        }
        if (TextUtils.equals(updateScope, "removeAppDescribe")) {
            List<String> packages = ListUtil.toList(packageName.split(","));
            mainFunction.removeAppDescribes(packages);
        }
    }

    private void updateKeepAlive(MainFunction mainFunction, ContentValues values) {
        String updateScope = values.getAsString("updateScope");
        Boolean value = values.getAsBoolean("value");
        if (TextUtils.isEmpty(updateScope) || Objects.isNull(value)) {
            return;
        }
        if (TextUtils.equals(updateScope, "keepAliveByNotification")) {
            MyUtils.setKeepAliveByNotification(value);
            mainFunction.keepAliveByNotification(value);
        }
        if (TextUtils.equals(updateScope, "keepAliveByFloatingWindow")) {
            MyUtils.setKeepAliveByFloatingWindow(value);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    mainFunction.keepAliveByFloatingWindow(value);
                }
            });
        }
    }

    private void updateAllData(MainFunction mainFunction, ContentValues values) {
        String updateScope = values.getAsString("updateScope");
        if (!TextUtils.equals(updateScope, "allDate")) {
            return;
        }
        mainFunction.refreshAllData();
    }

    private void showDbClickSetting(MainFunction mainFunction, ContentValues values) {
        String updateScope = values.getAsString("updateScope");
        if (!TextUtils.equals(updateScope, "showDbClickSetting")) {
            return;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                mainFunction.showDbClickSetting();
            }
        });
    }

    private void showDbClickFloating(MainFunction mainFunction, ContentValues values) {
        String updateScope = values.getAsString("updateScope");
        Boolean enable = values.getAsBoolean("value");
        if (!TextUtils.equals(updateScope, "showDbClickFloating") || Objects.isNull(enable)) {
            return;
        }
        MyUtils.setDbClickEnable(enable);
        handler.post(new Runnable() {
            @Override
            public void run() {
                mainFunction.showDbClickFloating(enable);
            }
        });
    }

    private void showAddDataWindow(MainFunction mainFunction, ContentValues values) {
        String updateScope = values.getAsString("updateScope");
        if (!TextUtils.equals(updateScope, "showAddDataWindow")) {
            return;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                mainFunction.showAddDataWindow(false);
            }
        });
    }
}
