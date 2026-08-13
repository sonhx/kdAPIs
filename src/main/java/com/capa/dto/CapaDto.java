package com.capa.dto;

import java.math.BigDecimal;

public class CapaDto {
    private Integer capaId;
    private String capaCode;
    private String title;
    private String description;
    private String capaType;       // CAP (Corrective) | PAP (Preventive)
    private String source;         // Audit, Survey, Complaint, etc.
    private String sourceRef;      // Reference ID to source record
    private String status;         // Open, InProgress, Closed, Overdue
    private String priority;       // Low, Medium, High, Critical
    private String rootCause;
    private String actionPlan;
    private String departmentId;
    private String departmentName;
    private String assignedTo;     // User ID
    private String assignedToName;
    private String createdBy;
    private String dueDate;
    private String completedDate;
    private String verifiedDate;
    private String verifiedBy;
    private String closedDate;
    private String openDate;
    private String feedback;       // Improvement requests/rejection comments from TTKT
    private String effectivenessStatus; // "Đang đánh giá", "Đạt", "Chưa đạt"
    private Integer effectiveness; // 1-5 rating after closure
    private String effectivenessNote;

    public CapaDto() {}

    // --- Getters & Setters ---
    public Integer getCapaId() { return capaId; }
    public void setCapaId(Integer capaId) { this.capaId = capaId; }

    public String getCapaCode() { return capaCode; }
    public void setCapaCode(String capaCode) { this.capaCode = capaCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCapaType() { return capaType; }
    public void setCapaType(String capaType) { this.capaType = capaType; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public String getActionPlan() { return actionPlan; }
    public void setActionPlan(String actionPlan) { this.actionPlan = actionPlan; }

    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public String getAssignedToName() { return assignedToName; }
    public void setAssignedToName(String assignedToName) { this.assignedToName = assignedToName; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public String getCompletedDate() { return completedDate; }
    public void setCompletedDate(String completedDate) { this.completedDate = completedDate; }

    public String getVerifiedDate() { return verifiedDate; }
    public void setVerifiedDate(String verifiedDate) { this.verifiedDate = verifiedDate; }

    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }

    public String getClosedDate() { return closedDate; }
    public void setClosedDate(String closedDate) { this.closedDate = closedDate; }

    private String createdAt;
    private String updatedAt;

    public String getOpenDate() { return openDate; }
    public void setOpenDate(String openDate) { this.openDate = openDate; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public String getEffectivenessStatus() { return effectivenessStatus; }
    public void setEffectivenessStatus(String effectivenessStatus) { this.effectivenessStatus = effectivenessStatus; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public Integer getEffectiveness() { return effectiveness; }
    public void setEffectiveness(Integer effectiveness) { this.effectiveness = effectiveness; }

    public String getEffectivenessNote() { return effectivenessNote; }
    public void setEffectivenessNote(String effectivenessNote) { this.effectivenessNote = effectivenessNote; }
}
