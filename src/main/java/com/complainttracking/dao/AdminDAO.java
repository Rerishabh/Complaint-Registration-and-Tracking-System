package com.complainttracking.dao;

import com.complainttracking.database.DatabaseConnection;
import com.complainttracking.model.Admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for Admin operations.
 */
public class AdminDAO {

    private static final Logger LOGGER = Logger.getLogger(AdminDAO.class.getName());

    /**
     * Finds an administrator by username.
     *
     * @param username the admin username
     * @return Admin object if found, null otherwise
     */
    public Admin findByUsername(String username) {
        String sql = "SELECT admin_id, username, password_hash, full_name, role, created_at FROM admins WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Admin(
                            rs.getInt("admin_id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("full_name"),
                            rs.getString("role"),
                            rs.getTimestamp("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding admin by username: " + username, e);
        }
        return null;
    }

    /**
     * Finds an administrator by ID.
     *
     * @param adminId the admin ID
     * @return Admin object if found, null otherwise
     */
    public Admin findById(int adminId) {
        String sql = "SELECT admin_id, username, password_hash, full_name, role, created_at FROM admins WHERE admin_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, adminId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Admin(
                            rs.getInt("admin_id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("full_name"),
                            rs.getString("role"),
                            rs.getTimestamp("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding admin by ID: " + adminId, e);
        }
        return null;
    }

    /**
     * Retrieves all administrators / staff members.
     *
     * @return list of admins
     */
    public java.util.List<Admin> findAllAdmins() {
        String sql = "SELECT admin_id, username, password_hash, full_name, role, created_at FROM admins ORDER BY full_name ASC";
        java.util.List<Admin> list = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(new Admin(
                        rs.getInt("admin_id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("full_name"),
                        rs.getString("role"),
                        rs.getTimestamp("created_at")
                ));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving all admins", e);
        }
        return list;
    }
}

