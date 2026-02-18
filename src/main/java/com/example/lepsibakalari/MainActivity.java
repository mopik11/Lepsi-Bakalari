package com.example.lepsibakalari;

import android.content.Intent;
import android.content.Context;
import android.app.DownloadManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.ProgressBar;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.lepsibakalari.api.AbsenceResponse;
import com.example.lepsibakalari.api.EventsResponse;
import com.example.lepsibakalari.api.HomeworksResponse;
import com.example.lepsibakalari.api.KomensResponse;
import com.example.lepsibakalari.api.MarksFinalResponse;
import com.example.lepsibakalari.api.MarksResponse;
import com.example.lepsibakalari.api.SubstitutionsResponse;
import com.example.lepsibakalari.api.TimetableResponse;
import com.example.lepsibakalari.databinding.ActivityMainBinding;
import com.example.lepsibakalari.databinding.ItemHomeworkBinding;
import com.example.lepsibakalari.databinding.ItemKomensBinding;
import com.example.lepsibakalari.databinding.ItemLessonBinding;
import com.example.lepsibakalari.databinding.ItemMarkBinding;
import com.example.lepsibakalari.databinding.ItemMarkSingleBinding;
import com.example.lepsibakalari.databinding.LayoutMarksToggleBinding;
import com.example.lepsibakalari.repository.BakalariRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import com.example.lepsibakalari.api.WeatherApi;
import com.example.lepsibakalari.api.WeatherResponse;
import com.example.lepsibakalari.api.GeocodingResponse;
import com.example.lepsibakalari.api.NominatimResponse;
import com.example.lepsibakalari.api.UserResponse;
import android.location.Location;
import android.location.LocationManager;
import android.location.Geocoder;
import android.location.Address;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.example.lepsibakalari.worker.BakalariWorker;
import com.example.lepsibakalari.ai.LocalLLMManager;
import com.example.lepsibakalari.ai.ModelDownloader;
import java.util.concurrent.TimeUnit;
import java.io.File;

