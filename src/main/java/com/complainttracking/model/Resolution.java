package com.complainttracking.model;

import java.sql.Timestamp;

/**
 * Resolution – domain model mapping the 'resolutions' table.
 *
 * <p>Column mapping:</p>
 * <ul>
 *   <li>resolution_id    → resolutionId</li>
 *   <li>complaint_id     → complaintId</li>
 *   <li>admin_id         → adminId</li>
 *   <li>resolution_notes → resolutionNotes</li>
 *   <li>resolved_at      → resolvedAt</li>
 * </ul>
 */
public class Resolution {

    private int       resolutionId;
    private int       complaintId;
    private int       adminId;
    private String    resolutionNotes;
    private Timestamp resolvedAt;

    // Optional field populated via JOIN for display purposes
    private String    adminName;

    /** Default constructor. */
    public Resolution() {
    }

    /** Constructor for creating a new resolution (pre-persist). */
    public Resolution(int complaintId, int adminId, String resolutionNotes) {
        this.complaintId     = complaintId;
        this.adminId         = adminId;
        this.resolutionNotes = resolutionNotes;
    }

    /** Full constructor for reading from the database. */
    public Resolution(int resolutionId, int complaintId, int adminId,
                      String resolutionNotes, Timestamp resolvedAt) {
        this.resolutionId    = resolutionId;
        this.complaintId     = complaintId;
        this.adminId         = adminId;
        this.resolutionNotes = resolutionNotes;
        this.resolvedAt      = resolvedAt;
    }

    // --- Getters & Setters ---

    public int getResolutionId() {
        return resolutionId;
    }

    public void setResolutionId(int resolutionId) {
        this.resolutionId = resolutionId;
    }

    public int getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(int complaintId) {
        this.complaintId = complaintId;
    }

    public int getAdminId() {
        return adminId;
    }

    public void setAdminId(int adminId) {
        this.adminId = adminId;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public Timestamp getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Timestamp resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getAdminName() {
        return adminName;
    }

    public void setAdminName(String adminName) {
        this.adminName = adminName;
    }

    @Override
    public String toString() {
        return "Resolution{" +
                "resolutionId=" + resolutionId +
                ", complaintId=" + complaintId +
                ", adminId=" + adminId +
                ", resolvedAt=" + resolvedAt +
                '}';
    }
}

