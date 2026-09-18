package com.juanchiz.fishfeeder.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Registro local de una alimentación detectada (cuando cambia "ultima_alimentacion"
 * en el estado del ESP32) o disparada manualmente desde la app.
 */
@Entity(tableName = "feeding_events")
public class FeedingEvent {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestampMillis;
    public int porciones;
    public boolean manual;

    public FeedingEvent(long timestampMillis, int porciones, boolean manual) {
        this.timestampMillis = timestampMillis;
        this.porciones = porciones;
        this.manual = manual;
    }
}
