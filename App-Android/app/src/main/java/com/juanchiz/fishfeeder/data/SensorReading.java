package com.juanchiz.fishfeeder.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Una lectura histórica guardada localmente en el celular.
 * El ESP32 solo expone el estado actual (/api/status); el historial para las
 * estadísticas lo construye la app guardando cada lectura que el polling trae.
 */
@Entity(tableName = "sensor_readings")
public class SensorReading {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestampMillis;
    public double humedad;
    public double temperatura;
    public int nivelTolvaPct;
    public boolean tolvaVacia;
    public boolean humedadAlta;

    public SensorReading(long timestampMillis, double humedad, double temperatura,
                          int nivelTolvaPct, boolean tolvaVacia, boolean humedadAlta) {
        this.timestampMillis = timestampMillis;
        this.humedad = humedad;
        this.temperatura = temperatura;
        this.nivelTolvaPct = nivelTolvaPct;
        this.tolvaVacia = tolvaVacia;
        this.humedadAlta = humedadAlta;
    }
}
