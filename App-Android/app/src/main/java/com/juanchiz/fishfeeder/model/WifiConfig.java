package com.juanchiz.fishfeeder.model;

/** Refleja la respuesta de GET /api/wifi-config: red actualmente configurada en el ESP32. */
public class WifiConfig {
    public String ssid;
    public boolean modo_ap;
}
