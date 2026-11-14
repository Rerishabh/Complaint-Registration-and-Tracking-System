package com.complainttracking.service;

import com.complainttracking.dao.ComplaintDAO;
import com.complainttracking.dao.ResolutionDAO;
import com.complainttracking.model.Admin;
import com.complainttracking.model.Complaint;
import com.complainttracking.model.Resolution;
import com.complainttracking.util.SessionManager;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ResolutionService – business logic layer for complaint resolution and status validation.
 * Enforces administrator authentication, status transition rules, and transaction boundaries.
 */
public class ResolutionService {

    private static final Logger LOGGER = Logger.getLogger(ResolutionService.class.getName());

    public static final int MIN_RESOLUTION_NOTES_LEN = 10;

    private final ComplaintDAO complaintDAO;
    private final ResolutionDAO resolutionDAO;

    /** Default constructor. */
    public ResolutionService() {
        this(new ComplaintDAO(), new ResolutionDAO());
    }

    /** Constructor allowing dependency injection (for unit tests). */
    public ResolutionService(ComplaintDAO complaintDAO, ResolutionDAO resolutionDAO) {
        this.complaintDAO  = complaintDAO;
        this.resolutionDAO = resolutionDAO;
    }

    /**
     * Verifies that an administrator is logged in.
     *
     * @return active Admin
     * @throws IllegalStateException if no admin session is active
     */
    public Admin requireAdminSession() {
        Admin admin = SessionManager.getInstance().getCurrentAdmin();
        if (admin == null) {
            throw new IllegalStateException("Unauthorized: Administrator authentication required.");
        }
        return admin;
    }

    /**
     * Validates whether a status transition from currentStatus to targetStatus is permitted.
     *
     * Rules:
     * - OPEN -> IN_PROGRESS, REJECTED
     * - IN_PROGRESS -> RESOLVED, REJECTED, OPEN
     * - RESOLVED -> CLOSED, IN_PROGRESS (reopening)
     * - REJECTED -> OPEN (reconsideration)
     * - CLOSED -> Terminal state (no further transitions)
     * - Forbidden: OPEN -> CLOSED, CLOSED -> IN_PROGRESS, REJECTED -> RESOLVED
     *
     * @param currentStatus status of the complaint
     * @param targetStatus  requested new status
     * @return true if permitted, false otherwise
     */
    public static boolean isValidStatusTransition(String currentStatus, String targetStatus) {
        if (currentStatus == null || targetStatus == null) return false;
        String from = currentStatus.trim().toUpperCase();
        String to   = targetStatus.trim().toUpperCase();

        if (from.equals(to)) return false;

        return switch (from) {
            case Complaint.STATUS_OPEN ->
                to.equals(Complaint.STATUS_IN_PROGRESS) ||
                to.equals(Complaint.STATUS_REJECTED);

            case Complaint.STATUS_IN_PROGRESS ->
                to.equals(Complaint.STATUS_RESOLVED) ||
                to.equals(Complaint.STATUS_REJECTED) ||
                to.equals(Complaint.STATUS_OPEN);

            case Complaint.STATUS_RESOLVED ->
                to.equals(Complaint.STATUS_CLOSED) ||
                to.equals(Complaint.STATUS_IN_PROGRESS); // Reopening

            case Complaint.STATUS_REJECTED ->
                to.equals(Complaint.STATUS_OPEN); // Reconsideration

            case Complaint.STATUS_CLOSED ->
                false; // Terminal state

            default -> false;
        };
    }

    /**
     * Validates complaint resolution prerequisites.
     *
     * @param complaint       complaint entity
     * @param resolutionNotes notes provided
     * @throws IllegalArgumentException on validation failure
     */
    public void validateResolution(Complaint complaint, String resolutionNotes) {
        if (complaint == null) {
            throw new IllegalArgumentException("Complaint does not exist.");
        }

        if (resolutionNotes == null || resolutionNotes.trim().isEmpty()) {
            throw new IllegalArgumentException("Resolution notes cannot be empty.");
        }

        if (resolutionNotes.trim().length() < MIN_RESOLUTION_NOTES_LEN) {
            throw new IllegalArgumentException(
                "Resolution notes must be at least " + MIN_RESOLUTION_NOTES_LEN + " characters long."
            );
        }

        String status = complaint.getStatus();
        // Eligible statuses for resolution: OPEN or IN_PROGRESS
        if (Complaint.STATUS_RESOLVED.equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Complaint is already resolved.");
        }
        if (Complaint.STATUS_CLOSED.equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Cannot resolve a closed complaint. It must be reopened first.");
        }
        if (Complaint.STATUS_REJECTED.equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Cannot resolve a rejected complaint. Invalid status transition (REJECTED -> RESOLVED).");
        }

        // Prevent duplicate resolution records
        Resolution existing = resolutionDAO.findByComplaintId(complaint.getComplaintId());
        if (existing != null) {
            throw new IllegalArgumentException("A resolution record already exists for this complaint.");
        }
    }

    /**
     * Resolves a complaint by writing a resolution record, updating status to RESOLVED,
     * and writing an audit trail history record in a single transaction.
     *
     * @param complaintId     target complaint ID
     * @param resolutionNotes details of resolution
     * @return true if resolution succeeded
     */
    public boolean resolveComplaint(int complaintId, String resolutionNotes) {
        Admin admin = requireAdminSession();

        Complaint complaint = complaintDAO.findById(complaintId);
        if (complaint == null) {
            throw new IllegalArgumentException("Complaint with ID " + complaintId + " not found.");
        }

        validateResolution(complaint, resolutionNotes);

        boolean success = complaintDAO.resolveComplaint(
            complaintId,
            admin.getAdminId(),
            complaint.getStatus(),
            admin.getFullName(),
            resolutionNotes.trim()
        );

        if (!success) {
            LOGGER.log(Level.SEVERE, "Failed to resolve complaint ID {0}", complaintId);
            throw new IllegalStateException("Database error while committing resolution transaction.");
        }

        LOGGER.log(Level.INFO, "Complaint {0} successfully resolved by {1}",
                new Object[]{complaint.getTrackingCode(), admin.getUsername()});
        return true;
    }

    /**
     * Retrieves resolution details for a complaint.
     *
     * @param complaintId complaint ID
     * @return Resolution or null if not yet resolved
     */
    public Resolution getResolutionDetails(int complaintId) {
        return resolutionDAO.findByComplaintId(complaintId);
    }
}

