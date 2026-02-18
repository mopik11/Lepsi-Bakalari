package com.example.lepsibakalari.ai;

import android.content.Context;
import android.util.Log;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import java.io.File;

public class LocalLLMManager {
    private static final String TAG = "LocalLLMManager";
    private static final String MODEL_PATH = "ai_model.task";

    private LlmInference llmInference;
    private final Context context;
    private boolean isReady = false;
    private boolean isInitializing = false;
    private String lastError = null;

    public LocalLLMManager(Context context) {
        this.context = context.getApplicationContext();
        initializeModel();
    }

    private void initializeModel() {
        File modelFile = new File(context.getExternalFilesDir(null), MODEL_PATH);
        if (!modelFile.exists()) {
            Log.w(TAG, "Model file not found.");
            return;
        }

        isInitializing = true;
        new Thread(() -> {
            try {
                Log.i(TAG, "Starting LLM initialization from: " + modelFile.getAbsolutePath());
                long startTime = System.currentTimeMillis();

                LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.getAbsolutePath())
                        .setMaxTokens(512)
                        .setTopK(40)
                        .setTemperature(0.7f)
                        .setRandomSeed(101)
                        .build();

                llmInference = LlmInference.createFromOptions(context, options);

                long duration = System.currentTimeMillis() - startTime;
                Log.i(TAG, "Local LLM Model loaded successfully in " + duration + "ms!");

                isReady = true;
                isInitializing = false;
            } catch (Exception e) {
                isInitializing = false;
                Log.e(TAG, "CRITICAL ERROR: Failed to initialize local LLM engine!", e);
                e.printStackTrace();
            }
        }).start();
    }

    public void generateSummary(String prompt, LLMCallback callback) {
        if (!isReady || llmInference == null) {
            callback.onError("Model není připraven. (Chybí soubor " + MODEL_PATH + ")");
            return;
        }

        new Thread(() -> {
            try {
                String result = llmInference.generateResponse(prompt);
                callback.onResult(result);
            } catch (Exception e) {
                Log.e(TAG, "Error during inference", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public boolean isReady() {
        return isReady;
    }

    public boolean isInitializing() {
        return isInitializing;
    }

    public interface LLMCallback {
        void onResult(String result);

        void onError(String error);
    }
}
