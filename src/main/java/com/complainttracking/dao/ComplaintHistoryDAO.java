package com.complainttracking.dao;

import com.complainttracking.database.DatabaseConnection;
import com.complainttracking.model.ComplaintHistory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ComplaintHistoryDAO – JDBC data-access object for the complaint_history table.
 */
public class ComplaintHistoryDAO {

    private static final Logger LOGGER = Logger.getLogger(ComplaintHistoryDAO.class.getName());

    /**
     * Returns the full audit trail for a complaint, ordered chronologically.
     *
     * @param complaintId the complaint's primary key
     * @return list of history entries (oldest first)
     */
    public List<ComplaintHistory> findByComplaintId(int complaintId) {
        final String sql =
            "SELECT history_id, complaint_id, old_status, new_status, " +
            "changed_by, note, changed_at " +
            "FROM complaint_history " +
            "WHERE complaint_id = ? ORDER BY changed_at ASC";

        List<ComplaintHistory> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new ComplaintHistory(
                        rs.getInt      ("history_id"),
                        rs.getInt      ("complaint_id"),
                        rs.getString   ("old_status"),
                        rs.getString   ("new_status"),
                        rs.getString   ("changed_by"),
                        rs.getString   ("note"),
                        rs.getTimestamp("changed_at")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Error fetching history for complaintId: " + complaintId, e);
        }
        return results;
    }
}

