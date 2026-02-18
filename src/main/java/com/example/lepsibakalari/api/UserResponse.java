package com.example.lepsibakalari.api;

import com.google.gson.annotations.SerializedName;

public class UserResponse {
    @SerializedName("UserFullName")
    private String userFullName;

    @SerializedName("FullName")
    private String fullName;

    @SerializedName("FirstName")
    private String firstName;

    @SerializedName("LastName")
    private String lastName;

    public String getFullName() {
        if (userFullName != null && !userFullName.isEmpty())
            return userFullName;
        if (fullName != null && !fullName.isEmpty())
            return fullName;
        if (firstName != null && lastName != null)
            return firstName + " " + lastName;
        return null;
    }

    public String getFirstName() {
        return firstName;
    }
}
