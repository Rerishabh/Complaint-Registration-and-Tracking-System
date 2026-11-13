package com.complainttracking.dao;

import com.complainttracking.database.DatabaseConnection;
import com.complainttracking.model.Complaint;
import com.complainttracking.model.ComplaintHistory;

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
 * {@link #createComplaint} wraps both the INSERT into complaints and the
 * initial history entry in a single database transaction.</p>
 */
public class ComplaintDAO {

    private static final Logger LOGGER = Logger.getLogger(ComplaintDAO.class.getName());

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

    // -----------------------------------------------------------------------
    // Read Operations
    // -----------------------------------------------------------------------

    /**
     * Returns all complaints submitted by a user, newest first.
     *
     * @param userId the user's primary key
     * @return ordered list of complaints (may be empty)
     */
    public List<Complaint> findByUserId(int userId) {
        final String sql =
            "SELECT complaint_id, tracking_code, user_id, title, description, " +
            "category, priority, status, created_at, updated_at " +
            "FROM complaints WHERE user_id = ? ORDER BY created_at DESC";

        List<Complaint> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapComplaint(rs));
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
    // Mapping Helper
    // -----------------------------------------------------------------------

    private Complaint mapComplaint(ResultSet rs) throws SQLException {
        return new Complaint(
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
    }
}

