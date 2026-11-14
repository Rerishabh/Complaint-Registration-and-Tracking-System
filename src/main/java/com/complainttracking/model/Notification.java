package com.complainttracking.model;

import java.sql.Timestamp;

/**
 * Represents a record in the notifications table.
 */
public class Notification {

    private int notificationId;
    private int userId;
    private int complaintId;
    private String title;
    private String message;
    private boolean isRead;
    private Timestamp createdAt;

    // Optional complaint tracking code populated via SQL JOIN for UI convenience
    private String complaintTrackingCode;

    /** Default constructor. */
    public Notification() {
    }

    /** Constructor with mandatory business fields. */
    public Notification(int userId, int complaintId, String title, String message) {
        this.userId      = userId;
        this.complaintId = complaintId;
        this.title       = title;
        this.message     = message;
        this.isRead      = false;
    }

    /** Full constructor mapping all table columns. */
    public Notification(int notificationId, int userId, int complaintId,
                        String title, String message, boolean isRead, Timestamp createdAt) {
        this.notificationId = notificationId;
        this.userId         = userId;
        this.complaintId    = complaintId;
        this.title          = title;
        this.message        = message;
        this.isRead         = isRead;
        this.createdAt      = createdAt;
    }

    /** Full constructor including joined tracking code. */
    public Notification(int notificationId, int userId, int complaintId,
                        String title, String message, boolean isRead, Timestamp createdAt,
                        String complaintTrackingCode) {
        this(notificationId, userId, complaintId, title, message, isRead, createdAt);
        this.complaintTrackingCode = complaintTrackingCode;
    }

    // --- Getters & Setters ---

    public int getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(int notificationId) {
        this.notificationId = notificationId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(int complaintId) {
        this.complaintId = complaintId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getComplaintTrackingCode() {
        return complaintTrackingCode;
    }

    public void setComplaintTrackingCode(String complaintTrackingCode) {
        this.complaintTrackingCode = complaintTrackingCode;
    }

    @Override
    public String toString() {
        return "Notification{" +
                "notificationId=" + notificationId +
                ", userId=" + userId +
                ", complaintId=" + complaintId +
                ", trackingCode='" + complaintTrackingCode + '\'' +
                ", title='" + title + '\'' +
                ", isRead=" + isRead +
                ", createdAt=" + createdAt +
                '}';
    }
}
