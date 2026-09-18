package com.juanchiz.fishfeeder.ui.schedule;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.NumberPicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.juanchiz.fishfeeder.R;
import com.juanchiz.fishfeeder.data.PrefsManager;
import com.juanchiz.fishfeeder.model.ScheduleItem;
import com.juanchiz.fishfeeder.network.FeederApiService;
import com.juanchiz.fishfeeder.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Permite ver, agregar, quitar y guardar los horarios de alimentación en el ESP32. */
public class ScheduleFragment extends Fragment {

    private RecyclerView recyclerSchedules;
    private ScheduleAdapter adapter;
    private final List<ScheduleItem> schedules = new ArrayList<>();
    private PrefsManager prefsManager;
    private MaterialButton btnSave;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefsManager = new PrefsManager(requireContext());

        recyclerSchedules = view.findViewById(R.id.recyclerSchedules);
        recyclerSchedules.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ScheduleAdapter(schedules, position -> {
            schedules.remove(position);
            adapter.notifyItemRemoved(position);
        });
        recyclerSchedules.setAdapter(adapter);

        ImageButton btnAdd = view.findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(v -> showAddScheduleDialog());

        btnSave = view.findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> saveSchedules());

        loadSchedules();
    }

    private void loadSchedules() {
        String baseUrl = prefsManager.getBaseUrl();
        if (baseUrl == null) return;

        FeederApiService api = RetrofitClient.getApi(baseUrl);
        api.getSchedule().enqueue(new Callback<List<ScheduleItem>>() {
            @Override
            public void onResponse(Call<List<ScheduleItem>> call, Response<List<ScheduleItem>> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null) return;
                schedules.clear();
                schedules.addAll(response.body());
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onFailure(Call<List<ScheduleItem>> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.connect_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddScheduleDialog() {
        TimePickerDialog timePicker = new TimePickerDialog(requireContext(),
                (timeView, hourOfDay, minute) -> showPortionsDialog(hourOfDay, minute),
                8, 0, true);
        timePicker.setTitle(getString(R.string.btn_add_schedule));
        timePicker.show();
    }

    private void showPortionsDialog(int hour, int minute) {
        NumberPicker numberPicker = new NumberPicker(requireContext());
        numberPicker.setMinValue(1);
        numberPicker.setMaxValue(10);
        numberPicker.setValue(1);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.portions)
                .setView(numberPicker)
                .setPositiveButton(R.string.accept, (dialog, which) -> {
                    schedules.add(new ScheduleItem(hour, minute, numberPicker.getValue()));
                    sortSchedules();
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void sortSchedules() {
        schedules.sort((a, b) -> {
            if (a.hora != b.hora) return Integer.compare(a.hora, b.hora);
            return Integer.compare(a.minuto, b.minuto);
        });
    }

    private void saveSchedules() {
        String baseUrl = prefsManager.getBaseUrl();
        if (baseUrl == null) return;

        btnSave.setEnabled(false);
        FeederApiService api = RetrofitClient.getApi(baseUrl);
        api.setSchedule(schedules).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!isAdded()) return;
                btnSave.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), R.string.schedule_saved, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), R.string.connect_error, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (!isAdded()) return;
                btnSave.setEnabled(true);
                Toast.makeText(requireContext(), R.string.connect_error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
