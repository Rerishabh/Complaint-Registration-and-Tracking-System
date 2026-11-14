package com.complainttracking.service;

import com.complainttracking.dao.AdminDAO;
import com.complainttracking.dao.ComplaintDAO;
import com.complainttracking.dao.ComplaintHistoryDAO;
import com.complainttracking.dao.ResolutionDAO;
import com.complainttracking.model.Admin;
import com.complainttracking.model.Complaint;
import com.complainttracking.model.ComplaintHistory;
import com.complainttracking.model.Resolution;
import com.complainttracking.util.SessionManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AdminComplaintService - Business logic layer for administrator complaint management.
 * Enforces administrator authentication, status transition rules, and assignment logic.
 */
public class AdminComplaintService {

    private static final Logger LOGGER = Logger.getLogger(AdminComplaintService.class.getName());

    private final ComplaintDAO complaintDAO;
    private final ComplaintHistoryDAO historyDAO;
    private final AdminDAO adminDAO;
    private final ResolutionService resolutionService;

    /**
     * Default constructor for production usage.
     */
    public AdminComplaintService() {
        this(new ComplaintDAO(), new ComplaintHistoryDAO(), new AdminDAO(), new ResolutionService());
    }

    /**
     * Constructor used by existing Phase 6 unit tests.
     */
    public AdminComplaintService(ComplaintDAO complaintDAO, ComplaintHistoryDAO historyDAO) {
        this(complaintDAO, historyDAO, new AdminDAO(), new ResolutionService(complaintDAO, new ResolutionDAO()));
    }

    /**
     * Full constructor allowing complete dependency injection.
     */
    public AdminComplaintService(ComplaintDAO complaintDAO, ComplaintHistoryDAO historyDAO,
                                 AdminDAO adminDAO, ResolutionService resolutionService) {
        this.complaintDAO = complaintDAO;
        this.historyDAO = historyDAO;
        this.adminDAO = adminDAO;
        this.resolutionService = resolutionService;
    }

    /**
     * Validates that an active administrator session exists.
     *
     * @return current authenticated Admin
     * @throws IllegalStateException if no admin is logged in
     */
    public Admin requireAdminSession() {
        Admin admin = SessionManager.getInstance().getCurrentAdmin();
        if (admin == null) {
            throw new IllegalStateException("Unauthorized: Active administrator session required.");
        }
        return admin;
    }

    /**
     * Returns KPI counts for the admin dashboard (Total, Open, In Progress, Resolved, Rejected).
     *
     * @return map of status keys to counts
     */
    public Map<String, Integer> getDashboardStats() {
        requireAdminSession();
        return complaintDAO.getAdminDashboardStats();
    }

    /**
     * Returns all complaints in the system with complainant details.
     *
     * @return list of all complaints
     */
    public List<Complaint> getAllComplaints() {
        requireAdminSession();
        return complaintDAO.findAllComplaints();
    }

    /**
     * Retrieves a single complaint by ID.
     */
    public Complaint getComplaintById(int complaintId) {
        requireAdminSession();
        return complaintDAO.findById(complaintId);
    }

    /**
     * Returns the most recent complaints up to the given limit.
     *
     * @param limit maximum number of recent complaints to return
     * @return list of recent complaints
     */
    public List<Complaint> getRecentComplaints(int limit) {
        requireAdminSession();
        List<Complaint> all = complaintDAO.findAllComplaints();
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        return all.subList(0, Math.min(limit, all.size()));
    }

    /**
     * Searches complaints matching tracking code, title, complainant name, or email.
     *
     * @param keyword search keyword
     * @return list of matching complaints
     */
    public List<Complaint> searchComplaints(String keyword) {
        requireAdminSession();
        return complaintDAO.searchComplaints(keyword);
    }

    /**
     * Filters complaints by status, category, and priority.
     *
     * @param status   status filter or null/"All"
     * @param category category filter or null/"All"
     * @param priority priority filter or null/"All"
     * @return list of filtered complaints
     */
    public List<Complaint> filterComplaints(String status, String category, String priority) {
        requireAdminSession();
        return complaintDAO.filterComplaints(status, category, priority);
    }

