package com.lgh.tapclick.myactivity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lgh.tapclick.BuildConfig;
import com.lgh.tapclick.R;
import com.lgh.tapclick.databinding.ActivityListDataBinding;
import com.lgh.tapclick.databinding.ViewEditFileNameBinding;
import com.lgh.tapclick.databinding.ViewItemAppBinding;
import com.lgh.tapclick.databinding.ViewOnOffWarningBinding;
import com.lgh.tapclick.mybean.AppDescribe;
import com.lgh.tapclick.mybean.Regulation;
import com.lgh.tapclick.mybean.RegulationExport;
import com.lgh.tapclick.myclass.DataDao;
import com.lgh.tapclick.myclass.ExportFileManager;
import com.lgh.tapclick.myclass.MyApplication;
import com.lgh.tapclick.myfunction.MyUtils;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;

public class ListDataActivity extends BaseActivity {

    private final MyAdapter myAdapter = new MyAdapter();
    private final List<AppDescribe> appDescribeList = new ArrayList<>();
    private final List<AppDescribeItem> appDescribeItemList = new ArrayList<>();
    private final List<AppDescribeItem> appDescribeItemFilterList = new ArrayList<>();
    private final Set<AppDescribeItem> appDescribeItemSelectedSet = new HashSet<>();
    private final Set<String> pkgSuggestNotOnList = new HashSet<>();
    private ActivityResultLauncher<Intent> itemResultLauncher;
    private ActivityListDataBinding listDataBinding;
    private Context context;
    private DataDao dataDao;
    private PackageManager packageManager;
    private AppDescribeItem curAppDescribeItem;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        listDataBinding = ActivityListDataBinding.inflate(getLayoutInflater());
        listDataBinding.llSelect.setVisibility(View.GONE);
        setContentView(listDataBinding.getRoot());
        context = getApplicationContext();
        dataDao = MyApplication.dataDao;
        packageManager = getPackageManager();

        itemResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                if (result.getData() == null || result.getResultCode() != RESULT_OK) {
                    return;
                }
                if (curAppDescribeItem == null) {
                    return;
                }
                String packageName = result.getData().getStringExtra("packageName");
                AppDescribeItem itemToRefresh = curAppDescribeItem;
                MyApplication.queryDatabase(() -> {
                    AppDescribe appDescribe = dataDao.getAppDescribeByPackage(packageName);
                    if (appDescribe != null) {
                        appDescribe.getOtherFieldsFromDatabase(dataDao);
                    }
                    return appDescribe;
                }, appDescribe -> {
                    if (appDescribe == null || isFinishing() || isDestroyed()) {
                        return;
                    }
                    itemToRefresh.appDescribe.copy(appDescribe);
                    itemToRefresh.refreshExistLongNoTrigger();
                    int position = appDescribeItemFilterList.indexOf(itemToRefresh);
                    if (position >= 0) {
                        myAdapter.notifyItemChanged(position);
                    }
                });
            }
        });

        if (!MyUtils.isServiceRunning()) {
            Toast.makeText(context, "无障碍服务未开启", Toast.LENGTH_SHORT).show();
        }

        Set<String> pkgSysSet = packageManager.
                getInstalledPackages(PackageManager.MATCH_SYSTEM_ONLY)
                .stream().map(e -> e.packageName)
                .collect(Collectors.toSet());
        Set<String> pkgInputMethodSet = getSystemService(InputMethodManager.class)
                .getInputMethodList()
                .stream()
                .map(InputMethodInfo::getPackageName)
                .collect(Collectors.toSet());
        Set<String> pkgHasHomeSet = packageManager
                .queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
                .stream()
                .map(e -> e.activityInfo.packageName)
                .collect(Collectors.toSet());
        pkgSuggestNotOnList.addAll(pkgSysSet);
        pkgSuggestNotOnList.addAll(pkgInputMethodSet);
        pkgSuggestNotOnList.addAll(pkgHasHomeSet);

        listDataBinding.recyclerView.setAdapter(myAdapter);

        List<String> searchKeyword = new ArrayList<>();
        searchKeyword.add("@开启");
        searchKeyword.add("@关闭");
        searchKeyword.add("@普通应用");
        searchKeyword.add("@系统应用");
        searchKeyword.add("@已创建规则");
        searchKeyword.add("@未创建规则");
        searchKeyword.add("@已安装应用");
        searchKeyword.add("@未安装应用");
        searchKeyword.add("@已选中选项");
        searchKeyword.add("@未选中选项");
        searchKeyword.add("@非必要不开启应用");
        listDataBinding.searchBox.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, searchKeyword));
        listDataBinding.searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String constraint = s.toString().trim();
                List<AppDescribeItem> listTemp = new ArrayList<>();
                switch (constraint) {
                    case "@开启":
                        for (AppDescribeItem e : appDescribeItemList) {
                            if (e.appDescribe.coordinateOnOff || e.appDescribe.widgetOnOff) {
                                listTemp.add(e);
                            }
                        }
                        break;
                    case "@关闭":
                        for (AppDescribeItem e : appDescribeItemList) {
                            if (!e.appDescribe.coordinateOnOff && !e.appDescribe.widgetOnOff) {
                                listTemp.add(e);
                            }
                        }
                        break;
                    case "@普通应用":
                        for (AppDescribeItem e : appDescribeItemList) {
                            if (!e.isSysApp) {
                                listTemp.add(e);
                            }
                        }
                        break;
                    case "@系统应用":
                        for (AppDescribeItem e : appDescribeItemList) {
                            if (e.isSysApp) {
                                listTemp.add(e);
                            }
                        }
                        break;
                    case "@已创建规则":
                        for (AppDescribeItem e : appDescribeItemList) {
                            if (!e.appDescribe.coordinateList.isEmpty() || !e.appDescribe.widgetList.isEmpty()) {
                                listTemp.add(e);
                            }
                        }
                        break;
                    case "@未创建规则":
                        for (AppDescribeItem e : appDescribeItemList) {
                            if (e.appDescribe.coordinateList.isEmpty() && e.appDescribe.widgetList.isEmpty()) {
                                listTemp.add(e);
                            }
                        }
                        break;
                    case "@已安装应用":
                        for (AppDescribeItem e : appDescribeItemList) {
                            if (e.isInstalled) {
                                listTemp.add(e);
                            }
                        }
                        break;
                    case "@未安装应用":
                        for (AppDescribeItem e : appDescribeItemList) {
                            if (!e.isInstalled) {
                                listTemp.add(e);
                            }
                        }
                        break;
                    case "@已选中选项":
                        for (AppDescribeItem e : appDescribeItemList) {
                            if (e.isSelected) {
                                listTemp.add(e);
                            }
                        }
                        break;
                    case "@未选中选项":
                        for (AppDescribeItem e : appDescribeItemList) {
                            if (!e.isSelected) {
                                listTemp.add(e);
                            }
                        }
                        break;
                    case "@非必要不开启应用":
                        for (AppDescribeItem e : appDescribeItemList) {
                            if (pkgSuggestNotOnList.contains(e.appDescribe.appPackage)) {
                                listTemp.add(e);
                            }
                        }
                        break;
                    default:
                        String str = constraint.toLowerCase();
                        for (AppDescribeItem e : appDescribeItemList) {
                            if (e.appDescribe.appName.toLowerCase().contains(str) || e.appDescribe.appPackage.contains(str)) {
                                listTemp.add(e);
                            }
                        }
                        break;
                }
                appDescribeItemFilterList.clear();
                appDescribeItemFilterList.addAll(listTemp);
                listDataBinding.cbSelectAll.setChecked(false);
                myAdapter.notifyDataSetChanged();
            }
        });

        listDataBinding.recyclerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_MOVE) {
                    v.requestFocus();
                }
                return false;
            }
        });

        listDataBinding.btCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appDescribeItemSelectedSet.clear();
                appDescribeItemList.forEach(e -> e.isSelected = false);
                listDataBinding.cbSelectAll.setChecked(false);
                listDataBinding.llSelect.setVisibility(View.GONE);
                myAdapter.notifyDataSetChanged();
            }
        });

        listDataBinding.btDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(ListDataActivity.this)
                        .setMessage(String.format(Locale.ROOT, "确定要删除选中的%d条数据吗？", appDescribeItemSelectedSet.size()))
                        .setNegativeButton("取消", null)
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                List<AppDescribe> appDescribeSelectedList = appDescribeItemSelectedSet.stream()
                                        .map(e -> e.appDescribe)
                                        .collect(Collectors.toList());
                                List<String> packages = appDescribeSelectedList.stream()
                                        .map(e -> e.appPackage)
                                        .collect(Collectors.toList());
                                Set<AppDescribeItem> selectedItems = new HashSet<>(appDescribeItemSelectedSet);
                                MyApplication.executeDatabase(new Runnable() {
                                    @Override
                                    public void run() {
                                        dataDao.deleteRegulations(appDescribeSelectedList);
                                        MyApplication.postToMain(new Runnable() {
                                            @Override
                                            public void run() {
                                                appDescribeList.removeAll(appDescribeSelectedList);
                                                appDescribeItemList.removeAll(selectedItems);
                                                appDescribeItemFilterList.removeAll(selectedItems);
                                                appDescribeItemSelectedSet.clear();
                                                MyUtils.requestRemoveAppDescribes(packages);
                                                listDataBinding.cbSelectAll.setChecked(false);
                                                listDataBinding.tvSelectedNum.setText(String.format(Locale.ROOT, "已选%d项", 0));
                                                myAdapter.notifyDataSetChanged();
                                            }
                                        });
                                    }
                                });
                            }
                        }).show();
            }
        });

        listDataBinding.btExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RegulationExport regulationExport = new RegulationExport();
                regulationExport.fingerPrint = Build.FINGERPRINT;
                regulationExport.displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(regulationExport.displayMetrics);
                for (AppDescribeItem appdescribeItem : appDescribeItemSelectedSet) {
                    Regulation regulation = new Regulation();
                    regulation.appDescribe = appdescribeItem.appDescribe;
                    regulation.coordinateList = appdescribeItem.appDescribe.coordinateList;
                    regulation.widgetList = appdescribeItem.appDescribe.widgetList;
                    regulationExport.regulationList.add(regulation);
                }
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String strRegulation = '"' + RegulationExport.class.getSimpleName() + '"' + ": " + gson.toJson(regulationExport);
                showEditShareFileNameDialog(strRegulation);
            }
        });

        listDataBinding.cbSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!listDataBinding.cbSelectAll.isChecked()) {
                    appDescribeItemSelectedSet.clear();
                }
                for (AppDescribeItem e : appDescribeItemFilterList) {
                    e.isSelected = listDataBinding.cbSelectAll.isChecked();
                    boolean b = e.isSelected && appDescribeItemSelectedSet.add(e);
                }
                listDataBinding.tvSelectedNum.setText(String.format(Locale.ROOT, "已选%s项", appDescribeItemSelectedSet.size()));
                myAdapter.notifyDataSetChanged();
            }
        });

        loadAppDescribeItems();
    }

    private void loadAppDescribeItems() {
        listDataBinding.recyclerView.setVisibility(View.INVISIBLE);
        listDataBinding.searchBox.setVisibility(View.INVISIBLE);
        listDataBinding.progress.setVisibility(View.VISIBLE);
        MyApplication.executeDatabase(new Runnable() {
            @Override
            public void run() {
                try {
                    List<AppDescribe> describes = new ArrayList<>(dataDao.getAllAppDescribes());
                    describes.sort(new Comparator<AppDescribe>() {
                        @Override
                        public int compare(AppDescribe o1, AppDescribe o2) {
                            return Collator.getInstance(Locale.CHINESE).compare(o1.appName, o2.appName);
                        }
                    });
                    List<AppDescribeItem> onList = new ArrayList<>();
                    List<AppDescribeItem> offList = new ArrayList<>();
                    Drawable uninstalledIcon = ResourcesCompat.getDrawable(getResources(), R.drawable.app_uninstalled, null);
                    for (AppDescribe appDescribe : describes) {
                        appDescribe.getOtherFieldsFromDatabase(dataDao);
                        AppDescribeItem item = new AppDescribeItem(appDescribe, uninstalledIcon, false, false);
                        try {
                            PackageInfo packageInfo = packageManager.getPackageInfo(appDescribe.appPackage, PackageManager.GET_META_DATA);
                            String label = packageInfo.applicationInfo.loadLabel(packageManager).toString();
                            item.appIcon = packageInfo.applicationInfo.loadIcon(packageManager);
                            item.isSysApp = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM;
                            item.isInstalled = true;
                            if (!TextUtils.equals(appDescribe.appName, label)) {
                                appDescribe.appName = label;
                                dataDao.updateAppDescribe(appDescribe);
                            }
                        } catch (PackageManager.NameNotFoundException ignored) {
                        }
                        if (appDescribe.coordinateOnOff || appDescribe.widgetOnOff) {
                            onList.add(item);
                        } else {
                            offList.add(item);
                        }
                    }
                    List<AppDescribeItem> items = new ArrayList<>(onList);
                    items.addAll(offList);
                    MyApplication.postToMain(() -> showLoadedItems(describes, items));
                } catch (RuntimeException e) {
                    MyApplication.postToMain(() -> showLoadError(e));
                }
            }
        });
    }

    private void showLoadedItems(List<AppDescribe> describes, List<AppDescribeItem> items) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        appDescribeList.clear();
        appDescribeList.addAll(describes);
        appDescribeItemList.clear();
        appDescribeItemList.addAll(items);
        appDescribeItemFilterList.clear();
        appDescribeItemFilterList.addAll(items);
        myAdapter.notifyDataSetChanged();
        listDataBinding.recyclerView.setVisibility(View.VISIBLE);
        listDataBinding.searchBox.setVisibility(View.VISIBLE);
        listDataBinding.progress.setVisibility(View.INVISIBLE);
    }

    private void showLoadError(RuntimeException error) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Toast.makeText(context, "读取规则失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        listDataBinding.recyclerView.setVisibility(View.VISIBLE);
        listDataBinding.searchBox.setVisibility(View.VISIBLE);
        listDataBinding.progress.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (!TextUtils.isEmpty(listDataBinding.searchBox.getText())) {
            listDataBinding.searchBox.setText(null);
            return;
        }
        if (listDataBinding.llSelect.getVisibility() == View.VISIBLE) {
            listDataBinding.btCancel.callOnClick();
            return;
        }
        super.onBackPressed();
    }

    private void showEditShareFileNameDialog(String strRegulation) {
        ViewEditFileNameBinding binding = ViewEditFileNameBinding.inflate(getLayoutInflater());
        binding.fileName.setHint(DigestUtil.md5Hex(strRegulation));
        new AlertDialog.Builder(ListDataActivity.this)
                .setView(binding.getRoot())
                .setCancelable(false)
                .setTitle("编辑文件名称")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String fileName = binding.fileName.getText().toString().trim();
                        String requestedName = (fileName.isEmpty() ? String.valueOf(binding.fileName.getHint()) : fileName) + ".txt";
                        MyApplication.executeIo(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    File file = ExportFileManager.writeText(getApplicationContext(), requestedName, strRegulation);
                                    MyApplication.postToMain(() -> shareRegulationFile(file));
                                } catch (Exception e) {
                                    MyApplication.postToMain(() -> Toast.makeText(context,
                                            "生成规则文件时发生错误", Toast.LENGTH_SHORT).show());
                                }
                            }
                        });
                    }
                }).show();
    }

    private void shareRegulationFile(File file) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", file);
        intent.setDataAndType(uri, "text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newUri(getContentResolver(), "regulation", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "保存"));
    }

    static class AppDescribeItem {
        AppDescribe appDescribe;
        Drawable appIcon;
        boolean isSysApp;
        boolean isInstalled;
        boolean isSelected;
        long longNoTriggerCount;

        public AppDescribeItem(AppDescribe appDescribe, Drawable appIcon, boolean isSysApp, boolean isInstalled) {
            this.appDescribe = appDescribe;
            this.appIcon = appIcon;
            this.isSysApp = isSysApp;
            this.isInstalled = isInstalled;
            this.refreshExistLongNoTrigger();
        }

        public void refreshExistLongNoTrigger() {
            longNoTriggerCount = appDescribe.coordinateList.stream()
                    .filter(e -> (System.currentTimeMillis() - e.createTime) / (1000 * 60 * 60 * 24) >= 60)
                    .filter(e -> (System.currentTimeMillis() - e.lastTriggerTime) / (1000 * 60 * 60 * 24) >= 60)
                    .count();
            longNoTriggerCount += appDescribe.widgetList.stream()
                    .filter(e -> (System.currentTimeMillis() - e.createTime) / (1000 * 60 * 60 * 24) >= 60)
                    .filter(e -> (System.currentTimeMillis() - e.lastTriggerTime) / (1000 * 60 * 60 * 24) >= 60)
                    .count();
        }
    }

    public class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> {

        @Override
        @androidx.annotation.NonNull
        public ViewHolder onCreateViewHolder(@androidx.annotation.NonNull ViewGroup parent, int viewType) {
            ViewItemAppBinding itemAppBinding = ViewItemAppBinding.inflate(getLayoutInflater(), parent, false);
            return new ViewHolder(itemAppBinding);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull ViewHolder holder, int position) {
            final AppDescribeItem item = appDescribeItemFilterList.get(position);
            holder.itemAppBinding.name.setText(StrUtil.blankToDefault(item.appDescribe.appName, "读取失败，无权限或未安装"));
            holder.itemAppBinding.pkg.setText(item.appDescribe.appPackage);
            holder.itemAppBinding.img.setImageDrawable(item.appIcon);
            holder.itemAppBinding.onOff.setChecked(item.appDescribe.coordinateOnOff || item.appDescribe.widgetOnOff);
            holder.itemAppBinding.cbSelect.setChecked(item.isSelected);
            holder.itemAppBinding.cbSelect.setVisibility(listDataBinding.llSelect.getVisibility());
            holder.itemAppBinding.desc.setText(String.format("%s个坐标，%s个控件", item.appDescribe.coordinateList.size(), item.appDescribe.widgetList.size()) + (item.longNoTriggerCount > 0 ? String.format("，%s个疑似无效规则", item.longNoTriggerCount) : ""));
            holder.itemAppBinding.desc.setTextColor(item.longNoTriggerCount > 0 ? Color.RED : holder.itemAppBinding.name.getCurrentTextColor());
        }

        @Override
        public int getItemCount() {
            return appDescribeItemFilterList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public ViewItemAppBinding itemAppBinding;

            public ViewHolder(ViewItemAppBinding binding) {
                super(binding.getRoot());
                itemAppBinding = binding;
                itemAppBinding.onOff.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AppDescribeItem item = appDescribeItemFilterList.get(getAdapterPosition());
                        boolean isChecked = itemAppBinding.onOff.isChecked();
                        Runnable runnable = new Runnable() {
                            @Override
                            public void run() {
                                item.appDescribe.widgetOnOff = isChecked;
                                item.appDescribe.coordinateOnOff = isChecked;
                                MyApplication.executeDatabase(() -> {
                                    dataDao.updateAppDescribe(item.appDescribe);
                                    MyUtils.requestUpdateAppDescribe(item.appDescribe.appPackage);
                                });
                            }
                        };
                        if (isChecked && pkgSuggestNotOnList.contains(item.appDescribe.appPackage)) {
                            itemAppBinding.onOff.setChecked(false);
                            View view = ViewOnOffWarningBinding.inflate(getLayoutInflater()).getRoot();
                            new AlertDialog.Builder(ListDataActivity.this)
                                    .setView(view)
                                    .setNegativeButton("取消", null)
                                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            runnable.run();
                                            itemAppBinding.onOff.setChecked(true);
                                        }
                                    }).show();
                        } else {
                            runnable.run();
                        }
                    }
                });
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        curAppDescribeItem = appDescribeItemFilterList.get(getAdapterPosition());
                        Intent intent = new Intent(context, EditDataActivity.class);
                        intent.putExtra("packageName", curAppDescribeItem.appDescribe.appPackage);
                        itemResultLauncher.launch(intent);
                    }
                });

                itemView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        AppDescribeItem item = appDescribeItemFilterList.get(getAdapterPosition());
                        item.isSelected = true;
                        appDescribeItemSelectedSet.add(item);
                        listDataBinding.llSelect.setVisibility(View.VISIBLE);
                        listDataBinding.tvSelectedNum.setText(String.format(Locale.ROOT, "已选%s项", appDescribeItemSelectedSet.size()));
                        notifyDataSetChanged();
                        return true;
                    }
                });

                itemAppBinding.cbSelect.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AppDescribeItem item = appDescribeItemFilterList.get(getAdapterPosition());
                        item.isSelected = itemAppBinding.cbSelect.isChecked();
                        boolean b = item.isSelected ? appDescribeItemSelectedSet.add(item) : appDescribeItemSelectedSet.remove(item);
                        listDataBinding.tvSelectedNum.setText(String.format(Locale.ROOT, "已选%s项", appDescribeItemSelectedSet.size()));
                        listDataBinding.cbSelectAll.setChecked(appDescribeItemSelectedSet.size() == appDescribeItemList.size());
                    }
                });
            }
        }
    }
}
