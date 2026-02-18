package com.example.lepsibakalari.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApi {
        @GET("v1/forecast")
        Call<WeatherResponse> getCurrentWeather(
                        @Query("latitude") double latitude,
                        @Query("longitude") double longitude,
                        @Query("current") String currentFields,
                        @Query("timezone") String timezone);

        @GET("https://geocoding-api.open-meteo.com/v1/search")
        Call<GeocodingResponse> searchCity(
                        @Query("name") String name,
                        @Query("count") int count,
                        @Query("language") String language,
                        @Query("format") String format);

        @GET("https://nominatim.openstreetmap.org/reverse")
        Call<NominatimResponse> reverseGeocode(
                        @Query("lat") double lat,
                        @Query("lon") double lon,
                        @Query("format") String format,
                        @Query("accept-language") String lang);
}
