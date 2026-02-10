package com.example.lepsibakalari;

import android.content.Intent;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.lepsibakalari.api.LoginResponse;
import com.example.lepsibakalari.databinding.ActivityLoginBinding;
import com.example.lepsibakalari.repository.BakalariRepository;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * LoginActivity - přihlašovací obrazovka s glassmorphic kartou.
 * iOS 26 Liquid Glass design s RenderEffect blur pro API 31+.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private BakalariRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = new BakalariRepository(this);
        if (repository.isLoggedIn()) {
            startMainActivity();
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Aplikace blur efektu na pozadí pro Liquid Glass vzhled (API 31+)
        applyBlurEffect(binding.meshGradient);
        
        // Defaultní URL školy
        binding.editBaseUrl.setText("https://bakalari.gymbk.cz/bakaweb");

        applyGlassTouchEffect(binding.buttonLogin);
        binding.buttonLogin.setOnClickListener(v -> performLogin());
    }

    private void applyGlassTouchEffect(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.8f).setDuration(120).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(120).start();
                    break;
            }
            return false;
        });
    }

    /**
     * Aplikuje RenderEffect blur na view pro iOS 26 Liquid Glass vzhled (API 31+).
     * Na pozadí vytvoří měkký „frosted“ efekt.
     */
    private void applyBlurEffect(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP));
        }
    }

    private void performLogin() {
        String baseUrl = binding.editBaseUrl.getText() != null ? binding.editBaseUrl.getText().toString().trim() : "";
        String username = binding.editUsername.getText() != null ? binding.editUsername.getText().toString().trim() : "";
        String password = binding.editPassword.getText() != null ? binding.editPassword.getText().toString() : "";

        if (baseUrl.isEmpty()) {
            showError("Zadejte URL školy");
            return;
        }
        if (username.isEmpty()) {
            showError("Zadejte uživatelské jméno");
            return;
        }
        if (password.isEmpty()) {
            showError("Zadejte heslo");
            return;
        }

        binding.textError.setVisibility(View.GONE);
        binding.buttonLogin.setEnabled(false);

        repository.login(baseUrl, username, password, new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                binding.buttonLogin.setEnabled(true);

                if (!response.isSuccessful()) {
                    showError("Chyba připojení: " + response.code());
                    return;
                }

                LoginResponse body = response.body();
                if (body == null) {
                    showError("Prázdná odpověď serveru");
                    return;
                }

                if (body.hasError()) {
                    String desc = body.getErrorDescription() != null ? body.getErrorDescription() : body.getError();
                    showError(desc != null ? desc : "Chyba přihlášení");
                    return;
                }

                if (body.isSuccess()) {
                    Toast.makeText(LoginActivity.this, "Přihlášení úspěšné", Toast.LENGTH_SHORT).show();
                    startMainActivity();
                    finish();
                } else {
                    showError("Přihlášení selhalo");
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                binding.buttonLogin.setEnabled(true);
                showError("Chyba: " + (t.getMessage() != null ? t.getMessage() : "Neznámá chyba"));
            }
        });
    }

    private void showError(String message) {
        binding.textError.setText(message);
        binding.textError.setVisibility(View.VISIBLE);
    }

    private void startMainActivity() {
        startActivity(new Intent(this, MainActivity.class));
    }
}
