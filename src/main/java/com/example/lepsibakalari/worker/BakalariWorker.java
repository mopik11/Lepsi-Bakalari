package com.example.lepsibakalari.worker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.lepsibakalari.api.BakalariApi;
import com.example.lepsibakalari.api.HomeworksResponse;
import com.example.lepsibakalari.api.MarksResponse;
import com.example.lepsibakalari.repository.BakalariRepository;
import com.example.lepsibakalari.util.NotificationHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Response;

public class BakalariWorker extends Worker {
    private static final String TAG = "BakalariWorker";
    private static final String PREFS_CHUNKS = "bakalari_worker_prefs";
    private static final String KEY_KNOWN_MARKS = "known_marks";
    private static final String KEY_KNOWN_HOMEWORKS = "known_homeworks";

    public BakalariWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Bakalari background check started");

        BakalariRepository repository = new BakalariRepository(getApplicationContext());
        if (!repository.isLoggedIn()) {
            return Result.success();
        }

        BakalariApi api = repository.getApi(repository.getStoredBaseUrl());

        try {
            checkMarks(api);
            checkHomeworks(api);
        } catch (IOException e) {
            Log.e(TAG, "Error checking updates", e);
            return Result.retry();
        }

        return Result.success();
    }

    private void checkMarks(BakalariApi api) throws IOException {
        Response<MarksResponse> response = api.getMarks(null).execute();
        if (response.isSuccessful() && response.body() != null) {
            MarksResponse marks = response.body();
            Set<String> currentMarkIds = new HashSet<>();
            int newMarksCount = 0;
            String lastMarkTitle = "";

            SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS_CHUNKS, Context.MODE_PRIVATE);
            Set<String> knownMarks = prefs.getStringSet(KEY_KNOWN_MARKS, new HashSet<>());

            if (marks.getSubjects() != null) {
                for (MarksResponse.SubjectMarks sm : marks.getSubjects()) {
                    if (sm.getMarks() != null) {
                        for (MarksResponse.Mark m : sm.getMarks()) {
                            // Unique ID for a mark: SubjectID + Caption + Date + MarkText
                            String id = sm.getSubject().getId() + "_" + m.getCaption() + "_" + m.getDate() + "_"
                                    + m.getMarkText();
                            currentMarkIds.add(id);

                            if (!knownMarks.isEmpty() && !knownMarks.contains(id)) {
                                newMarksCount++;
                                lastMarkTitle = sm.getSubject().getName() + ": " + m.getMarkText();
                            }
                        }
                    }
                }
            }

            if (newMarksCount > 0) {
                String msg = newMarksCount == 1 ? "Nová známka z " + lastMarkTitle
                        : "Máte " + newMarksCount + " nových známek";
                NotificationHelper.showNotification(getApplicationContext(), 1001, "Nové známky", msg);
            }

            prefs.edit().putStringSet(KEY_KNOWN_MARKS, currentMarkIds).apply();
        }
    }

    private void checkHomeworks(BakalariApi api) throws IOException {
        Response<HomeworksResponse> response = api.getHomeworks(null, null, null).execute();
        if (response.isSuccessful() && response.body() != null) {
            HomeworksResponse homeworks = response.body();
            Set<String> currentIds = new HashSet<>();
            int newCount = 0;
            String lastTitle = "";

            SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS_CHUNKS, Context.MODE_PRIVATE);
            Set<String> known = prefs.getStringSet(KEY_KNOWN_HOMEWORKS, new HashSet<>());

            if (homeworks.getHomeworks() != null) {
                for (HomeworksResponse.Homework h : homeworks.getHomeworks()) {
                    String id = h.getId();
                    currentIds.add(id);

                    if (!known.isEmpty() && !known.contains(id)) {
                        newCount++;
                        lastTitle = h.getSubject().getName();
                    }
                }
            }

            if (newCount > 0) {
                String msg = newCount == 1 ? "Nový úkol z " + lastTitle : "Máte " + newCount + " nových úkolů";
                NotificationHelper.showNotification(getApplicationContext(), 1002, "Nové úkoly", msg);
            }

            prefs.edit().putStringSet(KEY_KNOWN_HOMEWORKS, currentIds).apply();
        }
    }
}
