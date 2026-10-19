package com.ptit.qa.ims.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TrainingProgram {

    private Long id;
    private String name;
    private String url;
    private String code;
    private LocalDateTime updatedAt;
    private List<Course> courses = new ArrayList<>();

    public TrainingProgram() {
    }

    public TrainingProgram(String name, String url, String code) {
        this.name = name;
        this.url = url;
        this.code = code;
        this.updatedAt = LocalDateTime.now();
    }

    public void addCourse(Course course) {
        courses.add(course);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Course> getCourses() {
        return courses;
    }

    public void setCourses(List<Course> courses) {
        this.courses = courses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainingProgram that = (TrainingProgram) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return "TrainingProgram{" +
                "id=" + id +
                ", name='" + name + "', code='" + code + "', url='" + url + "'}";
    }
}
