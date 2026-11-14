package com.complainttracking.dao;

import com.complainttracking.database.DatabaseConnection;
import com.complainttracking.model.Resolution;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ResolutionDAO – JDBC data-access object for the 'resolutions' table.
 */
public class ResolutionDAO {

    private static final Logger LOGGER = Logger.getLogger(ResolutionDAO.class.getName());

    /**
     * Inserts a resolution record within an existing database transaction.
     *
     * @param conn       active JDBC connection with autocommit disabled
     * @param resolution resolution entity
     * @return generated resolution_id, or -1 on failure
     * @throws SQLException on database error
     */
    public int createResolution(Connection conn, Resolution resolution) throws SQLException {
        final String sql =
            "INSERT INTO resolutions (complaint_id, admin_id, resolution_notes) " +
            "VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt   (1, resolution.getComplaintId());
            ps.setInt   (2, resolution.getAdminId());
            ps.setString(3, resolution.getResolutionNotes());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int id = keys.getInt(1);
                        resolution.setResolutionId(id);
                        return id;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Standalone creation of a resolution record.
     *
     * @param resolution resolution entity
     * @return generated resolution_id, or -1 on failure
     */
    public int createResolution(Resolution resolution) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return createResolution(conn, resolution);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error creating resolution for complaintId: " + resolution.getComplaintId(), e);
            return -1;
        }
    }

    /**
     * Retrieves the resolution record for a given complaint ID.
     *
     * @param complaintId complaint ID
     * @return Resolution object if found, or null
     */
    public Resolution findByComplaintId(int complaintId) {
        final String sql =
            "SELECT r.resolution_id, r.complaint_id, r.admin_id, r.resolution_notes, r.resolved_at, " +
            "a.full_name AS admin_name " +
            "FROM resolutions r " +
            "LEFT JOIN admins a ON r.admin_id = a.admin_id " +
            "WHERE r.complaint_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Resolution res = new Resolution(
                        rs.getInt      ("resolution_id"),
                        rs.getInt      ("complaint_id"),
                        rs.getInt      ("admin_id"),
                        rs.getString   ("resolution_notes"),
                        rs.getTimestamp("resolved_at")
                    );
                    res.setAdminName(rs.getString("admin_name"));
                    return res;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding resolution for complaintId: " + complaintId, e);
        }
        return null;
    }

    /**
     * Retrieves all resolutions.
     *
     * @return list of all resolutions
     */
    public List<Resolution> findAllResolutions() {
        final String sql =
            "SELECT r.resolution_id, r.complaint_id, r.admin_id, r.resolution_notes, r.resolved_at, " +
            "a.full_name AS admin_name " +
            "FROM resolutions r " +
            "LEFT JOIN admins a ON r.admin_id = a.admin_id " +
            "ORDER BY r.resolved_at DESC";

        List<Resolution> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Resolution res = new Resolution(
                    rs.getInt      ("resolution_id"),
                    rs.getInt      ("complaint_id"),
                    rs.getInt      ("admin_id"),
                    rs.getString   ("resolution_notes"),
                    rs.getTimestamp("resolved_at")
                );
                res.setAdminName(rs.getString("admin_name"));
                list.add(res);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving all resolutions", e);
        }
        return list;
    }
}

