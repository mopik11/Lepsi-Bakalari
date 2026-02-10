package com.example.lepsibakalari.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class AbsenceResponse {

    @SerializedName("Absences")
    private List<AbsenceDay> absences;

    @SerializedName("AbsencesPerSubject")
    private List<AbsencePerSubject> absencesPerSubject;

    @SerializedName("PercentageThreshold")
    private Double percentageThreshold;

    public List<AbsenceDay> getAbsences() { return absences; }
    public void setAbsences(List<AbsenceDay> absences) { this.absences = absences; }
    public List<AbsencePerSubject> getAbsencesPerSubject() { return absencesPerSubject; }
    public void setAbsencesPerSubject(List<AbsencePerSubject> absencesPerSubject) { this.absencesPerSubject = absencesPerSubject; }
    public Double getPercentageThreshold() { return percentageThreshold; }
    public void setPercentageThreshold(Double percentageThreshold) { this.percentageThreshold = percentageThreshold; }

    public static class AbsenceDay {
        @SerializedName("Date")
        private String date;

        @SerializedName("Ok")
        private int ok;

        @SerializedName("Missed")
        private int missed;

        @SerializedName("Late")
        private int late;

        @SerializedName("Unsolved")
        private int unsolved;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public int getOk() { return ok; }
        public void setOk(int ok) { this.ok = ok; }
        public int getMissed() { return missed; }
        public void setMissed(int missed) { this.missed = missed; }
        public int getLate() { return late; }
        public void setLate(int late) { this.late = late; }
        public int getUnsolved() { return unsolved; }
        public void setUnsolved(int unsolved) { this.unsolved = unsolved; }
    }

    public static class AbsencePerSubject {
        @SerializedName("SubjectName")
        private String subjectName;

        @SerializedName("LessonsCount")
        private int lessonsCount;

        @SerializedName("Base")
        private int base;

        @SerializedName("Late")
        private int late;

        public String getSubjectName() { return subjectName; }
        public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
        public int getLessonsCount() { return lessonsCount; }
        public void setLessonsCount(int lessonsCount) { this.lessonsCount = lessonsCount; }
        public int getBase() { return base; }
        public void setBase(int base) { this.base = base; }
        public int getLate() { return late; }
        public void setLate(int late) { this.late = late; }
    }
}
