package com.juanchiz.fishfeeder.model;

import com.google.gson.annotations.SerializedName;

/**
 * Refleja el JSON que devuelve GET /api/status en el firmware del ESP32.
 * Los nombres de campo deben coincidir exactamente con handleStatus() en el .ino.
 */
public class FeederStatus {

    @SerializedName("humedad")
    public double humedad;

    @SerializedName("temperatura")
    public double temperatura;

    @SerializedName("distancia_mm")
    public int distanciaMm;

    @SerializedName("nivel_tolva_pct")
    public int nivelTolvaPct;

    @SerializedName("tolva_vacia")
    public boolean tolvaVacia;

    @SerializedName("humedad_alta")
    public boolean humedadAlta;

    @SerializedName("ultima_alimentacion")
    public String ultimaAlimentacion;

    @SerializedName("hora_actual")
    public String horaActual;

    @SerializedName("wifi_rssi")
    public int wifiRssi;
}
