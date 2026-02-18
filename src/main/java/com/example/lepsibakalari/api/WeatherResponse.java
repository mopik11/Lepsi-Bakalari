package com.example.lepsibakalari.api;

import com.google.gson.annotations.SerializedName;

public class WeatherResponse {
    @SerializedName("current")
    private CurrentWeather current;

    public CurrentWeather getCurrent() {
        return current;
    }

    public static class CurrentWeather {
        @SerializedName("temperature_2m")
        private double temperature;

        @SerializedName("relative_humidity_2m")
        private int humidity;

        @SerializedName("apparent_temperature")
        private double apparentTemperature;

        @SerializedName("weather_code")
        private int weatherCode;

        @SerializedName("wind_speed_10m")
        private double windSpeed;

        public double getTemperature() {
            return temperature;
        }

        public int getHumidity() {
            return humidity;
        }

        public double getApparentTemperature() {
            return apparentTemperature;
        }

        public int getWeatherCode() {
            return weatherCode;
        }

        public double getWindSpeed() {
            return windSpeed;
        }
    }
}
