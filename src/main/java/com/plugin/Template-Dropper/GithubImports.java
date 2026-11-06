package com.plugin;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GithubImports {
    // Constants to avoid magic numbers / strings
    private static final int BUFFER_SIZE = 8 * 1024;

    // removes first little segment from the zip path
    private static String removeFirstSegment(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] segments = path.split(Pattern.quote("/"), -1);
        if (segments.length <= 1) return "";
        return String.join("/", Arrays.copyOfRange(segments, 1, segments.length));
    }

    // normalizes forward slashes
    private static String normalizeZipPath(String p) {
        if (p == null) return "";
        String s = p.replace("\\", "/").trim();
        // Remove leading/trailing slashes
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /*
     Generic importer: download a ZIP from zipUrl, strip the archive's top-level folder and import only entries under srcPathInZip into the project's basePath/destRelativePath.
     basePath=The project base path (as in Project.getBasePath()).
     zipUrl=The URL of the zip to download.
     srcPathInZip=Path inside the zip to import (no leading slashes)
     destRelativePath=Destination path relative to basePath where files will be created.
     progressIndicator=ProgressIndicator for reporting and cancellation.
     */
    public static void importFromZip(String basePath, String zipUrl, String srcPathInZip, String destRelativePath, ProgressIndicator progressIndicator) {
        File projectRoot = new File(basePath);
        try {
            // Normalize the important paths for reliable comparisons
            final String srcNormalized = normalizeZipPath(srcPathInZip);
            final String destRelativeNormalized = destRelativePath == null ? "" : destRelativePath.replace("\\", File.separator);

            // Resolve and canonicalize the destination root path to prevent path traversal later
            File destRootDir = new File(projectRoot, destRelativeNormalized);
            String destRootCanonical = destRootDir.getCanonicalPath();

            URL url = URI.create(zipUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept", "application/zip");
            connection.connect();

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Failed to download zip, HTTP " + code);
            }

            try (InputStream is = connection.getInputStream();
                 ZipInputStream reader = new ZipInputStream(is)) {

                ZipEntry entry;
                progressIndicator.checkCanceled();

                while ((entry = reader.getNextEntry()) != null) {
                    String entryAfterRoot = removeFirstSegment(entry.getName());
                    if (entryAfterRoot.isEmpty()) continue;

                    // Normalize zip entry path (forward slashes)
                    String entryNormalized = entryAfterRoot.replace("\\", "/");

                    // Only process entries that are inside the requested src path
                    if (!srcNormalized.isEmpty()) {
                        if (entryNormalized.equals(srcNormalized)) {
                            // exact directory match -> treat as directory (relative path is empty)
                        } else if (!entryNormalized.startsWith(srcNormalized + "/")) {
                            continue; // not under desired source path
                        }
                    }

                    // relative path
                    String relativeInside;
                    if (srcNormalized.isEmpty()) {
                        relativeInside = entryNormalized;
                    } else if (entryNormalized.equals(srcNormalized)) {
                        relativeInside = ""; // top-level directory itself
                    } else {
                        relativeInside = entryNormalized.substring(srcNormalized.length() + 1);
                    }

                    // Build destination file path
                    File destFile = relativeInside.isEmpty()
                            ? new File(destRootDir, "") // directory itself
                            : new File(destRootDir, relativeInside.replace("/", File.separator));

                    String destFileCanonical = destFile.getCanonicalPath();

                    // Security check: ensure the destination file is inside the destination root (prevents path traversal)
                    if (!destFileCanonical.equals(destRootCanonical) && !destFileCanonical.startsWith(destRootCanonical + File.separator)) {
                        throw new RuntimeException("Entry is outside of the target dir: " + destFileCanonical);
                    }

                    progressIndicator.setText("Extracting " + entryNormalized);

                    if (entry.isDirectory()) {
                        if (!destFile.isDirectory() && !destFile.mkdirs()) {
                            throw new IOException("Failed to create zip directory: " + destFile);
                        }
                    } else {
                        // Ensure parent directories exist
                        File parent = destFile.getParentFile();
                        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                            throw new IOException("Failed to create directory " + parent);
                        }

                        // Write file contents using try-with-resources
                        try (FileOutputStream fos = new FileOutputStream(destFile)) {
                            byte[] buffer = new byte[BUFFER_SIZE];
                            int len;
                            while ((len = reader.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }

                    progressIndicator.checkCanceled();
                }
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            // Surface failure so callers / the user can see it
            throw new RuntimeException("Failed to import from zip: " + e.getMessage(), e);
        }

        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl("file://" + basePath.replace("\\", "/"));
        file.refresh(true, true);
    }
}
