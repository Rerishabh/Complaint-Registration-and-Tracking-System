package com.complainttracking.dao;

import com.complainttracking.database.DatabaseConnection;
import com.complainttracking.model.Notification;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NotificationDAO – Data Access Object for in-app user notifications.
 *
 * <p>All queries use PreparedStatement to prevent SQL injection.
 * Strict user ownership is enforced on all mutation and retrieval operations.</p>
 */
public class NotificationDAO {

    private static final Logger LOGGER = Logger.getLogger(NotificationDAO.class.getName());

    /**
     * Inserts a notification record within an existing database transaction.
     *
     * @param conn         active JDBC Connection with autocommit disabled
     * @param notification Notification entity to insert
     * @return generated notification_id, or -1 on failure
     * @throws SQLException on database error
     */
    public int createNotification(Connection conn, Notification notification) throws SQLException {
        if (notification == null) {
            throw new IllegalArgumentException("Notification cannot be null.");
        }

        final String sql =
            "INSERT INTO notifications (user_id, complaint_id, title, message, is_read) " +
            "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt    (1, notification.getUserId());
            ps.setInt    (2, notification.getComplaintId());
            ps.setString (3, notification.getTitle());
            ps.setString (4, notification.getMessage());
            ps.setBoolean(5, notification.isRead());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        notification.setNotificationId(id);
                        return id;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Standalone insertion of a notification record.
     *
     * @param notification Notification entity
     * @return generated notification_id, or -1 on failure
     */
    public int createNotification(Notification notification) {
        if (notification == null) {
            return -1;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            return createNotification(conn, notification);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error creating standalone notification for user " + notification.getUserId(), e);
            return -1;
        }
    }

    /**
     * Retrieves all notifications for a specific user ordered by created_at DESC.
     * Joins with complaints table to include tracking_code.
     *
     * @param userId target user ID
     * @return list of notifications (empty if none found)
     */
    public List<Notification> findByUserId(int userId) {
        final String sql =
            "SELECT n.notification_id, n.user_id, n.complaint_id, n.title, n.message, " +
            "       n.is_read, n.created_at, c.tracking_code " +
            "FROM notifications n " +
            "JOIN complaints c ON n.complaint_id = c.complaint_id " +
            "WHERE n.user_id = ? " +
            "ORDER BY n.created_at DESC";

        List<Notification> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Notification n = new Notification(
                        rs.getInt      ("notification_id"),
                        rs.getInt      ("user_id"),
                        rs.getInt      ("complaint_id"),
                        rs.getString   ("title"),
                        rs.getString   ("message"),
                        rs.getBoolean  ("is_read"),
                        rs.getTimestamp("created_at")
                    );
                    n.setComplaintTrackingCode(rs.getString("tracking_code"));
                    list.add(n);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving notifications for user ID " + userId, e);
        }
        return list;
    }

    /**
     * Calculates the number of unread notifications for a user.
     *
     * @param userId target user ID
     * @return count of unread notifications
     */
    public int getUnreadCount(int userId) {
        final String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error counting unread notifications for user ID " + userId, e);
        }
        return 0;
    }

    /**
     * Marks a specific notification as read, strictly enforcing ownership by userId.
     *
     * @param notificationId notification ID
     * @param userId         owner user ID
     * @return true if updated, false if not found or unauthorized
     */
    public boolean markAsRead(int notificationId, int userId) {
        final String sql = "UPDATE notifications SET is_read = TRUE WHERE notification_id = ? AND user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, notificationId);
            ps.setInt(2, userId);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error marking notification " + notificationId + " as read for user " + userId, e);
            return false;
        }
    }

    /**
     * Marks all notifications as read for a specific user.
     *
     * @param userId target user ID
     * @return true if executed successfully
     */
    public boolean markAllAsRead(int userId) {
        final String sql = "UPDATE notifications SET is_read = TRUE WHERE user_id = ? AND is_read = FALSE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error marking all notifications as read for user " + userId, e);
            return false;
        }
    }
}
