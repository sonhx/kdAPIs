package com.kpi;

import java.time.LocalDateTime;

public class KpiAssignment {
    private Integer assignmentId;
    private Integer kpiId;
    private Integer departmentId;
    private String role;
    private LocalDateTime assignedDate;
    private Integer assignedBy;

    public KpiAssignment() {
    }

    public KpiAssignment(Integer kpiId, Integer departmentId, String role, Integer assignedBy) {
        this.kpiId = kpiId;
        this.departmentId = departmentId;
        this.role = role;
        this.assignedBy = assignedBy;
    }

    // Getters and Setters
    public Integer getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Integer assignmentId) {
        this.assignmentId = assignmentId;
    }

    public Integer getKpiId() {
        return kpiId;
    }

    public void setKpiId(Integer kpiId) {
        this.kpiId = kpiId;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getAssignedDate() {
        return assignedDate;
    }

    public void setAssignedDate(LocalDateTime assignedDate) {
        this.assignedDate = assignedDate;
    }

    public Integer getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(Integer assignedBy) {
        this.assignedBy = assignedBy;
    }
}
