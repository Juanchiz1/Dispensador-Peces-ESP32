package com.juanchiz.fishfeeder.ui.connect;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.juanchiz.fishfeeder.R;
import com.juanchiz.fishfeeder.data.PrefsManager;
import com.juanchiz.fishfeeder.model.FeederStatus;
import com.juanchiz.fishfeeder.network.FeederApiService;
import com.juanchiz.fishfeeder.network.RetrofitClient;
import com.juanchiz.fishfeeder.ui.MainActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Primera pantalla: pide la IP local del ESP32 y verifica la conexión llamando
 * a GET /api/status antes de pasar al panel principal.
 */
public class ConnectActivity extends AppCompatActivity {

    private TextInputEditText etIp;
    private View tvError;
    private View progressBar;
    private com.google.android.material.button.MaterialButton btnConnect;
    private com.google.android.material.button.MaterialButton btnSkip;
    private PrefsManager prefsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        prefsManager = new PrefsManager(this);

        etIp = findViewById(R.id.etIp);
        tvError = findViewById(R.id.tvError);
        progressBar = findViewById(R.id.progressBar);
        btnConnect = findViewById(R.id.btnConnect);
        btnSkip = findViewById(R.id.btnSkip);

        String savedIp = prefsManager.getDeviceIp();
        if (savedIp != null) {
            etIp.setText(savedIp);
        }

        btnConnect.setOnClickListener(v -> attemptConnect());
        btnSkip.setOnClickListener(v -> goToMain());
    }

    private void attemptConnect() {
        String ip = etIp.getText() != null ? etIp.getText().toString().trim() : "";
        if (TextUtils.isEmpty(ip)) {
            showError(getString(R.string.hint_ip));
            return;
        }

        setLoading(true);
        String baseUrl = "http://" + ip + "/";
        FeederApiService api = RetrofitClient.getApi(baseUrl);

        api.getStatus().enqueue(new Callback<FeederStatus>() {
            @Override
            public void onResponse(Call<FeederStatus> call, Response<FeederStatus> response) {
                setLoading(false);
                if (response.isSuccessful()) {
                    prefsManager.setDeviceIp(ip);
                    goToMain();
                } else {
                    showError(getString(R.string.connect_error));
                }
            }

            @Override
            public void onFailure(Call<FeederStatus> call, Throwable t) {
                setLoading(false);
                showError(getString(R.string.connect_error));
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnConnect.setEnabled(!loading);
        btnConnect.setText(loading ? getString(R.string.connecting) : getString(R.string.btn_connect));
        if (loading) {
            tvError.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        if (tvError instanceof android.widget.TextView) {
            ((android.widget.TextView) tvError).setText(message);
        }
        tvError.setVisibility(View.VISIBLE);
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Si ya hay una IP guardada de una sesión anterior, entra directo al panel.
        if (prefsManager.isConnected() && etIp.getText() != null
                && etIp.getText().toString().equals(prefsManager.getDeviceIp())) {
            // No autologin automático: dejamos que el usuario confirme presionando "Conectar",
            // por si el dispensador cambió de IP (DHCP) desde la última vez.
        }
    }
}
