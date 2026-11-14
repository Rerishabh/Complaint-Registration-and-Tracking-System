package com.complainttracking.dao;

import com.complainttracking.database.DatabaseConnection;
import com.complainttracking.model.Complaint;
import com.complainttracking.model.Notification;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ComplaintDAO – JDBC data-access object for the complaints table.
 *
 * <p>All queries use {@link PreparedStatement} to prevent SQL injection.
 * Multi-operation updates are executed inside single JDBC transactions.</p>
 */
public class ComplaintDAO {

    private static final Logger LOGGER = Logger.getLogger(ComplaintDAO.class.getName());

    private static final String SELECT_BASE =
        "SELECT c.complaint_id, c.tracking_code, c.user_id, c.title, c.description, " +
        "c.category, c.priority, c.status, c.created_at, c.updated_at, " +
        "c.assigned_admin_id, c.assigned_at, " +
        "u.full_name AS complainant_name, u.email AS complainant_email, u.department AS complainant_department, " +
        "a.full_name AS assigned_admin_name, " +
        "r.resolution_notes " +
        "FROM complaints c " +
        "JOIN users u ON c.user_id = u.user_id " +
        "LEFT JOIN admins a ON c.assigned_admin_id = a.admin_id " +
        "LEFT JOIN resolutions r ON c.complaint_id = r.complaint_id ";

    // -----------------------------------------------------------------------
    // Write Operations
    // -----------------------------------------------------------------------

