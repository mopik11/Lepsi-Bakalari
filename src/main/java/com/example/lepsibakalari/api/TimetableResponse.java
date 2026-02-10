package com.example.lepsibakalari.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Odpověď z GET /api/3/timetable/actual
 */
public class TimetableResponse {

    @SerializedName("Hours")
    private List<Hour> hours;

    @SerializedName("Days")
    private List<Day> days;

    @SerializedName("Subjects")
    private List<Subject> subjects;

    @SerializedName("Teachers")
    private List<Teacher> teachers;

    @SerializedName("Rooms")
    private List<Room> rooms;

    public List<Hour> getHours() {
        return hours;
    }

    public void setHours(List<Hour> hours) {
        this.hours = hours;
    }

    public List<Day> getDays() {
        return days;
    }

    public void setDays(List<Day> days) {
        this.days = days;
    }

    public List<Subject> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<Subject> subjects) {
        this.subjects = subjects;
    }

    public List<Teacher> getTeachers() {
        return teachers;
    }

    public void setTeachers(List<Teacher> teachers) {
        this.teachers = teachers;
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public void setRooms(List<Room> rooms) {
        this.rooms = rooms;
    }

    public static class Hour {
        @SerializedName("Id")
        private int id;

        @SerializedName("Caption")
        private String caption;

        @SerializedName("BeginTime")
        private String beginTime;

        @SerializedName("EndTime")
        private String endTime;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getCaption() { return caption; }
        public void setCaption(String caption) { this.caption = caption; }
        public String getBeginTime() { return beginTime; }
        public void setBeginTime(String beginTime) { this.beginTime = beginTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
    }

    public static class Atom {
        @SerializedName("HourId")
        private int hourId;

        @SerializedName("SubjectId")
        private String subjectId;

        @SerializedName("TeacherId")
        private String teacherId;

        @SerializedName("RoomId")
        private String roomId;

        @SerializedName("Theme")
        private String theme;

        @SerializedName("Change")
        private Change change;

        public int getHourId() { return hourId; }
        public void setHourId(int hourId) { this.hourId = hourId; }
        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
        public String getTeacherId() { return teacherId; }
        public void setTeacherId(String teacherId) { this.teacherId = teacherId; }
        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }
        public String getTheme() { return theme; }
        public void setTheme(String theme) { this.theme = theme; }
        public Change getChange() { return change; }
        public void setChange(Change change) { this.change = change; }
    }

    public static class Change {
        @SerializedName("ChangeType")
        private String changeType;

        @SerializedName("Description")
        private String description;

        public String getChangeType() { return changeType; }
        public void setChangeType(String changeType) { this.changeType = changeType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class Day {
        @SerializedName("Atoms")
        private List<Atom> atoms;

        @SerializedName("DayOfWeek")
        private int dayOfWeek;

        @SerializedName("Date")
        private String date;

        @SerializedName("DayType")
        private String dayType;

        public List<Atom> getAtoms() { return atoms; }
        public void setAtoms(List<Atom> atoms) { this.atoms = atoms; }
        public int getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getDayType() { return dayType; }
        public void setDayType(String dayType) { this.dayType = dayType; }
    }

    public static class Subject {
        @SerializedName("Id")
        private String id;

        @SerializedName("Abbrev")
        private String abbrev;

        @SerializedName("Name")
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAbbrev() { return abbrev; }
        public void setAbbrev(String abbrev) { this.abbrev = abbrev; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class Teacher {
        @SerializedName("Id")
        private String id;

        @SerializedName("Abbrev")
        private String abbrev;

        @SerializedName("Name")
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAbbrev() { return abbrev; }
        public void setAbbrev(String abbrev) { this.abbrev = abbrev; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class Room {
        @SerializedName("Id")
        private String id;

        @SerializedName("Abbrev")
        private String abbrev;

        @SerializedName("Name")
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAbbrev() { return abbrev; }
        public void setAbbrev(String abbrev) { this.abbrev = abbrev; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
