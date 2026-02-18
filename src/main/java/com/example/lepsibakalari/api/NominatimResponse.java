package com.example.lepsibakalari.api;

import com.google.gson.annotations.SerializedName;

public class NominatimResponse {
    @SerializedName("address")
    private Address address;

    public Address getAddress() {
        return address;
    }

    public static class Address {
        @SerializedName("city")
        private String city;

        @SerializedName("town")
        private String town;

        @SerializedName("village")
        private String village;

        @SerializedName("suburb")
        private String suburb;

        @SerializedName("municipality")
        private String municipality;

        public String getCity() {
            if (city != null)
                return city;
            if (town != null)
                return town;
            if (village != null)
                return village;
            if (municipality != null)
                return municipality;
            if (suburb != null)
                return suburb;
            return null;
        }
    }
}
