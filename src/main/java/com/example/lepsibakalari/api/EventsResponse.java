package com.example.lepsibakalari.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class EventsResponse {

    @SerializedName("Events")
    private List<Event> events;

    public List<Event> getEvents() { return events; }
    public void setEvents(List<Event> events) { this.events = events; }

    public static class Event {
        @SerializedName("Id")
        private String id;

        @SerializedName("Title")
        private String title;

        @SerializedName("Description")
        private String description;

        @SerializedName("Times")
        private List<EventTime> times;

        @SerializedName("EventType")
        private EventType eventType;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<EventTime> getTimes() { return times; }
        public void setTimes(List<EventTime> times) { this.times = times; }
        public EventType getEventType() { return eventType; }
        public void setEventType(EventType eventType) { this.eventType = eventType; }
    }

    public static class EventTime {
        @SerializedName("StartTime")
        private String startTime;

        @SerializedName("EndTime")
        private String endTime;

        @SerializedName("WholeDay")
        private boolean wholeDay;

        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        public boolean isWholeDay() { return wholeDay; }
        public void setWholeDay(boolean wholeDay) { this.wholeDay = wholeDay; }
    }

    public static class EventType {
        @SerializedName("Name")
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
