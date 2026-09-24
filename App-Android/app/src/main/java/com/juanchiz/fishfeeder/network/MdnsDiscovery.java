package com.juanchiz.fishfeeder.network;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Busca el dispensador en la red local vía mDNS/DNS-SD (protocolo Bonjour), sin que el
 * usuario tenga que conocer ni escribir la IP. El firmware anuncia el servicio con
 * MDNS.addService("http", "tcp", 80) en fish_feeder_firmware.ino, así que aquí buscamos
 * servicios de tipo "_http._tcp." y devolvemos la IP resuelta de cada uno encontrado.
 */
public class MdnsDiscovery {

    private static final String SERVICE_TYPE = "_http._tcp.";
    private static final long DISCOVERY_TIMEOUT_MS = 4000;

    public interface Listener {
        void onDeviceFound(String name, String host, int port);
        void onFinished();
    }

    private final NsdManager nsdManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private NsdManager.DiscoveryListener discoveryListener;

    public MdnsDiscovery(Context context) {
        nsdManager = (NsdManager) context.getApplicationContext().getSystemService(Context.NSD_SERVICE);
    }

    public void start(Listener listener) {
        stopped.set(false);

        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
                // no-op
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        // Puede fallar si dos servicios resuelven a la vez; se ignora ese hallazgo.
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo serviceInfo) {
                        if (stopped.get() || serviceInfo.getHost() == null) return;
                        String host = serviceInfo.getHost().getHostAddress();
                        int port = serviceInfo.getPort();
                        handler.post(() -> listener.onDeviceFound(serviceInfo.getServiceName(), host, port));
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                // no-op
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                // no-op
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                stop();
                handler.post(listener::onFinished);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                // no-op
            }
        };

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);

        handler.postDelayed(() -> {
            stop();
            listener.onFinished();
        }, DISCOVERY_TIMEOUT_MS);
    }

    public void stop() {
        if (stopped.getAndSet(true)) return;
        try {
            if (discoveryListener != null) {
                nsdManager.stopServiceDiscovery(discoveryListener);
            }
        } catch (IllegalArgumentException ignored) {
            // ya estaba detenida, puede pasar si el timeout y una parada manual coinciden
        }
    }
}
