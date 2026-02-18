package com.example.lepsibakalari.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Odpověď z GET /api/3/marks
 */
public class MarksResponse {

    @SerializedName("Subjects")
    private List<SubjectMarks> subjects;

    public List<SubjectMarks> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<SubjectMarks> subjects) {
        this.subjects = subjects;
    }

    public static class SubjectMarks {
        @SerializedName("Subject")
        private SubjectInfo subject;

        @SerializedName("Marks")
        private List<Mark> marks;

        @SerializedName("AverageText")
        private String averageText;

        public SubjectInfo getSubject() {
            return subject;
        }

        public void setSubject(SubjectInfo subject) {
            this.subject = subject;
        }

        public List<Mark> getMarks() {
            return marks;
        }

        public void setMarks(List<Mark> marks) {
            this.marks = marks;
        }

        public String getAverageText() {
            return averageText;
        }

        public void setAverageText(String averageText) {
            this.averageText = averageText;
        }
    }

    public static class SubjectInfo {
        @SerializedName("Id")
        private String id;

        @SerializedName("Abbrev")
        private String abbrev;

        @SerializedName("Name")
        private String name;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getAbbrev() {
            return abbrev;
        }

        public void setAbbrev(String abbrev) {
            this.abbrev = abbrev;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Mark {
        @SerializedName("MarkText")
        private String markText;

        @SerializedName("Caption")
        private String caption;

        @SerializedName("TypeNote")
        private String typeNote;

        @SerializedName("Weight")
        private Integer weight;

        @SerializedName("Date")
        private String date;

        @SerializedName("MarkDate")
        private String markDate;

        @SerializedName("EditDate")
        private String editDate;

        public String getMarkText() {
            return markText;
        }

        public void setMarkText(String markText) {
            this.markText = markText;
        }

        public String getCaption() {
            return caption;
        }

        public void setCaption(String caption) {
            this.caption = caption;
        }

        public String getTypeNote() {
            return typeNote;
        }

        public void setTypeNote(String typeNote) {
            this.typeNote = typeNote;
        }

        public Integer getWeight() {
            return weight;
        }

        public void setWeight(Integer weight) {
            this.weight = weight;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getMarkDate() {
            return markDate;
        }

        public void setMarkDate(String markDate) {
            this.markDate = markDate;
        }

        public String getEditDate() {
            return editDate;
        }

        public void setEditDate(String editDate) {
            this.editDate = editDate;
        }
    }
}
