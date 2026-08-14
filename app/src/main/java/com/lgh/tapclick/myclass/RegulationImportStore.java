package com.lgh.tapclick.myclass;

import android.content.Context;

import com.google.gson.Gson;
import com.lgh.tapclick.mybean.Regulation;
import com.lgh.tapclick.mybean.RegulationExport;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class RegulationImportStore {
    private static final String IMPORT_DIRECTORY = "imports";
    private static final String IMPORT_FILE = "pending-regulations.json";

    private RegulationImportStore() {
    }

    public static void write(Context context, List<Regulation> regulations) throws IOException {
        File file = getFile(context);
        FileUtils.forceMkdirParent(file);
        RegulationExport export = new RegulationExport();
        export.regulationList.addAll(regulations);
        FileUtils.writeStringToFile(file, new Gson().toJson(export), StandardCharsets.UTF_8, false);
    }

    public static List<Regulation> read(Context context) throws IOException {
        File file = getFile(context);
        if (!file.isFile()) {
            throw new IOException("待导入规则已失效，请重新选择规则文件");
        }
        String json = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        RegulationExport export = new Gson().fromJson(json, RegulationExport.class);
        if (export == null || export.regulationList == null) {
            throw new IOException("待导入规则内容无效");
        }
        return export.regulationList;
    }

    public static void clear(Context context) {
        FileUtils.deleteQuietly(getFile(context));
    }

    private static File getFile(Context context) {
        return new File(new File(context.getCacheDir(), IMPORT_DIRECTORY), IMPORT_FILE);
    }
}
