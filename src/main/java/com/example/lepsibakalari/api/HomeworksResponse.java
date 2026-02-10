package com.example.lepsibakalari.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class HomeworksResponse {

    @SerializedName("Homeworks")
    private List<Homework> homeworks;

    public List<Homework> getHomeworks() { return homeworks; }
    public void setHomeworks(List<Homework> homeworks) { this.homeworks = homeworks; }

    public static class Homework {
        @SerializedName("ID")
        private String id;

        @SerializedName("DateStart")
        private String dateStart;

        @SerializedName("DateEnd")
        private String dateEnd;

        @SerializedName("Content")
        private String content;

        @SerializedName("Done")
        private boolean done;

        @SerializedName("Closed")
        private Boolean closed;

        @SerializedName("Subject")
        private SubjectRef subject;

        @SerializedName("Teacher")
        private TeacherRef teacher;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDateStart() { return dateStart; }
        public void setDateStart(String dateStart) { this.dateStart = dateStart; }
        public String getDateEnd() { return dateEnd; }
        public void setDateEnd(String dateEnd) { this.dateEnd = dateEnd; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public boolean isDone() { return done; }
        public void setDone(boolean done) { this.done = done; }
        public Boolean getClosed() { return closed; }
        public void setClosed(Boolean closed) { this.closed = closed; }
        public SubjectRef getSubject() { return subject; }
        public void setSubject(SubjectRef subject) { this.subject = subject; }
        public TeacherRef getTeacher() { return teacher; }
        public void setTeacher(TeacherRef teacher) { this.teacher = teacher; }
    }

    public static class SubjectRef {
        @SerializedName("Name")
        private String name;

        @SerializedName("Abbrev")
        private String abbrev;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAbbrev() { return abbrev; }
        public void setAbbrev(String abbrev) { this.abbrev = abbrev; }
    }

    public static class TeacherRef {
        @SerializedName("Name")
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
