package com.juanchiz.fishfeeder.ui.stats;

import android.graphics.Color;
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
import com.juanchiz.fishfeeder.data.SensorReading;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
            });
        });
    }

    private void bindHumidityChart(List<SensorReading> readings) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < readings.size(); i++) {
            entries.add(new Entry(i, (float) readings.get(i).humedad));
        }
        LineDataSet dataSet = new LineDataSet(entries, "Humedad");
        styleDataSet(dataSet, Color.parseColor("#3B82C4"));
        chartHumidity.setData(new LineData(dataSet));
        chartHumidity.invalidate();
    }

    private void bindLevelChart(List<SensorReading> readings) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < readings.size(); i++) {
            entries.add(new Entry(i, readings.get(i).nivelTolvaPct));
        }
        LineDataSet dataSet = new LineDataSet(entries, "Nivel");
        styleDataSet(dataSet, Color.parseColor("#2F8F6F"));
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
