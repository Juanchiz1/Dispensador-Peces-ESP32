package com.juanchiz.fishfeeder.network;

import com.juanchiz.fishfeeder.model.FeederStatus;
import com.juanchiz.fishfeeder.model.ScheduleItem;
import com.juanchiz.fishfeeder.model.TimeSyncRequest;
import com.juanchiz.fishfeeder.model.WifiConfig;
import com.juanchiz.fishfeeder.model.WifiConfigRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Coincide con los endpoints servidos por setupServidorWeb() en el firmware
 * (fish_feeder_firmware.ino): /api/status, /api/feed, /api/schedule,
 * /api/wifi-config, /api/time.
 */
public interface FeederApiService {

    @GET("api/status")
    Call<FeederStatus> getStatus();

    @POST("api/feed")
    Call<Void> feedNow(@Query("porciones") int porciones);

    @GET("api/schedule")
    Call<List<ScheduleItem>> getSchedule();

    @POST("api/schedule")
    Call<Void> setSchedule(@Body List<ScheduleItem> schedule);

    @GET("api/wifi-config")
    Call<WifiConfig> getWifiConfig();

    @POST("api/wifi-config")
    Call<Void> setWifiConfig(@Body WifiConfigRequest config);

    @POST("api/time")
    Call<Void> syncTime(@Body TimeSyncRequest time);
}
