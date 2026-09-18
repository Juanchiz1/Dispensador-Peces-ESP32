package com.juanchiz.fishfeeder.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.juanchiz.fishfeeder.R;
import com.juanchiz.fishfeeder.data.PrefsManager;
import com.juanchiz.fishfeeder.ui.dashboard.DashboardFragment;
import com.juanchiz.fishfeeder.ui.schedule.ScheduleFragment;
import com.juanchiz.fishfeeder.ui.settings.SettingsFragment;
import com.juanchiz.fishfeeder.ui.stats.StatsFragment;
import com.juanchiz.fishfeeder.work.PollingWorker;

/** Contenedor principal: navegación inferior entre Inicio, Horarios, Estadísticas y Ajustes. */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_NOTIFICATIONS = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestNotificationPermissionIfNeeded();
        PollingWorker.schedule(getApplicationContext());

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int id = item.getItemId();
            if (id == R.id.nav_schedule) {
                fragment = new ScheduleFragment();
            } else if (id == R.id.nav_stats) {
                fragment = new StatsFragment();
            } else if (id == R.id.nav_settings) {
                fragment = new SettingsFragment();
            } else {
                fragment = new DashboardFragment();
            }
            showFragment(fragment);
            return true;
        });

        if (savedInstanceState == null) {
            showFragment(new DashboardFragment());
        }
    }

    private void showFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            }
        }
    }

    /** Llamado desde SettingsFragment tras desconectar, para volver a la pantalla de conexión. */
    public void restartToConnectScreen() {
        PrefsManager prefs = new PrefsManager(this);
        prefs.disconnect();
        android.content.Intent intent = new android.content.Intent(this, com.juanchiz.fishfeeder.ui.connect.ConnectActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
