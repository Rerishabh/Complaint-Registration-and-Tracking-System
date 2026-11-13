package com.complainttracking.service;

import com.complainttracking.dao.AdminDAO;
import com.complainttracking.dao.UserDAO;
import com.complainttracking.model.Admin;
import com.complainttracking.model.User;
import com.complainttracking.util.SessionManager;
import org.mindrot.jbcrypt.BCrypt;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * AuthenticationService - Handles user registration, user login, and admin login
 * with comprehensive validation, BCrypt password hashing, and session management.
 */
public class AuthenticationService {

    private static final Logger LOGGER = Logger.getLogger(AuthenticationService.class.getName());

    // RFC 5322 compliant simplified email regex
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    // Standard phone regex: optional '+' followed by 7 to 15 digits, spaces, hyphens, or parentheses
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[0-9\\s\\-\\(\\)]{7,20}$"
    );

    private final UserDAO userDAO;
    private final AdminDAO adminDAO;

    /**
     * Default constructor initializing standard DAOs.
     */
    public AuthenticationService() {
        this(new UserDAO(), new AdminDAO());
    }

    /**
     * Constructor allowing dependency injection (e.g. for unit testing).
     */
    public AuthenticationService(UserDAO userDAO, AdminDAO adminDAO) {
        this.userDAO = userDAO;
        this.adminDAO = adminDAO;
    }

    /**
     * Registers a new user after comprehensive validation and password hashing.
     *
     * @param fullName        full name of user
     * @param email           email address (unique)
     * @param password        plaintext password (will be hashed)
     * @param confirmPassword confirmation password (must match)
     * @param phone           contact number (optional, validated if provided)
     * @param department      selected department
     * @return registered User instance with generated ID
     * @throws IllegalArgumentException on any validation failure
     * @throws IllegalStateException    if database persistence fails
     */
    public User registerUser(String fullName, String email, String password,
                             String confirmPassword, String phone, String department) {

        // 1. Validate full name
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (fullName.trim().length() < 2 || fullName.trim().length() > 100) {
            throw new IllegalArgumentException("Full name must be between 2 and 100 characters.");
        }

        // 2. Validate email
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required.");
        }
        String cleanEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(cleanEmail).matches()) {
            throw new IllegalArgumentException("Please enter a valid email address (e.g., user@example.com).");
        }
        if (userDAO.emailExists(cleanEmail)) {
            throw new IllegalArgumentException("This email address is already registered. Please log in instead.");
        }

        // 3. Validate phone (optional, but validate format if present)
        String cleanPhone = (phone != null && !phone.trim().isEmpty()) ? phone.trim() : null;
        if (cleanPhone != null && !PHONE_PATTERN.matcher(cleanPhone).matches()) {
            throw new IllegalArgumentException("Please enter a valid phone number (digits, optional '+', '-', spaces).");
        }

        // 4. Validate department
        if (department == null || department.trim().isEmpty() || "Select Department".equalsIgnoreCase(department.trim())) {
            throw new IllegalArgumentException("Please select your department.");
        }

        // 5. Validate password
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("Password must contain at least 6 characters.");
        }

        // 6. Validate password confirmation
        if (confirmPassword == null || !password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match. Please re-enter.");
        }

        // 7. Hash password securely using BCrypt with work factor 12
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));

        // 8. Create and persist User entity
        User newUser = new User(fullName.trim(), cleanEmail, passwordHash, cleanPhone, department.trim());
        boolean success = userDAO.registerUser(newUser);

        if (!success) {
            LOGGER.log(Level.SEVERE, "Failed to persist user in database: {0}", cleanEmail);
            throw new IllegalStateException("Registration failed due to a database error. Please try again later.");
        }

        LOGGER.log(Level.INFO, "User successfully registered with email: {0}", cleanEmail);
        return newUser;
    }

    /**
     * Authenticates a user by email and password, establishing an active session.
     *
     * @param email    user email
     * @param password user plaintext password
     * @return authenticated User
     * @throws IllegalArgumentException on missing inputs or authentication failure
     */
    public User loginUser(String email, String password) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address cannot be empty.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }

        String cleanEmail = email.trim().toLowerCase();
        User user = userDAO.findByEmail(cleanEmail);

        if (user == null) {
            LOGGER.log(Level.WARNING, "User login failed: email not found ({0})", cleanEmail);
            throw new IllegalArgumentException("Invalid email or password.");
        }

        boolean passwordValid = false;
        try {
            passwordValid = BCrypt.checkpw(password, user.getPasswordHash());
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Corrupt BCrypt hash in database for user: " + cleanEmail, e);
            passwordValid = false;
        }

        if (!passwordValid) {
            LOGGER.log(Level.WARNING, "User login failed: incorrect password for email ({0})", cleanEmail);
            throw new IllegalArgumentException("Invalid email or password.");
        }

        // Establish session in SessionManager
        SessionManager.getInstance().loginUser(user);
        LOGGER.log(Level.INFO, "User login successful: {0}", cleanEmail);
        return user;
    }

    /**
     * Authenticates an administrator by username and password, establishing an active admin session.
     *
     * @param username admin username
     * @param password admin plaintext password
     * @return authenticated Admin
     * @throws IllegalArgumentException on missing inputs or authentication failure
     */
    public Admin loginAdmin(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }

        String cleanUsername = username.trim();
        Admin admin = adminDAO.findByUsername(cleanUsername);

        if (admin == null) {
            LOGGER.log(Level.WARNING, "Admin login failed: username not found ({0})", cleanUsername);
            throw new IllegalArgumentException("Invalid username or password.");
        }

        boolean passwordValid = false;
        try {
            passwordValid = BCrypt.checkpw(password, admin.getPasswordHash());
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Corrupt BCrypt hash in database for admin: " + cleanUsername, e);
            passwordValid = false;
        }

        if (!passwordValid) {
            LOGGER.log(Level.WARNING, "Admin login failed: incorrect password for admin ({0})", cleanUsername);
            throw new IllegalArgumentException("Invalid username or password.");
        }

        // Establish session in SessionManager
        SessionManager.getInstance().loginAdmin(admin);
        LOGGER.log(Level.INFO, "Admin login successful: {0}", cleanUsername);
        return admin;
    }
}

