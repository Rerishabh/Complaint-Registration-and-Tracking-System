package com.complainttracking.model;

import java.sql.Timestamp;

/**
 * ComplaintHistory – domain model mapping the 'complaint_history' table.
 *
 * <p>Column mapping (SQL → Java):</p>
 * <ul>
 *   <li>history_id    → historyId</li>
 *   <li>complaint_id  → complaintId</li>
 *   <li>old_status    → oldStatus</li>
 *   <li>new_status    → newStatus</li>
 *   <li>changed_by    → changedBy</li>
 *   <li>note          → note</li>
 *   <li>changed_at    → changedAt</li>
 * </ul>
 */
public class ComplaintHistory {

    private int       historyId;
    private int       complaintId;
    private String    oldStatus;   // nullable (null for initial submission)
    private String    newStatus;
    private String    changedBy;
    private String    note;        // nullable
    private Timestamp changedAt;

    /** Default constructor. */
    public ComplaintHistory() {
    }

    /** Constructor for creating a new history record (pre-persist). */
    public ComplaintHistory(int complaintId, String oldStatus,
                            String newStatus, String changedBy, String note) {
        this.complaintId = complaintId;
        this.oldStatus   = oldStatus;
        this.newStatus   = newStatus;
        this.changedBy   = changedBy;
        this.note        = note;
    }

    /** Full constructor for reading from the database. */
    public ComplaintHistory(int historyId, int complaintId, String oldStatus,
                            String newStatus, String changedBy,
                            String note, Timestamp changedAt) {
        this.historyId   = historyId;
        this.complaintId = complaintId;
        this.oldStatus   = oldStatus;
        this.newStatus   = newStatus;
        this.changedBy   = changedBy;
        this.note        = note;
        this.changedAt   = changedAt;
    }

    // --- Getters & Setters ---

    public int getHistoryId() { return historyId; }
    public void setHistoryId(int historyId) { this.historyId = historyId; }

    public int getComplaintId() { return complaintId; }
    public void setComplaintId(int complaintId) { this.complaintId = complaintId; }

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Timestamp getChangedAt() { return changedAt; }
    public void setChangedAt(Timestamp changedAt) { this.changedAt = changedAt; }

    @Override
    public String toString() {
        return "ComplaintHistory{id=" + historyId + ", complaintId=" + complaintId +
               ", " + oldStatus + " → " + newStatus + ", by=" + changedBy + "}";
    }
}