/**
 * MainActivity - Dashboard s rozvrhem, známkami, Komens a dalšími moduly
 * Bakalářů.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private BakalariRepository repository;
    private static final int TAB_TIMETABLE = 0;
    private static final int TAB_MARKS = 1;
    private static final int TAB_KOMENS = 2;
    private static final int TAB_HOMEWORKS = 3;
    private static final int TAB_MORE = 4;
    private int currentTab = TAB_TIMETABLE;
    private boolean marksByDate = false;
    private MarksResponse lastMarksData;
    private TimetableResponse lastTimetableData;
    private HomeworksResponse lastHomeworksData;
    private int activeLoadMoreRequests = 0;
    private WeatherResponse.CurrentWeather storedWeather;
    private double lastLat, lastLon;
    private LocalLLMManager localLLM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Adjust both floating bubbles to be under status bar
            int topMargin = systemBars.top + (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                    getResources().getDisplayMetrics());

            ViewGroup.MarginLayoutParams lpTitle = (ViewGroup.MarginLayoutParams) binding.titleBubble.getLayoutParams();
            lpTitle.topMargin = topMargin;
            binding.titleBubble.setLayoutParams(lpTitle);

            ViewGroup.MarginLayoutParams lpMenu = (ViewGroup.MarginLayoutParams) binding.menuBubble.getLayoutParams();
            lpMenu.topMargin = topMargin;
            binding.menuBubble.setLayoutParams(lpMenu);

            ViewGroup.MarginLayoutParams lpWeather = (ViewGroup.MarginLayoutParams) binding.weatherPill
                    .getLayoutParams();
            lpWeather.topMargin = topMargin;
            binding.weatherPill.setLayoutParams(lpWeather);

            return insets;
        });

        repository = new BakalariRepository(this);

        if (!repository.isLoggedIn()) {
            startLoginAndFinish();
            return;
        }

        setSupportActionBar(binding.toolbar);

        // Hide system navigation buttons for a truly immersive "Liquid Glass" look
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(),
                getWindow().getDecorView());
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        // Liquid Glass blur efekt na pozadí (API 31+)
        applyBlurEffect(binding.meshGradient);

        // Ensure floating elements are strictly on top and stay there
        binding.dockContainer.setTranslationZ(100f);
        binding.titleBubble.setTranslationZ(100f);
        binding.menuBubble.setTranslationZ(100f);
        binding.dockContainer.bringToFront();
        binding.titleBubble.bringToFront();
        binding.menuBubble.bringToFront();

        // Menu Bubble Click
        binding.menuBubble.setOnClickListener(v -> showGlassMenu());

        // Apply Glass Touch effects to top bubbles too
        applyGlassTouchEffect(binding.titleBubble);
        applyGlassTouchEffect(binding.menuBubble);

        setupNavigation();
        setupSwipeRefresh();
        loadTimetable();

        // Restore last weather if available for instant feel
        restoreLastWeather();
        setupWeather();

        // Apply Glass Touch to weather pill too
        applyGlassTouchEffect(binding.weatherPill);
        binding.weatherPill.setOnClickListener(v -> showWeatherDetailDialog());

        showWelcomeScreen();
        scheduleBackgroundChecks();
        requestNotificationPermission();

        // Inicializace lokálního LLM (běží na pozadí)
        localLLM = new LocalLLMManager(this);
        startAIStatusPolling();
        checkAndOfferModelDownload();
    }

    private void restoreLastWeather() {
        SharedPreferences prefs = getSharedPreferences("weather_cache", MODE_PRIVATE);
        float temp = prefs.getFloat("temp", -999);
        String city = prefs.getString("city", null);
        int code = prefs.getInt("code", 0);
        lastLat = Double.longBitsToDouble(prefs.getLong("lat", 0));
        lastLon = Double.longBitsToDouble(prefs.getLong("lon", 0));

        if (temp != -999) {
            binding.weatherPill.setVisibility(View.VISIBLE);
            binding.textTemp.setText(Math.round(temp) + "°");
            if (city != null) {
                binding.textWeatherCity.setText(city);
                binding.textWeatherCity.setVisibility(View.VISIBLE);
            }
            updateWeatherIcon(binding.imgWeather, code);
        }
    }

    private void setupWeather() {
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION },
                    1001);
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null)
            return;

        try {
            Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }

            if (location != null) {
                fetchWeather(location.getLatitude(), location.getLongitude());
            } else {
                // Request single update with a timeout
                final android.location.LocationListener listener = new android.location.LocationListener() {
                    @Override
                    public void onLocationChanged(Location loc) {
                        fetchWeather(loc.getLatitude(), loc.getLongitude());
                    }

                    @Override
                    public void onStatusChanged(String s, int i, Bundle b) {
                    }

                    @Override
                    public void onProviderEnabled(String s) {
                    }

                    @Override
                    public void onProviderDisabled(String s) {
                    }
                };

                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, null);
                } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, null);
                }

                // Timeout fallback after 5 seconds
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (storedWeather == null) {
                        // If still nothing, show a hint or the pill with "Vybrat město"
                        runOnUiThread(() -> {
                            binding.weatherPill.setVisibility(View.VISIBLE);
                            binding.textTemp.setText("--");
                            binding.textWeatherCity.setText("Vybrat město");
                            binding.textWeatherCity.setVisibility(View.VISIBLE);
                        });
                    }
                }, 5000);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupWeather();
            }
        }
    }

    private void fetchWeather(double lat, double lon) {
        this.lastLat = lat;
        this.lastLon = lon;
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        WeatherApi weatherApi = retrofit.create(WeatherApi.class);
        String fields = "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m";
        weatherApi.getCurrentWeather(lat, lon, fields, "auto").enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    WeatherResponse.CurrentWeather current = response.body().getCurrent();
                    if (current != null) {
                        storedWeather = current;
                        runOnUiThread(() -> {
                            binding.weatherPill.setVisibility(View.VISIBLE);
                            binding.textTemp.setText(Math.round(current.getTemperature()) + "°");
                            updateWeatherIcon(binding.imgWeather, current.getWeatherCode());

                            // Save update time
                            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                            String time = sdf.format(new Date());
                            getSharedPreferences("weather_cache", MODE_PRIVATE).edit()
                                    .putString("last_update", time)
                                    .apply();
                        });
                        // Toast.makeText(MainActivity.this, "Počasí pro " + lat + ", " + lon + ": " +
                        // current.getTemperature() + "°", Toast.LENGTH_SHORT).show();
                        // Get city name using Nominatim (much more reliable for small CZ towns)
                        weatherApi.reverseGeocode(lat, lon, "jsonv2", "cs").enqueue(new Callback<NominatimResponse>() {
                            @Override
                            public void onResponse(Call<NominatimResponse> call, Response<NominatimResponse> response) {
                                if (response.isSuccessful() && response.body() != null
                                        && response.body().getAddress() != null) {
                                    String city = response.body().getAddress().getCity();
                                    if (city != null) {
                                        final String finalCity = city;
                                        runOnUiThread(() -> {
                                            binding.textWeatherCity.setText(finalCity);
                                            binding.textWeatherCity.setVisibility(View.VISIBLE);

                                            getSharedPreferences("weather_cache", MODE_PRIVATE).edit()
                                                    .putFloat("temp", (float) current.getTemperature())
                                                    .putString("city", finalCity)
                                                    .putInt("code", current.getWeatherCode())
                                                    .putLong("lat", Double.doubleToRawLongBits(lat))
                                                    .putLong("lon", Double.doubleToRawLongBits(lon))
                                                    .apply();
                                        });
                                    }
                                }
                            }

                            @Override
                            public void onFailure(Call<NominatimResponse> call, Throwable t) {
                                // Fallback to basic label if API fails
                                runOnUiThread(() -> binding.textWeatherCity.setText("Moje poloha"));
                            }
                        });
                    } else {
                        runOnUiThread(() -> Toast
                                .makeText(MainActivity.this, "API nevrátilo data (current=null)", Toast.LENGTH_SHORT)
                                .show());
                    }
                } else {
                    runOnUiThread(() -> Toast
                            .makeText(MainActivity.this, "API chyba: " + response.code(), Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                runOnUiThread(() -> Toast
                        .makeText(MainActivity.this, "Chyba sítě u počasí: " + t.getMessage(), Toast.LENGTH_SHORT)
                        .show());
            }
        });
    }

    private void updateWeatherIcon(ImageView imageView, int code) {
        if (code <= 1) {
            imageView.setImageResource(R.drawable.ic_weather_sun);
        } else if (code <= 3) {
            imageView.setImageResource(R.drawable.ic_weather_cloud); // Partly cloudy
        } else if (code >= 51 && code <= 67) {
            imageView.setImageResource(R.drawable.ic_weather_cloud); // Rain (using cloud for now)
        } else if (code >= 80 && code <= 82) {
            imageView.setImageResource(R.drawable.ic_weather_cloud); // Showers
        } else {
            imageView.setImageResource(R.drawable.ic_weather_cloud);
        }
    }

    private void showWeatherDetailDialog() {
        if (storedWeather == null) {
            // Show search directly if no data
            showCitySearchDialog();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.layout_weather_detail, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.dimAmount = 0.45f;
                lp.setBlurBehindRadius(120);
                dialog.getWindow().setAttributes(lp);
            }
        }

        TextView textCity = dialogView.findViewById(R.id.textDetailCity);
        TextView textTemp = dialogView.findViewById(R.id.textDetailTemp);
        TextView textApparent = dialogView.findViewById(R.id.textDetailApparent);
        TextView textHumidity = dialogView.findViewById(R.id.textDetailHumidity);
        TextView textWind = dialogView.findViewById(R.id.textDetailWind);
        TextView textRaw = dialogView.findViewById(R.id.textDetailRaw);
        ImageView imgWeather = dialogView.findViewById(R.id.imgDetailWeather);
        View btnClose = dialogView.findViewById(R.id.btnWeatherClose);

        textRaw.setText(String.format(Locale.getDefault(), "Lat: %.5f, Lon: %.5f", lastLat, lastLon));

        // Show last update time
        String lastTime = getSharedPreferences("weather_cache", MODE_PRIVATE).getString("last_update", "--:--");
        textRaw.setText(textRaw.getText() + " | Aktualizováno: " + lastTime);

        // Get City Name from UI pill or cache
        String currentCity = binding.textWeatherCity.getText().toString();
        if (!currentCity.isEmpty() && !currentCity.equals("Vybrat město")
                && !currentCity.equals("Zjišťuji polohu...")) {
            textCity.setText(currentCity);
        } else {
            textCity.setText("Moje poloha"); // default fallback
            // Background update if still "Locating..."
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://api.open-meteo.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            WeatherApi weatherApi = retrofit.create(WeatherApi.class);
            weatherApi.reverseGeocode(lastLat, lastLon, "jsonv2", "cs").enqueue(new Callback<NominatimResponse>() {
                @Override
                public void onResponse(Call<NominatimResponse> call, Response<NominatimResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getAddress() != null) {
                        String city = response.body().getAddress().getCity();
                        if (city != null) {
                            runOnUiThread(() -> {
                                textCity.setText(city);
                                binding.textWeatherCity.setText(city);
                                binding.textWeatherCity.setVisibility(View.VISIBLE);
                            });
                        }
                    }
                }

                @Override
                public void onFailure(Call<NominatimResponse> call, Throwable t) {
                }
            });
        }

        textTemp.setText(Math.round(storedWeather.getTemperature()) + "°");
        textApparent.setText(Math.round(storedWeather.getApparentTemperature()) + "°");
        textHumidity.setText(storedWeather.getHumidity() + "%");
        textWind.setText(Math.round(storedWeather.getWindSpeed()) + " km/h");

        updateWeatherIcon(imgWeather, storedWeather.getWeatherCode());

        applyGlassTouchEffect(btnClose);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Change City Click
        textCity.setOnClickListener(v -> {
            dialog.dismiss();
            showCitySearchDialog();
        });

        dialog.show();
    }

    private void showCitySearchDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Např. Ostrava, Brno...");
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(pad, pad / 2, pad, pad / 2);
        input.setLayoutParams(lp);
        container.addView(input);

        AlertDialog searchDialog = new AlertDialog.Builder(this)
                .setTitle("Změnit lokaci")
                .setView(container)
                .setPositiveButton("Hledat", (d, which) -> {
                    String query = input.getText().toString().trim();
                    if (!query.isEmpty()) {
                        searchCity(query);
                    }
                })
                .setNegativeButton("Zpět", null)
                .create();

        if (searchDialog.getWindow() != null) {
            searchDialog.getWindow().setBackgroundDrawableResource(R.drawable.glass_card);
        }
        searchDialog.show();
    }

    private void searchCity(String cityName) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://geocoding-api.open-meteo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        WeatherApi weatherApi = retrofit.create(WeatherApi.class);
        weatherApi.searchCity(cityName, 1, "cs", "json").enqueue(new Callback<GeocodingResponse>() {
            @Override
            public void onResponse(Call<GeocodingResponse> call, Response<GeocodingResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResults() != null
                        && !response.body().getResults().isEmpty()) {
                    GeocodingResponse.Result result = response.body().getResults().get(0);
                    runOnUiThread(() -> {
                        // Manually update the city name in cache and display
                        getSharedPreferences("weather_cache", MODE_PRIVATE).edit()
                                .putString("city", result.getName())
                                .apply();
                        binding.textWeatherCity.setText(result.getName());
                        binding.textWeatherCity.setVisibility(View.VISIBLE);

                        fetchWeather(result.getLatitude(), result.getLongitude());
                        Toast.makeText(MainActivity.this, "Lokace změněna: " + result.getName(), Toast.LENGTH_SHORT)
                                .show();
                    });
                } else {
                    runOnUiThread(
                            () -> Toast.makeText(MainActivity.this, "Město nenalezeno", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(Call<GeocodingResponse> call, Throwable t) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Chyba při hledání", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showWelcomeScreen() {
        binding.welcomeOverlay.setAlpha(0f);
        binding.welcomeOverlay.setVisibility(View.VISIBLE);

        // Animate in with bounce
        binding.welcomeOverlay.animate().alpha(1f).setDuration(400).start();

        // Target the inner LinearLayout for scale
        View card = ((ViewGroup) binding.welcomeOverlay).getChildAt(0);
        card.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(600)
                .setInterpolator(new android.view.animation.OvershootInterpolator())
                .start();

        repository.getUserInfo(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                android.util.Log.d("LepsiBakalari", "User Profile Code: " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    final UserResponse user = response.body();
                    final String name = user.getFullName();
                    runOnUiThread(() -> {
                        String firstName = user.getFirstName();
                        if (firstName == null && name != null && !name.isEmpty()) {
                            String[] parts = name.trim().split("\\s+");
                            // If it's SURNAME NAME, the second part is likely the first name
                            if (parts.length > 1)
                                firstName = parts[1];
                            else
                                firstName = parts[0];
                        }

                        if (firstName != null && !firstName.isEmpty()) {
                            binding.textWelcomeName.setText(firstName);
                        } else {
                            String savedUser = getSharedPreferences("login", MODE_PRIVATE).getString("username", "");
                            if (!savedUser.isEmpty())
                                binding.textWelcomeName.setText(savedUser);
                            else
                                binding.textWelcomeName.setText("Student");
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        String savedUser = getSharedPreferences("login", MODE_PRIVATE).getString("username", "");
                        if (!savedUser.isEmpty())
                            binding.textWelcomeName.setText(savedUser);
                        else
                            binding.textWelcomeName.setText("Kamaráde");
                    });
                }
                scheduleHideWelcome();
            }

            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                scheduleHideWelcome();
            }
        });
    }

    private void scheduleHideWelcome() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            binding.welcomeOverlay.animate()
                    .alpha(0f)
                    .setDuration(800)
                    .withEndAction(() -> binding.welcomeOverlay.setVisibility(View.GONE))
                    .start();
        }, 2000);
    }

    private void setupSwipeRefresh() {
        binding.swipeTimetable.setOnRefreshListener(this::loadTimetable);
        binding.swipeMarks.setOnRefreshListener(this::loadMarks);
        binding.swipeKomens.setOnRefreshListener(this::loadKomens);
        binding.swipeHomeworks.setOnRefreshListener(this::loadHomeworksTab);
        binding.swipeMore.setOnRefreshListener(this::loadMore);

        // Customize SwipeRefresh colors for Liquid Glass look
        int glassAccent = Color.parseColor("#40FFFFFF");
        binding.swipeTimetable.setProgressBackgroundColorSchemeColor(glassAccent);
        binding.swipeMarks.setProgressBackgroundColorSchemeColor(glassAccent);
        binding.swipeKomens.setProgressBackgroundColorSchemeColor(glassAccent);
        binding.swipeHomeworks.setProgressBackgroundColorSchemeColor(glassAccent);
        binding.swipeMore.setProgressBackgroundColorSchemeColor(glassAccent);

        binding.swipeTimetable.setColorSchemeColors(Color.WHITE);
        binding.swipeMarks.setColorSchemeColors(Color.WHITE);
        binding.swipeKomens.setColorSchemeColors(Color.WHITE);
        binding.swipeHomeworks.setColorSchemeColors(Color.WHITE);
        binding.swipeMore.setColorSchemeColors(Color.WHITE);
    }

    private void toggleLoading(boolean show) {
        if (show) {
            binding.liquidLoading.setVisibility(View.VISIBLE);
            binding.liquidLoading.animate().alpha(1.0f).setDuration(400).start();
        } else {
            binding.liquidLoading.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                binding.liquidLoading.setVisibility(View.GONE);
            }).start();
        }
    }

    private void startLoginAndFinish() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Aplikuje RenderEffect blur pro Liquid Glass vzhled (API 31+).
     */
    private void applyBlurEffect(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Subtle blur for the background balls
            view.setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP));
        }
    }

    private void setupNavigation() {
        // Nastavení posluchačů kliknutí (haptic feedback je v applyGlassTouchEffect)
        binding.navTimetable.setOnClickListener(v -> switchTab(TAB_TIMETABLE));
        binding.navMarks.setOnClickListener(v -> switchTab(TAB_MARKS));
        binding.navKomens.setOnClickListener(v -> switchTab(TAB_KOMENS));
        binding.navHomeworks.setOnClickListener(v -> switchTab(TAB_HOMEWORKS));
        binding.navMore.setOnClickListener(v -> switchTab(TAB_MORE));

        // Marks Toggle Listeners
        View toggleRoot = binding.marksToggle.getRoot();
        toggleRoot.findViewById(R.id.btnBySubject).setOnClickListener(v -> {
            marksByDate = false;
            updateMarksToggleUI(binding.marksToggle.getRoot());
            if (lastMarksData != null)
                showMarks(lastMarksData);
        });
        toggleRoot.findViewById(R.id.btnByDate).setOnClickListener(v -> {
            marksByDate = true;
            updateMarksToggleUI(binding.marksToggle.getRoot());
            if (lastMarksData != null)
                showMarks(lastMarksData);
        });

        // Apple Glass Touch Effect (zmenšení při dotyku)
        applyGlassTouchEffect(binding.navTimetable);
        applyGlassTouchEffect(binding.navMarks);
        applyGlassTouchEffect(binding.navKomens);
        applyGlassTouchEffect(binding.navHomeworks);
        applyGlassTouchEffect(binding.navMore);

        // AI Summary on Logo Click
        binding.titleBubble.setOnClickListener(v -> showAISummaryDialog());
    }

    private void updateMarksToggleUI(View root) {
        TextView btnSubj = root.findViewById(R.id.btnBySubject);
        TextView btnDate = root.findViewById(R.id.btnByDate);
        btnSubj.setAlpha(marksByDate ? 0.5f : 1.0f);
        btnSubj.setTypeface(null, marksByDate ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        btnDate.setAlpha(marksByDate ? 1.0f : 0.5f);
        btnDate.setTypeface(null, marksByDate ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    /**
     * Aplikuje "Apple Glass" efekt při dotyku - jemné zmenšení prvku.
     * Vylepšeno o spolehlivé vracení do původního stavu.
     */
    private void applyGlassTouchEffect(final View view) {
        view.setOnTouchListener(new android.view.View.OnTouchListener() {
            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        v.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.85f).setDuration(100).start();
                        return true; // Musíme vrátit true, abychom dostali ACTION_UP

                    case android.view.MotionEvent.ACTION_UP:
                        v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start();
                        // Pokud jsme pustili prst nad prvkem, vyvoláme click
                        float x = event.getX();
                        float y = event.getY();
                        if (x >= 0 && x <= v.getWidth() && y >= 0 && y <= v.getHeight()) {
                            // Direct vibration for guaranteed haptic feedback
                            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(
                                    Context.VIBRATOR_SERVICE);
                            if (vibrator != null && vibrator.hasVibrator()) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(50,
                                            android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                                } else {
                                    vibrator.vibrate(50);
                                }
                            }
                            v.performClick();
                        }
                        return true;

                    case android.view.MotionEvent.ACTION_CANCEL:
                    case android.view.MotionEvent.ACTION_OUTSIDE:
                        v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start();
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        float mx = event.getX();
                        float my = event.getY();
                        if (mx < 0 || mx > v.getWidth() || my < 0 || my > v.getHeight()) {
                            // Vyjeli jsme prstem ven - zvětšíme zpět
                            v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void setAllViewsGone() {
        binding.swipeTimetable.setVisibility(View.GONE);
        binding.swipeMarks.setVisibility(View.GONE);
        binding.swipeKomens.setVisibility(View.GONE);
        binding.swipeHomeworks.setVisibility(View.GONE);
        binding.swipeMore.setVisibility(View.GONE);
    }

    private void setNavIconsInactive() {
        // Reset all icons to half-alpha and original scale
        binding.navTimetableIcon.setColorFilter(0x80FFFFFF);
        binding.navTimetableIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();

        binding.navMarksIcon.setColorFilter(0x80FFFFFF);
        binding.navMarksIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();

        binding.navKomensIcon.setColorFilter(0x80FFFFFF);
        binding.navKomensIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();

        binding.navHomeworksIcon.setColorFilter(0x80FFFFFF);
        binding.navHomeworksIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();

        binding.navMoreIcon.setColorFilter(0x80FFFFFF);
        binding.navMoreIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
    }

    private void switchTab(int tab) {
        currentTab = tab;
        setAllViewsGone();
        setNavIconsInactive();

        switch (tab) {
            case TAB_TIMETABLE:
                binding.swipeTimetable.setVisibility(View.VISIBLE);
                binding.navTimetableIcon.setColorFilter(0xFFFFFFFF);
                binding.navTimetableIcon.animate().scaleX(1.4f).scaleY(1.4f).setDuration(250).start();
                loadTimetable();
                break;
            case TAB_MARKS:
                binding.swipeMarks.setVisibility(View.VISIBLE);
                binding.navMarksIcon.setColorFilter(0xFFFFFFFF);
                binding.navMarksIcon.animate().scaleX(1.4f).scaleY(1.4f).setDuration(250).start();
                loadMarks();
                break;
            case TAB_KOMENS:
                binding.swipeKomens.setVisibility(View.VISIBLE);
                binding.navKomensIcon.setColorFilter(0xFFFFFFFF);
                binding.navKomensIcon.animate().scaleX(1.4f).scaleY(1.4f).setDuration(250).start();
                loadKomens();
                break;
            case TAB_HOMEWORKS:
                binding.swipeHomeworks.setVisibility(View.VISIBLE);
                binding.navHomeworksIcon.setColorFilter(0xFFFFFFFF);
                binding.navHomeworksIcon.animate().scaleX(1.4f).scaleY(1.4f).setDuration(250).start();
                loadHomeworksTab();
                break;
            case TAB_MORE:
                binding.swipeMore.setVisibility(View.VISIBLE);
                binding.navMoreIcon.setColorFilter(0xFFFFFFFF);
                binding.navMoreIcon.animate().scaleX(1.4f).scaleY(1.4f).setDuration(250).start();
                loadMore();
                break;
        }
    }

    private void loadTimetable() {
        toggleLoading(true);
        binding.textLoading.setVisibility(View.GONE);
        binding.lessonsContainer.removeAllViews();
        addEmptyView(binding.lessonsContainer, getString(R.string.loading));

        repository.getTimetableToday(new Callback<TimetableResponse>() {
            @Override
            public void onResponse(Call<TimetableResponse> call, Response<TimetableResponse> response) {
                toggleLoading(false);
                binding.swipeTimetable.setRefreshing(false);
                binding.lessonsContainer.removeAllViews();
                if (!response.isSuccessful() || response.body() == null) {
                    addEmptyView(binding.lessonsContainer, "Chyba načítání rozvrhu.");
                    return;
                }
                lastTimetableData = response.body();
                saveTimetableForWidget(response.body());
                performSuccessVibration();
                showTimetable(response.body());
                // Automatically refresh homeworks and marks in background for AI summary
                preloadAILatestData();
            }

            @Override
            public void onFailure(Call<TimetableResponse> call, Throwable t) {
                toggleLoading(false);
                binding.swipeTimetable.setRefreshing(false);
                binding.lessonsContainer.removeAllViews();
                addEmptyView(binding.lessonsContainer, "Chyba sítě: " + t.getMessage());
            }
        });
    }

    private String formatDisplayDate(String dateStr) {
        if (dateStr == null || dateStr.length() < 10)
            return dateStr != null ? dateStr : "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat out = new SimpleDateFormat("EEEE d. MMMM", Locale.forLanguageTag("cs-CZ"));
            Date d = in.parse(dateStr.substring(0, 10));
            return d != null ? out.format(d) : dateStr;
        } catch (ParseException e) {
            return dateStr;
        }
    }

    private String formatShortDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty())
            return "";
        if (dateStr.length() < 10) {
            // Zkusíme jestli to není už zformátované "d.M."
            if (dateStr.matches("\\d{1,2}\\.\\d{1,2}\\."))
                return dateStr;
            return dateStr;
        }
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat out = new SimpleDateFormat("d.M.", Locale.getDefault());
            Date d = in.parse(dateStr.substring(0, 10));
            return d != null ? out.format(d) : dateStr.substring(0, 10);
        } catch (ParseException e) {
            return dateStr.substring(0, 10);
        }
    }

    /**
     * Zkusí najít datum v textu (např. "12.2." nebo "5.10.")
     */
    private String extractDateFromText(String text) {
        if (text == null || text.isEmpty())
            return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{1,2}\\.\\d{1,2}\\.)");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void showTimetable(TimetableResponse data) {
        binding.lessonsContainer.removeAllViews();
        Map<Integer, TimetableResponse.Hour> hoursMap = data.getHours() != null
                ? data.getHours().stream().collect(Collectors.toMap(TimetableResponse.Hour::getId, h -> h))
                : Map.of();
        Map<String, TimetableResponse.Subject> subjectsMap = data.getSubjects() != null
                ? data.getSubjects().stream().collect(Collectors.toMap(s -> s.getId() != null ? s.getId() : "", s -> s))
                : Map.of();
        Map<String, TimetableResponse.Room> roomsMap = data.getRooms() != null
                ? data.getRooms().stream().collect(Collectors.toMap(r -> r.getId() != null ? r.getId() : "", r -> r))
                : Map.of();

        List<TimetableResponse.Day> days = data.getDays();
        if (days == null || days.isEmpty()) {
            addEmptyView(binding.lessonsContainer, getString(R.string.no_lessons));
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        int dp24 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        int dp12 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());

        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            TimetableResponse.Day day = days.get(dayIndex);
            String dateStr = day.getDate() != null ? day.getDate().substring(0, Math.min(10, day.getDate().length()))
                    : "";

            // Show only today and future days
            if (dateStr.compareTo(todayStr) < 0)
                continue;

            TextView dayHeader = (TextView) inflater.inflate(R.layout.item_day_header, binding.lessonsContainer, false);
            dayHeader.setText(formatDisplayDate(dateStr));
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);

            // Only no margin for the very first VISIBLE day
            headerParams.topMargin = binding.lessonsContainer.getChildCount() == 0 ? 0 : dp24;
            headerParams.bottomMargin = dp12;
            dayHeader.setLayoutParams(headerParams);
            binding.lessonsContainer.addView(dayHeader);

            List<TimetableResponse.Atom> atoms = day.getAtoms();
            if (atoms == null || atoms.isEmpty()) {
                addEmptyView(binding.lessonsContainer, getString(R.string.no_lessons), 14);
                continue;
            }

            atoms = new java.util.ArrayList<>(atoms);
            atoms.sort((a, b) -> Integer.compare(a.getHourId(), b.getHourId()));

            for (TimetableResponse.Atom atom : atoms) {
                ItemLessonBinding itemBinding = ItemLessonBinding.inflate(inflater, binding.lessonsContainer, false);
                TimetableResponse.Hour hour = hoursMap.get(atom.getHourId());
                itemBinding.textHour.setText(hour != null && hour.getCaption() != null ? hour.getCaption() + "." : "");

                String subjId = atom.getSubjectId() != null ? atom.getSubjectId().trim() : "";
                TimetableResponse.Subject subj = subjectsMap.get(subjId);
                if (subj == null && atom.getSubjectId() != null)
                    subj = subjectsMap.get(atom.getSubjectId());
                itemBinding.textSubject.setText(subj != null && subj.getName() != null ? subj.getName() : "-");

                String roomId = atom.getRoomId() != null ? atom.getRoomId().trim() : "";
                TimetableResponse.Room room = roomsMap.get(roomId);
                if (room == null && atom.getRoomId() != null)
                    room = roomsMap.get(atom.getRoomId());
                String roomText = room != null && room.getAbbrev() != null ? room.getAbbrev() : "";
                itemBinding.textRoom.setText(roomText);
                itemBinding.textRoom.setVisibility(roomText.isEmpty() ? View.GONE : View.VISIBLE);

                String theme = atom.getTheme();
                itemBinding.textTheme.setText(theme != null && !theme.isEmpty() ? theme : "");
                itemBinding.textTheme.setVisibility(theme != null && !theme.isEmpty() ? View.VISIBLE : View.GONE);

                // Substitution Icon (Výměna/Suplování)
                itemBinding.icSubstitution.setVisibility(atom.getChange() != null ? View.VISIBLE : View.GONE);

                binding.lessonsContainer.addView(itemBinding.getRoot());
                applyGlassTouchEffect(itemBinding.getRoot());

                // AUTOMATIC ENTRANCE ANIMATION (The Zoomer)
                itemBinding.getRoot().setAlpha(0f);
                itemBinding.getRoot().setScaleX(0.92f);
                itemBinding.getRoot().setScaleY(0.92f);
                itemBinding.getRoot().animate()
                        .alpha(1.0f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(450)
                        .setStartDelay(binding.lessonsContainer.getChildCount() * 40L)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                        .start();

                if (binding.lessonsContainer.getChildCount() < 12) {
                    tickVibration(binding.lessonsContainer.getChildCount() * 40L);
                }

                // Feature: Lesson Detail Dialog
                final TimetableResponse.Subject fSubj = subj;
                final TimetableResponse.Room fRoom = room;
                final TimetableResponse.Hour fHour = hour;
                final TimetableResponse.Atom fAtom = atom;

                itemBinding.getRoot().setOnClickListener(v -> {
                    showLessonDetail(fSubj, fRoom, fHour, fAtom, data.getTeachers(), fAtom.getTeacherId());
                });
            }
        }
    }

    private void showLessonDetail(TimetableResponse.Subject subject,
            TimetableResponse.Room room,
            TimetableResponse.Hour hour,
            TimetableResponse.Atom atom,
            List<TimetableResponse.Teacher> teachers,
            String teacherId) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // We use a custom view for the Liquid Glass look
        com.example.lepsibakalari.databinding.DialogLessonDetailBinding dBinding = com.example.lepsibakalari.databinding.DialogLessonDetailBinding
                .inflate(getLayoutInflater());

        String subjName = subject != null && subject.getName() != null ? subject.getName() : "Předmět";
        dBinding.dialogSubjectName.setText(subjName);

        String tName = "-";
        if (teachers != null && teacherId != null) {
            String tid = teacherId.trim();
            TimetableResponse.Teacher t = teachers.stream()
                    .filter(te -> te.getId().trim().equals(tid))
                    .findFirst().orElse(null);
            if (t != null)
                tName = t.getName();
        }
        dBinding.dialogTeacher.setText("Učitel: " + tName);

        String rName = "-";
        if (room != null) {
            // Priority: Full Name -> Abbrev -> Raw ID
            if (room.getName() != null && !room.getName().trim().isEmpty()) {
                rName = room.getName();
            } else if (room.getAbbrev() != null && !room.getAbbrev().trim().isEmpty()) {
                rName = room.getAbbrev();
            } else {
                rName = atom.getRoomId();
            }
        } else if (atom.getRoomId() != null) {
            rName = atom.getRoomId();
        }

        dBinding.dialogRoom.setText("Místnost: " + rName);
        dBinding.dialogRoom.setVisibility(View.VISIBLE);

        String theme = atom.getTheme() != null && !atom.getTheme().isEmpty() ? atom.getTheme() : "Bez tématu";
        dBinding.dialogTheme.setText("Téma: " + theme);

        String time = hour != null ? hour.getBeginTime() + " - " + hour.getEndTime() : "";
        dBinding.dialogTime.setText(time);

        builder.setView(dBinding.getRoot());
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.dimAmount = 0.45f;
                lp.setBlurBehindRadius(120);
                dialog.getWindow().setAttributes(lp);
            }
        }
        dialog.show();
    }

    private void loadMarks() {
        toggleLoading(true);
        binding.marksContent.removeAllViews();
        addEmptyView(binding.marksContent, getString(R.string.loading));

        repository.getMarks(new Callback<MarksResponse>() {
            @Override
            public void onResponse(Call<MarksResponse> call, Response<MarksResponse> response) {
                toggleLoading(false);
                binding.swipeMarks.setRefreshing(false);
                binding.marksContent.removeAllViews();
                if (!response.isSuccessful() || response.body() == null) {
                    addEmptyView(binding.marksContent, "Chyba načítání známek.");
                    return;
                }
                lastMarksData = response.body();
                showMarks(lastMarksData);
            }

            @Override
            public void onFailure(Call<MarksResponse> call, Throwable t) {
                toggleLoading(false);
                binding.swipeMarks.setRefreshing(false);
                binding.marksContent.removeAllViews();
                addEmptyView(binding.marksContent, "Chyba sítě: " + t.getMessage());
            }
        });
    }

    private void showMarks(MarksResponse data) {
        binding.marksContent.removeAllViews();
        if (marksByDate) {
            showMarksByDate(data);
        } else {
            showMarksBySubject(data);
        }
    }

    private void showMarksBySubject(MarksResponse data) {
        List<MarksResponse.SubjectMarks> subjects = data.getSubjects();
        if (subjects == null || subjects.isEmpty()) {
            addEmptyView(binding.marksContent, "Žádné známky");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarksResponse.SubjectMarks sm : subjects) {
            ItemMarkBinding itemBinding = ItemMarkBinding.inflate(inflater, binding.marksContent, false);
            String name = sm.getSubject() != null && sm.getSubject().getName() != null ? sm.getSubject().getName()
                    : "-";
            itemBinding.textSubjectName.setText(name);
            itemBinding.textAverage.setText("Průměr: " + (sm.getAverageText() != null ? sm.getAverageText() : ""));
            StringBuilder marksStr = new StringBuilder();
            if (sm.getMarks() != null) {
                for (MarksResponse.Mark m : sm.getMarks()) {
                    if (marksStr.length() > 0)
                        marksStr.append(", ");
                    String mText = m.getMarkText() != null ? m.getMarkText() : "-";
                    String mDate = m.getDate() != null ? " (" + formatShortDate(m.getDate()) + ")" : "";
                    marksStr.append(mText).append(mDate);
                }
            }
            itemBinding.textMarks.setText("Známky: " + marksStr);
            binding.marksContent.addView(itemBinding.getRoot());
            applyGlassTouchEffect(itemBinding.getRoot());

            // Long-press for "What If" Grade Predictor
            itemBinding.getRoot().setOnLongClickListener(v -> {
                android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(50,
                                android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(50);
                    }
                }
                showGradePredictorDialog(sm);
                return true;
            });

            // ANIMATION
            itemBinding.getRoot().setAlpha(0f);
            itemBinding.getRoot().setScaleX(0.92f);
            itemBinding.getRoot().setScaleY(0.92f);
            itemBinding.getRoot().animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(450)
                    .setStartDelay(binding.marksContent.getChildCount() * 40L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                    .start();

            if (binding.marksContent.getChildCount() < 12) {
                tickVibration(binding.marksContent.getChildCount() * 40L);
            }
        }
    }

    private int getGradeColor(String markText) {
        if (markText == null || markText.isEmpty())
            return Color.WHITE;
        char firstChar = markText.charAt(0);
        if (firstChar == '1')
            return Color.parseColor("#404CAF50"); // Greenish Glow
        if (firstChar == '2')
            return Color.parseColor("#408BC34A"); // Lime Glow
        if (firstChar == '3')
            return Color.parseColor("#40FFC107"); // Amber Glow
        if (firstChar == '4')
            return Color.parseColor("#40FF9800"); // Orange Glow
        if (firstChar == '5')
            return Color.parseColor("#40F44336"); // Red Glow
        return Color.parseColor("#40FFFFFF"); // Default White Glow
    }

    private void showGradePredictorDialog(MarksResponse.SubjectMarks subject) {
        String subjectName = subject.getSubject() != null ? subject.getSubject().getName() : "Předmět";
        String currentAvgStr = subject.getAverageText() != null ? subject.getAverageText() : "0.00";
        double currentAvg = 0.0;
        try {
            currentAvg = Double.parseDouble(currentAvgStr.replace(",", "."));
        } catch (NumberFormatException e) {
            // ignore
        }

        // Inflate custom layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_grade_predictor, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Make background transparent for glass effect
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.dimAmount = 0.45f;
                lp.setBlurBehindRadius(120);
                dialog.getWindow().setAttributes(lp);
            }
        }

        // UI References
        TextView textSubject = dialogView.findViewById(R.id.textSubjectName);
        TextView textCurrent = dialogView.findViewById(R.id.textCurrentAvg);
        TextView textPredicted = dialogView.findViewById(R.id.textPredictedAvg);
        LinearLayout containerAdded = dialogView.findViewById(R.id.containerAddedMarks);
        TextView textNoMarks = dialogView.findViewById(R.id.textNoMarks);
        android.widget.EditText inputGrade = dialogView.findViewById(R.id.inputGrade);
        android.widget.EditText inputWeight = dialogView.findViewById(R.id.inputWeight);
        View btnAdd = dialogView.findViewById(R.id.btnAddMark);
        View btnClose = dialogView.findViewById(R.id.btnClose);

        // Init UI
        textSubject.setText(subjectName);
        textCurrent.setText("Současný průměr: " + currentAvgStr);
        textPredicted.setText(currentAvgStr);
        TextView textDiff = dialogView.findViewById(R.id.textDiff);

        // Initial color set
        setPredictorColor(textPredicted, currentAvg);

        // Hypothetical marks Data
        List<Double> addedGrades = new ArrayList<>();
        List<Double> addedWeights = new ArrayList<>();

        // Add Mark Logic
        btnAdd.setOnClickListener(v -> {
            String gStr = inputGrade.getText().toString();
            String wStr = inputWeight.getText().toString();

            if (gStr.isEmpty() || wStr.isEmpty())
                return;

            try {
                double g = Double.parseDouble(gStr);
                double w = Double.parseDouble(wStr);

                if (g < 1 || g > 5) {
                    Toast.makeText(this, "Známka musí být 1-5", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Add to list
                addedGrades.add(g);
                addedWeights.add(w);

                // Add to UI as a ROW
                if (textNoMarks.getVisibility() == View.VISIBLE) {
                    textNoMarks.setVisibility(View.GONE);
                }

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(16, 16, 16, 16);
                row.setBackgroundResource(R.drawable.glass_card);
                row.getBackground().setTint(Color.parseColor("#10FFFFFF")); // subtle row bg
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.setMargins(0, 0, 0, 16);
                row.setLayoutParams(rowParams);

                // Grade Circle
                TextView markCircle = new TextView(this);
                markCircle.setText(String.format(java.util.Locale.getDefault(), "%.0f", g));
                markCircle.setTextColor(Color.WHITE);
                markCircle.setTextSize(18);
                markCircle.setTypeface(null, android.graphics.Typeface.BOLD);
                markCircle.setGravity(android.view.Gravity.CENTER);
                markCircle.setBackgroundResource(R.drawable.glass_circle);
                markCircle.getBackground().setTint(getGradeColor(String.valueOf((int) g)));
                // Circle size
                LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(80, 80);
                circleParams.setMargins(0, 0, 24, 0);
                markCircle.setLayoutParams(circleParams);
                row.addView(markCircle);

                // Weight Text
                TextView weightText = new TextView(this);
                weightText.setText(String.format(java.util.Locale.getDefault(), "Váha: %.0f", w));
                weightText.setTextColor(Color.parseColor("#CCFFFFFF"));
                weightText.setTextSize(14);
                LinearLayout.LayoutParams weightParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                weightText.setLayoutParams(weightParams);
                row.addView(weightText);

                // Delete Button
                TextView deleteBtn = new TextView(this);
                deleteBtn.setText("✕");
                deleteBtn.setTextColor(Color.parseColor("#F44336")); // Red X
                deleteBtn.setTextSize(18);
                deleteBtn.setTypeface(null, android.graphics.Typeface.BOLD);
                deleteBtn.setPadding(24, 12, 24, 12);
                // Make it clickable
                deleteBtn.setOnClickListener(delView -> {
                    int index = containerAdded.indexOfChild(row);
                    if (index != -1) {
                        // Safely remove OBJECT not index
                        addedGrades.remove(Double.valueOf(g));
                        addedWeights.remove(Double.valueOf(w));

                        containerAdded.removeView(row);
                        if (containerAdded.getChildCount() <= 1) { // only textNoMarks left
                            textNoMarks.setVisibility(View.VISIBLE);
                        }
                        recalculatePredictor(subject, addedGrades, addedWeights, textPredicted, textDiff);
                    }
                });
                row.addView(deleteBtn);

                containerAdded.addView(row);

                // Scroll to bottom
                if (containerAdded.getParent() instanceof android.widget.ScrollView) {
                    final android.widget.ScrollView scrollView = (android.widget.ScrollView) containerAdded.getParent();
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                }

                // Clear input
                inputGrade.setText("");

                // Haptic
                android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(30,
                                android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(30);
                    }
                }

                // RECALCULATE
                recalculatePredictor(subject, addedGrades, addedWeights, textPredicted, textDiff);

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Neplatné číslo", Toast.LENGTH_SHORT).show();
            }
        });

        applyGlassTouchEffect(btnAdd);

        btnClose.setOnClickListener(v -> dialog.dismiss());
        applyGlassTouchEffect(btnClose);

        dialog.show();
    }

    private void recalculatePredictor(MarksResponse.SubjectMarks subject, List<Double> newGrades,
            List<Double> newWeights, TextView resultView, TextView diffView) {
        double totalWeighted = 0;
        double totalWeight = 0;

        // 1. Calculate base (existing marks)
        if (subject.getMarks() != null) {
            for (MarksResponse.Mark m : subject.getMarks()) {
                try {
                    String text = m.getMarkText();
                    int weight = m.getWeight() != null ? m.getWeight() : 0;

                    // Simple parsing: if it starts with digit
                    if (text != null && !text.isEmpty() && Character.isDigit(text.charAt(0))) {
                        // Check for specialized marks like "1-" => 1.5 is common in CZ but Bakalari
                        // usually treat as text.
                        // Standard implementation: take the first number.
                        // Some schools use "1-" as 1.5, others as 1. Let's do simple parsing of the
                        // first digit for safety, or parse double if possible.

                        double val = 0;
                        if (text.contains("-")) {
                            val = Double.parseDouble(text.substring(0, 1)) + 0.5;
                        } else {
                            // Try parse full (e.g. "1.5")
                            val = Double.parseDouble(text.replace(",", "."));
                        }

                        totalWeighted += val * weight;
                        totalWeight += weight;
                    }
                } catch (Exception e) {
                    // ignore complex marks
                }
            }
        }

        // 2. Add hypothetical marks
        for (int i = 0; i < newGrades.size(); i++) {
            totalWeighted += newGrades.get(i) * newWeights.get(i);
            totalWeight += newWeights.get(i);
        }

        if (totalWeight == 0) {
            resultView.setText("?");
            return;
        }

        double newAvg = totalWeighted / totalWeight;
        resultView.setText(String.format(java.util.Locale.getDefault(), "%.2f", newAvg));

        // Advanced coloring based on ranges
        setPredictorColor(resultView, newAvg);

        // Calculate diff
        String currentStr = subject.getAverageText();
        if (currentStr != null) {
            try {
                double current = Double.parseDouble(currentStr.replace(",", "."));
                double diff = newAvg - current;

                if (Math.abs(diff) < 0.005) {
                    diffView.setText("-- stejný --");
                    diffView.setTextColor(Color.parseColor("#80FFFFFF"));
                } else if (diff < 0) {
                    // Better (grade is lower)
                    diffView.setText(String.format(java.util.Locale.getDefault(), "zlepšení o %.2f", Math.abs(diff)));
                    diffView.setTextColor(Color.parseColor("#804CAF50")); // Green tint
                } else {
                    // Worse
                    diffView.setText(String.format(java.util.Locale.getDefault(), "zhoršení o %.2f", Math.abs(diff)));
                    diffView.setTextColor(Color.parseColor("#80F44336")); // Red tint
                }
            } catch (Exception e) {
            }
        }
    }

    private void setPredictorColor(TextView view, double avg) {
        if (avg <= 1.50) {
            view.setTextColor(Color.parseColor("#4CAF50")); // Green 1
        } else if (avg <= 2.50) {
            view.setTextColor(Color.parseColor("#8BC34A")); // Light Green 2
        } else if (avg <= 3.50) {
            view.setTextColor(Color.parseColor("#FFC107")); // Amber 3
        } else if (avg <= 4.50) {
            view.setTextColor(Color.parseColor("#FF9800")); // Orange 4
        } else {
            view.setTextColor(Color.parseColor("#F44336")); // Red 5
        }
    }

    private void showMarksByDate(MarksResponse data) {
        java.util.List<MarkWithSubject> allMarks = new java.util.ArrayList<>();
        if (data.getSubjects() != null) {
            for (MarksResponse.SubjectMarks sm : data.getSubjects()) {
                String subjName = sm.getSubject() != null ? sm.getSubject().getName() : "-";
                if (sm.getMarks() != null) {
                    for (MarksResponse.Mark m : sm.getMarks()) {
                        allMarks.add(new MarkWithSubject(m, subjName));
                    }
                }
            }
        }

        if (allMarks.isEmpty()) {
            addEmptyView(binding.marksContent, "Žádné známky");
            return;
        }

        // Sort by date - newest first
        allMarks.sort((a, b) -> {
            String da = a.mark.getMarkDate();
            if (da == null)
                da = a.mark.getDate();

            String db = b.mark.getMarkDate();
            if (db == null)
                db = b.mark.getDate();

            if (da == null)
                return 1;
            if (db == null)
                return -1;
            return db.compareTo(da);
        });

        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarkWithSubject mws : allMarks) {
            ItemMarkSingleBinding itemBinding = ItemMarkSingleBinding.inflate(inflater, binding.marksContent, false);
            itemBinding.textMark.setText(mws.mark.getMarkText());
            itemBinding.textSubject.setText(mws.subjectName);
            itemBinding.textTopic.setText(mws.mark.getCaption() != null ? mws.mark.getCaption() : "-");

            // DISPLAY BADGES (Weight and Date) separately

            // 1. Weight (Váha)
            int weight = (mws.mark.getWeight() != null) ? mws.mark.getWeight() : 0;
            if (weight > 0) {
                itemBinding.textWeight.setText("⚖️ " + weight);
                itemBinding.textWeight.setVisibility(View.VISIBLE);
            } else {
                itemBinding.textWeight.setVisibility(View.GONE);
            }

            // 2. Date (Datum) - Try multiple API fields
            String dateStr = mws.mark.getMarkDate(); // Native API v3 field
            if (dateStr == null || dateStr.isEmpty()) {
                dateStr = mws.mark.getDate(); // Secondary field
            }
            if (dateStr == null || dateStr.isEmpty()) {
                dateStr = mws.mark.getEditDate(); // Fallback to modification date
            }
            if (dateStr == null || dateStr.isEmpty()) {
                dateStr = extractDateFromText(mws.mark.getCaption());
                if (dateStr == null)
                    dateStr = extractDateFromText(mws.mark.getTypeNote());
            }

            if (dateStr != null && !dateStr.isEmpty()) {
                itemBinding.textDate.setText(formatShortDate(dateStr)); // No calendar icon
                itemBinding.textDate.setVisibility(View.VISIBLE);
            } else {
                itemBinding.textDate.setVisibility(View.GONE);
            }

            // Apply Dynamic Glow based on Grade
            int glowColor = getGradeColor(mws.mark.getMarkText());
            itemBinding.viewMarkGlow.getBackground().setTint(glowColor);

            // "What If" Predictor on Long Click (By Date view)
            itemBinding.getRoot().setOnLongClickListener(v -> {
                // Find subject
                if (data.getSubjects() != null) {
                    for (MarksResponse.SubjectMarks sm : data.getSubjects()) {
                        String sName = sm.getSubject() != null ? sm.getSubject().getName() : "-";
                        if (sName.equals(mws.subjectName)) {
                            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(
                                    Context.VIBRATOR_SERVICE);
                            if (vibrator != null && vibrator.hasVibrator()) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(50,
                                            android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                                } else {
                                    vibrator.vibrate(50);
                                }
                            }
                            showGradePredictorDialog(sm);
                            return true;
                        }
                    }
                }
                return false;
            });

            binding.marksContent.addView(itemBinding.getRoot());
            applyGlassTouchEffect(itemBinding.getRoot());

            // AUTOMATIC ENTRANCE ANIMATION (The Zoomer)
            itemBinding.getRoot().setAlpha(0f);
            itemBinding.getRoot().setScaleX(0.85f);
            itemBinding.getRoot().setScaleY(0.85f);
            itemBinding.getRoot().animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(400)
                    .setStartDelay(binding.marksContent.getChildCount() * 50L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                    .start();

            if (binding.marksContent.getChildCount() < 12) {
                tickVibration(binding.marksContent.getChildCount() * 50L);
            }
        }
    }

    private static class MarkWithSubject {
        MarksResponse.Mark mark;
        String subjectName;

        MarkWithSubject(MarksResponse.Mark m, String s) {
            this.mark = m;
            this.subjectName = s;
        }
    }

    private void loadKomens() {
        toggleLoading(true);
        binding.textKomensLoading.setVisibility(View.GONE);
        binding.komensList.removeAllViews();
        addEmptyView(binding.komensList, getString(R.string.loading));

        repository.getKomensReceived(new Callback<KomensResponse>() {
            @Override
            public void onResponse(Call<KomensResponse> call, Response<KomensResponse> response) {
                toggleLoading(false);
                binding.swipeKomens.setRefreshing(false);
                binding.komensList.removeAllViews();
                if (response.isSuccessful() && response.body() != null) {
                    showKomens(response.body());
                } else {
                    addEmptyView(binding.komensList, "Žádné zprávy.");
                }
            }

            @Override
            public void onFailure(Call<KomensResponse> call, Throwable t) {
                toggleLoading(false);
                binding.swipeKomens.setRefreshing(false);
                binding.komensList.removeAllViews();
                addEmptyView(binding.komensList, "Chyba sítě: " + t.getMessage());
            }
        });
    }

    private void showKomens(KomensResponse data) {
        binding.komensList.removeAllViews();
        List<KomensResponse.KomensMessage> messages = data.getMessages();
        if (messages == null || messages.isEmpty()) {
            addEmptyView(binding.komensList, getString(R.string.no_messages));
            return;
        }

        // Sort: Newest first
        messages = new java.util.ArrayList<>(messages);
        messages.sort((a, b) -> {
            String da = a.getSentDate();
            String db = b.getSentDate();
            if (da == null)
                return 1;
            if (db == null)
                return -1;
            return db.compareTo(da);
        });

        LayoutInflater inflater = LayoutInflater.from(this);
        for (KomensResponse.KomensMessage msg : messages) {
            ItemKomensBinding itemBinding = ItemKomensBinding.inflate(inflater, binding.komensList, false);
            itemBinding.textTitle.setText(msg.getTitle() != null ? msg.getTitle() : "-");
            String sName = msg.getSender() != null ? msg.getSender().getName() : "";
            itemBinding.textSender.setText(sName);
            itemBinding.textAvatar.setText(getInitials(sName));

            String sentDate = msg.getSentDate();
            if (sentDate != null && sentDate.length() >= 10) {
                // Showing date and time in the badge for messages
                String displayDate = formatShortDate(sentDate.substring(0, 10));
                if (sentDate.length() >= 16) {
                    displayDate += " " + sentDate.substring(11, 16);
                }
                itemBinding.textDate.setText(displayDate);
                itemBinding.textDate.setVisibility(View.VISIBLE);
            } else {
                itemBinding.textDate.setVisibility(View.GONE);
            }

            // Rich Preview - show first 100 chars of message text
            String msgText = msg.getText();
            if (msgText != null && !msgText.isEmpty()) {
                // Strip HTML tags for preview
                String preview = msgText.replaceAll("<[^>]*>", "").trim();
                if (preview.length() > 100) {
                    preview = preview.substring(0, 100) + "...";
                }
                itemBinding.textPreview.setText(preview);
                itemBinding.textPreview.setVisibility(View.VISIBLE);
            } else {
                itemBinding.textPreview.setVisibility(View.GONE);
            }

            // Attachment Indicator - API doesn't expose attachments yet
            // Keep the UI element for future updates, but hide it for now
            itemBinding.textAttachment.setVisibility(View.GONE);
            binding.komensList.addView(itemBinding.getRoot());
            applyGlassTouchEffect(itemBinding.getRoot());

            // ANIMATION
            itemBinding.getRoot().setAlpha(0f);
            itemBinding.getRoot().setScaleX(0.92f);
            itemBinding.getRoot().setScaleY(0.92f);
            itemBinding.getRoot().animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(450)
                    .setStartDelay(binding.komensList.getChildCount() * 40L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                    .start();

            if (binding.komensList.getChildCount() < 12) {
                tickVibration(binding.komensList.getChildCount() * 40L);
            }
        }
    }

    private void showSubjectSelectionDialog() {
        if (lastMarksData != null) {
            showSubjectListDialog(lastMarksData);
        } else {
            // Need to fetch marks first
            toggleLoading(true);
            repository.getMarks(new Callback<MarksResponse>() {
                @Override
                public void onResponse(Call<MarksResponse> call, Response<MarksResponse> response) {
                    toggleLoading(false);
                    if (response.isSuccessful() && response.body() != null) {
                        lastMarksData = response.body();
                        showSubjectListDialog(lastMarksData);
                    } else {
                        Toast.makeText(MainActivity.this, "Nelze načíst předměty", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<MarksResponse> call, Throwable t) {
                    toggleLoading(false);
                    Toast.makeText(MainActivity.this, "Chyba sítě", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void saveTimetableForWidget(TimetableResponse data) {
        String json = new com.google.gson.Gson().toJson(data);
        getSharedPreferences("widget_data", MODE_PRIVATE).edit()
                .putString("timetable_json", json)
                .apply();

        // Notify widget provider
        Intent intent = new Intent(this, com.example.lepsibakalari.widget.NextLessonWidget.class);
        intent.setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] ids = android.appwidget.AppWidgetManager.getInstance(getApplication())
                .getAppWidgetIds(new android.content.ComponentName(getApplication(),
                        com.example.lepsibakalari.widget.NextLessonWidget.class));
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }

    private void tickVibration(long delay) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // Stronger click effect
                    v.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK));
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    // Increased duration and amplitude
                    v.vibrate(android.os.VibrationEffect.createOneShot(25, 150));
                } else {
                    v.vibrate(25);
                }
            }
        }, delay);
    }

    private void performSuccessVibration() {
        android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Double pulse for success
                long[] pattern = { 0, 50, 50, 50 };
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(100);
            }
        }
    }

    private void showSubjectListDialog(MarksResponse data) {
        if (data.getSubjects() == null || data.getSubjects().isEmpty()) {
            Toast.makeText(this, "Žádné předměty k dispozici", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Custom title view
        TextView titleView = new TextView(this);
        titleView.setText("Vyber předmět");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(22);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setPadding(50, 60, 50, 30);
        builder.setCustomTitle(titleView);

        // Custom Adapter for Glass Cards
        android.widget.ArrayAdapter<MarksResponse.SubjectMarks> adapter = new android.widget.ArrayAdapter<MarksResponse.SubjectMarks>(
                this, R.layout.item_list_subject, data.getSubjects()) {
            @androidx.annotation.NonNull
            @Override
            public View getView(int position, @androidx.annotation.Nullable View convertView,
                    @androidx.annotation.NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_list_subject, parent, false);
                }

                MarksResponse.SubjectMarks item = getItem(position);
                TextView text1 = convertView.findViewById(android.R.id.text1); // Subject Name
                TextView text2 = convertView.findViewById(android.R.id.text2); // Average

                if (item != null) {
                    String name = item.getSubject() != null ? item.getSubject().getName() : "Předmět";
                    text1.setText(name);

                    String avg = item.getAverageText() != null ? "Průměr: " + item.getAverageText() : "";
                    text2.setText(avg);
                }

                // Animation
                convertView.setTranslationY(60f);
                convertView.setAlpha(0f);
                convertView.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(400)
                        .setStartDelay(position * 50L)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.05f))
                        .start();

                // Vibration Sync
                if (position < 10) {
                    tickVibration(position * 50L);
                }

                return convertView;
            }
        };

        builder.setAdapter(adapter, (dialog, which) -> {
            MarksResponse.SubjectMarks selected = data.getSubjects().get(which);
            showGradePredictorDialog(selected);
        });

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.glass_card);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.dimAmount = 0.45f;
                lp.setBlurBehindRadius(120);
                dialog.getWindow().setAttributes(lp);
            }
            // Style List View
            dialog.setOnShowListener(d -> {
                android.widget.ListView lw = ((AlertDialog) d).getListView();
                if (lw != null) {
                    lw.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    lw.setDividerHeight(30); // Spacing
                    lw.setPadding(30, 0, 30, 30);
                    lw.setClipToPadding(false);
                    lw.setScrollbarFadingEnabled(false);
                    lw.setVerticalScrollBarEnabled(false);
                }
            });
        }
        dialog.show();
    }

    private void loadMore() {
        toggleLoading(true);
        activeLoadMoreRequests = 3;
        binding.moreContent.removeAllViews();

        LinearLayout finalMarksContainer = createSectionContainer();
        LinearLayout eventsContainer = createSectionContainer();
        LinearLayout substitutionsContainer = createSectionContainer();

        // Grade Predictor Button
        android.widget.TextView predictorBtn = new android.widget.TextView(this);
        predictorBtn.setText("🔮 Předvídač známek");
        predictorBtn.setTextColor(Color.WHITE);
        predictorBtn.setTextSize(18);
        predictorBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        predictorBtn.setPadding(40, 40, 40, 40);
        predictorBtn.setBackgroundResource(R.drawable.glass_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 40);
        predictorBtn.setLayoutParams(params);

        predictorBtn.setOnClickListener(v -> {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                            android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(50);
                }
            }
            showSubjectSelectionDialog();
        });

        binding.moreContent.addView(predictorBtn);
        applyGlassTouchEffect(predictorBtn);

        addSectionHeader(binding.moreContent, getString(R.string.section_substitutions));
        binding.moreContent.addView(substitutionsContainer);

        addSectionHeader(binding.moreContent, getString(R.string.section_final_marks));
        binding.moreContent.addView(finalMarksContainer);

        addSectionHeader(binding.moreContent, getString(R.string.section_events));
        binding.moreContent.addView(eventsContainer);

        repository.getMarksFinal(new Callback<MarksFinalResponse>() {
            @Override
            public void onResponse(Call<MarksFinalResponse> call, Response<MarksFinalResponse> response) {
                activeLoadMoreRequests--;
                if (activeLoadMoreRequests <= 0) {
                    toggleLoading(false);
                    binding.swipeMore.setRefreshing(false);
                    performSuccessVibration();
                }
                if (response.isSuccessful() && response.body() != null) {
                    showFinalMarks(response.body(), finalMarksContainer);
                } else {
                    addEmptyView(finalMarksContainer, getString(R.string.loading), 12);
                }
            }

            @Override
            public void onFailure(Call<MarksFinalResponse> call, Throwable t) {
                activeLoadMoreRequests--;
                if (activeLoadMoreRequests <= 0) {
                    toggleLoading(false);
                    binding.swipeMore.setRefreshing(false);
                    performSuccessVibration();
                }
                addEmptyView(finalMarksContainer, "Chyba: " + t.getMessage(), 12);
            }
        });

        repository.getEvents(getDateDaysAgo(0), new Callback<EventsResponse>() {
            @Override
            public void onResponse(Call<EventsResponse> call, Response<EventsResponse> response) {
                activeLoadMoreRequests--;
                if (activeLoadMoreRequests <= 0) {
                    toggleLoading(false);
                    binding.swipeMore.setRefreshing(false);
                    performSuccessVibration();
                }
                if (response.isSuccessful() && response.body() != null) {
                    showEvents(response.body(), eventsContainer);
                } else {
                    addEmptyView(eventsContainer, getString(R.string.no_events), 12);
                }
            }

            @Override
            public void onFailure(Call<EventsResponse> call, Throwable t) {
                activeLoadMoreRequests--;
                if (activeLoadMoreRequests <= 0) {
                    toggleLoading(false);
                    binding.swipeMore.setRefreshing(false);
                    performSuccessVibration();
                }
                addEmptyView(eventsContainer, "Chyba: " + t.getMessage(), 12);
            }
        });

        repository.getSubstitutions(getDateDaysAgo(0), new Callback<SubstitutionsResponse>() {
            @Override
            public void onResponse(Call<SubstitutionsResponse> call, Response<SubstitutionsResponse> response) {
                activeLoadMoreRequests--;
                if (activeLoadMoreRequests <= 0) {
                    toggleLoading(false);
                    binding.swipeMore.setRefreshing(false);
                    performSuccessVibration();
                }
                if (response.isSuccessful() && response.body() != null) {
                    showSubstitutions(response.body(), substitutionsContainer);
                } else {
                    addEmptyView(substitutionsContainer, getString(R.string.no_substitutions), 12);
                }
            }

            @Override
            public void onFailure(Call<SubstitutionsResponse> call, Throwable t) {
                activeLoadMoreRequests--;
                if (activeLoadMoreRequests <= 0) {
                    toggleLoading(false);
                    binding.swipeMore.setRefreshing(false);
                    performSuccessVibration();
                }
                addEmptyView(substitutionsContainer, "Chyba: " + t.getMessage(), 12);
            }
        });
    }

    private LinearLayout createSectionContainer() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        return ll;
    }

    private void showFinalMarks(MarksFinalResponse data, LinearLayout container) {
        List<MarksFinalResponse.CertificateTerm> termsList = data.getCertificateTerms();
        if (termsList == null || termsList.isEmpty()) {
            addEmptyView(container, "Žádná vysvědčení", 12);
            return;
        }

        // Reverse list to show newest first
        termsList = new java.util.ArrayList<>(termsList);
        java.util.Collections.reverse(termsList);

        for (MarksFinalResponse.CertificateTerm term : termsList) {
            TextView tv = new TextView(this);
            tv.setBackgroundResource(R.drawable.glass_card);
            int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                    getResources().getDisplayMetrics());
            tv.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8,
                    getResources().getDisplayMetrics());
            tv.setLayoutParams(lp);

            String year = term.getSchoolYear() != null ? term.getSchoolYear() : "";
            String sem = term.getSemesterName() != null ? term.getSemesterName() : "";
            String grade = term.getGradeName() != null ? term.getGradeName() : "";
            String achievement = term.getAchievementText() != null ? term.getAchievementText() : "";
            Double avg = term.getMarksAverage();

            StringBuilder sb = new StringBuilder();
            sb.append(year).append(" – ").append(sem).append(" pololetí (").append(grade).append(")\n");
            if (avg != null)
                sb.append("Průměr: ").append(String.format(Locale.getDefault(), "%.2f", avg)).append("\n");
            sb.append(achievement);

            tv.setText(sb.toString());
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(14);
            container.addView(tv);
            applyGlassTouchEffect(tv);

            // ANIMATION
            tv.setAlpha(0f);
            tv.setScaleX(0.95f);
            tv.animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .setDuration(400)
                    .setStartDelay(container.getChildCount() * 60L)
                    .start();

            if (container.getChildCount() < 12) {
                tickVibration(container.getChildCount() * 60L);
            }
        }
    }

    private void loadHomeworksTab() {
        toggleLoading(true);
        binding.homeworksContent.removeAllViews();
        addEmptyView(binding.homeworksContent, getString(R.string.loading), 14);

        // Fetch only from today onwards
        String from = getDateDaysAgo(0);
        String to = getDateDaysAgo(30);
        repository.getHomeworks(from, to, new Callback<HomeworksResponse>() {
            @Override
            public void onResponse(Call<HomeworksResponse> call, Response<HomeworksResponse> response) {
                toggleLoading(false);
                binding.swipeHomeworks.setRefreshing(false);
                binding.homeworksContent.removeAllViews();
                if (response.isSuccessful() && response.body() != null) {
                    lastHomeworksData = response.body();
                    showHomeworks(response.body(), binding.homeworksContent);
                } else {
                    addEmptyView(binding.homeworksContent, getString(R.string.no_homeworks), 14);
                }
            }

            @Override
            public void onFailure(Call<HomeworksResponse> call, Throwable t) {
                toggleLoading(false);
                binding.swipeHomeworks.setRefreshing(false);
                binding.homeworksContent.removeAllViews();
                addEmptyView(binding.homeworksContent, "Chyba: " + t.getMessage(), 14);
            }
        });
    }

    private void showHomeworks(HomeworksResponse data, LinearLayout container) {
        List<HomeworksResponse.Homework> list = data.getHomeworks();
        if (list == null || list.isEmpty()) {
            addEmptyView(container, getString(R.string.no_homeworks), 12);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (HomeworksResponse.Homework hw : list) {
            ItemHomeworkBinding itemBinding = ItemHomeworkBinding.inflate(inflater, container, false);
            itemBinding.textSubject.setText(hw.getSubject() != null ? hw.getSubject().getName() : "-");

            String content = hw.getContent();
            if (content != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                itemBinding.textContent.setText(Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY).toString());
            } else {
                itemBinding.textContent.setText(content != null ? content : "");
            }

            String dateEnd = hw.getDateEnd();
            int urgencyColor = Color.parseColor("#40FFFFFF"); // Default white glow
            if (dateEnd != null && dateEnd.length() >= 10) {
                itemBinding.textDate.setText(formatShortDate(dateEnd.substring(0, 10)));
                itemBinding.textDate.setVisibility(View.VISIBLE);

                // Calculate urgency based on days until deadline
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    Date deadline = sdf.parse(dateEnd.substring(0, 10));
                    Date today = new Date();
                    long diffMs = deadline.getTime() - today.getTime();
                    long daysUntil = diffMs / (1000 * 60 * 60 * 24);

                    if (daysUntil < 0) {
                        urgencyColor = Color.parseColor("#60F44336"); // Overdue - Strong Red
                    } else if (daysUntil == 0) {
                        urgencyColor = Color.parseColor("#60FF5722"); // Today - Orange-Red
                    } else if (daysUntil == 1) {
                        urgencyColor = Color.parseColor("#60FF9800"); // Tomorrow - Orange
                    } else if (daysUntil <= 3) {
                        urgencyColor = Color.parseColor("#60FFC107"); // Soon - Amber
                    } else if (daysUntil <= 7) {
                        urgencyColor = Color.parseColor("#408BC34A"); // This week - Lime
                    } else {
                        urgencyColor = Color.parseColor("#404CAF50"); // Distant - Green
                    }
                } catch (ParseException e) {
                    // Keep default white
                }
            } else {
                itemBinding.textDate.setVisibility(View.GONE);
            }

            // Apply urgency glow to homework card background
            if (itemBinding.getRoot().getBackground() != null) {
                itemBinding.getRoot().getBackground().setTint(urgencyColor);
            }

            // Local save/check for "Manual Mark as Done"
            String hwId = hw.getId() != null ? hw.getId()
                    : (hw.getSubject() != null ? hw.getSubject().getAbbrev() : "") + hw.getDateEnd();
            SharedPreferences prefs = getSharedPreferences("homework_prefs", MODE_PRIVATE);
            boolean isChecked = prefs.getBoolean(hwId, false);

            updateHomeworkDoneUI(itemBinding, isChecked);

            itemBinding.layoutDone.setOnClickListener(v -> {
                boolean nextState = !itemBinding.checkDone.isChecked();
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                prefs.edit().putBoolean(hwId, nextState).apply();
                updateHomeworkDoneUI(itemBinding, nextState);
            });

            container.addView(itemBinding.getRoot());
            applyGlassTouchEffect(itemBinding.getRoot());

            // ANIMATION
            itemBinding.getRoot().setAlpha(0f);
            itemBinding.getRoot().setScaleX(0.92f);
            itemBinding.getRoot().setScaleY(0.92f);
            itemBinding.getRoot().animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(450)
                    .setStartDelay(container.getChildCount() * 40L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                    .start();

            if (container.getChildCount() < 12) {
                tickVibration(container.getChildCount() * 40L);
            }
        }
    }

    private void updateHomeworkDoneUI(ItemHomeworkBinding itemBinding, boolean isChecked) {
        itemBinding.checkDone.setChecked(isChecked);
        if (isChecked) {
            itemBinding.textDoneStatus.setText("Hotovo");
            itemBinding.textDoneStatus.setTextColor(Color.parseColor("#80FFFFFF"));
            itemBinding.textSubject.setAlpha(0.6f);
            itemBinding.textContent.setAlpha(0.4f);
        } else {
            itemBinding.textDoneStatus.setText("Nedokončeno");
            itemBinding.textDoneStatus.setTextColor(Color.WHITE);
            itemBinding.textSubject.setAlpha(1.0f);
            itemBinding.textContent.setAlpha(1.0f);
        }
    }

    private void showAbsence(AbsenceResponse data, LinearLayout container) {
        List<AbsenceResponse.AbsencePerSubject> perSubject = data.getAbsencesPerSubject();
        if (perSubject != null && !perSubject.isEmpty()) {
            for (AbsenceResponse.AbsencePerSubject a : perSubject) {
                TextView tv = createGlassTextView(a.getSubjectName() + ": " + a.getBase() + " hodin absence");
                container.addView(tv);

                // ANIMATION
                tv.setAlpha(0f);
                tv.setTranslationY(20f);
                tv.animate().alpha(1.0f).translationY(0f).setDuration(400)
                        .setStartDelay(container.getChildCount() * 50L).start();
                if (container.getChildCount() < 12)
                    tickVibration(container.getChildCount() * 50L);
            }
        } else {
            List<AbsenceResponse.AbsenceDay> days = data.getAbsences();
            if (days != null && !days.isEmpty()) {
                int totalMissed = 0, totalOk = 0;
                for (AbsenceResponse.AbsenceDay d : days) {
                    totalMissed += d.getMissed() + d.getUnsolved();
                    totalOk += d.getOk();
                }
                TextView tv = createGlassTextView("Omluvené: " + totalOk + ", Neomluvené: " + totalMissed);
                container.addView(tv);

                // ANIMATION
                tv.setAlpha(0f);
                tv.setTranslationY(20f);
                tv.animate().alpha(1.0f).translationY(0f).setDuration(400).start();
                tickVibration(0);
            } else {
                addEmptyView(container, "Žádná absence", 12);
            }
        }
    }

    private void showEvents(EventsResponse data, LinearLayout container) {
        List<EventsResponse.Event> events = data.getEvents();
        if (events == null || events.isEmpty()) {
            addEmptyView(container, getString(R.string.no_events), 12);
            return;
        }

        // Sort: Newest first based on start time
        events = new java.util.ArrayList<>(events);
        events.sort((a, b) -> {
            String da = (a.getTimes() != null && !a.getTimes().isEmpty()) ? a.getTimes().get(0).getStartTime() : "";
            String db = (b.getTimes() != null && !b.getTimes().isEmpty()) ? b.getTimes().get(0).getStartTime() : "";
            return db.compareTo(da);
        });

        for (EventsResponse.Event ev : events) {
            TextView tv = createGlassTextView((ev.getTitle() != null ? ev.getTitle() : "-") + "\n" +
                    (ev.getEventType() != null && ev.getEventType().getName() != null ? ev.getEventType().getName()
                            : ""));
            container.addView(tv);
            applyGlassTouchEffect(tv);

            // ANIMATION
            tv.setAlpha(0f);
            tv.setTranslationX(-20f);
            tv.animate()
                    .alpha(1.0f)
                    .translationX(0f)
                    .setDuration(400)
                    .setStartDelay(container.getChildCount() * 50L)
                    .start();

            if (container.getChildCount() < 12) {
                tickVibration(container.getChildCount() * 50L);
            }
        }
    }

    private void showSubstitutions(SubstitutionsResponse data, LinearLayout container) {
        List<SubstitutionsResponse.Change> changes = data.getChanges();
        if (changes == null || changes.isEmpty()) {
            addEmptyView(container, getString(R.string.no_substitutions), 12);
            return;
        }

        String today = getDateDaysAgo(0);
        // Filter: Today inclusive
        List<SubstitutionsResponse.Change> filtered = new java.util.ArrayList<>();
        for (SubstitutionsResponse.Change ch : changes) {
            String day = ch.getDay();
            if (day != null && day.compareTo(today) >= 0) {
                filtered.add(ch);
            }
        }

        if (filtered.isEmpty()) {
            addEmptyView(container, "Žádné aktuální ani budoucí změny", 12);
            return;
        }

        // Sort: Chronological (nearest first)
        filtered.sort((a, b) -> {
            String ta = a.getDay() != null ? a.getDay() : "";
            String tb = b.getDay() != null ? b.getDay() : "";
            return ta.compareTo(tb);
        });

        for (SubstitutionsResponse.Change ch : filtered) {
            String day = ch.getDay() != null && ch.getDay().length() >= 10
                    ? formatShortDate(ch.getDay().substring(0, 10))
                    : "";
            String hour = ch.getHours() != null ? ch.getHours() : "";
            TextView tv = createGlassTextView(
                    day + " | " + hour + ". hod: " + (ch.getDescription() != null ? ch.getDescription() : ""));
            container.addView(tv);
            applyGlassTouchEffect(tv);

            // ANIMATION
            tv.setAlpha(0f);
            tv.setTranslationY(30f);
            tv.animate()
                    .alpha(1.0f)
                    .translationY(0f)
                    .setDuration(350)
                    .setStartDelay(container.getChildCount() * 60L)
                    .start();

            if (container.getChildCount() < 12) {
                tickVibration(container.getChildCount() * 60L);
            }
        }
    }

    private TextView createGlassTextView(String text) {
        TextView tv = new TextView(this);
        tv.setBackgroundResource(R.drawable.glass_card);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        tv.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8,
                getResources().getDisplayMetrics());
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(14);
        return tv;
    }

    private void addSectionHeader(LinearLayout parent, String title) {
        TextView header = (TextView) LayoutInflater.from(this).inflate(R.layout.item_section_header, parent, false);
        header.setText(title);
        parent.addView(header);
    }

    private void addEmptyView(LinearLayout parent, String text) {
        addEmptyView(parent, text, 16);
    }

    private void addEmptyView(LinearLayout parent, String text, int sp) {
        TextView empty = new TextView(this);
        empty.setText(text);
        empty.setTextColor(0xFFFFFFFF);
        empty.setTextSize(sp);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setBackgroundResource(R.drawable.glass_card);

        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        empty.setPadding(pad, pad, pad, pad);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                getResources().getDisplayMetrics());
        lp.setMargins(0, margin, 0, margin);
        empty.setLayoutParams(lp);

        parent.addView(empty);
    }

    private String getDateDaysAgo(int daysOffset) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, daysOffset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            confirmLogout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout)
                .setMessage("Opravdu se chcete odhlásit?")
                .setPositiveButton("Ano", (dialog, which) -> {
                    repository.clearCredentials();
                    startLoginAndFinish();
                })
                .setNegativeButton("Ne", null)
                .show();
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty())
            return "?";
        // Remove common academic titles if needed, but simple split is okay for now
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            // Find first and last name initials
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    private int getSubjectColor(String id) {
        if (id == null || id.isEmpty())
            return 0xFF6366F1;
        int hash = id.hashCode();
        float[] hsv = new float[3];
        hsv[0] = Math.abs(hash % 360);
        hsv[1] = 0.65f;
        hsv[2] = 0.95f;
        return android.graphics.Color.HSVToColor(hsv);
    }

    private void showGlassMenu() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        View menuView = LayoutInflater.from(this).inflate(R.layout.layout_glass_menu, null);
        dialog.setContentView(menuView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.dimAmount = 0.45f;
                lp.setBlurBehindRadius(120);
                dialog.getWindow().setAttributes(lp);
            }
        }

        View btnLogout = menuView.findViewById(R.id.btnLogout);
        applyGlassTouchEffect(btnLogout);
        btnLogout.setOnClickListener(v -> {
            dialog.dismiss();
            confirmLogout();
        });
        // The following line is added based on the instruction, assuming 'binding' is
        // accessible in the context
        // where this method is called or that this snippet is part of a larger
        // 'setupNavigation' method.
        // For this specific change, it's placed here as per the provided diff's
        // context.
        // If 'binding' is not defined, this will cause a compilation error.
        // Assuming 'binding' is a field of the class.
        // binding.navHomeworks.setOnClickListener(v -> {
        // v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        // showTab(binding.swipeHomeworks, "Úkoly", binding.navHomeworksIcon);
        // loadHomeworksTab();
        // });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.85),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void preloadAILatestData() {
        repository.getMarks(new Callback<MarksResponse>() {
            @Override
            public void onResponse(Call<MarksResponse> call, Response<MarksResponse> response) {
                if (response.isSuccessful())
                    lastMarksData = response.body();
            }

            @Override
            public void onFailure(Call<MarksResponse> call, Throwable t) {
            }
        });
        repository.getHomeworks(null, null, new Callback<HomeworksResponse>() {
            @Override
            public void onResponse(Call<HomeworksResponse> call, Response<HomeworksResponse> response) {
                if (response.isSuccessful())
                    lastHomeworksData = response.body();
            }

            @Override
            public void onFailure(Call<HomeworksResponse> call, Throwable t) {
            }
        });
    }

    private void showAISummaryDialog() {
        tickVibration(50);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(60, 60, 60, 60);
        container.setBackgroundResource(R.drawable.glass_card);

        TextView title = new TextView(this);
        title.setText("AI Přehled Dne");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setGravity(android.view.Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(title);

        TextView content = new TextView(this);
        content.setTextColor(0xD0FFFFFF);
        content.setTextSize(16);
        content.setPadding(0, 40, 0, 40);
        content.setLineSpacing(0, 1.2f);

        ProgressBar loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        loading.setVisibility(View.GONE);
        LinearLayout.LayoutParams lpLoading = new LinearLayout.LayoutParams(80, 80);
        lpLoading.gravity = android.view.Gravity.CENTER;
        loading.setLayoutParams(lpLoading);
        container.addView(loading);

        if (localLLM != null && localLLM.isInitializing()) {
            content.setText(
                    "Neural Engine se právě startuje...\nMá 1.5 GB, tak mu to chvíli trvá. Zkus to za 10 vteřin.");
            container.addView(content);
            showBuiltDialog(builder, container);
            return;
        }

        if (localLLM != null && localLLM.isReady()) {
            TextView modelBadge = new TextView(this);
            modelBadge.setText("⚡ Local AI (Qwen 0.5B)");
            modelBadge.setTextSize(10);
            modelBadge.setTextColor(0x90FFFFFF);
            modelBadge.setGravity(android.view.Gravity.CENTER);
            container.addView(modelBadge);

            content.setText("Neural Engine přemýšlí...");
            loading.setVisibility(View.VISIBLE);
            container.addView(content);
            showBuiltDialog(builder, container);

            // Qwen Chat Template
            StringBuilder sbPrompt = new StringBuilder();
            sbPrompt.append(
                    "<|im_start|>system\nJsi školní asistent Bakalářů. Tvůj tón je lidský, kámošský a stručný.<|im_end|>\n");
            sbPrompt.append("<|im_start|>user\nData o studentovi:\n");
            if (lastTimetableData != null && lastTimetableData.getDays() != null
                    && !lastTimetableData.getDays().isEmpty()) {
                sbPrompt.append("- Rozvrh: ").append(lastTimetableData.getDays().get(0).getAtoms().size())
                        .append(" hodin dnes.\n");
            }
            if (lastHomeworksData != null) {
                int undone = 0;
                for (HomeworksResponse.Homework h : lastHomeworksData.getHomeworks())
                    if (!h.isDone())
                        undone++;
                sbPrompt.append("- Úkoly: ").append(undone).append(" restů.\n");
            }
            sbPrompt.append("Počasí: ").append(binding.textTemp.getText()).append(".\n");
            sbPrompt.append("\nNapiš motivační shrnutí dne (česky, max 3 věty).<|im_end|>\n");
            sbPrompt.append("<|im_start|>assistant\n");

            localLLM.generateSummary(sbPrompt.toString(), new LocalLLMManager.LLMCallback() {
                @Override
                public void onResult(String result) {
                    runOnUiThread(() -> {
                        loading.setVisibility(View.GONE);
                        // Clean up Qwen artifacts if any
                        String clean = result.trim().replace("<|im_end|>", "").replace("<|endoftext|>", "");
                        content.setText(clean);
                    });
                }
                // ... (onError remains the same)

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        loading.setVisibility(View.GONE);
                        content.setText("Chyba: " + error);
                    });
                }
            });
            return;
        }

        // --- FALLBACK SMART RULES (if model file missing) ---
        StringBuilder sb = new StringBuilder();
        java.util.Random rnd = new java.util.Random();

        String[] greetings = {
                "Čau! Mám pro tebe bleskový update:",
                "Ahoj! Tady je info na dnešek:",
                "Pozdrav z digitálního světa!",
                "Vítej! Tady je tvůj školní briefing:"
        };
        sb.append(greetings[rnd.nextInt(greetings.length)]).append("\n\n");

        if (lastTimetableData != null && lastTimetableData.getDays() != null
                && !lastTimetableData.getDays().isEmpty()) {
            int total = lastTimetableData.getDays().get(0).getAtoms().size();
            sb.append("📅 ").append(total > 6 ? "Drsný den: " : "Pohoda: ").append(total).append(" hodin.\n");
        }

        if (lastHomeworksData != null && lastHomeworksData.getHomeworks() != null) {
            int undone = 0;
            for (HomeworksResponse.Homework h : lastHomeworksData.getHomeworks())
                if (!h.isDone())
                    undone++;
            sb.append(undone > 0 ? "✏️ Úkoly: Zbývá ti " + undone + "." : "✅ Úkoly: Vše hotovo!").append("\n");
        }

        // 5. Weather Aware Advice (original logic, slightly modified for brevity)
        String tempText = binding.textTemp.getText().toString();
        if (!tempText.equals("--")) {
            try {
                int temp = Integer.parseInt(tempText.replace("°", "").replace("+", "").trim());
                sb.append("\n☁️ ");
                if (temp < 5)
                    sb.append("Venku mrzne, tak se pořádně nabal.");
                else if (temp < 15)
                    sb.append("Je celkem chladno, mikina se bude hodit.");
                else if (temp > 25)
                    sb.append("Dneska je vedro, nezapomeň hodně pít!");
                else
                    sb.append("Počasí vypadá celkem fajn.");
                sb.append("\n");
            } catch (Exception ignored) {
            }
        }

        File modelFileCheck = new File(getExternalFilesDir(null), "ai_model.task");
        boolean exists = modelFileCheck.exists();
        long size = exists ? modelFileCheck.length() : 0;

        sb.append("\n💡 AI Status: ")
                .append(exists ? "Soubor nalezen (" + (size / (1024 * 1024)) + " MB), ale mozek se nespustil."
                        : "Model nenalezen.");
        if (!exists)
            sb.append("\nZkuste stahování znovu nebo nahrajte ai_model.task.");

        sb.append("\n\nUžij si den! 🚀");
        content.setText(sb.toString());
        container.addView(content);

        showBuiltDialog(builder, container);
    }

    private void showBuiltDialog(AlertDialog.Builder builder, View container) {
        builder.setView(container);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.setBlurBehindRadius(100);
                dialog.getWindow().setAttributes(lp);
            }
        }
        dialog.show();
        container.setAlpha(0f);
        container.setScaleX(0.9f);
        container.animate().alpha(1f).scaleX(1f).setDuration(400)
                .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
    }

    private void startAIStatusPolling() {
        new Thread(() -> {
            // Kontrola každé 2 sekundy, dokud se AI nenahraje
            while (!isFinishing()) {
                if (localLLM != null && localLLM.isReady()) {
                    runOnUiThread(() -> {
                        binding.imgAIReady.setVisibility(View.VISIBLE);
                        binding.imgAIReady.setAlpha(0f);
                        binding.imgAIReady.animate().alpha(1f).scaleX(1.3f).scaleY(1.3f).setDuration(600)
                                .withEndAction(() -> binding.imgAIReady.animate().scaleX(1.0f).scaleY(1.0f)
                                        .setDuration(400).start())
                                .start();
                    });
                    break;
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void scheduleBackgroundChecks() {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                BakalariWorker.class,
                30, TimeUnit.MINUTES) // Check every 30 minutes
                .addTag("bakalari_update_check")
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "bakalari_periodic_check",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String postNotif = "android.permission.POST_NOTIFICATIONS";
            if (ActivityCompat.checkSelfPermission(this, postNotif) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] { postNotif }, 101);
            }
        }
    }

    private void checkAndOfferModelDownload() {
        File modelFile = new File(getExternalFilesDir(null), "ai_model.task");
        if (!modelFile.exists()) {
            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            boolean alreadyAsked = prefs.getBoolean("model_check_asked", false);

            if (!alreadyAsked) {
                new AlertDialog.Builder(this)
                        .setTitle("Aktivovat plné AI?")
                        .setMessage(
                                "Chcete stáhnout Neural Engine (cca 400MB)? Umožní to generování chytrých přehledů offline pomocí skutečného lokálního modelu. Doporučujeme WiFi.")
                        .setPositiveButton("Stáhnout", (dialog, which) -> {
                            prefs.edit().putBoolean("model_check_asked", true).apply();
                            startModelDownload();
                        })
                        .setNegativeButton("Později", (dialog, which) -> {
                            prefs.edit().putBoolean("model_check_asked", true).apply();
                        })
                        .show();
            }
        }
    }

    private void startModelDownload() {
        View dialogView = getLayoutInflater().inflate(R.layout.layout_download_progress, null);
        ProgressBar pb = dialogView.findViewById(R.id.downloadProgressBar);
        TextView tvStatus = dialogView.findViewById(R.id.downloadStatus);
        TextView tvPercent = dialogView.findViewById(R.id.downloadPercent);

        AlertDialog downloadDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (downloadDialog.getWindow() != null) {
            downloadDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        downloadDialog.show();

        new ModelDownloader(this).startDownload(new ModelDownloader.DownloadCallback() {
            @Override
            public void onFinished(File file) {
                runOnUiThread(() -> {
                    downloadDialog.dismiss();
                    Toast.makeText(MainActivity.this, "Neural Engine byl úspěšně stažen!", Toast.LENGTH_SHORT).show();
                    localLLM = new LocalLLMManager(MainActivity.this);
                    startAIStatusPolling();
                });
            }

            @Override
            public void onProgress(int progress, long current, long total) {
                runOnUiThread(() -> {
                    pb.setProgress(progress);
                    tvPercent.setText(progress + "%");
                    String status;
                    if (total > 0) {
                        status = String.format(Locale.getDefault(), "Staženo %.1f MB / %.1f MB",
                                current / (1024.0 * 1024.0), total / (1024.0 * 1024.0));
                    } else if (total < 0) {
                        // Special case: paused with reason
                        int reason = (int) Math.abs(total);
                        status = "Pozastaveno: ";
                        if (reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI)
                            status += "Čekání na WiFi";
                        else if (reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK)
                            status += "Čekání na síť";
                        else
                            status += "Kód " + reason;
                    } else {
                        status = String.format(Locale.getDefault(), "Staženo %.1f MB (zjišťování velikosti...)",
                                current / (1024.0 * 1024.0));
                    }
                    tvStatus.setText(status);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    downloadDialog.dismiss();
                    Toast.makeText(MainActivity.this, "Chyba stahování: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
