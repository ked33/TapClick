package com.lgh.tapclick.myclass;

import android.content.Context;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ExportFileManager {
    private static final String EXPORT_DIRECTORY = "exports";

    private ExportFileManager() {
    }

    public static File writeText(Context context, String requestedName, String content) throws IOException {
        File file = prepareFile(context, requestedName);
        FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8, false);
        return file;
    }

    public static File copy(Context context, File source, String requestedName) throws IOException {
        File file = prepareFile(context, requestedName);
        FileUtils.copyFile(source, file);
        return file;
    }

    public static File getExportDirectory(Context context) throws IOException {
        File directory = new File(context.getCacheDir(), EXPORT_DIRECTORY);
        FileUtils.forceMkdir(directory);
        return directory;
    }

    private static File prepareFile(Context context, String requestedName) throws IOException {
        File directory = getExportDirectory(context);
        String safeName = sanitizeFileName(requestedName);
        File file = new File(directory, safeName);
        String directoryPath = directory.getCanonicalPath() + File.separator;
        if (!file.getCanonicalPath().startsWith(directoryPath)) {
            throw new IOException("导出文件名无效");
        }
        FileUtils.deleteQuietly(file);
        return file;
    }

    private static String sanitizeFileName(String requestedName) {
        String name = requestedName == null ? "export.txt" : requestedName.trim();
        if (name.isEmpty()) {
            name = "export.txt";
        }
        StringBuilder safe = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean invalid = c < 32 || c == '\\' || c == '/' || c == ':' || c == '*'
                    || c == '?' || c == '"' || c == '<' || c == '>' || c == '|';
            safe.append(invalid ? '_' : c);
        }
        String result = safe.toString();
        return result.equals(".") || result.equals("..") ? "export.txt" : result;
    }
}
