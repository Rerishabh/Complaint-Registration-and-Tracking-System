package com.complainttracking.util;

import com.complainttracking.model.Admin;
import com.complainttracking.model.User;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SessionManager - Manages the active session state for users and administrators.
 * Enforces mutual exclusion between user and admin sessions.
 */
public final class SessionManager {

    private static final Logger LOGGER = Logger.getLogger(SessionManager.class.getName());

    private static volatile SessionManager instance;

    private User currentUser;
    private Admin currentAdmin;

    private SessionManager() {
    }

    /**
     * Retrieves the singleton instance of SessionManager.
     *
     * @return singleton instance
     */
    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    /**
     * Starts a session for a regular user.
     * Automatically invalidates any existing admin session.
     *
     * @param user the authenticated User
     */
    public synchronized void loginUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Cannot create session for null user.");
        }
        this.currentAdmin = null; // Prevent simultaneous admin session
        this.currentUser = user;
        LOGGER.log(Level.INFO, "User session started for user ID: {0} ({1})",
                new Object[]{user.getUserId(), user.getEmail()});
    }

    /**
     * Starts a session for an administrator.
     * Automatically invalidates any existing user session.
     *
     * @param admin the authenticated Admin
     */
    public synchronized void loginAdmin(Admin admin) {
        if (admin == null) {
            throw new IllegalArgumentException("Cannot create session for null admin.");
        }
        this.currentUser = null; // Prevent simultaneous user session
        this.currentAdmin = admin;
        LOGGER.log(Level.INFO, "Admin session started for admin ID: {0} ({1})",
                new Object[]{admin.getAdminId(), admin.getUsername()});
    }

    /**
     * Returns the currently logged-in user, or null if no user session is active.
     */
    public synchronized User getCurrentUser() {
        return currentUser;
    }

    /**
     * Returns the currently logged-in admin, or null if no admin session is active.
     */
    public synchronized Admin getCurrentAdmin() {
        return currentAdmin;
    }

    /**
     * Checks if a user is currently logged in.
     */
    public synchronized boolean isUserLoggedIn() {
        return currentUser != null;
    }

    /**
     * Checks if an admin is currently logged in.
     */
    public synchronized boolean isAdminLoggedIn() {
        return currentAdmin != null;
    }

    /**
     * Checks if any account (user or admin) is currently logged in.
     */
    public synchronized boolean isLoggedIn() {
        return currentUser != null || currentAdmin != null;
    }

    /**
     * Logs out the current user session.
     */
    public synchronized void logoutUser() {
        if (currentUser != null) {
            LOGGER.log(Level.INFO, "Logging out user: {0}", currentUser.getEmail());
            currentUser = null;
        }
    }

    /**
     * Logs out the current admin session.
     */
    public synchronized void logoutAdmin() {
        if (currentAdmin != null) {
            LOGGER.log(Level.INFO, "Logging out admin: {0}", currentAdmin.getUsername());
            currentAdmin = null;
        }
    }

    /**
     * Logs out any active session.
     */
    public synchronized void logout() {
        LOGGER.log(Level.INFO, "Clearing all active sessions.");
        currentUser = null;
        currentAdmin = null;
    }
}

