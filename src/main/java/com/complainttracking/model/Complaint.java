package com.complainttracking.model;

import java.sql.Timestamp;

/**
 * Complaint – domain model mapping the 'complaints' table.
 *
 * <p>Column mapping (SQL → Java):</p>
 * <ul>
 *   <li>complaint_id  → complaintId</li>
 *   <li>tracking_code → trackingCode</li>
 *   <li>user_id       → userId</li>
 *   <li>title         → title</li>
 *   <li>description   → description</li>
 *   <li>category      → category</li>
 *   <li>priority      → priority (ENUM: LOW, MEDIUM, HIGH, CRITICAL)</li>
 *   <li>status        → status (ENUM: OPEN, IN_PROGRESS, RESOLVED, CLOSED, REJECTED)</li>
 *   <li>created_at    → createdAt</li>
 *   <li>updated_at    → updatedAt</li>
 * </ul>
 */
public class Complaint {

    // --- Status constants (match the MySQL ENUM exactly) ---
    public static final String STATUS_OPEN        = "OPEN";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_RESOLVED    = "RESOLVED";
    public static final String STATUS_CLOSED      = "CLOSED";
    public static final String STATUS_REJECTED    = "REJECTED";

    // --- Priority constants (match the MySQL ENUM exactly) ---
    public static final String PRIORITY_LOW      = "LOW";
    public static final String PRIORITY_MEDIUM   = "MEDIUM";
    public static final String PRIORITY_HIGH     = "HIGH";
    public static final String PRIORITY_CRITICAL = "CRITICAL";

    private int       complaintId;
    private String    trackingCode;
    private int       userId;
    private String    title;
    private String    description;
    private String    category;
    private String    priority;
    private String    status;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Optional complainant fields populated via SQL JOIN with users table
    private String complainantName;
    private String complainantEmail;
    private String complainantDepartment;

    // Optional assignment and resolution fields
    private Integer   assignedAdminId;
    private String    assignedAdminName;
    private Timestamp assignedAt;
    private String    resolutionNotes;

    /** Default constructor. */
    public Complaint() {
    }

    /** Constructor for creating a new complaint (pre-persist). */
    public Complaint(int userId, String trackingCode, String title,
                     String description, String category, String priority) {
        this.userId       = userId;
        this.trackingCode = trackingCode;
        this.title        = title;
        this.description  = description;
        this.category     = category;
        this.priority     = priority;
        this.status       = STATUS_OPEN;
    }

    /** Full constructor for reading from the database. */
    public Complaint(int complaintId, String trackingCode, int userId,
                     String title, String description, String category,
                     String priority, String status,
                     Timestamp createdAt, Timestamp updatedAt) {
        this.complaintId  = complaintId;
        this.trackingCode = trackingCode;
        this.userId       = userId;
        this.title        = title;
        this.description  = description;
        this.category     = category;
        this.priority     = priority;
        this.status       = status;
        this.createdAt    = createdAt;
        this.updatedAt    = updatedAt;
    }

    /** Full constructor with complainant information. */
    public Complaint(int complaintId, String trackingCode, int userId,
                     String title, String description, String category,
                     String priority, String status,
                     Timestamp createdAt, Timestamp updatedAt,
                     String complainantName, String complainantEmail,
                     String complainantDepartment) {
        this(complaintId, trackingCode, userId, title, description, category,
             priority, status, createdAt, updatedAt);
        this.complainantName       = complainantName;
        this.complainantEmail      = complainantEmail;
        this.complainantDepartment = complainantDepartment;
    }

    // --- Getters & Setters ---

    public int getComplaintId() { return complaintId; }
    public void setComplaintId(int complaintId) { this.complaintId = complaintId; }

    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public String getComplainantName() { return complainantName; }
    public void setComplainantName(String complainantName) { this.complainantName = complainantName; }

    public String getComplainantEmail() { return complainantEmail; }
    public void setComplainantEmail(String complainantEmail) { this.complainantEmail = complainantEmail; }

    public String getComplainantDepartment() { return complainantDepartment; }
    public void setComplainantDepartment(String complainantDepartment) { this.complainantDepartment = complainantDepartment; }

    public Integer getAssignedAdminId() { return assignedAdminId; }
    public void setAssignedAdminId(Integer assignedAdminId) { this.assignedAdminId = assignedAdminId; }

    public String getAssignedAdminName() { return assignedAdminName; }
    public void setAssignedAdminName(String assignedAdminName) { this.assignedAdminName = assignedAdminName; }

    public Timestamp getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Timestamp assignedAt) { this.assignedAt = assignedAt; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    @Override
    public String toString() {
        return "Complaint{id=" + complaintId + ", tracking='" + trackingCode +
               "', title='" + title + "', status='" + status + "'}";
    }
}

