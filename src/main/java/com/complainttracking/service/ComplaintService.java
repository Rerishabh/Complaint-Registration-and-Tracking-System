package com.complainttracking.service;

import com.complainttracking.dao.ComplaintDAO;
import com.complainttracking.dao.ComplaintHistoryDAO;
import com.complainttracking.model.Complaint;
import com.complainttracking.model.ComplaintHistory;
import com.complainttracking.model.User;
import com.complainttracking.util.SessionManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ComplaintService – business logic layer for complaint operations.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Input validation (title, category, priority, description)</li>
 *   <li>Unique tracking-code generation (CMP-YYYY-XXXX)</li>
 *   <li>Session enforcement via {@link SessionManager}</li>
 *   <li>Delegation to {@link ComplaintDAO} and {@link ComplaintHistoryDAO}</li>
 * </ul>
 */
public class ComplaintService {

    private static final Logger LOGGER = Logger.getLogger(ComplaintService.class.getName());

    /** Valid categories exposed to the UI. */
    public static final List<String> CATEGORIES = List.of(
            "Infrastructure",
            "IT & Network",
            "Academic",
            "Hostel & Facilities",
            "Administration",
            "Other"
    );

    /**
     * Display labels for priority (shown in ComboBox).
     * Maps to the MySQL ENUM: LOW, MEDIUM, HIGH, CRITICAL.
     */
    public static final List<String> PRIORITY_LABELS = List.of(
            "Low", "Medium", "High", "Urgent"
    );

    /** Internal ENUM values that map to the display labels above (same order). */
    private static final Set<String> VALID_DB_PRIORITIES =
            Set.of(Complaint.PRIORITY_LOW, Complaint.PRIORITY_MEDIUM,
                   Complaint.PRIORITY_HIGH, Complaint.PRIORITY_CRITICAL);

    private static final int MIN_TITLE_LEN       = 5;
    private static final int MAX_TITLE_LEN       = 150;
    private static final int MIN_DESCRIPTION_LEN = 15;

    private static final Random RNG = new Random();

    private final ComplaintDAO        complaintDAO;
    private final ComplaintHistoryDAO historyDAO;

    /** Default constructor used by production code. */
    public ComplaintService() {
        this(new ComplaintDAO(), new ComplaintHistoryDAO());
    }

