package com.example.lepsibakalari.api;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Retrofit interface pro Bakaláři API v3.
 * Base URL je dynamická - uživatel zadává URL školy.
 */
public interface BakalariApi {

    @FormUrlEncoded
    @POST("api/login")
    Call<LoginResponse> login(
            @Field("client_id") String clientId,
            @Field("grant_type") String grantType,
            @Field("username") String username,
            @Field("password") String password
    );

    @FormUrlEncoded
    @POST("api/login")
    Call<LoginResponse> refreshToken(
            @Field("client_id") String clientId,
            @Field("grant_type") String grantType,
            @Field("refresh_token") String refreshToken
    );

    @GET("api/3/timetable/actual")
    Call<TimetableResponse> getTimetable(
            @Header("Authorization") String authorization,
            @Query("date") String date
    );

    @GET("api/3/marks")
    Call<MarksResponse> getMarks(@Header("Authorization") String authorization);

    @GET("api/3/marks/final")
    Call<MarksFinalResponse> getMarksFinal(@Header("Authorization") String authorization);

    @FormUrlEncoded
    @POST("api/3/komens/messages/received")
    Call<KomensResponse> getKomensReceived(@Header("Authorization") String authorization, @FieldMap Map<String, String> fields);

    @GET("api/3/absence/student")
    Call<AbsenceResponse> getAbsence(@Header("Authorization") String authorization);

    @GET("api/3/homeworks")
    Call<HomeworksResponse> getHomeworks(@Header("Authorization") String authorization, @Query("from") String from, @Query("to") String to);

    @GET("api/3/events/my")
    Call<EventsResponse> getEventsMy(@Header("Authorization") String authorization, @Query("from") String from);

    @GET("api/3/events")
    Call<EventsResponse> getEvents(@Header("Authorization") String authorization, @Query("from") String from);

    @GET("api/3/substitutions")
    Call<SubstitutionsResponse> getSubstitutions(@Header("Authorization") String authorization, @Query("from") String from);
}
