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

    @Override
    public String toString() {
        return "Complaint{id=" + complaintId + ", tracking='" + trackingCode +
               "', title='" + title + "', status='" + status + "'}";
    }
}