    /** Dependency-injection constructor used in unit tests. */
    public ComplaintService(ComplaintDAO complaintDAO, ComplaintHistoryDAO historyDAO) {
        this.complaintDAO = complaintDAO;
        this.historyDAO   = historyDAO;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Submits a new complaint after validation and tracking-code generation.
     *
     * @param title       complaint title (5–150 chars, required)
     * @param category    must be one of {@link #CATEGORIES}
     * @param priorityLabel display label: "Low" | "Medium" | "High" | "Urgent"
     * @param description minimum 15 characters, required
     * @return the persisted {@link Complaint} with generated ID and tracking code
     * @throws IllegalArgumentException if any field fails validation
     * @throws IllegalStateException    if no active user session exists, or DB fails
     */
    public Complaint submitComplaint(String title, String category,
                                     String priorityLabel, String description) {

        // 1. Enforce active user session
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) {
            throw new IllegalStateException("No active user session. Please log in first.");
        }

        // 2. Validate fields
        validateTitle(title);
        validateCategory(category);
        String dbPriority = validateAndMapPriority(priorityLabel);
        validateDescription(description);

        // 3. Generate unique tracking code
        String trackingCode = generateTrackingCode();

        // 4. Build the complaint entity
        Complaint complaint = new Complaint(
                currentUser.getUserId(),
                trackingCode,
                title.trim(),
                description.trim(),
                category.trim(),
                dbPriority
        );

        // 5. Persist via DAO (transaction: complaint + history)
        int generatedId = complaintDAO.createComplaint(complaint, currentUser.getFullName());
        if (generatedId < 0) {
            throw new IllegalStateException(
                    "Failed to save complaint. Please check database connectivity and try again.");
        }

        // 6. Ensure returned complaint has database-generated timestamp populated
        if (complaint.getCreatedAt() == null) {
            Complaint saved = complaintDAO.findById(generatedId);
            if (saved != null && saved.getCreatedAt() != null) {
                complaint.setCreatedAt(saved.getCreatedAt());
                complaint.setUpdatedAt(saved.getUpdatedAt());
            }
        }

        LOGGER.log(Level.INFO, "Complaint submitted: ID={0}, tracking={1}",
                new Object[]{generatedId, trackingCode});
        return complaint;
    }

    /**
     * Returns all complaints for the currently logged-in user.
     */
    public List<Complaint> getMyComplaints() {
        User currentUser = requireSession();
        return complaintDAO.findByUserId(currentUser.getUserId());
    }

    /**
     * Returns KPI stats for the currently logged-in user.
     * Keys: "TOTAL", "OPEN", "IN_PROGRESS", "RESOLVED"
     */
    public Map<String, Integer> getMyStats() {
        User currentUser = requireSession();
        return complaintDAO.getUserStats(currentUser.getUserId());
    }

    /**
     * Returns the full audit history for a specific complaint.
     * Enforces user ownership check.
     *
     * @param complaintId the complaint's primary key
     */
    public List<ComplaintHistory> getHistory(int complaintId) {
        User currentUser = requireSession();
        Complaint c = complaintDAO.findById(complaintId);
        if (c != null && c.getUserId() != currentUser.getUserId()) {
            throw new SecurityException("Unauthorized: You can only view history for your own complaints.");
        }
        return historyDAO.findByComplaintId(complaintId);
    }

    // -----------------------------------------------------------------------
    // Tracking Code Generation
    // -----------------------------------------------------------------------

    /**
     * Generates a human-readable unique tracking code: {@code CMP-YYYY-XXXX}.
     * The random 4-digit suffix provides 10 000 permutations per year;
     * sufficient for a desktop academic system.
     */
    public String generateTrackingCode() {
        String year   = String.valueOf(LocalDateTime.now().getYear());
        String suffix = String.format("%04d", RNG.nextInt(10_000));
        return "CMP-" + year + "-" + suffix;
    }

    // -----------------------------------------------------------------------
    // Validation Helpers
    // -----------------------------------------------------------------------

    private void validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Complaint title is required.");
        }
        int len = title.trim().length();
        if (len < MIN_TITLE_LEN) {
            throw new IllegalArgumentException(
                    "Title must be at least " + MIN_TITLE_LEN + " characters long.");
        }
        if (len > MAX_TITLE_LEN) {
            throw new IllegalArgumentException(
                    "Title must not exceed " + MAX_TITLE_LEN + " characters.");
        }
    }

    private void validateCategory(String category) {
        if (category == null || category.trim().isEmpty() ||
                "Select Category".equalsIgnoreCase(category.trim())) {
            throw new IllegalArgumentException("Please select a complaint category.");
        }
        if (!CATEGORIES.contains(category.trim())) {
            throw new IllegalArgumentException(
                    "Invalid category selected: " + category);
        }
    }

    /**
     * Maps the UI display label ("Low", "Medium", "High", "Urgent") to the
     * MySQL ENUM value ("LOW", "MEDIUM", "HIGH", "CRITICAL").
     */
    public String validateAndMapPriority(String label) {
        if (label == null || label.trim().isEmpty() ||
                "Select Priority".equalsIgnoreCase(label.trim())) {
            throw new IllegalArgumentException("Please select a priority level.");
        }
        return switch (label.trim()) {
            case "Low"    -> Complaint.PRIORITY_LOW;
            case "Medium" -> Complaint.PRIORITY_MEDIUM;
            case "High"   -> Complaint.PRIORITY_HIGH;
            case "Urgent" -> Complaint.PRIORITY_CRITICAL;
            default -> throw new IllegalArgumentException(
                    "Invalid priority: '" + label + "'. Choose Low, Medium, High, or Urgent.");
        };
    }

    private void validateDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Complaint description is required.");
        }
        if (description.trim().length() < MIN_DESCRIPTION_LEN) {
            throw new IllegalArgumentException(
                    "Description must be at least " + MIN_DESCRIPTION_LEN +
                    " characters. Please provide more detail.");
        }
    }

    private User requireSession() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("No active user session.");
        }
        return user;
    }
}

