package com.complainttracking.service;

import com.complainttracking.dao.ComplaintDAO;
import com.complainttracking.dao.ComplaintHistoryDAO;
import com.complainttracking.model.Complaint;
import com.complainttracking.model.User;
import com.complainttracking.util.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ComplaintService.
 * Uses in-memory stub DAOs – no database required.
 */
class ComplaintServiceTest {

    private ComplaintService service;
    private StubComplaintDAO    stubDAO;
    private StubHistoryDAO      stubHistoryDAO;
    private User                testUser;

    // -----------------------------------------------------------------------
    // Stubs
    // -----------------------------------------------------------------------

    static class StubComplaintDAO extends ComplaintDAO {
        int lastReturnId = 1;
        Complaint lastCreated;

        @Override
        public int createComplaint(Complaint complaint, String submittedBy) {
            complaint.setComplaintId(lastReturnId);
            complaint.setStatus(Complaint.STATUS_OPEN);
            lastCreated = complaint;
            return lastReturnId;
        }

        @Override
        public List<Complaint> findByUserId(int userId) {
            return lastCreated != null ? List.of(lastCreated) : Collections.emptyList();
        }

        @Override
        public Map<String, Integer> getUserStats(int userId) {
            return Map.of("TOTAL", 1, "OPEN", 1, "IN_PROGRESS", 0, "RESOLVED", 0);
        }
    }

    static class StubHistoryDAO extends ComplaintHistoryDAO {
        @Override
        public java.util.List<com.complainttracking.model.ComplaintHistory> findByComplaintId(int id) {
            return Collections.emptyList();
        }
    }

    // -----------------------------------------------------------------------
    // Setup
    // -----------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        SessionManager.getInstance().logout();
        stubDAO        = new StubComplaintDAO();
        stubHistoryDAO = new StubHistoryDAO();
        service        = new ComplaintService(stubDAO, stubHistoryDAO);

        // Plant a fake logged-in user
        testUser = new User("Alice Test", "alice@test.com", "hash", "9876543210", "IT");
        testUser.setUserId(42);
        SessionManager.getInstance().loginUser(testUser);
    }

    // -----------------------------------------------------------------------
    // Tracking Code Tests
    // -----------------------------------------------------------------------

    @Test
    void trackingCodeHasCorrectFormat() {
        String code = service.generateTrackingCode();
        // CMP-YYYY-XXXX
        assertTrue(code.matches("CMP-\\d{4}-\\d{4}"),
                "Expected format CMP-YYYY-XXXX but got: " + code);
    }

    @Test
    void trackingCodesAreReasonablyUnique() {
        // With 10 000 possibilities, 20 consecutive draws should rarely repeat
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) codes.add(service.generateTrackingCode());
        // At least 15 distinct values expected (probability of collision extremely low)
        assertTrue(codes.size() >= 15,
                "Too many collisions in tracking code generation: " + codes.size());
    }

    // -----------------------------------------------------------------------
    // Priority Mapping Tests
    // -----------------------------------------------------------------------

    @Test
    void priorityLabelMapsToDatabaseEnum() {
        assertEquals("LOW",      service.validateAndMapPriority("Low"));
        assertEquals("MEDIUM",   service.validateAndMapPriority("Medium"));
        assertEquals("HIGH",     service.validateAndMapPriority("High"));
        assertEquals("CRITICAL", service.validateAndMapPriority("Urgent"));
    }

    @Test
    void invalidPriorityThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndMapPriority("Ultra"));
    }

    @Test
    void emptyPriorityThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAndMapPriority(""));
    }

    // -----------------------------------------------------------------------
    // Field Validation Tests
    // -----------------------------------------------------------------------

    @Test
    void submitComplaintSucceeds() {
        Complaint c = service.submitComplaint(
                "Network is down in lab",
                "IT & Network",
                "High",
                "The network switch in Lab 3 has been down since morning, affecting 30 students."
        );
        assertNotNull(c);
        assertEquals("HIGH", c.getPriority());
        assertEquals(Complaint.STATUS_OPEN, c.getStatus());
        assertTrue(c.getTrackingCode().startsWith("CMP-"));
        assertEquals(42, c.getUserId());
    }

    @Test
    void emptyTitleThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                service.submitComplaint("", "Infrastructure", "Low",
                        "Some valid description of the problem."));
    }

    @Test
    void titleTooShortThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                service.submitComplaint("Hi", "Infrastructure", "Low",
                        "Some valid description of the problem."));
    }

    @Test
    void titleTooLongThrows() {
        String longTitle = "A".repeat(151);
        assertThrows(IllegalArgumentException.class, () ->
                service.submitComplaint(longTitle, "Infrastructure", "Low",
                        "Some valid description of the problem."));
    }

    @Test
    void invalidCategoryThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                service.submitComplaint("Valid title here", "InvalidCategory", "Low",
                        "Some valid description of the problem."));
    }

    @Test
    void emptyDescriptionThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                service.submitComplaint("Valid title here", "Infrastructure", "Low", ""));
    }

    @Test
    void descriptionTooShortThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                service.submitComplaint("Valid title here", "Infrastructure", "Low", "Too short"));
    }

    @Test
    void noSessionThrows() {
        SessionManager.getInstance().logout();
        assertThrows(IllegalStateException.class, () ->
                service.submitComplaint("Valid title here", "Infrastructure", "Low",
                        "Some valid description of the problem."));
    }

    @Test
    void emptySelectCategoryThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                service.submitComplaint("Valid title here", "Select Category", "Low",
                        "Some valid description of the problem."));
    }

    // -----------------------------------------------------------------------
    // getMyComplaints / getMyStats Tests
    // -----------------------------------------------------------------------

    @Test
    void getMyComplaintsReturnsListForCurrentUser() {
        // First submit so the stub has data
        service.submitComplaint("Network down in lab 3", "IT & Network", "High",
                "The switch in Lab 3 has been offline since 8 AM, blocking student access.");
        List<Complaint> list = service.getMyComplaints();
        assertFalse(list.isEmpty());
    }

    @Test
    void getMyStatsReturnsExpectedKeys() {
        Map<String, Integer> stats = service.getMyStats();
        assertTrue(stats.containsKey("TOTAL"));
        assertTrue(stats.containsKey("OPEN"));
        assertTrue(stats.containsKey("IN_PROGRESS"));
        assertTrue(stats.containsKey("RESOLVED"));
    }
}

