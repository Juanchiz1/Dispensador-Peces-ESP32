package com.juanchiz.fishfeeder.ui.dashboard;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.juanchiz.fishfeeder.R;
import com.juanchiz.fishfeeder.data.AppDatabase;
import com.juanchiz.fishfeeder.data.FeedingEventTracker;
import com.juanchiz.fishfeeder.data.PrefsManager;
import com.juanchiz.fishfeeder.data.SensorReading;
import com.juanchiz.fishfeeder.model.FeederStatus;
import com.juanchiz.fishfeeder.network.FeederApiService;
import com.juanchiz.fishfeeder.network.RetrofitClient;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Pantalla de inicio: estado en vivo de los sensores y alimentación manual. */
public class DashboardFragment extends Fragment {

    private static final int POLL_INTERVAL_MS = 5000;

    private SwipeRefreshLayout swipeRefresh;
    private View dotStatus;
    private TextView tvConnectionStatus;
    private TextView tvClock;
    private View alertBanner;
    private View tvVacationBanner;
    private TextView tvHumidity;
    private TextView tvTemperature;
    private TextView tvLevel;
    private ProgressBar progressLevel;
    private TextView tvDaysRemaining;
    private TextView tvLastFeeding;
    private MaterialButtonToggleGroup toggleGroupPortions;
    private MaterialButton btnFeedNow;