    /**
     * Returns the complete audit trail history for a specific complaint.
     *
     * @param complaintId complaint ID
     * @return list of complaint history logs
     */
    public List<ComplaintHistory> getComplaintHistory(int complaintId) {
        requireAdminSession();
        return historyDAO.findByComplaintId(complaintId);
    }

    /**
     * Returns all administrators/staff members available for assignment.
     */
    public List<Admin> getAvailableAssignees() {
        requireAdminSession();
        return adminDAO.findAllAdmins();
    }

    /**
     * Updates complaint status with validation, history insertion, and transaction management.
     *
     * @param complaintId target complaint ID
     * @param newStatus   requested status (OPEN, IN_PROGRESS, RESOLVED, CLOSED, REJECTED)
     * @param remarks     admin remarks
     * @return true if successful
     */
    public boolean updateComplaintStatus(int complaintId, String newStatus, String remarks) {
        Admin admin = requireAdminSession();

        Complaint complaint = complaintDAO.findById(complaintId);
        if (complaint == null) {
            throw new IllegalArgumentException("Complaint with ID " + complaintId + " not found.");
        }

        if (newStatus == null || newStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Target status cannot be empty.");
        }
        String cleanNewStatus = newStatus.trim().toUpperCase();

        if (remarks == null || remarks.trim().isEmpty()) {
            throw new IllegalArgumentException("Remarks are required when updating status.");
        }

        // Validate status transition
        boolean valid = ResolutionService.isValidStatusTransition(complaint.getStatus(), cleanNewStatus);
        if (!valid) {
            throw new IllegalArgumentException(
                "Invalid status transition from " + complaint.getStatus() + " to " + cleanNewStatus + "."
            );
        }

        boolean success = complaintDAO.updateStatusWithHistory(
            complaintId,
            complaint.getStatus(),
            cleanNewStatus,
            admin.getFullName(),
            remarks.trim()
        );

        if (!success) {
            LOGGER.log(Level.SEVERE, "Failed to update status for complaint ID {0}", complaintId);
            throw new IllegalStateException("Database transaction failed while updating complaint status.");
        }

        LOGGER.log(Level.INFO, "Admin {0} updated complaint {1} from {2} to {3}",
                new Object[]{admin.getUsername(), complaint.getTrackingCode(), complaint.getStatus(), cleanNewStatus});
        return true;
    }

    /**
     * Assigns or changes assignment of a complaint to an administrator/staff member.
     *
     * @param complaintId target complaint ID
     * @param adminId     assignee ID (or null to unassign)
     * @param remarks     assignment remarks
     * @return true if successful
     */
    public boolean assignComplaint(int complaintId, Integer adminId, String remarks) {
        Admin currentAdmin = requireAdminSession();

        Complaint complaint = complaintDAO.findById(complaintId);
        if (complaint == null) {
            throw new IllegalArgumentException("Complaint with ID " + complaintId + " not found.");
        }

        String note = (remarks != null && !remarks.trim().isEmpty()) ? remarks.trim() : "";
        if (adminId != null) {
            Admin assignee = adminDAO.findById(adminId);
            if (assignee == null) {
                throw new IllegalArgumentException("Selected assignee does not exist.");
            }
            note = "Assigned to " + assignee.getFullName() + (note.isEmpty() ? "" : " – " + note);
        } else {
            note = "Assignment removed" + (note.isEmpty() ? "" : " – " + note);
        }

        boolean success = complaintDAO.assignComplaint(
            complaintId,
            adminId,
            complaint.getStatus(),
            currentAdmin.getFullName(),
            note
        );

        if (!success) {
            LOGGER.log(Level.SEVERE, "Failed to assign complaint ID {0}", complaintId);
            throw new IllegalStateException("Database transaction failed while assigning complaint.");
        }

        LOGGER.log(Level.INFO, "Admin {0} updated assignment for complaint {1}",
                new Object[]{currentAdmin.getUsername(), complaint.getTrackingCode()});
        return true;
    }

    /**
     * Resolves a complaint via ResolutionService.
     */
    public boolean resolveComplaint(int complaintId, String resolutionNotes) {
        requireAdminSession();
        return resolutionService.resolveComplaint(complaintId, resolutionNotes);
    }

    /**
     * Returns resolution details for a complaint.
     */
    public Resolution getResolutionDetails(int complaintId) {
        return resolutionService.getResolutionDetails(complaintId);
    }
}
