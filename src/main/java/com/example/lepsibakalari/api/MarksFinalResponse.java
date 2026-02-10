package com.example.lepsibakalari.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class MarksFinalResponse {

    @SerializedName("CertificateTerms")
    private List<CertificateTerm> certificateTerms;

    public List<CertificateTerm> getCertificateTerms() {
        return certificateTerms;
    }

    public void setCertificateTerms(List<CertificateTerm> certificateTerms) {
        this.certificateTerms = certificateTerms;
    }

    public static class CertificateTerm {
        @SerializedName("FinalMarks")
        private List<FinalMark> finalMarks;

        @SerializedName("Subjects")
        private List<SubjectInfo> subjects;

        @SerializedName("SchoolYear")
        private String schoolYear;

        @SerializedName("SemesterName")
        private String semesterName;

        @SerializedName("GradeName")
        private String gradeName;

        @SerializedName("AchievementText")
        private String achievementText;

        @SerializedName("MarksAverage")
        private Double marksAverage;

        @SerializedName("AbsentHours")
        private Integer absentHours;

        @SerializedName("NotExcusedHours")
        private Integer notExcusedHours;

        public List<FinalMark> getFinalMarks() { return finalMarks; }
        public void setFinalMarks(List<FinalMark> finalMarks) { this.finalMarks = finalMarks; }
        public List<SubjectInfo> getSubjects() { return subjects; }
        public void setSubjects(List<SubjectInfo> subjects) { this.subjects = subjects; }
        public String getSchoolYear() { return schoolYear; }
        public void setSchoolYear(String schoolYear) { this.schoolYear = schoolYear; }
        public String getSemesterName() { return semesterName; }
        public void setSemesterName(String semesterName) { this.semesterName = semesterName; }
        public String getGradeName() { return gradeName; }
        public void setGradeName(String gradeName) { this.gradeName = gradeName; }
        public String getAchievementText() { return achievementText; }
        public void setAchievementText(String achievementText) { this.achievementText = achievementText; }
        public Double getMarksAverage() { return marksAverage; }
        public void setMarksAverage(Double marksAverage) { this.marksAverage = marksAverage; }
        public Integer getAbsentHours() { return absentHours; }
        public void setAbsentHours(Integer absentHours) { this.absentHours = absentHours; }
        public Integer getNotExcusedHours() { return notExcusedHours; }
        public void setNotExcusedHours(Integer notExcusedHours) { this.notExcusedHours = notExcusedHours; }
    }

    public static class FinalMark {
        @SerializedName("MarkText")
        private String markText;

        @SerializedName("SubjectId")
        private String subjectId;

        public String getMarkText() { return markText; }
        public void setMarkText(String markText) { this.markText = markText; }
        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    }

    public static class SubjectInfo {
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