    /**
     * Persists a new complaint and its initial history entry in one transaction.
     *
     * @param complaint   fully-populated Complaint (trackingCode already generated)
     * @param submittedBy display name of the submitting user (for history)
     * @return the generated complaint_id, or -1 on failure
     */
    public int createComplaint(Complaint complaint, String submittedBy) {
        final String insertComplaint =
            "INSERT INTO complaints (tracking_code, user_id, title, description, " +
            "category, priority, status) VALUES (?, ?, ?, ?, ?, ?, ?)";

        final String insertHistory =
            "INSERT INTO complaint_history (complaint_id, old_status, new_status, " +
            "changed_by, note) VALUES (?, NULL, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);   // BEGIN TRANSACTION

            int generatedId = -1;

            // 1. Insert complaint
            try (PreparedStatement ps = conn.prepareStatement(
                    insertComplaint, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, complaint.getTrackingCode());
                ps.setInt   (2, complaint.getUserId());
                ps.setString(3, complaint.getTitle());
                ps.setString(4, complaint.getDescription());
                ps.setString(5, complaint.getCategory());
                ps.setString(6, complaint.getPriority());
                ps.setString(7, Complaint.STATUS_OPEN);
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        generatedId = keys.getInt(1);
                        complaint.setComplaintId(generatedId);
                        complaint.setStatus(Complaint.STATUS_OPEN);
                    }
                }
            }

            // 2. Insert initial history record
            if (generatedId > 0) {
                try (PreparedStatement ps = conn.prepareStatement(insertHistory)) {
                    ps.setInt   (1, generatedId);
                    ps.setString(2, Complaint.STATUS_OPEN);
                    ps.setString(3, submittedBy);
                    ps.setString(4, "Complaint submitted by user.");
                    ps.executeUpdate();
                }

                // 3. Fetch database-generated timestamps
                final String selectTimestamps = "SELECT created_at, updated_at FROM complaints WHERE complaint_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(selectTimestamps)) {
                    ps.setInt(1, generatedId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            complaint.setCreatedAt(rs.getTimestamp("created_at"));
                            complaint.setUpdatedAt(rs.getTimestamp("updated_at"));
                        }
                    }
                }
            }

            conn.commit();   // COMMIT
            LOGGER.log(Level.INFO, "Complaint created with ID: {0}, tracking: {1}",
                    new Object[]{generatedId, complaint.getTrackingCode()});
            return generatedId;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to create complaint – rolling back.", e);
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException re) {
                    LOGGER.log(Level.WARNING, "Rollback failed.", re);
                }
            }
            return -1;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Could not restore auto-commit.", e);
                }
            }
        }
    }

    /**
     * Updates complaint status and records the history entry in a single transaction.
     *
     * @param complaintId  target complaint ID
     * @param oldStatus    current status
     * @param newStatus    target new status
     * @param changedBy    admin name
     * @param remarks      admin remarks / explanation
     * @return true if successful
     */
    public boolean updateStatusWithHistory(int complaintId, String oldStatus,
                                           String newStatus, String changedBy, String remarks) {
        final String updateSql =
            "UPDATE complaints SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
        final String historySql =
            "INSERT INTO complaint_history (complaint_id, old_status, new_status, changed_by, note) " +
            "VALUES (?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                psUpdate.setString(1, newStatus);
                psUpdate.setInt   (2, complaintId);
                int rows = psUpdate.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
            }

            try (PreparedStatement psHistory = conn.prepareStatement(historySql)) {
                psHistory.setInt   (1, complaintId);
                psHistory.setString(2, oldStatus);
                psHistory.setString(3, newStatus);
                psHistory.setString(4, changedBy);
                psHistory.setString(5, remarks);
                psHistory.executeUpdate();
            }

            // Phase 8: Insert notification within the same transaction
            String notifMsg = "Your complaint {code} status has changed from " + oldStatus + " to " + newStatus + ".";
            if (remarks != null && !remarks.trim().isEmpty()) {
                notifMsg += " Note: " + remarks.trim();
            }
            insertNotification(conn, complaintId, "Complaint Status Updated", notifMsg);

            conn.commit();
            LOGGER.log(Level.INFO, "Complaint ID {0} status updated from {1} to {2}",
                    new Object[]{complaintId, oldStatus, newStatus});
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update status for complaint ID " + complaintId, e);
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException re) {
                    LOGGER.log(Level.WARNING, "Rollback failed", re);
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Could not restore auto-commit", e);
                }
            }
        }
    }

    /**
     * Assigns or reassigns a complaint to an admin/staff member within a single transaction.
     *
     * @param complaintId    target complaint ID
     * @param assignedAdminId ID of admin to assign (or null to unassign)
     * @param currentStatus  current complaint status
     * @param changedBy      admin executing the assignment
     * @param remarks        assignment remarks
     * @return true if successful
     */
    public boolean assignComplaint(int complaintId, Integer assignedAdminId,
                                   String currentStatus, String changedBy, String remarks) {
        final String updateSql =
            "UPDATE complaints SET assigned_admin_id = ?, " +
            "assigned_at = " + (assignedAdminId != null ? "CURRENT_TIMESTAMP" : "NULL") + ", " +
            "updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
        final String historySql =
            "INSERT INTO complaint_history (complaint_id, old_status, new_status, changed_by, note) " +
            "VALUES (?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                if (assignedAdminId != null) {
                    psUpdate.setInt(1, assignedAdminId);
                } else {
                    psUpdate.setNull(1, Types.INTEGER);
                }
                psUpdate.setInt(2, complaintId);
                int rows = psUpdate.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
            }

            try (PreparedStatement psHistory = conn.prepareStatement(historySql)) {
                psHistory.setInt   (1, complaintId);
                psHistory.setString(2, currentStatus);
                psHistory.setString(3, currentStatus);
                psHistory.setString(4, changedBy);
                psHistory.setString(5, remarks);
                psHistory.executeUpdate();
            }

            // Phase 8: Insert notification within the same transaction
            String notifTitle;
            String notifMsg;
            if (assignedAdminId != null) {
                notifTitle = "Complaint Assigned";
                notifMsg = "Your complaint {code} has been assigned to staff for investigation.";
                if (remarks != null && !remarks.trim().isEmpty()) {
                    notifMsg += " Note: " + remarks.trim();
                }
            } else {
                notifTitle = "Complaint Unassigned";
                notifMsg = "Your complaint {code} has been unassigned.";
            }
            insertNotification(conn, complaintId, notifTitle, notifMsg);

            conn.commit();
            LOGGER.log(Level.INFO, "Complaint ID {0} assigned to admin {1}",
                    new Object[]{complaintId, assignedAdminId});
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to assign complaint ID " + complaintId, e);
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException re) {
                    LOGGER.log(Level.WARNING, "Rollback failed", re);
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Could not restore auto-commit", e);
                }
            }
        }
    }

    /**
     * Resolves a complaint: inserts resolution record, updates status to RESOLVED,
     * and writes complaint_history all within a single JDBC transaction.
     *
     * @param complaintId     complaint ID
     * @param adminId         admin ID resolving the complaint
     * @param oldStatus       status prior to resolution
     * @param adminName       admin full name
     * @param resolutionNotes resolution details
     * @return true if successful
     */
    public boolean resolveComplaint(int complaintId, int adminId, String oldStatus,
                                    String adminName, String resolutionNotes) {
        final String resolutionSql =
            "INSERT INTO resolutions (complaint_id, admin_id, resolution_notes) VALUES (?, ?, ?)";
        final String updateSql =
            "UPDATE complaints SET status = 'RESOLVED', updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
        final String historySql =
            "INSERT INTO complaint_history (complaint_id, old_status, new_status, changed_by, note) " +
            "VALUES (?, ?, 'RESOLVED', ?, ?)";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Insert resolution
            try (PreparedStatement psRes = conn.prepareStatement(resolutionSql)) {
                psRes.setInt   (1, complaintId);
                psRes.setInt   (2, adminId);
                psRes.setString(3, resolutionNotes);
                psRes.executeUpdate();
            }

            // 2. Update complaint status
            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                psUpdate.setInt(1, complaintId);
                int rows = psUpdate.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
            }

            // 3. Write audit history
            try (PreparedStatement psHistory = conn.prepareStatement(historySql)) {
                psHistory.setInt   (1, complaintId);
                psHistory.setString(2, oldStatus);
                psHistory.setString(3, adminName);
                psHistory.setString(4, "Resolved: " + resolutionNotes);
                psHistory.executeUpdate();
            }

            // Phase 8: Insert notification within the same transaction (avoiding duplicate notifications)
            String notifMsg = "Your complaint {code} has been successfully resolved.";
            if (resolutionNotes != null && !resolutionNotes.trim().isEmpty()) {
                notifMsg += " Resolution: " + resolutionNotes.trim();
            }
            insertNotification(conn, complaintId, "Complaint Resolved", notifMsg);

            conn.commit();
            LOGGER.log(Level.INFO, "Complaint ID {0} successfully resolved by admin ID {1}",
                    new Object[]{complaintId, adminId});
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to resolve complaint ID " + complaintId, e);
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException re) {
                    LOGGER.log(Level.WARNING, "Rollback failed", re);
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Could not restore auto-commit", e);
                }
            }
        }
    }

    /**
     * Helper to insert an in-app notification within an existing transaction.
     * Throws SQLException so the parent transaction rolls back on notification failure.
     */
    private void insertNotification(Connection conn, int complaintId, String title, String messageTemplate) throws SQLException {
        int userId = -1;
        String trackingCode = null;
        String lookupSql = "SELECT user_id, tracking_code FROM complaints WHERE complaint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(lookupSql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    userId = rs.getInt("user_id");
                    trackingCode = rs.getString("tracking_code");
                }
            }
        }

        if (userId > 0) {
            String codeStr = (trackingCode != null && !trackingCode.isEmpty()) ? trackingCode : ("#" + complaintId);
            String message = messageTemplate.replace("{code}", codeStr);

            Notification notif = new Notification();
            notif.setUserId(userId);
            notif.setComplaintId(complaintId);
            notif.setTitle(title);
            notif.setMessage(message);
            notif.setRead(false);

            new NotificationDAO().createNotification(conn, notif);
        }
    }

    // -----------------------------------------------------------------------
    // Read Operations
    // -----------------------------------------------------------------------

    /**
     * Finds a single complaint by ID with all complainant and assignment details.
     *
     * @param complaintId complaint ID
     * @return Complaint object or null if not found
     */
    public Complaint findById(int complaintId) {
        final String sql = SELECT_BASE + "WHERE c.complaint_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapComplaintFull(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding complaint by ID: " + complaintId, e);
        }
        return null;
    }

    /**
     * Finds a single complaint by tracking code with all complainant and assignment details.
     *
     * @param trackingCode tracking code (e.g. CMP-2026-1234)
     * @return Complaint object or null if not found
     */
    public Complaint findByTrackingCode(String trackingCode) {
        if (trackingCode == null || trackingCode.trim().isEmpty()) {
            return null;
        }
        final String sql = SELECT_BASE + "WHERE c.tracking_code = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, trackingCode.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapComplaintFull(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding complaint by tracking code: " + trackingCode, e);
        }
        return null;
    }

    /**
     * Returns all complaints submitted by a user, newest first.
     *
     * @param userId the user's primary key
     * @return ordered list of complaints (may be empty)
     */
    public List<Complaint> findByUserId(int userId) {
        final String sql = SELECT_BASE + "WHERE c.user_id = ? ORDER BY c.created_at DESC";

        List<Complaint> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapComplaintFull(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching complaints for userId: " + userId, e);
        }
        return results;
    }

    /**
     * Returns KPI counts for a user's complaints.
     * Keys: "TOTAL", "OPEN", "IN_PROGRESS", "RESOLVED"
     *
     * @param userId the user's primary key
     * @return map of status label → count
     */
    public Map<String, Integer> getUserStats(int userId) {
        final String sql =
            "SELECT " +
            "  COUNT(*) AS total, " +
            "  SUM(CASE WHEN status = 'OPEN'        THEN 1 ELSE 0 END) AS open_count, " +
            "  SUM(CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress_count, " +
            "  SUM(CASE WHEN status IN ('RESOLVED','CLOSED') THEN 1 ELSE 0 END) AS resolved_count " +
            "FROM complaints WHERE user_id = ?";

        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("TOTAL",       0);
        stats.put("OPEN",        0);
        stats.put("IN_PROGRESS", 0);
        stats.put("RESOLVED",    0);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.put("TOTAL",       rs.getInt("total"));
                    stats.put("OPEN",        rs.getInt("open_count"));
                    stats.put("IN_PROGRESS", rs.getInt("in_progress_count"));
                    stats.put("RESOLVED",    rs.getInt("resolved_count"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching stats for userId: " + userId, e);
        }
        return stats;
    }

    // -----------------------------------------------------------------------
    // Admin Read Operations
    // -----------------------------------------------------------------------

    /**
     * Returns all complaints with complainant and assignment details, newest first.
     *
     * @return list of all complaints
     */
    public List<Complaint> findAllComplaints() {
        final String sql = SELECT_BASE + "ORDER BY c.created_at DESC";

        List<Complaint> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                results.add(mapComplaintFull(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching all complaints for admin", e);
        }
        return results;
    }

    /**
     * Returns KPI counts across all complaints in the database.
     * Keys: "TOTAL", "OPEN", "IN_PROGRESS", "RESOLVED", "REJECTED"
     *
     * @return map of status label -> count
     */
    public Map<String, Integer> getAdminDashboardStats() {
        final String sql =
            "SELECT " +
            "  COUNT(*) AS total, " +
            "  SUM(CASE WHEN status = 'OPEN'        THEN 1 ELSE 0 END) AS open_count, " +
            "  SUM(CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress_count, " +
            "  SUM(CASE WHEN status = 'RESOLVED'    THEN 1 ELSE 0 END) AS resolved_count, " +
            "  SUM(CASE WHEN status = 'REJECTED'    THEN 1 ELSE 0 END) AS rejected_count " +
            "FROM complaints";

        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("TOTAL",       0);
        stats.put("OPEN",        0);
        stats.put("IN_PROGRESS", 0);
        stats.put("RESOLVED",    0);
        stats.put("REJECTED",    0);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                stats.put("TOTAL",       rs.getInt("total"));
                stats.put("OPEN",        rs.getInt("open_count"));
                stats.put("IN_PROGRESS", rs.getInt("in_progress_count"));
                stats.put("RESOLVED",    rs.getInt("resolved_count"));
                stats.put("REJECTED",    rs.getInt("rejected_count"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching admin dashboard stats", e);
        }
        return stats;
    }

    /**
     * Searches complaints matching tracking code, title, complainant name, or email.
     *
     * @param keyword search term
     * @return matching complaints
     */
    public List<Complaint> searchComplaints(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAllComplaints();
        }

        final String sql =
            SELECT_BASE +
            "WHERE LOWER(c.tracking_code) LIKE ? " +
            "   OR LOWER(c.title) LIKE ? " +
            "   OR LOWER(u.full_name) LIKE ? " +
            "   OR LOWER(u.email) LIKE ? " +
            "ORDER BY c.created_at DESC";

        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        List<Complaint> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapComplaintFull(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error searching complaints with keyword: " + keyword, e);
        }
        return results;
    }

    /**
     * Filters complaints by optional status, category, and priority.
     *
     * @param status   status filter (e.g. "OPEN" or null/"All")
     * @param category category filter (or null/"All")
     * @param priority priority filter (or null/"All")
     * @return filtered complaints list
     */
    public List<Complaint> filterComplaints(String status, String category, String priority) {
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE 1=1 ");

        List<String> params = new ArrayList<>();

        if (status != null && !status.trim().isEmpty() && !"All".equalsIgnoreCase(status.trim()) && !"All Statuses".equalsIgnoreCase(status.trim())) {
            sql.append("AND c.status = ? ");
            params.add(status.trim());
        }
        if (category != null && !category.trim().isEmpty() && !"All".equalsIgnoreCase(category.trim()) && !"All Categories".equalsIgnoreCase(category.trim())) {
            sql.append("AND c.category = ? ");
            params.add(category.trim());
        }
        if (priority != null && !priority.trim().isEmpty() && !"All".equalsIgnoreCase(priority.trim()) && !"All Priorities".equalsIgnoreCase(priority.trim())) {
            sql.append("AND c.priority = ? ");
            params.add(priority.trim());
        }

        sql.append("ORDER BY c.created_at DESC");

        List<Complaint> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapComplaintFull(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error filtering complaints", e);
        }
        return results;
    }

    // -----------------------------------------------------------------------
    // Mapping Helper
    // -----------------------------------------------------------------------

    /**
     * Maps a ResultSet row to a Complaint object with complainant and assignment details.
     *
     * @param rs active ResultSet
     * @return fully populated Complaint
     * @throws SQLException on database error
     */
    public Complaint mapComplaintWithComplainant(ResultSet rs) throws SQLException {
        Complaint c = new Complaint(
            rs.getInt      ("complaint_id"),
            rs.getString   ("tracking_code"),
            rs.getInt      ("user_id"),
            rs.getString   ("title"),
            rs.getString   ("description"),
            rs.getString   ("category"),
            rs.getString   ("priority"),
            rs.getString   ("status"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at")
        );

        c.setComplainantName(rs.getString("complainant_name"));
        c.setComplainantEmail(rs.getString("complainant_email"));
        c.setComplainantDepartment(rs.getString("complainant_department"));

        c.setAssignedAdminId((Integer) rs.getObject("assigned_admin_id"));
        c.setAssignedAt(rs.getTimestamp("assigned_at"));
        c.setAssignedAdminName(rs.getString("assigned_admin_name"));
        c.setResolutionNotes(rs.getString("resolution_notes"));

        return c;
    }

    private Complaint mapComplaintFull(ResultSet rs) throws SQLException {
        return mapComplaintWithComplainant(rs);
    }
}
