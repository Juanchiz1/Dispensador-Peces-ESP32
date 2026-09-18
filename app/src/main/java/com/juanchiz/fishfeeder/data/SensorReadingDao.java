package com.juanchiz.fishfeeder.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SensorReadingDao {

    @Insert
    void insert(SensorReading reading);

    @Query("SELECT * FROM sensor_readings ORDER BY timestampMillis DESC LIMIT :limit")
    List<SensorReading> getRecent(int limit);

    @Query("SELECT COUNT(*) FROM sensor_readings WHERE timestampMillis >= :sinceMillis")
    int countSince(long sinceMillis);

    @Query("DELETE FROM sensor_readings WHERE timestampMillis < :beforeMillis")
    void deleteOlderThan(long beforeMillis);
}
