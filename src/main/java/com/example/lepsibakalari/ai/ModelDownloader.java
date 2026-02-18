package com.example.lepsibakalari.ai;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.util.Log;

import java.io.File;

public class ModelDownloader {
    private static final String TAG = "ModelDownloader";
    // Tady by měla být URL na model. Pro demo účely použijeme placeholder.
    // POZNÁMKA: Skutečný Gemma 2B model vyžaduje souhlas na Kaggle/HuggingFace.
    private static final String DEFAULT_MODEL_URL = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task";
    private static final String MODEL_FILENAME = "ai_model.task";

    private final Context context;
    private long downloadId = -1;
    private DownloadCallback callback;
    private java.util.concurrent.ScheduledExecutorService executor;

    public ModelDownloader(Context context) {
        this.context = context.getApplicationContext();
    }

    public void startDownload(DownloadCallback callback) {
        this.callback = callback;
        File targetFile = new File(context.getExternalFilesDir(null), MODEL_FILENAME);

        if (targetFile.exists()) {
            callback.onFinished(targetFile);
            return;
        }

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(DEFAULT_MODEL_URL))
                .setTitle("Stahování AI Modelu")
                .setDescription("Stahování balíčku pro Neural Engine (cca 1.5GB)")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(context, null, MODEL_FILENAME)
                .setAllowedOverMetered(true) // Povolíme i data, když už si to uživatel vyžádal
                .setAllowedOverRoaming(false);

        downloadId = downloadManager.enqueue(request);
        Log.i(TAG, "Download enqueued with ID: " + downloadId);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
        startPollingProgress(downloadManager);
    }

    private void startPollingProgress(DownloadManager downloadManager) {
        executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            android.database.Cursor cursor = downloadManager.query(query);
            if (cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                int bytesDownloaded = cursor
                        .getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                int bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                if (status == DownloadManager.STATUS_FAILED) {
                    int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    if (callback != null)
                        callback.onError("Chyba stahování (kód " + reason + ")");
                    executor.shutdown();
                } else if (status == DownloadManager.STATUS_PAUSED) {
                    int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    String reasonText = "Čekání... (" + reason + ")";
                    if (reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI)
                        reasonText = "Čekání na WiFi...";
                    if (reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK)
                        reasonText = "Čekání na síť...";
                    if (reason == DownloadManager.PAUSED_WAITING_TO_RETRY)
                        reasonText = "Čekání na opakování...";

                    if (callback != null)
                        callback.onProgress(0, bytesDownloaded, -reason); // Use negative total to signal status in UI
                } else if (bytesTotal > 0) {
                    int progress = (int) ((bytesDownloaded * 100L) / bytesTotal);
                    if (callback != null) {
                        callback.onProgress(progress, bytesDownloaded, bytesTotal);
                    }
                } else {
                    if (callback != null)
                        callback.onProgress(0, bytesDownloaded, 0);
                }
            } else {
                if (callback != null)
                    callback.onError("Stahování nenalezeno v systému.");
                executor.shutdown();
            }
            cursor.close();
        }, 0, 2000, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private final BroadcastReceiver onComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == downloadId) {
                Log.i(TAG, "Download broadcast received. Checking status...");
                if (executor != null)
                    executor.shutdown();

                DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
                android.database.Cursor c = dm.query(query);

                if (c.moveToFirst()) {
                    int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        File targetFile = new File(context.getExternalFilesDir(null), MODEL_FILENAME);
                        if (callback != null)
                            callback.onFinished(targetFile);
                    } else {
                        int reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                        if (callback != null)
                            callback.onError("Stahování selhalo (kód " + reason + ")");
                    }
                }
                c.close();
                context.unregisterReceiver(this);
            }
        }
    };

    public interface DownloadCallback {
        void onFinished(File file);

        void onProgress(int progress, long current, long total);

        void onError(String message);
    }
}
