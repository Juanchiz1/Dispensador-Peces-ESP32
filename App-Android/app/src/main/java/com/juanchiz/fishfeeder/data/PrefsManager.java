package com.juanchiz.fishfeeder.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Guarda la IP del dispensador y las preferencias de notificación. Sin backend, todo es local. */
public class PrefsManager {

    private static final String PREFS_NAME = "fishfeeder_prefs";
    private static final String KEY_DEVICE_IP = "device_ip";
    private static final String KEY_NOTIFY_EMPTY = "notify_empty";
    private static final String KEY_NOTIFY_HUMIDITY = "notify_humidity";

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getDeviceIp() {
        return prefs.getString(KEY_DEVICE_IP, null);
    }

    public void setDeviceIp(String ip) {
        prefs.edit().putString(KEY_DEVICE_IP, ip).apply();
    }

    public boolean isConnected() {
        return getDeviceIp() != null;
    }

    public void disconnect() {
        prefs.edit().remove(KEY_DEVICE_IP).apply();
    }

    public boolean isNotifyEmptyEnabled() {
        return prefs.getBoolean(KEY_NOTIFY_EMPTY, true);
    }

    public void setNotifyEmptyEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFY_EMPTY, enabled).apply();
    }

    public boolean isNotifyHumidityEnabled() {
        return prefs.getBoolean(KEY_NOTIFY_HUMIDITY, true);
    }

    public void setNotifyHumidityEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFY_HUMIDITY, enabled).apply();
    }

    public String getBaseUrl() {
        String ip = getDeviceIp();
        return ip == null ? null : "http://" + ip + "/";
    }
}
