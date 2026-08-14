package com.lgh.tapclick.myactivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import com.lgh.tapclick.databinding.ActivitySettingBinding;
import com.lgh.tapclick.mybean.MyAppConfig;
import com.lgh.tapclick.myclass.MyApplication;
import com.lgh.tapclick.myfunction.MyUtils;

public class SettingActivity extends BaseActivity {
    private MyAppConfig myAppConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySettingBinding settingBinding = ActivitySettingBinding.inflate(getLayoutInflater());
        setContentView(settingBinding.getRoot());

        settingBinding.settingAutoHideOnTaskList.setEnabled(false);
        MyApplication.queryDatabase(() -> MyApplication.dataDao.getMyAppConfig(), result -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            myAppConfig = result;
            settingBinding.settingAutoHideOnTaskList.setChecked(myAppConfig.autoHideOnTaskList);
            settingBinding.settingAutoHideOnTaskList.setEnabled(true);
        });

        settingBinding.settingOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        settingBinding.settingAutoHideOnTaskList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myAppConfig == null) {
                    return;
                }
                myAppConfig.autoHideOnTaskList = settingBinding.settingAutoHideOnTaskList.isChecked();
                MyUtils.setExcludeFromRecents(myAppConfig.autoHideOnTaskList);
                MyApplication.executeDatabase(() -> MyApplication.dataDao.updateMyAppConfig(myAppConfig));
            }
        });
    }
}