    private PrefsManager prefsManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = this::refreshStatus;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int selectedPortions = 1;
    private boolean pendingManualFeedLog = false;
    private int lastManualPortionsFed = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefsManager = new PrefsManager(requireContext());

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        dotStatus = view.findViewById(R.id.dotStatus);
        tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus);
        tvClock = view.findViewById(R.id.tvClock);
        alertBanner = view.findViewById(R.id.alertBanner);
        tvVacationBanner = view.findViewById(R.id.tvVacationBanner);
        tvHumidity = view.findViewById(R.id.tvHumidity);
        tvTemperature = view.findViewById(R.id.tvTemperature);
        tvLevel = view.findViewById(R.id.tvLevel);
        progressLevel = view.findViewById(R.id.progressLevel);
        tvDaysRemaining = view.findViewById(R.id.tvDaysRemaining);
        tvLastFeeding = view.findViewById(R.id.tvLastFeeding);
        toggleGroupPortions = view.findViewById(R.id.toggleGroupPortions);
        btnFeedNow = view.findViewById(R.id.btnFeedNow);

        swipeRefresh.setOnRefreshListener(this::refreshStatus);

        toggleGroupPortions.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnPortion1) selectedPortions = 1;
            else if (checkedId == R.id.btnPortion2) selectedPortions = 2;
            else if (checkedId == R.id.btnPortion3) selectedPortions = 3;
        });

        btnFeedNow.setOnClickListener(v -> feedNow());

        if (prefsManager.getBaseUrl() == null) {
            setConnected(false);
            btnFeedNow.setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.post(pollRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(pollRunnable);
    }

    private void refreshStatus() {
        String baseUrl = prefsManager.getBaseUrl();
        if (baseUrl == null) return;

        FeederApiService api = RetrofitClient.getApi(baseUrl);
        api.getStatus().enqueue(new Callback<FeederStatus>() {
            @Override
            public void onResponse(Call<FeederStatus> call, Response<FeederStatus> response) {
                if (!isAdded()) return;
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    bindStatus(response.body());
                    setConnected(true);
                    btnFeedNow.setEnabled(true);
                } else {
                    setConnected(false);
                    btnFeedNow.setEnabled(false);
                }
                handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
            }

            @Override
            public void onFailure(Call<FeederStatus> call, Throwable t) {
                if (!isAdded()) return;
                swipeRefresh.setRefreshing(false);
                setConnected(false);
                handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
            }
        });
    }

    private void bindStatus(FeederStatus status) {
        tvHumidity.setText(String.format(Locale.getDefault(), "%.0f%%", status.humedad));
        tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", status.temperatura));
        tvLevel.setText(status.nivelTolvaPct + "%");
        progressLevel.setProgress(status.nivelTolvaPct);
        tvLastFeeding.setText(status.ultimaAlimentacion);
        tvClock.setText(status.horaActual);

        tvVacationBanner.setVisibility(status.horariosPausados ? View.VISIBLE : View.GONE);

        FeedingEventTracker.detectAndLog(requireContext(), status, pendingManualFeedLog, lastManualPortionsFed);
        pendingManualFeedLog = false;

        estimateDaysRemaining(status.nivelTolvaPct);

        StringBuilder alertText = new StringBuilder();
        if (status.tolvaVacia) {
            alertText.append(getString(R.string.alert_empty));
        }
        if (status.humedadAlta) {
            if (alertText.length() > 0) alertText.append(" · ");
            alertText.append(getString(R.string.alert_humidity));
        }

        if (alertText.length() > 0) {
            alertBanner.setVisibility(View.VISIBLE);
            // Tolva vacía es más urgente que humedad alta, así que decide el color del banner.
            int bgColorRes = status.tolvaVacia ? R.color.alert_danger_bg : R.color.alert_warning_bg;
            int textColorRes = status.tolvaVacia ? R.color.alert_danger : R.color.alert_warning;
            int bgColor = androidx.core.content.ContextCompat.getColor(requireContext(), bgColorRes);
            int textColor = androidx.core.content.ContextCompat.getColor(requireContext(), textColorRes);

            if (alertBanner.getBackground() != null) {
                alertBanner.getBackground().mutate().setTint(bgColor);
            }

            if (alertBanner instanceof android.widget.LinearLayout) {
                TextView existing = alertBanner.findViewWithTag("alertLabel");
                if (existing == null) {
                    existing = new TextView(requireContext());
                    existing.setTag("alertLabel");
                    existing.setTextSize(13);
                    ((android.widget.LinearLayout) alertBanner).addView(existing);
                }
                existing.setTextColor(textColor);
                existing.setText(alertText.toString());
            }
        } else {
            alertBanner.setVisibility(View.GONE);
        }
    }

    /**
     * Estima cuántos días quedan de alimento comparando el nivel de tolva actual contra una
     * lectura de hace hasta 3 días: si el nivel bajó, proyecta esa misma tasa de consumo hacia
     * adelante. No usa datos del ESP32 directamente porque no lleva ese historial; se apoya en
     * las lecturas que PollingWorker/este mismo fragmento ya guardaron en Room.
     */
    private void estimateDaysRemaining(int nivelActual) {
        AppDatabase db = AppDatabase.getInstance(requireContext());
        executor.execute(() -> {
            long haceTresDias = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3);
            SensorReading masAntigua = db.sensorReadingDao().getOldestSince(haceTresDias);

            if (!isAdded()) return;
            if (masAntigua == null) {
                requireActivity().runOnUiThread(() -> tvDaysRemaining.setVisibility(View.GONE));
                return;
            }

            double horasTranscurridas = (System.currentTimeMillis() - masAntigua.timestampMillis) / 3_600_000.0;
            double nivelPerdido = masAntigua.nivelTolvaPct - nivelActual;

            // Con menos de 6 horas de historial, o si el nivel no bajó (se acaba de rellenar la
            // tolva, o subió por ruido del sensor), no hay una tasa confiable que proyectar.
            if (horasTranscurridas < 6 || nivelPerdido <= 0) {
                requireActivity().runOnUiThread(() -> tvDaysRemaining.setVisibility(View.GONE));
                return;
            }

            double tasaPorHora = nivelPerdido / horasTranscurridas;
            double diasRestantes = (nivelActual / tasaPorHora) / 24.0;

            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                tvDaysRemaining.setVisibility(View.VISIBLE);
                String prefijo = getString(R.string.label_days_remaining) + ": ";
                if (diasRestantes < 1) {
                    tvDaysRemaining.setText(prefijo + getString(R.string.days_remaining_less_than_one));
                } else {
                    tvDaysRemaining.setText(prefijo + getString(R.string.days_remaining_format, (int) Math.round(diasRestantes)));
                }
            });
        });
    }

    private void setConnected(boolean connected) {
        tvConnectionStatus.setText(connected ? R.string.status_connected : R.string.status_disconnected);
        dotStatus.setBackgroundResource(R.drawable.dot_status);
        dotStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                connected ? getResources().getColor(R.color.alert_ok, null)
                        : getResources().getColor(R.color.alert_danger, null)));
    }

    private void feedNow() {
        String baseUrl = prefsManager.getBaseUrl();
        if (baseUrl == null) return;

        btnFeedNow.setEnabled(false);
        btnFeedNow.setText(R.string.connecting);

        FeederApiService api = RetrofitClient.getApi(baseUrl);
        api.feedNow(selectedPortions).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!isAdded()) return;
                btnFeedNow.setEnabled(true);
                btnFeedNow.setText(R.string.btn_feed_now);
                // El próximo refreshStatus() traerá la nueva "ultima_alimentacion" que el ESP32
                // ya actualizó de forma síncrona al responder /api/feed; la marcamos como manual
                // para que FeedingEventTracker registre las porciones correctas.
                pendingManualFeedLog = true;
                lastManualPortionsFed = selectedPortions;
                refreshStatus();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (!isAdded()) return;
                btnFeedNow.setEnabled(true);
                btnFeedNow.setText(R.string.btn_feed_now);
            }
        });
    }
}
