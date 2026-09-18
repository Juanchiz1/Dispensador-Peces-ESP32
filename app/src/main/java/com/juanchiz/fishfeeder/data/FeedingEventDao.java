package com.juanchiz.fishfeeder.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FeedingEventDao {

    @Insert
    void insert(FeedingEvent event);

    @Query("SELECT * FROM feeding_events WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis DESC")
    List<FeedingEvent> getSince(long sinceMillis);

    @Query("SELECT COUNT(*) FROM feeding_events WHERE timestampMillis >= :sinceMillis")
    int countSince(long sinceMillis);
}
