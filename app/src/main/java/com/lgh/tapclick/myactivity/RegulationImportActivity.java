package com.lgh.tapclick.myactivity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lgh.tapclick.databinding.ActivityRegulationImportBinding;
import com.lgh.tapclick.databinding.ViewItemImportBinding;
import com.lgh.tapclick.mybean.Regulation;
import com.lgh.tapclick.myclass.MyApplication;
import com.lgh.tapclick.myclass.RegulationImportStore;
import com.lgh.tapclick.myfunction.MyUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class RegulationImportActivity extends BaseActivity {
    private final MyAdapter myAdapter = new MyAdapter();
    private final List<RegulationItem> regulationItemList = new ArrayList<>();
    private final List<RegulationItem> regulationItemFilterList = new ArrayList<>();
    private final List<Regulation> importList = new ArrayList<>();
    private ActivityRegulationImportBinding regulationImportBinding;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        regulationImportBinding = ActivityRegulationImportBinding.inflate(getLayoutInflater());
        setContentView(regulationImportBinding.getRoot());
        regulationImportBinding.recyclerView.setAdapter(myAdapter);
        regulationImportBinding.btImport.setEnabled(false);
        regulationImportBinding.searchBox.setEnabled(false);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!TextUtils.isEmpty(regulationImportBinding.searchBox.getText())) {
                    regulationImportBinding.searchBox.setText(null);
                    return;
                }
                finish();
            }
        });

        Filter filter = createFilter();
        regulationImportBinding.searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filter.filter(s.toString().trim());
            }
        });

        regulationImportBinding.cbSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importList.clear();
                for (RegulationItem item : regulationItemList) {
                    item.isSelected = regulationImportBinding.cbSelectAll.isChecked();
                    if (item.isSelected) {
                        importList.add(item.regulation);
                    }
                }
                myAdapter.notifyDataSetChanged();
                updateSelectedCount();
            }
        });

        regulationImportBinding.btImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmImport();
            }
        });

        regulationImportBinding.recyclerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_MOVE) {
                    v.requestFocus();
                }
                return false;
            }
        });

        MyApplication.executeIo(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Regulation> regulations = RegulationImportStore.read(getApplicationContext());
                    regulations.sort(new Comparator<Regulation>() {
                        @Override
                        public int compare(Regulation o1, Regulation o2) {
                            return Collator.getInstance(Locale.CHINESE).compare(o1.appDescribe.appName, o2.appDescribe.appName);
                        }
                    });
                    MyApplication.postToMain(() -> showRegulations(regulations));
                } catch (Exception e) {
                    MyApplication.postToMain(() -> {
                        Toast.makeText(RegulationImportActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        finish();
                    });
                }
            }
        });
    }

    private Filter createFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String keyword = constraint.toString().toLowerCase(Locale.ROOT);
                List<RegulationItem> filtered = new ArrayList<>();
                for (RegulationItem item : regulationItemList) {
                    if (item.regulation.appDescribe.appName.toLowerCase(Locale.ROOT).contains(keyword)
                            || item.regulation.appDescribe.appPackage.toLowerCase(Locale.ROOT).contains(keyword)) {
                        filtered.add(item);
                    }
                }
                FilterResults results = new FilterResults();
                results.values = filtered;
                results.count = filtered.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                regulationItemFilterList.clear();
                if (results.values instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<RegulationItem> filtered = (List<RegulationItem>) results.values;
                    regulationItemFilterList.addAll(filtered);
                }
                myAdapter.notifyDataSetChanged();
            }
        };
    }

    private void showRegulations(List<Regulation> regulations) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        regulationItemList.clear();
        regulationItemFilterList.clear();
        for (Regulation regulation : regulations) {
            RegulationItem item = new RegulationItem(regulation);
            regulationItemList.add(item);
            regulationItemFilterList.add(item);
        }
        regulationImportBinding.btImport.setEnabled(true);
        regulationImportBinding.searchBox.setEnabled(true);
        myAdapter.notifyDataSetChanged();
    }

    private void confirmImport() {
        if (importList.isEmpty()) {
            Toast.makeText(this, "请选择要导入的规则", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("原有规则将被覆盖，确定导入？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        List<Regulation> selected = new ArrayList<>(importList);
                        regulationImportBinding.btImport.setEnabled(false);
                        MyApplication.executeDatabase(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    MyApplication.dataDao.replaceRegulations(selected);
                                    RegulationImportStore.clear(getApplicationContext());
                                    MyApplication.postToMain(new Runnable() {
                                        @Override
                                        public void run() {
                                            MyUtils.requestUpdateAllDate();
                                            Toast.makeText(RegulationImportActivity.this, "导入成功", Toast.LENGTH_SHORT).show();
                                            finish();
                                        }
                                    });
                                } catch (RuntimeException e) {
                                    MyApplication.postToMain(() -> {
                                        regulationImportBinding.btImport.setEnabled(true);
                                        Toast.makeText(RegulationImportActivity.this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                                    });
                                }
                            }
                        });
                    }
                })
                .show();
    }

    private void updateSelectedCount() {
        regulationImportBinding.tvSelectedNum.setText(String.format(Locale.ROOT, "已选%d项", importList.size()));
    }

    static class RegulationItem {
        final Regulation regulation;
        boolean isSelected;

        RegulationItem(Regulation regulation) {
            this.regulation = regulation;
        }
    }

    public class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ViewItemImportBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RegulationItem item = regulationItemFilterList.get(position);
            holder.itemImportBinding.tvName.setText(String.format(Locale.ROOT, "%s (%s)",
                    item.regulation.appDescribe.appName,
                    item.regulation.appDescribe.appPackage));
            holder.itemImportBinding.tvDesc.setText(String.format(Locale.ROOT, "%d条坐标规则，%d条控件规则",
                    item.regulation.coordinateList.size(),
                    item.regulation.widgetList.size()));
            holder.itemImportBinding.cbSelect.setChecked(item.isSelected);
        }

        @Override
        public int getItemCount() {
            return regulationItemFilterList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            final ViewItemImportBinding itemImportBinding;

            ViewHolder(ViewItemImportBinding binding) {
                super(binding.getRoot());
                itemImportBinding = binding;
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int position = getAdapterPosition();
                        if (position == RecyclerView.NO_POSITION) {
                            return;
                        }
                        RegulationItem item = regulationItemFilterList.get(position);
                        item.isSelected = !item.isSelected;
                        if (item.isSelected) {
                            importList.add(item.regulation);
                        } else {
                            importList.remove(item.regulation);
                        }
                        notifyItemChanged(position);
                        updateSelectedCount();
                        regulationImportBinding.cbSelectAll.setChecked(importList.size() == regulationItemList.size());
                    }
                });
            }
        }
    }
}
