package com.example.lepsibakalari.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.example.lepsibakalari.api.AbsenceResponse;
import com.example.lepsibakalari.api.BakalariApi;
import com.example.lepsibakalari.api.EventsResponse;
import com.example.lepsibakalari.api.HomeworksResponse;
import com.example.lepsibakalari.api.KomensResponse;
import com.example.lepsibakalari.api.LoginResponse;
import com.example.lepsibakalari.api.MarksFinalResponse;
import com.example.lepsibakalari.api.MarksResponse;
import com.example.lepsibakalari.api.SubstitutionsResponse;
import com.example.lepsibakalari.api.TimetableResponse;
import com.example.lepsibakalari.api.UserResponse;

import java.util.Collections;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Repository pro veškerou síťovou logiku a persistentní uložení tokenů.
 * Používá EncryptedSharedPreferences pro bezpečné uložení access tokenu a base
 * URL.
 */
public class BakalariRepository {

    private static final String PREFS_NAME = "bakalari_secure_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String CLIENT_ID = "ANDR";

    private final Context context;
    private SharedPreferences encryptedPrefs;
    private BakalariApi api;
    private String currentBaseUrl;

    public BakalariRepository(Context context) {
        this.context = context.getApplicationContext();
        initEncryptedPrefs();
    }

    private void initEncryptedPrefs() {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize EncryptedSharedPreferences", e);
        }
    }

    /**
     * Normalizuje Base URL - odstraní koncové lomítko, přidá https:// pokud chybí
     */
    public static String normalizeBaseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        String normalized = url.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * Vytvoří nebo získá Retrofit instanci pro danou Base URL
     */
    public synchronized BakalariApi getApi(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized == null) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }
        if (api != null && normalized.equals(currentBaseUrl)) {
            return api;
        }

        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(chain -> {
                    okhttp3.Request request = chain.request();
                    // Do NOT add Authorization header for login endpoints
                    if (request.url().encodedPath().endsWith("api/login")) {
                        return chain.proceed(request);
                    }

                    String token = getStoredAccessToken();
                    if (token != null) {
                        return chain.proceed(request.newBuilder()
                                .header("Authorization", "Bearer " + token)
                                .build());
                    }
                    return chain.proceed(request);
                })
                .authenticator((route, response) -> {
                    // Pokud dostaneme 401, zkusíme refresh
                    if (response.code() == 401) {
                        String newToken = performSyncRefreshToken();
                        if (newToken != null) {
                            return response.request().newBuilder()
                                    .header("Authorization", "Bearer " + newToken)
                                    .build();
                        }
                    }
                    return null;
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(normalized + "/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        currentBaseUrl = normalized;
        api = retrofit.create(BakalariApi.class);
        return api;
    }

    private String performSyncRefreshToken() {
        String baseUrl = getStoredBaseUrl();
        String refreshToken = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null);
        if (baseUrl == null || refreshToken == null)
            return null;

        // Vytvoříme dočasný Retrofit s lenient nastavením a bez interceptoru
        Gson gson = new GsonBuilder().setLenient().create();
        OkHttpClient simpleClient = new OkHttpClient.Builder().build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(normalizeBaseUrl(baseUrl) + "/")
                .client(simpleClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        BakalariApi refreshApi = retrofit.create(BakalariApi.class);
        try {
            retrofit2.Response<LoginResponse> response = refreshApi.refreshToken(
                    CLIENT_ID, "refresh_token", refreshToken).execute();
            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                LoginResponse body = response.body();
                saveCredentials(baseUrl, body.getAccessToken(), body.getRefreshToken(), null, null);
                return body.getAccessToken();
            } else {
                // Refresh se nepovedl - zkusíme full login pokud máme uloženo heslo
                String savedUser = encryptedPrefs.getString(KEY_USERNAME, null);
                String savedPass = encryptedPrefs.getString(KEY_PASSWORD, null);
                if (savedUser != null && savedPass != null) {
                    retrofit2.Response<LoginResponse> loginResp = refreshApi
                            .login(CLIENT_ID, "password", savedUser, savedPass).execute();
                    if (loginResp.isSuccessful() && loginResp.body() != null && loginResp.body().isSuccess()) {
                        LoginResponse b = loginResp.body();
                        saveCredentials(baseUrl, b.getAccessToken(), b.getRefreshToken(), savedUser, savedPass);
                        return b.getAccessToken();
                    }
                }
                // I full login selhal - smažeme vše
                clearCredentials();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getStoredBaseUrl() {
        return encryptedPrefs.getString(KEY_BASE_URL, null);
    }

    public String getStoredAccessToken() {
        return encryptedPrefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public boolean isLoggedIn() {
        return getStoredAccessToken() != null && getStoredBaseUrl() != null;
    }

    public void saveCredentials(String baseUrl, String accessToken, String refreshToken, String username,
            String password) {
        SharedPreferences.Editor editor = encryptedPrefs.edit()
                .putString(KEY_BASE_URL, normalizeBaseUrl(baseUrl))
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken);

        if (username != null)
            editor.putString(KEY_USERNAME, username);
        if (password != null)
            editor.putString(KEY_PASSWORD, password);

        editor.apply();
    }

    public void clearCredentials() {
        encryptedPrefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_BASE_URL)
                .remove(KEY_USERNAME)
                .remove(KEY_PASSWORD)
                .apply();
        api = null;
        currentBaseUrl = null;
    }

    /**
     * OAuth2 přihlášení - POST /api/login s username a password
     */
    public void login(String baseUrl, String username, String password, Callback<LoginResponse> callback) {
        try {
            BakalariApi api = getApi(baseUrl);
            Call<LoginResponse> call = api.login(CLIENT_ID, "password", username, password);
            call.enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(Call<LoginResponse> call, retrofit2.Response<LoginResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        LoginResponse loginResponse = response.body();
                        if (loginResponse.isSuccess()) {
                            saveCredentials(baseUrl, loginResponse.getAccessToken(), loginResponse.getRefreshToken(),
                                    username, password);
                        }
                    }
                    callback.onResponse(call, response);
                }

                @Override
                public void onFailure(Call<LoginResponse> call, Throwable t) {
                    callback.onFailure(call, t);
                }
            });
        } catch (Exception e) {
            callback.onFailure(null, e);
        }
    }

    /**
     * Obnovení tokenu pomocí refresh_token
     */
    public void refreshToken(Callback<LoginResponse> callback) {
        String baseUrl = getStoredBaseUrl();
        String refreshToken = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null);
        if (baseUrl == null || refreshToken == null) {
            callback.onFailure(null, new IllegalStateException("No stored credentials"));
            return;
        }
        BakalariApi api = getApi(baseUrl);
        Call<LoginResponse> call = api.refreshToken(CLIENT_ID, "refresh_token", refreshToken);
        call.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, retrofit2.Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    saveCredentials(baseUrl, response.body().getAccessToken(), response.body().getRefreshToken(), null,
                            null);
                }
                callback.onResponse(call, response);
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                callback.onFailure(call, t);
            }
        });
    }

    /**
     * Načte informace o uživateli
     */
    public void getUserInfo(Callback<UserResponse> callback) {
        String baseUrl = getStoredBaseUrl();
        if (baseUrl == null) {
            callback.onFailure(null, new IllegalStateException("Not logged in"));
            return;
        }
        String token = getStoredAccessToken();
        if (token == null) {
            callback.onFailure(null, new IllegalStateException("Not logged in"));
            return;
        }
        getApi(baseUrl).getUserInfo("Bearer " + token).enqueue(callback);
    }

    /**
     * Načte aktuální rozvrh pro dané datum
     */
    public void getTimetable(String date, Callback<TimetableResponse> callback) {
        String baseUrl = getStoredBaseUrl();
        if (baseUrl == null) {
            callback.onFailure(null, new IllegalStateException("Not logged in"));
            return;
        }
        getApi(baseUrl).getTimetable(null, date).enqueue(callback);
    }

    /**
     * Načte aktuální rozvrh pro dnešek
     */
    public void getTimetableToday(Callback<TimetableResponse> callback) {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        getTimetable(date, callback);
    }

    /**
     * Načte známky
     */
    public void getMarks(Callback<MarksResponse> callback) {
        String baseUrl = getStoredBaseUrl();
        if (baseUrl == null) {
            callback.onFailure(null, new IllegalStateException("Not logged in"));
            return;
        }
        getApi(baseUrl).getMarks(null).enqueue(callback);
    }

    /**
     * Načte pololetní / vysvědčení známky
     */
    public void getMarksFinal(Callback<MarksFinalResponse> callback) {
        String baseUrl = getStoredBaseUrl();
        if (baseUrl == null) {
            callback.onFailure(null, new IllegalStateException("Not logged in"));
            return;
        }
        getApi(baseUrl).getMarksFinal(null).enqueue(callback);
    }

    /**
     * Načte přijaté Komens zprávy
     */
    public void getKomensReceived(Callback<KomensResponse> callback) {
        String baseUrl = getStoredBaseUrl();
        if (baseUrl == null) {
            callback.onFailure(null, new IllegalStateException("Not logged in"));
            return;
        }
        getApi(baseUrl).getKomensReceived(null, Collections.emptyMap()).enqueue(callback);
    }

    /**
     * Načte absenci žáka
     */
    public void getAbsence(Callback<AbsenceResponse> callback) {
        String baseUrl = getStoredBaseUrl();
        if (baseUrl == null) {
            callback.onFailure(null, new IllegalStateException("Not logged in"));
            return;
        }
        getApi(baseUrl).getAbsence(null).enqueue(callback);
    }

    /**
     * Načte úkoly (volitelné from, to - při null použijí se výchozí hodnoty API)
     */
    public void getHomeworks(String from, String to, Callback<HomeworksResponse> callback) {
        String baseUrl = getStoredBaseUrl();
        if (baseUrl == null) {
            callback.onFailure(null, new IllegalStateException("Not logged in"));
            return;
        }
        getApi(baseUrl).getHomeworks(null, from, to).enqueue(callback);
    }

    /**
     * Načte události (volitelné from)
     */
    public void getEvents(String from, Callback<EventsResponse> callback) {
        String baseUrl = getStoredBaseUrl();
        if (baseUrl == null) {
            callback.onFailure(null, new IllegalStateException("Not logged in"));
            return;
        }
        getApi(baseUrl).getEvents(null, from).enqueue(callback);
    }

    /**
     * Načte zastupování (volitelné from)
     */
    public void getSubstitutions(String from, Callback<SubstitutionsResponse> callback) {
        String baseUrl = getStoredBaseUrl();
        if (baseUrl == null) {
            callback.onFailure(null, new IllegalStateException("Not logged in"));
            return;
        }
        getApi(baseUrl).getSubstitutions(null, from).enqueue(callback);
    }
}
