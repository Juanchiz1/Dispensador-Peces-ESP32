package com.juanchiz.fishfeeder.ui.stats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.juanchiz.fishfeeder.R;
import com.juanchiz.fishfeeder.data.AppDatabase;
import com.juanchiz.fishfeeder.data.FeedingEvent;
import com.juanchiz.fishfeeder.data.SensorReading;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Muestra estadísticas construidas a partir del historial local (tabla Room),
 * que se va llenando con cada ciclo de PollingWorker mientras la app está conectada.
 */
public class StatsFragment extends Fragment {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView tvEmptyState;
    private TextView tvFeedingsToday;
    private LineChart chartHumidity;
    private LineChart chartLevel;
    private android.widget.LinearLayout containerFeedingHistory;
    private TextView tvHistoryEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        tvFeedingsToday = view.findViewById(R.id.tvFeedingsToday);
        chartHumidity = view.findViewById(R.id.chartHumidity);
        chartLevel = view.findViewById(R.id.chartLevel);
        containerFeedingHistory = view.findViewById(R.id.containerFeedingHistory);
        tvHistoryEmpty = view.findViewById(R.id.tvHistoryEmpty);

        setupChartStyle(chartHumidity);
        setupChartStyle(chartLevel);

        loadStats();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStats();
    }

    private void setupChartStyle(LineChart chart) {
        Description description = new Description();
        description.setText("");
        chart.setDescription(description);
        chart.getAxisRight().setEnabled(false);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setGranularity(1f);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setNoDataText("");
    }

    private void loadStats() {
        AppDatabase db = AppDatabase.getInstance(requireContext());
        executor.execute(() -> {
            List<SensorReading> recent = db.sensorReadingDao().getRecent(50);
            Collections.reverse(recent); // orden cronológico para la gráfica

            long midnightMillis = startOfTodayMillis();
            int feedingsToday = db.feedingEventDao().countSince(midnightMillis);

            long sieteDiasAtras = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
            List<FeedingEvent> historial = db.feedingEventDao().getSince(sieteDiasAtras);

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                tvFeedingsToday.setText(String.valueOf(feedingsToday));
                if (recent.isEmpty()) {
                    tvEmptyState.setVisibility(View.VISIBLE);
                } else {
                    tvEmptyState.setVisibility(View.GONE);
                    bindHumidityChart(recent);
                    bindLevelChart(recent);
                }
                bindFeedingHistory(historial);
            });
        });
    }

    private void bindFeedingHistory(List<FeedingEvent> eventos) {
        // Quita las filas dibujadas en la carga anterior, dejando "tvHistoryEmpty" (primer hijo).
        while (containerFeedingHistory.getChildCount() > 1) {
            containerFeedingHistory.removeViewAt(containerFeedingHistory.getChildCount() - 1);
        }

        if (eventos.isEmpty()) {
            tvHistoryEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvHistoryEmpty.setVisibility(View.GONE);

        SimpleDateFormat formato = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        for (FeedingEvent evento : eventos) {
            android.widget.LinearLayout fila = new android.widget.LinearLayout(requireContext());
            fila.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            fila.setGravity(android.view.Gravity.CENTER_VERTICAL);
            fila.setPadding(0, dpToPx(8), 0, dpToPx(8));

            TextView tvFecha = new TextView(requireContext());
            tvFecha.setText(formato.format(new Date(evento.timestampMillis)));
            tvFecha.setTextSize(13);
            tvFecha.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary_light));
            android.widget.LinearLayout.LayoutParams paramsFecha = new android.widget.LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            fila.addView(tvFecha, paramsFecha);

            TextView tvOrigen = new TextView(requireContext());
            tvOrigen.setText(getString(evento.manual ? R.string.feeding_manual_label : R.string.feeding_scheduled_label));
            tvOrigen.setTextSize(12);
            tvOrigen.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary_light));
            tvOrigen.setPadding(dpToPx(8), 0, dpToPx(8), 0);
            fila.addView(tvOrigen);

            TextView tvPorciones = new TextView(requireContext());
            tvPorciones.setText(getString(R.string.feeding_portions_format, evento.porciones));
            tvPorciones.setTextSize(13);
            tvPorciones.setTypeface(null, android.graphics.Typeface.BOLD);
            fila.addView(tvPorciones);

            containerFeedingHistory.addView(fila);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void bindHumidityChart(List<SensorReading> readings) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < readings.size(); i++) {
            entries.add(new Entry(i, (float) readings.get(i).humedad));
        }
        LineDataSet dataSet = new LineDataSet(entries, "Humedad");
        styleDataSet(dataSet, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.brand_secondary));
        chartHumidity.setData(new LineData(dataSet));
        chartHumidity.invalidate();
    }

    private void bindLevelChart(List<SensorReading> readings) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < readings.size(); i++) {
            entries.add(new Entry(i, readings.get(i).nivelTolvaPct));
        }
        LineDataSet dataSet = new LineDataSet(entries, "Nivel");
        styleDataSet(dataSet, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.brand_primary));
        chartLevel.setData(new LineData(dataSet));
        chartLevel.invalidate();
    }

    private void styleDataSet(LineDataSet dataSet, int color) {
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(color);
        dataSet.setFillAlpha(30);
    }

    private long startOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
