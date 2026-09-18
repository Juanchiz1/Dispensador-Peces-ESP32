package com.juanchiz.fishfeeder.model;

import com.google.gson.annotations.SerializedName;

/** Un horario de alimentación, tal como lo guarda/devuelve /api/schedule en el ESP32. */
public class ScheduleItem {

    @SerializedName("hora")
    public int hora;

    @SerializedName("minuto")
    public int minuto;

    @SerializedName("porciones")
    public int porciones;

    public ScheduleItem() {
    }

    public ScheduleItem(int hora, int minuto, int porciones) {
        this.hora = hora;
        this.minuto = minuto;
        this.porciones = porciones;
    }

    public String horaFormateada() {
        return String.format("%02d:%02d", hora, minuto);
    }
}
