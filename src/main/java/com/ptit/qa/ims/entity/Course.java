package com.ptit.qa.ims.entity;

import java.time.LocalDateTime;
import java.util.Objects;

public class Course {

    private Long id;
    private Long programId;
    private String courseCode;
    private String courseName;
    private Integer credits;
    private LocalDateTime updatedAt;

    public Course() {
    }

    public Course(Long programId, String courseCode, String courseName, Integer credits) {
        this.programId = programId;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.credits = credits;
        this.updatedAt = LocalDateTime.now();
    }

    public Course(String courseCode, String courseName, Integer credits) {
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.credits = credits;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProgramId() {
        return programId;
    }

    public void setProgramId(Long programId) {
        this.programId = programId;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public Integer getCredits() {
        return credits;
    }

    public void setCredits(Integer credits) {
        this.credits = credits;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Course course = (Course) o;
        return Objects.equals(courseCode, course.courseCode) &&
                Objects.equals(courseName, course.courseName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courseCode, courseName);
    }

    @Override
    public String toString() {
        return "Course{" +
                "id=" + id +
                ", programId=" + programId +
                ", courseCode='" + courseCode + "', courseName='" + courseName + "', credits=" + credits + "}";
    }
}
