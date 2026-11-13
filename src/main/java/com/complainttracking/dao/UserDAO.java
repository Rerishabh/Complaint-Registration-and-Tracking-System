package com.complainttracking.dao;

import com.complainttracking.database.DatabaseConnection;
import com.complainttracking.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for User operations.
 */
public class UserDAO {

    private static final Logger LOGGER = Logger.getLogger(UserDAO.class.getName());

    /**
     * Inserts a new user record into the database.
     *
     * @param user the User to register
     * @return true if insertion succeeded, false otherwise
     */
    public boolean registerUser(User user) {
        String sql = "INSERT INTO users (full_name, email, password_hash, phone, department) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPasswordHash());
            stmt.setString(4, user.getPhone());
            stmt.setString(5, user.getDepartment());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        user.setUserId(generatedKeys.getInt(1));
                    }
                }
                LOGGER.log(Level.INFO, "User registered successfully with ID: {0}", user.getUserId());
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to register user with email: " + user.getEmail(), e);
        }
        return false;
    }

    /**
     * Finds a user by email address.
     *
     * @param email the user's email
     * @return User object if found, null otherwise
     */
    public User findByEmail(String email) {
        String sql = "SELECT user_id, full_name, email, password_hash, phone, department, created_at FROM users WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("user_id"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getString("phone"),
                            rs.getString("department"),
                            rs.getTimestamp("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding user by email: " + email, e);
        }
        return null;
    }

    /**
     * Checks if a user already exists with the given email.
     *
     * @param email the email to check
     * @return true if email exists, false otherwise
     */
    public boolean emailExists(String email) {
        String sql = "SELECT 1 FROM users WHERE email = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking email existence: " + email, e);
        }
        return false;
    }
}

