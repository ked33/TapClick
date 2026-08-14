package com.lgh.tapclick.myactivity;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lgh.tapclick.R;
import com.lgh.tapclick.databinding.ActivityMainBinding;
import com.lgh.tapclick.databinding.ViewAccessibilityStatementBinding;
import com.lgh.tapclick.databinding.ViewMainItemBinding;
import com.lgh.tapclick.databinding.ViewNewRuleBinding;
import com.lgh.tapclick.databinding.ViewPrivacyAgreementBinding;
import com.lgh.tapclick.mybean.AppDescribe;
import com.lgh.tapclick.mybean.CoordinateShare;
import com.lgh.tapclick.mybean.Regulation;
import com.lgh.tapclick.mybean.RegulationExport;
import com.lgh.tapclick.mybean.WidgetShare;
import com.lgh.tapclick.myclass.DataDao;
import com.lgh.tapclick.myclass.MyApplication;
import com.lgh.tapclick.myclass.RegulationImportStore;
import com.lgh.tapclick.myfunction.MyUtils;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends BaseActivity {

    private Context context;
    private DataDao dataDao;
    private Handler handler;
    private LayoutInflater inflater;
    private ActivityMainBinding mainBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        dataDao = MyApplication.dataDao;
        handler = new Handler(Looper.getMainLooper());
        mainBinding = ActivityMainBinding.inflate(inflater = getLayoutInflater());
        setContentView(mainBinding.getRoot());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAndRemoveTask();
            }
        });

        final List<Resource> source = new ArrayList<>();
        source.add(new Resource("授权管理", R.drawable.authorization));
        source.add(new Resource("创建规则", R.drawable.add_data));
        source.add(new Resource("规则管理", R.drawable.edit_data));
        source.add(new Resource("运行日志", R.drawable.log));
        source.add(new Resource("应用设置", R.drawable.setting));
        BaseAdapter baseAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return source.size();
            }

            @Override
            public Object getItem(int position) {
                return position;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ViewMainItemBinding itemBinding = ViewMainItemBinding.inflate(inflater);
                Resource resource = source.get(position);
                itemBinding.mainImg.setImageResource(resource.drawableId);
                itemBinding.mainName.setText(resource.name);
                return itemBinding.getRoot();
            }
        };
        mainBinding.mainListView.setAdapter(baseAdapter);
        mainBinding.mainListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: {
                        startActivity(new Intent(context, AuthorizationActivity.class));
                        break;
                    }
                    case 1: {
                        startActivity(new Intent(context, AddDataActivity.class));
                        break;
                    }
                    case 2: {
                        startActivity(new Intent(context, ListDataActivity.class));
                        break;
                    }
                    case 3: {
                        startActivity(new Intent(context, LogActivity.class));
                        break;
                    }
                    case 4: {
                        Intent intent = new Intent(context, SettingActivity.class);
                        startActivity(intent);
                        break;
                    }
                }
            }
        });

        if (MyUtils.getIsFirstStart()) {
            showPrivacyAgreement();
        }
        MyApplication.queryDatabase(dataDao::getMyAppConfig, config -> {
            if (config.autoHideOnTaskList) {
                MyUtils.setExcludeFromRecents(true);
            }
        });
        handleImportRule(getIntent());
        // 触发允许读取应用列表授权弹窗
        getPackageManager().getInstalledPackages(PackageManager.GET_META_DATA);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(new Runnable() {
            @Override
            public void run() {
                refreshAccessibilityServiceStatus();
            }
        });
    }

    private void refreshAccessibilityServiceStatus() {
        if (MyUtils.isServiceRunning()) {
            mainBinding.statusImg.setImageResource(R.drawable.ic_ok);
            mainBinding.statusTip.setText("无障碍服务已开启");
        } else {
            mainBinding.statusImg.setImageResource(R.drawable.ic_error);
            mainBinding.statusTip.setText("无障碍服务未开启");
        }
    }

    private void showPrivacyAgreement() {
        ViewPrivacyAgreementBinding privacyAgreementBinding = ViewPrivacyAgreementBinding.inflate(getLayoutInflater());
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setCancelable(false).setView(privacyAgreementBinding.getRoot()).create();
        privacyAgreementBinding.content.setText(R.string.privacyAgreement);
        privacyAgreementBinding.sure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.dismiss();
                showAccessibilityStatement();
            }
        });
        privacyAgreementBinding.cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.dismiss();
                finishAndRemoveTask();
            }
        });
        Window window = alertDialog.getWindow();
        window.setBackgroundDrawableResource(R.drawable.add_data_background);
        alertDialog.show();
        WindowManager.LayoutParams lp = window.getAttributes();
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        lp.width = metrics.widthPixels / 5 * 4;
        lp.height = metrics.heightPixels / 5 * 2;
        window.setAttributes(lp);
    }

    private void showAccessibilityStatement() {
        ViewAccessibilityStatementBinding accessibilityStatementBinding = ViewAccessibilityStatementBinding.inflate(getLayoutInflater());
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setCancelable(false).setView(accessibilityStatementBinding.getRoot()).create();
        accessibilityStatementBinding.content.setText(R.string.accessibilityStatement);
        accessibilityStatementBinding.sure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MyUtils.setIsFirstStart(false);
                alertDialog.dismiss();
            }
        });
        accessibilityStatementBinding.cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.dismiss();
                finishAndRemoveTask();
            }
        });
        Window window = alertDialog.getWindow();
        window.setBackgroundDrawableResource(R.drawable.add_data_background);
        alertDialog.show();
        WindowManager.LayoutParams lp = window.getAttributes();
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        lp.width = metrics.widthPixels / 5 * 4;
        lp.height = metrics.heightPixels / 5 * 2;
        window.setAttributes(lp);
    }

    private void handleImportRule(Intent intent) {
        try {
            String strRule = intent.getStringExtra(Intent.EXTRA_TEXT);

            if (TextUtils.isEmpty(strRule)) {
                Uri uri = intent.getData();
                if (uri == null) {
                    uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                }
                if (uri == null && intent.getClipData() != null) {
                    for (int n = 0; n < intent.getClipData().getItemCount(); n++) {
                        ClipData.Item item = intent.getClipData().getItemAt(n);
                        if (item.getUri() != null) {
                            uri = item.getUri();
                            break;
                        }
                    }
                }
                if (uri != null) {
                    StringBuilder stringBuilder = new StringBuilder();
                    Scanner scanner = new Scanner(getContentResolver().openInputStream(uri));
                    while (scanner.hasNextLine()) {
                        stringBuilder.append(scanner.nextLine());
                    }
                    strRule = stringBuilder.toString().trim();
                    scanner.close();
                }
            }

            if (TextUtils.isEmpty(strRule)) {
                return;
            }
            String regStr = "^\"(" + WidgetShare.class.getSimpleName() + "|" + CoordinateShare.class.getSimpleName() + "|" + RegulationExport.class.getSimpleName() + ")\"\\s*:\\s*(.+)$";
            Pattern pattern = Pattern.compile(regStr, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(strRule);
            if (!matcher.matches()) {
                Toast.makeText(context, "无效的规则", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.equals(matcher.group(1), WidgetShare.class.getSimpleName())) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                WidgetShare widgetShare = gson.fromJson(matcher.group(2), WidgetShare.class);
                PackageInfo packageInfo = null;
                String appName = "";
                try {
                    packageInfo = getPackageManager().getPackageInfo(widgetShare.widget.appPackage, PackageManager.GET_META_DATA);
                    appName = getPackageManager().getApplicationLabel(packageInfo.applicationInfo).toString();
                } catch (PackageManager.NameNotFoundException e) {
                    // e.printStackTrace();
                }
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("应用包名：").append(widgetShare.widget.appPackage).append(!appName.isEmpty() ? String.format("（%s）", appName) : "（无权限或未安装）").append("\n\n");
                stringBuilder.append("我的系统指纹：").append(Build.FINGERPRINT).append("\n");
                stringBuilder.append("他的系统指纹：").append(widgetShare.basicContent.fingerPrint).append("\n\n");
                DisplayMetrics displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
                stringBuilder.append("我的手机屏幕：").append(displayMetrics).append("\n");
                stringBuilder.append("他的手机屏幕：").append(widgetShare.basicContent.displayMetrics).append("\n\n");
                stringBuilder.append("我的应用版本名：").append(packageInfo != null ? packageInfo.versionName : "无权限或未安装").append("\n");
                stringBuilder.append("他的应用版本名：").append(widgetShare.basicContent.versionName).append("\n\n");
                stringBuilder.append("我的应用版本号：").append(packageInfo != null ? packageInfo.versionCode : "无权限或未安装").append("\n");
                stringBuilder.append("他的应用版本号：").append(widgetShare.basicContent.versionCode).append("\n\n");
                stringBuilder.append("控件内容：").append(gson.toJson(widgetShare.widget));

                ViewNewRuleBinding newRuleBinding = ViewNewRuleBinding.inflate(getLayoutInflater());
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setCancelable(false).setView(newRuleBinding.getRoot()).create();
                newRuleBinding.sure.setTag(appName);
                newRuleBinding.content.setText(stringBuilder.toString());
                newRuleBinding.sure.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                        String importedAppName = String.valueOf(v.getTag());
                        MyApplication.executeDatabase(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    AppDescribe appDescribe = dataDao.importWidget(widgetShare.widget, importedAppName);
                                    MyApplication.postToMain(() -> finishSingleRuleImport(appDescribe));
                                } catch (RuntimeException e) {
                                    MyApplication.postToMain(() -> Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show());
                                }
                            }
                        });
                    }
                });
                newRuleBinding.cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                    }
                });
                Window window = alertDialog.getWindow();
                window.setBackgroundDrawableResource(R.drawable.add_data_background);
                alertDialog.show();
                WindowManager.LayoutParams lp = window.getAttributes();
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                lp.width = metrics.widthPixels / 5 * 4;
                lp.height = metrics.heightPixels / 2;
                window.setAttributes(lp);
            } else if (TextUtils.equals(matcher.group(1), CoordinateShare.class.getSimpleName())) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                CoordinateShare coordinateShare = gson.fromJson(matcher.group(2), CoordinateShare.class);
                PackageInfo packageInfo = null;
                String appName = "";
                try {
                    packageInfo = getPackageManager().getPackageInfo(coordinateShare.coordinate.appPackage, PackageManager.GET_META_DATA);
                    appName = getPackageManager().getApplicationLabel(packageInfo.applicationInfo).toString();
                } catch (PackageManager.NameNotFoundException e) {
                    // e.printStackTrace();
                }
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("应用包名：").append(coordinateShare.coordinate.appPackage).append(!appName.isEmpty() ? String.format("（%s）", appName) : "（无权限或未安装）").append("\n\n");
                stringBuilder.append("我的系统指纹：").append(Build.FINGERPRINT).append("\n");
                stringBuilder.append("他的系统指纹：").append(coordinateShare.basicContent.fingerPrint).append("\n\n");
                DisplayMetrics displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
                stringBuilder.append("我的手机屏幕：").append(displayMetrics).append("\n");
                stringBuilder.append("他的手机屏幕：").append(coordinateShare.basicContent.displayMetrics).append("\n\n");
                stringBuilder.append("我的应用版本名：").append(packageInfo != null ? packageInfo.versionName : "无权限或未安装").append("\n");
                stringBuilder.append("他的应用版本名：").append(coordinateShare.basicContent.versionName).append("\n\n");
                stringBuilder.append("我的应用版本号：").append(packageInfo != null ? packageInfo.versionCode : "无权限或未安装").append("\n");
                stringBuilder.append("他的应用版本号：").append(coordinateShare.basicContent.versionCode).append("\n\n");
                stringBuilder.append("坐标内容：").append(gson.toJson(coordinateShare.coordinate));

                ViewNewRuleBinding newRuleBinding = ViewNewRuleBinding.inflate(getLayoutInflater());
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setCancelable(false).setView(newRuleBinding.getRoot()).create();
                newRuleBinding.sure.setTag(appName);
                newRuleBinding.content.setText(stringBuilder.toString());
                newRuleBinding.sure.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                        String importedAppName = String.valueOf(v.getTag());
                        MyApplication.executeDatabase(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    AppDescribe appDescribe = dataDao.importCoordinate(coordinateShare.coordinate, importedAppName);
                                    MyApplication.postToMain(() -> finishSingleRuleImport(appDescribe));
                                } catch (RuntimeException e) {
                                    MyApplication.postToMain(() -> Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show());
                                }
                            }
                        });
                    }
                });
                newRuleBinding.cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                    }
                });
                Window window = alertDialog.getWindow();
                window.setBackgroundDrawableResource(R.drawable.add_data_background);
                alertDialog.show();
                WindowManager.LayoutParams lp = window.getAttributes();
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                lp.width = metrics.widthPixels / 5 * 4;
                lp.height = metrics.heightPixels / 2;
                window.setAttributes(lp);
            } else if (TextUtils.equals(matcher.group(1), RegulationExport.class.getSimpleName())) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                RegulationExport regulationExport = gson.fromJson(matcher.group(2), RegulationExport.class);

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("我的系统指纹：").append(Build.FINGERPRINT).append("\n");
                stringBuilder.append("他的系统指纹：").append(regulationExport.fingerPrint).append("\n\n");
                DisplayMetrics displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
                stringBuilder.append("我的手机屏幕：").append(displayMetrics).append("\n");
                stringBuilder.append("他的手机屏幕：").append(regulationExport.displayMetrics).append("\n\n");
                int coordinateNum = 0;
                int widgetNum = 0;
                for (Regulation e : regulationExport.regulationList) {
                    coordinateNum += e.coordinateList.size();
                    widgetNum += e.widgetList.size();
                }
                stringBuilder.append(String.format(Locale.ROOT, "共%d个应用，%d条控件规则，%d条坐标规则。", regulationExport.regulationList.size(), widgetNum, coordinateNum));

                ViewNewRuleBinding newRuleBinding = ViewNewRuleBinding.inflate(getLayoutInflater());
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setCancelable(false).setView(newRuleBinding.getRoot()).create();
                newRuleBinding.content.setText(stringBuilder);
                newRuleBinding.sure.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                        MyApplication.executeIo(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    RegulationImportStore.write(getApplicationContext(), regulationExport.regulationList);
                                    MyApplication.postToMain(new Runnable() {
                                        @Override
                                        public void run() {
                                            Intent intent = new Intent(MainActivity.this, RegulationImportActivity.class);
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                            startActivity(intent);
                                        }
                                    });
                                } catch (Exception e) {
                                    MyApplication.postToMain(() -> Toast.makeText(context, "暂存导入规则失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
                                }
                            }
                        });
                    }
                });
                newRuleBinding.cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                    }
                });
                Window window = alertDialog.getWindow();
                window.setBackgroundDrawableResource(R.drawable.add_data_background);
                alertDialog.show();
                WindowManager.LayoutParams lp = window.getAttributes();
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                lp.width = metrics.widthPixels / 5 * 4;
                lp.height = metrics.heightPixels / 2;
                window.setAttributes(lp);
            }
        } catch (RuntimeException | FileNotFoundException e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void finishSingleRuleImport(AppDescribe appDescribe) {
        MyUtils.requestUpdateAppDescribe(appDescribe.appPackage);
        Intent intent = new Intent(context, EditDataActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("packageName", appDescribe.appPackage);
        startActivity(intent);
        Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show();
    }

    static class Resource {
        public String name;
        public int drawableId;

        public Resource(String name, int drawableId) {
            this.name = name;
            this.drawableId = drawableId;
        }
    }
}
