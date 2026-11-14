package com.complainttracking.service;

import com.complainttracking.dao.NotificationDAO;
import com.complainttracking.model.Notification;
import com.complainttracking.model.User;
import com.complainttracking.util.SessionManager;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NotificationService – Business logic and validation layer for user notifications.
 * Enforces session ownership and input constraints.
 */
public class NotificationService {

    private static final Logger LOGGER = Logger.getLogger(NotificationService.class.getName());

    private final NotificationDAO notificationDAO;

    /** Default constructor. */
    public NotificationService() {
        this(new NotificationDAO());
    }

    /** Constructor allowing dependency injection (for unit tests). */
    public NotificationService(NotificationDAO notificationDAO) {
        this.notificationDAO = notificationDAO;
    }

    /**
     * Verifies that a regular user is logged in.
     *
     * @return current active User
     * @throws IllegalStateException if unauthenticated
     */
    private User requireUserSession() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("Unauthorized: Active user session required.");
        }
        return user;
    }

    /**
     * Returns all notifications for the currently logged-in user in reverse chronological order.
     *
     * @return list of notifications
     */
    public List<Notification> getMyNotifications() {
        User user = requireUserSession();
        return notificationDAO.findByUserId(user.getUserId());
    }

    /**
     * Returns the count of unread notifications for the currently logged-in user.
     *
     * @return unread notification count
     */
    public int getMyUnreadCount() {
        User user = requireUserSession();
        return notificationDAO.getUnreadCount(user.getUserId());
    }

    /**
     * Marks a specific notification as read after validating ownership by the current user.
     *
     * @param notificationId target notification ID
     * @return true if updated, false otherwise
     */
    public boolean markAsRead(int notificationId) {
        User user = requireUserSession();
        if (notificationId <= 0) {
            throw new IllegalArgumentException("Invalid notification ID: " + notificationId);
        }
        return notificationDAO.markAsRead(notificationId, user.getUserId());
    }

    /**
     * Marks all unread notifications as read for the currently logged-in user.
     *
     * @return true if successful
     */
    public boolean markAllAsRead() {
        User user = requireUserSession();
        return notificationDAO.markAllAsRead(user.getUserId());
    }

    /**
     * Retrieves notifications for a given user ID with explicit security ownership checks.
     *
     * @param userId user ID
     * @return list of notifications
     */
    public List<Notification> getNotificationsForUser(int userId) {
        User user = requireUserSession();
        if (user.getUserId() != userId) {
            throw new SecurityException("Access denied: You cannot view notifications for other users.");
        }
        return notificationDAO.findByUserId(userId);
    }

    /**
     * Validates and creates a notification record.
     *
     * @param notification Notification entity
     * @return generated notification ID or -1 on failure
     */
    public int sendNotification(Notification notification) {
        if (notification == null) {
            throw new IllegalArgumentException("Notification cannot be null.");
        }
        if (notification.getUserId() <= 0) {
            throw new IllegalArgumentException("Target user ID must be positive.");
        }
        if (notification.getComplaintId() <= 0) {
            throw new IllegalArgumentException("Associated complaint ID must be positive.");
        }
        if (notification.getTitle() == null || notification.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Notification title cannot be empty.");
        }
        if (notification.getMessage() == null || notification.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Notification message cannot be empty.");
        }

        return notificationDAO.createNotification(notification);
    }
}
