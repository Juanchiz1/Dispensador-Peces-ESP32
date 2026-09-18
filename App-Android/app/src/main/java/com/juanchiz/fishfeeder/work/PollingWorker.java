package com.juanchiz.fishfeeder.work;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
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
import com.juanchiz.fishfeeder.data.FeedingEvent;
import com.juanchiz.fishfeeder.data.PrefsManager;
import com.juanchiz.fishfeeder.data.SensorReading;
import com.juanchiz.fishfeeder.model.FeederStatus;
import com.juanchiz.fishfeeder.network.FeederApiService;
import com.juanchiz.fishfeeder.network.RetrofitClient;

import java.util.concurrent.TimeUnit;

import retrofit2.Response;

/**
 * Se ejecuta periódicamente en segundo plano (WorkManager), aunque la app esté cerrada:
 * consulta /api/status, guarda una lectura en Room para las estadísticas, y dispara
 * notificaciones locales si la tolva está vacía o la humedad es alta.
 */
public class PollingWorker extends Worker {

    private static final String WORK_NAME = "fish_feeder_polling";
    private static final String CHANNEL_ID = "feeder_alerts";
    private static final int NOTIF_ID_EMPTY = 1;
    private static final int NOTIF_ID_HUMIDITY = 2;
    private static final String PREF_LAST_FEED_LABEL = "last_feed_label";

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

            detectNewFeedingEvent(context, db, status, now);

            createChannelIfNeeded(context);
            if (status.tolvaVacia && prefs.isNotifyEmptyEnabled()) {
                notify(context, NOTIF_ID_EMPTY, context.getString(R.string.alert_empty));
            } else {
                NotificationManagerCompat.from(context).cancel(NOTIF_ID_EMPTY);
            }

            if (status.humedadAlta && prefs.isNotifyHumidityEnabled()) {
                notify(context, NOTIF_ID_HUMIDITY, context.getString(R.string.alert_humidity));
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

    /**
     * El ESP32 no expone un endpoint de "eventos"; solo un campo de texto con la última
     * alimentación. Si ese texto cambió desde el último polling, asumimos que ocurrió una
     * alimentación (programada o manual) y la registramos para las estadísticas.
     */
    private void detectNewFeedingEvent(Context context, AppDatabase db, FeederStatus status, long now) {
        android.content.SharedPreferences sp = context.getSharedPreferences("fishfeeder_polling", Context.MODE_PRIVATE);
        String lastLabel = sp.getString(PREF_LAST_FEED_LABEL, null);
        if (status.ultimaAlimentacion != null && !status.ultimaAlimentacion.equals(lastLabel)
                && !"Nunca".equals(status.ultimaAlimentacion)) {
            db.feedingEventDao().insert(new FeedingEvent(now, 1, false));
            sp.edit().putString(PREF_LAST_FEED_LABEL, status.ultimaAlimentacion).apply();
        }
    }

    private void createChannelIfNeeded(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notif_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription(context.getString(R.string.notif_channel_desc));
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void notify(Context context, int id, String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && androidx.core.content.ContextCompat.checkSelfPermission(context,
                        android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return; // el usuario no concedió el permiso de notificaciones; no podemos avisar
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_fish)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(id, builder.build());
    }
}
