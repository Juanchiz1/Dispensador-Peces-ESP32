package com.juanchiz.fishfeeder.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.juanchiz.fishfeeder.R;
import com.juanchiz.fishfeeder.data.AppDatabase;
import com.juanchiz.fishfeeder.data.FeedingEventTracker;
import com.juanchiz.fishfeeder.data.PrefsManager;
import com.juanchiz.fishfeeder.data.SensorReading;
import com.juanchiz.fishfeeder.model.FeederStatus;
import com.juanchiz.fishfeeder.network.FeederApiService;
import com.juanchiz.fishfeeder.network.RetrofitClient;

import java.util.concurrent.TimeUnit;

import retrofit2.Response;

/**
 * Se ejecuta periódicamente en segundo plano (WorkManager), aunque la app esté cerrada:
 * consulta /api/status, guarda una lectura en Room para las estadísticas, registra eventos de
 * alimentación nuevos (ver FeedingEventTracker) y dispara notificaciones locales si la tolva
 * está vacía o la humedad es alta.
 */
public class PollingWorker extends Worker {

    private static final String WORK_NAME = "fish_feeder_polling";
    private static final int NOTIF_ID_EMPTY = 1;
    private static final int NOTIF_ID_HUMIDITY = 2;

    public PollingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                        PollingWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        PrefsManager prefs = new PrefsManager(context);
        String baseUrl = prefs.getBaseUrl();
        if (baseUrl == null) {
            return Result.success(); // no hay dispositivo conectado, nada que consultar
        }

        try {
            FeederApiService api = RetrofitClient.getApi(baseUrl);
            Response<FeederStatus> response = api.getStatus().execute();
            if (!response.isSuccessful() || response.body() == null) {
                return Result.retry();
            }

            FeederStatus status = response.body();
            long now = System.currentTimeMillis();

            AppDatabase db = AppDatabase.getInstance(context);
            db.sensorReadingDao().insert(new SensorReading(
                    now, status.humedad, status.temperatura,
                    status.nivelTolvaPct, status.tolvaVacia, status.humedadAlta));

            FeedingEventTracker.detectAndLog(context, status, false, 0);

            NotificationHelper.createChannelIfNeeded(context);
            if (status.tolvaVacia && prefs.isNotifyEmptyEnabled()) {
                NotificationHelper.notify(context, NOTIF_ID_EMPTY, context.getString(R.string.alert_empty));
            } else {
                NotificationManagerCompat.from(context).cancel(NOTIF_ID_EMPTY);
            }

            if (status.humedadAlta && prefs.isNotifyHumidityEnabled()) {
                NotificationHelper.notify(context, NOTIF_ID_HUMIDITY, context.getString(R.string.alert_humidity));
            } else {
                NotificationManagerCompat.from(context).cancel(NOTIF_ID_HUMIDITY);
            }

            // Purga lecturas de más de 30 días para no crecer indefinidamente.
            long thirtyDaysAgo = now - TimeUnit.DAYS.toMillis(30);
            db.sensorReadingDao().deleteOlderThan(thirtyDaysAgo);

            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
