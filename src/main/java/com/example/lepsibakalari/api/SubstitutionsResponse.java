package com.example.lepsibakalari.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SubstitutionsResponse {

    @SerializedName("From")
    private String from;

    @SerializedName("To")
    private String to;

    @SerializedName("Changes")
    private List<Change> changes;

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    public List<Change> getChanges() { return changes; }
    public void setChanges(List<Change> changes) { this.changes = changes; }

    public static class Change {
        @SerializedName("Day")
        private String day;

        @SerializedName("Hours")
        private String hours;

        @SerializedName("ChangeType")
        private String changeType;

        @SerializedName("Description")
        private String description;

        @SerializedName("Time")
        private String time;

        public String getDay() { return day; }
        public void setDay(String day) { this.day = day; }
        public String getHours() { return hours; }
        public void setHours(String hours) { this.hours = hours; }
        public String getChangeType() { return changeType; }
        public void setChangeType(String changeType) { this.changeType = changeType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
    }
}
