package com.juanchiz.fishfeeder.model;

/** Cuerpo de POST /api/wifi-config: credenciales que el ESP32 guardará e intentará usar. */
public class WifiConfigRequest {
    public String ssid;
    public String password;

    public WifiConfigRequest(String ssid, String password) {
        this.ssid = ssid;
        this.password = password;
    }
}
