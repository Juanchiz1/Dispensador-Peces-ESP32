package com.juanchiz.fishfeeder.model;

/** Cuerpo de POST /api/time: hora local del celular para ajustar el DS3231 del ESP32. */
public class TimeSyncRequest {
    public int anio;
    public int mes;
    public int dia;
    public int hora;
    public int minuto;
    public int segundo;

    public TimeSyncRequest(int anio, int mes, int dia, int hora, int minuto, int segundo) {
        this.anio = anio;
        this.mes = mes;
        this.dia = dia;
        this.hora = hora;
        this.minuto = minuto;
        this.segundo = segundo;
    }
}
