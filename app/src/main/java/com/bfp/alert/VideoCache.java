package com.bfp.alert;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VideoCache {

    private static final String PREFS  = "video_cache";
    private static final int    BUFFER = 8192;

    // Get local cached file path for a video URL
    public static File getCachedFile(Context ctx, String videoUrl) {
        String fileName = "video_"
                + Math.abs(videoUrl.hashCode()) + ".mp4";
        return new File(ctx.getCacheDir(), fileName);
    }

    // Check if video is already cached
    public static boolean isCached(Context ctx, String videoUrl) {
        File file = getCachedFile(ctx, videoUrl);
        return file.exists() && file.length() > 0;
    }

    // Save the URL → file mapping
    public static void markCached(Context ctx,
                                  String videoUrl,
                                  String localPath) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(videoUrl, localPath)
                .apply();
    }

    // Get cached local path for a URL
    public static String getCachedPath(Context ctx, String videoUrl) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(videoUrl, null);
    }

    // Download video to cache directory
    public static void downloadVideo(Context ctx,
                                     String videoUrl,
                                     DownloadCallback callback) {
        new Thread(() -> {
            try {
                File outFile = getCachedFile(ctx, videoUrl);

                // Already cached — skip
                if (outFile.exists() && outFile.length() > 0) {
                    markCached(ctx, videoUrl,
                            outFile.getAbsolutePath());
                    callback.onComplete(outFile.getAbsolutePath());
                    return;
                }

                URL url = new URL(videoUrl);
                HttpURLConnection conn =
                        (HttpURLConnection) url.openConnection();
                conn.connect();

                long total   = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    total = conn.getContentLengthLong();
                }
                long downloaded = 0;

                InputStream  in  = conn.getInputStream();
                FileOutputStream out =
                        new FileOutputStream(outFile);

                byte[] buf = new byte[BUFFER];
                int    len;

                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                    downloaded += len;
                    if (total > 0) {
                        int pct = (int)(100.0
                                * downloaded / total);
                        callback.onProgress(pct);
                    }
                }

                out.flush();
                out.close();
                in.close();
                conn.disconnect();

                markCached(ctx, videoUrl,
                        outFile.getAbsolutePath());
                callback.onComplete(outFile.getAbsolutePath());

            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    // Delete cached video
    public static void clearCache(Context ctx, String videoUrl) {
        File file = getCachedFile(ctx, videoUrl);
        if (file.exists()) file.delete();
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(videoUrl).apply();
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onComplete(String localPath);
        void onError(String error);
    }
}