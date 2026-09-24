package com.juanchiz.fishfeeder.ui.wifi;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.juanchiz.fishfeeder.R;
import com.juanchiz.fishfeeder.data.PrefsManager;
import com.juanchiz.fishfeeder.model.WifiConfig;
import com.juanchiz.fishfeeder.model.WifiConfigRequest;
import com.juanchiz.fishfeeder.network.FeederApiService;
import com.juanchiz.fishfeeder.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Pantalla para configurar (o reconfigurar) a qué red WiFi se conecta el ESP32.
 *
 * Dos formas de llegar aquí:
 *  - EXTRA_MODE = MODE_SETUP: primera vez, el dispensador todavía no tiene WiFi de casa
 *    configurado, así que quedó en su propia red "PezFeeder-Config" (192.168.4.1). El usuario
 *    debe conectar el celular a esa red manualmente desde los ajustes de WiFi de Android antes
 *    de guardar aquí.
 *  - EXTRA_MODE = MODE_RECONFIGURE: el dispensador ya está conectado y en la misma red que el
 *    celular; se usa su IP normal para cambiarle el WiFi.
 *
 * En ambos casos el flujo es el mismo: POST /api/wifi-config a la IP indicada. El firmware
 * guarda las credenciales y se reinicia (ver handlePostWifiConfig en fish_feeder_firmware.ino).
 */
public class WifiConfigActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "modo";
    public static final String MODE_SETUP = "setup";
    public static final String MODE_RECONFIGURE = "reconfigure";

    private static final String IP_MODO_CONFIGURACION = "192.168.4.1";

    private TextInputEditText etTargetIp;
    private TextInputEditText etNewSsid;
    private TextInputEditText etNewPassword;
    private android.widget.TextView tvSubtitle;
    private android.widget.TextView tvMessage;
    private android.widget.TextView tvCurrentSsid;
    private MaterialButton btnLoadCurrent;
    private MaterialButton btnSaveWifi;
    private MaterialButton btnDone;
    private View progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_config);

        etTargetIp = findViewById(R.id.etTargetIp);
        etNewSsid = findViewById(R.id.etNewSsid);
        etNewPassword = findViewById(R.id.etNewPassword);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvMessage = findViewById(R.id.tvMessage);
        tvCurrentSsid = findViewById(R.id.tvCurrentSsid);
        btnLoadCurrent = findViewById(R.id.btnLoadCurrent);
        btnSaveWifi = findViewById(R.id.btnSaveWifi);
        btnDone = findViewById(R.id.btnDone);
        progressBar = findViewById(R.id.progressBar);

        String modo = getIntent().getStringExtra(EXTRA_MODE);
        boolean esPrimeraVez = !MODE_RECONFIGURE.equals(modo);

        if (esPrimeraVez) {
            tvSubtitle.setText(R.string.wifi_config_subtitle_setup);
            etTargetIp.setText(IP_MODO_CONFIGURACION);
        } else {
            tvSubtitle.setText(R.string.wifi_config_subtitle_reconfig);
            PrefsManager prefsManager = new PrefsManager(this);
            String ipActual = prefsManager.getDeviceIp();
            if (ipActual != null) {
                etTargetIp.setText(ipActual);
            }
        }

        btnLoadCurrent.setOnClickListener(v -> cargarRedActual());
        btnSaveWifi.setOnClickListener(v -> guardarWifi());
        btnDone.setOnClickListener(v -> finish());
    }

    private void cargarRedActual() {
        String ip = getIpDestino();
        if (ip == null) return;

        setLoading(true);
        FeederApiService api = RetrofitClient.getApi("http://" + ip + "/");
        api.getWifiConfig().enqueue(new Callback<WifiConfig>() {
            @Override
            public void onResponse(Call<WifiConfig> call, Response<WifiConfig> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    tvCurrentSsid.setVisibility(View.VISIBLE);
                    tvCurrentSsid.setText(getString(R.string.wifi_config_current_ssid, response.body().ssid));
                } else {
                    showMessage(getString(R.string.wifi_config_error), true);
                }
            }

            @Override
            public void onFailure(Call<WifiConfig> call, Throwable t) {
                setLoading(false);
                showMessage(getString(R.string.wifi_config_error), true);
            }
        });
    }

    private void guardarWifi() {
        String ip = getIpDestino();
        if (ip == null) return;

        String ssid = etNewSsid.getText() != null ? etNewSsid.getText().toString().trim() : "";
        String password = etNewPassword.getText() != null ? etNewPassword.getText().toString() : "";

        if (TextUtils.isEmpty(ssid)) {
            showMessage(getString(R.string.hint_new_ssid), true);
            return;
        }

        setLoading(true);
        FeederApiService api = RetrofitClient.getApi("http://" + ip + "/");
        api.setWifiConfig(new WifiConfigRequest(ssid, password)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                setLoading(false);
                if (response.isSuccessful()) {
                    showMessage(getString(R.string.wifi_config_success), false);
                    btnSaveWifi.setVisibility(View.GONE);
                    btnDone.setVisibility(View.VISIBLE);
                } else {
                    showMessage(getString(R.string.wifi_config_error), true);
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                setLoading(false);
                // El ESP32 reinicia justo después de responder, así que a veces la conexión
                // se corta antes de que Retrofit reciba el cuerpo: lo tratamos como éxito
                // probable en vez de un error confuso para el usuario.
                showMessage(getString(R.string.wifi_config_success), false);
                btnSaveWifi.setVisibility(View.GONE);
                btnDone.setVisibility(View.VISIBLE);
            }
        });
    }

    private String getIpDestino() {
        String ip = etTargetIp.getText() != null ? etTargetIp.getText().toString().trim() : "";
        if (TextUtils.isEmpty(ip)) {
            showMessage(getString(R.string.hint_target_ip), true);
            return null;
        }
        return ip;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSaveWifi.setEnabled(!loading);
        btnLoadCurrent.setEnabled(!loading);
        btnSaveWifi.setText(loading ? getString(R.string.wifi_config_saving) : getString(R.string.btn_save_wifi_config));
    }

    private void showMessage(String message, boolean isError) {
        tvMessage.setText(message);
        tvMessage.setTextColor(ContextCompat.getColor(this, isError ? R.color.alert_danger : R.color.alert_ok));
        tvMessage.setVisibility(View.VISIBLE);
    }
}
