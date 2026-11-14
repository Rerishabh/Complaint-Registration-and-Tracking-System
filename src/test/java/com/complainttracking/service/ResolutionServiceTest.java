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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive automated unit test suite for Phase 7:
 * - Status transition validation
 * - Complaint assignment management
 * - Resolution validation and processing
 * - Resolution retrieval and audit verification
 *
 * Uses in-memory DAO stubs without requiring external database dependencies.
 */
class ResolutionServiceTest {

    private ResolutionService resolutionService;
    private AdminComplaintService adminComplaintService;
    private StubComplaintDAO stubComplaintDAO;
    private StubResolutionDAO stubResolutionDAO;
    private StubAdminDAO stubAdminDAO;
    private StubHistoryDAO stubHistoryDAO;

    private Admin activeAdmin;

    // -----------------------------------------------------------------------
    // DAO Stubs
    // -----------------------------------------------------------------------

    static class StubComplaintDAO extends ComplaintDAO {
        final Map<Integer, Complaint> store = new HashMap<>();
        StubResolutionDAO resolutionDAO;
        StubHistoryDAO historyDAO;

        StubComplaintDAO(StubResolutionDAO resDAO) {
            this(resDAO, null);
        }

        StubComplaintDAO(StubResolutionDAO resDAO, StubHistoryDAO historyDAO) {
            this.resolutionDAO = resDAO;
            this.historyDAO = historyDAO;
        }

        @Override
        public Complaint findById(int id) {
            return store.get(id);
        }

        @Override
        public boolean updateStatusWithHistory(int complaintId, String oldStatus, String newStatus, String changedBy, String remarks) {
            Complaint c = store.get(complaintId);
            if (c == null) return false;
            c.setStatus(newStatus);
            c.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            if (historyDAO != null) {
                historyDAO.histories.add(new ComplaintHistory(
                        historyDAO.histories.size() + 1,
                        complaintId,
                        oldStatus,
                        newStatus,
                        changedBy,
                        remarks,
                        new Timestamp(System.currentTimeMillis())
                ));
            }
            return true;
        }

        @Override
        public boolean assignComplaint(int complaintId, Integer assignedAdminId, String currentStatus, String changedBy, String remarks) {
            Complaint c = store.get(complaintId);
            if (c == null) return false;
            c.setAssignedAdminId(assignedAdminId);
            c.setAssignedAt(assignedAdminId != null ? new Timestamp(System.currentTimeMillis()) : null);
            c.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            if (historyDAO != null) {
                historyDAO.histories.add(new ComplaintHistory(
                        historyDAO.histories.size() + 1,
                        complaintId,
                        currentStatus,
                        currentStatus,
                        changedBy,
                        remarks,
                        new Timestamp(System.currentTimeMillis())
                ));
            }
            return true;
        }

        @Override
        public boolean resolveComplaint(int complaintId, int adminId, String oldStatus, String adminName, String resolutionNotes) {
            Complaint c = store.get(complaintId);
            if (c == null) return false;
            c.setStatus("RESOLVED");
            c.setResolutionNotes(resolutionNotes);
            c.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

            if (resolutionDAO != null) {
                Resolution res = new Resolution(resolutionDAO.idSeq++, complaintId, adminId, resolutionNotes, new Timestamp(System.currentTimeMillis()));
                res.setAdminName(adminName);
                resolutionDAO.resStoreByComplaint.put(complaintId, res);
            }
            return true;
        }
    }

    static class StubResolutionDAO extends ResolutionDAO {
        final Map<Integer, Resolution> resStoreByComplaint = new HashMap<>();
        int idSeq = 1;

        @Override
        public int createResolution(Resolution r) {
            r.setResolutionId(idSeq++);
            r.setResolvedAt(new Timestamp(System.currentTimeMillis()));
            resStoreByComplaint.put(r.getComplaintId(), r);
            return r.getResolutionId();
        }

        @Override
        public Resolution findByComplaintId(int complaintId) {
            return resStoreByComplaint.get(complaintId);
        }
    }

    static class StubAdminDAO extends AdminDAO {
        final Map<Integer, Admin> adminMap = new HashMap<>();

        @Override
        public Admin findById(int adminId) {
            return adminMap.get(adminId);
        }

        @Override
        public List<Admin> findAllAdmins() {
            return new ArrayList<>(adminMap.values());
        }
    }

    static class StubHistoryDAO extends ComplaintHistoryDAO {
        final List<ComplaintHistory> histories = new ArrayList<>();

        @Override
        public List<ComplaintHistory> findByComplaintId(int complaintId) {
            List<ComplaintHistory> list = new ArrayList<>();
            for (ComplaintHistory h : histories) {
                if (h.getComplaintId() == complaintId) list.add(h);
            }
            return list;
        }
    }

    // -----------------------------------------------------------------------
    // Setup & Teardown
    // -----------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        stubResolutionDAO = new StubResolutionDAO();
        stubHistoryDAO = new StubHistoryDAO();
        stubComplaintDAO = new StubComplaintDAO(stubResolutionDAO, stubHistoryDAO);
        stubAdminDAO = new StubAdminDAO();

        resolutionService = new ResolutionService(stubComplaintDAO, stubResolutionDAO);
        adminComplaintService = new AdminComplaintService(
                stubComplaintDAO, stubHistoryDAO, stubAdminDAO, resolutionService);

        // Setup test admin
        activeAdmin = new Admin(10, "admin_test", "hashed_pwd", "Sarah Admin", "ADMIN", new Timestamp(System.currentTimeMillis()));
        Admin staffMember = new Admin(20, "officer_bob", "hashed_pwd", "Bob Officer", "STAFF", new Timestamp(System.currentTimeMillis()));
        stubAdminDAO.adminMap.put(10, activeAdmin);
        stubAdminDAO.adminMap.put(20, staffMember);

        // Login session
        SessionManager.getInstance().loginAdmin(activeAdmin);

        // Seed complaints
        Complaint c1 = new Complaint();
        c1.setComplaintId(101);
        c1.setTrackingCode("CMP-2026-0001");
        c1.setUserId(1);
        c1.setTitle("Broken Projector in Hall 3");
        c1.setDescription("Projector will not power on for lecture.");
        c1.setCategory("Infrastructure");
        c1.setPriority("HIGH");
        c1.setStatus("OPEN");
        c1.setCreatedAt(new Timestamp(System.currentTimeMillis() - 100000));
        c1.setUpdatedAt(new Timestamp(System.currentTimeMillis() - 100000));
        stubComplaintDAO.store.put(101, c1);

        Complaint c2 = new Complaint();
        c2.setComplaintId(102);
        c2.setTrackingCode("CMP-2026-0002");
        c2.setUserId(2);
        c2.setTitle("Hostel WiFi Disconnects");
        c2.setDescription("Frequent dropouts occurring in Block B.");
        c2.setCategory("Network / IT");
        c2.setPriority("MEDIUM");
        c2.setStatus("IN_PROGRESS");
        c2.setCreatedAt(new Timestamp(System.currentTimeMillis() - 80000));
        c2.setUpdatedAt(new Timestamp(System.currentTimeMillis() - 80000));
        stubComplaintDAO.store.put(102, c2);

        Complaint c3 = new Complaint();
        c3.setComplaintId(103);
        c3.setTrackingCode("CMP-2026-0003");
        c3.setUserId(3);
        c3.setTitle("Library Fine Calculation Error");
        c3.setDescription("Charged overdue fee for book returned on time.");
        c3.setCategory("Library");
        c3.setPriority("LOW");
        c3.setStatus("RESOLVED");
        c3.setResolutionNotes("Fine cancelled after book return receipt verification.");
        c3.setCreatedAt(new Timestamp(System.currentTimeMillis() - 60000));
        c3.setUpdatedAt(new Timestamp(System.currentTimeMillis() - 60000));
        stubComplaintDAO.store.put(103, c3);

        Resolution r3 = new Resolution();
        r3.setResolutionId(1);
        r3.setComplaintId(103);
        r3.setAdminId(10);
        r3.setResolutionNotes("Fine cancelled after book return receipt verification.");
        r3.setResolvedAt(new Timestamp(System.currentTimeMillis() - 50000));
        stubResolutionDAO.resStoreByComplaint.put(103, r3);

        Complaint c4 = new Complaint();
        c4.setComplaintId(104);
        c4.setTrackingCode("CMP-2026-0004");
        c4.setUserId(4);
        c4.setTitle("Old Closed Ticket");
        c4.setDescription("Water leakage fixed last week.");
        c4.setCategory("Maintenance");
        c4.setPriority("MEDIUM");
        c4.setStatus("CLOSED");
        c4.setCreatedAt(new Timestamp(System.currentTimeMillis() - 40000));
        c4.setUpdatedAt(new Timestamp(System.currentTimeMillis() - 40000));
        stubComplaintDAO.store.put(104, c4);

        Complaint c5 = new Complaint();
        c5.setComplaintId(105);
        c5.setTrackingCode("CMP-2026-0005");
        c5.setUserId(5);
        c5.setTitle("Duplicate Request");
        c5.setDescription("This is a duplicate ticket.");
        c5.setCategory("Other");
        c5.setPriority("LOW");
        c5.setStatus("REJECTED");
        c5.setCreatedAt(new Timestamp(System.currentTimeMillis() - 20000));
        c5.setUpdatedAt(new Timestamp(System.currentTimeMillis() - 20000));
        stubComplaintDAO.store.put(105, c5);
    }

    @AfterEach
    void tearDown() {
        SessionManager.getInstance().logout();
    }

    // -----------------------------------------------------------------------
    // 1. Status Transition Rules Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Status Transition Rules Tests")
    class StatusTransitionTests {

        @Test
        @DisplayName("Valid transitions from OPEN status")
        void testValidTransitionsFromOpen() {
            assertTrue(ResolutionService.isValidStatusTransition("OPEN", "IN_PROGRESS"));
            assertTrue(ResolutionService.isValidStatusTransition("OPEN", "REJECTED"));
        }

        @Test
        @DisplayName("Invalid transitions from OPEN status")
        void testInvalidTransitionsFromOpen() {
            // Cannot directly jump to RESOLVED or CLOSED from OPEN
            assertFalse(ResolutionService.isValidStatusTransition("OPEN", "RESOLVED"));
            assertFalse(ResolutionService.isValidStatusTransition("OPEN", "CLOSED"));
            // Same status transition is invalid
            assertFalse(ResolutionService.isValidStatusTransition("OPEN", "OPEN"));
        }

        @Test
        @DisplayName("Valid transitions from IN_PROGRESS status")
        void testValidTransitionsFromInProgress() {
            assertTrue(ResolutionService.isValidStatusTransition("IN_PROGRESS", "RESOLVED"));
            assertTrue(ResolutionService.isValidStatusTransition("IN_PROGRESS", "REJECTED"));
            assertTrue(ResolutionService.isValidStatusTransition("IN_PROGRESS", "OPEN"));
        }

        @Test
        @DisplayName("Invalid transitions from IN_PROGRESS status")
        void testInvalidTransitionsFromInProgress() {
            // Cannot directly jump to CLOSED without first being RESOLVED
            assertFalse(ResolutionService.isValidStatusTransition("IN_PROGRESS", "CLOSED"));
            assertFalse(ResolutionService.isValidStatusTransition("IN_PROGRESS", "IN_PROGRESS"));
        }

        @Test
        @DisplayName("Valid transitions from RESOLVED status")
        void testValidTransitionsFromResolved() {
            // Can be closed once verified, or reopened if issues persist
            assertTrue(ResolutionService.isValidStatusTransition("RESOLVED", "CLOSED"));
            assertTrue(ResolutionService.isValidStatusTransition("RESOLVED", "IN_PROGRESS"));
        }

        @Test
        @DisplayName("Invalid transitions from RESOLVED status")
        void testInvalidTransitionsFromResolved() {
            assertFalse(ResolutionService.isValidStatusTransition("RESOLVED", "OPEN"));
            assertFalse(ResolutionService.isValidStatusTransition("RESOLVED", "REJECTED"));
            assertFalse(ResolutionService.isValidStatusTransition("RESOLVED", "RESOLVED"));
        }

        @Test
        @DisplayName("Transitions from CLOSED (terminal status)")
        void testTransitionsFromClosed() {
            // CLOSED is terminal; cannot transition to anything
            assertFalse(ResolutionService.isValidStatusTransition("CLOSED", "OPEN"));
            assertFalse(ResolutionService.isValidStatusTransition("CLOSED", "IN_PROGRESS"));
            assertFalse(ResolutionService.isValidStatusTransition("CLOSED", "RESOLVED"));
            assertFalse(ResolutionService.isValidStatusTransition("CLOSED", "REJECTED"));
            assertFalse(ResolutionService.isValidStatusTransition("CLOSED", "CLOSED"));
        }

        @Test
        @DisplayName("Transitions from REJECTED status")
        void testTransitionsFromRejected() {
            // A rejected complaint can only be reconsidered to OPEN
            assertTrue(ResolutionService.isValidStatusTransition("REJECTED", "OPEN"));
            assertFalse(ResolutionService.isValidStatusTransition("REJECTED", "IN_PROGRESS"));
            assertFalse(ResolutionService.isValidStatusTransition("REJECTED", "RESOLVED"));
            assertFalse(ResolutionService.isValidStatusTransition("REJECTED", "CLOSED"));
        }

        @Test
        @DisplayName("Case-insensitive and null transition safety")
        void testTransitionNullSafety() {
            assertTrue(ResolutionService.isValidStatusTransition("open", "in_progress"));
            assertFalse(ResolutionService.isValidStatusTransition(null, "OPEN"));
            assertFalse(ResolutionService.isValidStatusTransition("OPEN", null));
            assertFalse(ResolutionService.isValidStatusTransition(null, null));
        }
    }

    // -----------------------------------------------------------------------
    // 2. Status Update Service Execution Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Admin Status Update Execution Tests")
    class AdminStatusUpdateExecutionTests {

        @Test
        @DisplayName("Successful status transition updates complaint in repository and logs history")
        void testSuccessfulStatusUpdate() {
            int historyCountBefore = stubHistoryDAO.findByComplaintId(101).size();
            boolean result = adminComplaintService.updateComplaintStatus(101, "IN_PROGRESS", "Assigning to tech team");
            assertTrue(result);
            assertEquals("IN_PROGRESS", stubComplaintDAO.store.get(101).getStatus());

            List<ComplaintHistory> histories = stubHistoryDAO.findByComplaintId(101);
            assertEquals(historyCountBefore + 1, histories.size());
            ComplaintHistory latest = histories.get(histories.size() - 1);
            assertEquals("OPEN", latest.getOldStatus());
            assertEquals("IN_PROGRESS", latest.getNewStatus());
            assertTrue(latest.isStatusTransition());
            assertFalse(latest.isAssignmentEvent());
        }

        @Test
        @DisplayName("No-op status update OPEN -> OPEN is rejected with IllegalArgumentException and creates no history")
        void testNoOpStatusUpdateOpenToOpenRejected() {
            int historyCountBefore = stubHistoryDAO.findByComplaintId(101).size();
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    adminComplaintService.updateComplaintStatus(101, "OPEN", "Reaffirming open status"));
            assertEquals("Complaint is already in status OPEN.", ex.getMessage());
            assertEquals("OPEN", stubComplaintDAO.store.get(101).getStatus());
            assertEquals(historyCountBefore, stubHistoryDAO.findByComplaintId(101).size());
        }

        @Test
        @DisplayName("No-op status update IN_PROGRESS -> IN_PROGRESS is rejected with IllegalArgumentException and creates no history")
        void testNoOpStatusUpdateInProgressToInProgressRejected() {
            int historyCountBefore = stubHistoryDAO.findByComplaintId(102).size();
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    adminComplaintService.updateComplaintStatus(102, "IN_PROGRESS", "Still working"));
            assertEquals("Complaint is already in status IN_PROGRESS.", ex.getMessage());
            assertEquals("IN_PROGRESS", stubComplaintDAO.store.get(102).getStatus());
            assertEquals(historyCountBefore, stubHistoryDAO.findByComplaintId(102).size());
        }

        @Test
        @DisplayName("Illegal status transition throws IllegalArgumentException")
        void testIllegalStatusTransitionRejected() {
            // OPEN -> CLOSED is forbidden
            assertThrows(IllegalArgumentException.class, () ->
                    adminComplaintService.updateComplaintStatus(101, "CLOSED", "Direct close attempt"));
            assertEquals("OPEN", stubComplaintDAO.store.get(101).getStatus());
        }

        @Test
        @DisplayName("Cannot update non-existent complaint throws IllegalArgumentException")
        void testNonExistentComplaintStatusUpdate() {
            assertThrows(IllegalArgumentException.class, () ->
                    adminComplaintService.updateComplaintStatus(99999, "IN_PROGRESS", "Note"));
        }

        @Test
        @DisplayName("Cannot update status without active admin session throws IllegalStateException")
        void testStatusUpdateWithoutSession() {
            SessionManager.getInstance().logout();
            assertThrows(IllegalStateException.class, () ->
                    adminComplaintService.updateComplaintStatus(101, "IN_PROGRESS", "Note"));
            assertEquals("OPEN", stubComplaintDAO.store.get(101).getStatus());
        }
    }

    // -----------------------------------------------------------------------
    // 3. Complaint Assignment Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Complaint Assignment Tests")
    class AssignmentTests {

        @Test
        @DisplayName("Successfully assign complaint to valid staff member and record assignment history without changing status")
        void testAssignToValidStaff() {
            Complaint original = stubComplaintDAO.store.get(101);
            assertEquals("OPEN", original.getStatus());

            boolean result = adminComplaintService.assignComplaint(101, 20, "Please inspect projector cable.");
            assertTrue(result);

            Complaint updated = stubComplaintDAO.store.get(101);
            assertEquals(20, updated.getAssignedAdminId());
            assertNotNull(updated.getAssignedAt());
            assertEquals("OPEN", updated.getStatus(), "Assigning complaint must not change complaint status");

            // Verify assignment history event
            List<ComplaintHistory> histories = stubHistoryDAO.findByComplaintId(101);
            assertFalse(histories.isEmpty());
            ComplaintHistory latest = histories.get(histories.size() - 1);
            assertTrue(latest.isAssignmentEvent(), "Assignment must be classified as an assignment event");
            assertFalse(latest.isStatusTransition(), "Assignment must not create a false status transition");
            assertTrue(latest.getNote().contains("Assigned to Bob Officer"));
            assertTrue(latest.getNote().contains("Please inspect projector cable."));
        }

        @Test
        @DisplayName("Successfully unassign complaint (set assigned_admin_id to null) and record history")
        void testUnassignComplaint() {
            // First assign
            adminComplaintService.assignComplaint(101, 20, "Initial assignment");
            assertEquals(20, stubComplaintDAO.store.get(101).getAssignedAdminId());

            // Now unassign
            boolean unassignResult = adminComplaintService.assignComplaint(101, null, "Handoff unassigned");
            assertTrue(unassignResult);

            Complaint updated = stubComplaintDAO.store.get(101);
            assertNull(updated.getAssignedAdminId());
            assertNull(updated.getAssignedAt());
            assertEquals("OPEN", updated.getStatus(), "Unassigning complaint must preserve complaint status");

            List<ComplaintHistory> histories = stubHistoryDAO.findByComplaintId(101);
            ComplaintHistory latest = histories.get(histories.size() - 1);
            assertTrue(latest.isAssignmentEvent());
            assertFalse(latest.isStatusTransition());
            assertTrue(latest.getNote().contains("Assignment removed"));
        }

        @Test
        @DisplayName("Assigning to non-existent admin ID throws IllegalArgumentException")
        void testAssignToInvalidAdminId() {
            assertThrows(IllegalArgumentException.class, () ->
                    adminComplaintService.assignComplaint(101, 99999, "Note"));
            assertNull(stubComplaintDAO.store.get(101).getAssignedAdminId());
        }

        @Test
        @DisplayName("Get available assignees returns all staff/admins")
        void testGetAvailableAssignees() {
            List<Admin> assignees = adminComplaintService.getAvailableAssignees();
            assertEquals(2, assignees.size());
        }

        @Test
        @DisplayName("Assigning fails if no admin session is active throws IllegalStateException")
        void testAssignWithoutSessionFails() {
            SessionManager.getInstance().logout();
            assertThrows(IllegalStateException.class, () ->
                    adminComplaintService.assignComplaint(101, 20, "Note"));
        }
    }

    // -----------------------------------------------------------------------
    // 4. Resolution Management & Validation Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Resolution Management & Validation Tests")
    class ResolutionValidationTests {

        @Test
        @DisplayName("Validate resolution notes minimum length and criteria")
        void testResolutionNotesValidation() {
            Complaint validComplaint = stubComplaintDAO.store.get(102);

            // Null or empty
            assertThrows(IllegalArgumentException.class, () ->
                    resolutionService.validateResolution(validComplaint, null));
            assertThrows(IllegalArgumentException.class, () ->
                    resolutionService.validateResolution(validComplaint, ""));
            assertThrows(IllegalArgumentException.class, () ->
                    resolutionService.validateResolution(validComplaint, "   "));

            // Shorter than 10 chars
            assertThrows(IllegalArgumentException.class, () ->
                    resolutionService.validateResolution(validComplaint, "Done"));
            assertThrows(IllegalArgumentException.class, () ->
                    resolutionService.validateResolution(validComplaint, "Fixed it"));

            // Null complaint
            assertThrows(IllegalArgumentException.class, () ->
                    resolutionService.validateResolution(null, "Valid note explaining resolution"));

            // 10 or more chars on valid complaint passes without exception
            assertDoesNotThrow(() ->
                    resolutionService.validateResolution(validComplaint, "Fixed HDMI cable successfully"));
            assertDoesNotThrow(() ->
                    resolutionService.validateResolution(validComplaint, "Replaced lamp bulb and verified display projection."));
        }

        @Test
        @DisplayName("Successfully resolve IN_PROGRESS complaint")
        void testResolveInProgressComplaint() {
            String notes = "Technician dispatched to Block B; replaced faulty WiFi access point.";
            boolean result = adminComplaintService.resolveComplaint(102, notes);
            assertTrue(result);

            Complaint updated = stubComplaintDAO.store.get(102);
            assertEquals("RESOLVED", updated.getStatus());
            assertEquals(notes, updated.getResolutionNotes());

            // Verify resolution entry recorded in ResolutionDAO
            Resolution res = stubResolutionDAO.resStoreByComplaint.get(102);
            assertNotNull(res);
            assertEquals(102, res.getComplaintId());
            assertEquals(activeAdmin.getAdminId(), res.getAdminId());
            assertEquals(notes, res.getResolutionNotes());
        }

        @Test
        @DisplayName("Cannot resolve an already RESOLVED complaint throws IllegalArgumentException")
        void testCannotResolveAlreadyResolvedComplaint() {
            assertThrows(IllegalArgumentException.class, () ->
                    adminComplaintService.resolveComplaint(103, "Attempting second resolution"));
        }

        @Test
        @DisplayName("Cannot resolve a CLOSED complaint throws IllegalArgumentException")
        void testCannotResolveClosedComplaint() {
            assertThrows(IllegalArgumentException.class, () ->
                    adminComplaintService.resolveComplaint(104, "Attempting to resolve closed complaint"));
            assertEquals("CLOSED", stubComplaintDAO.store.get(104).getStatus());
        }

        @Test
        @DisplayName("Cannot resolve a REJECTED complaint without prior reconsideration throws IllegalArgumentException")
        void testCannotResolveRejectedComplaint() {
            assertThrows(IllegalArgumentException.class, () ->
                    adminComplaintService.resolveComplaint(105, "Attempting to resolve rejected complaint"));
            assertEquals("REJECTED", stubComplaintDAO.store.get(105).getStatus());
        }

        @Test
        @DisplayName("Cannot resolve non-existent complaint throws IllegalArgumentException")
        void testResolveNonExistentComplaint() {
            assertThrows(IllegalArgumentException.class, () ->
                    adminComplaintService.resolveComplaint(99999, "Valid resolution notes for ghost ticket."));
        }

        @Test
        @DisplayName("Retrieve resolution details for resolved complaint")
        void testGetResolutionDetails() {
            Resolution res = adminComplaintService.getResolutionDetails(103);
            assertNotNull(res);
            assertEquals(103, res.getComplaintId());
            assertEquals("Fine cancelled after book return receipt verification.", res.getResolutionNotes());

            // For complaint without resolution, should return null
            assertNull(adminComplaintService.getResolutionDetails(101));
        }

        @Test
        @DisplayName("Reopening a resolved complaint allows subsequent resolution")
        void testReopenAndResolveCycle() {
            // Complaint 103 is currently RESOLVED.
            // 1. Reopen: RESOLVED -> IN_PROGRESS
            boolean reopen = adminComplaintService.updateComplaintStatus(103, "IN_PROGRESS", "Issue recurred; fines still showing.");
            assertTrue(reopen);
            assertEquals("IN_PROGRESS", stubComplaintDAO.store.get(103).getStatus());

            // 2. Clear old resolution record to allow fresh resolution or re-resolve
            stubResolutionDAO.resStoreByComplaint.remove(103);

            // 3. Resolve again
            String newNotes = "Database sync trigger re-executed; fines permanently zeroed out.";
            boolean reResolve = adminComplaintService.resolveComplaint(103, newNotes);
            assertTrue(reResolve);
            assertEquals("RESOLVED", stubComplaintDAO.store.get(103).getStatus());
            assertEquals(newNotes, stubComplaintDAO.store.get(103).getResolutionNotes());
        }
    }

    // -----------------------------------------------------------------------
    // 5. Assigned Staff Display & Regression Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Assigned Staff Mapping & Display Regression Tests")
    class AssignedStaffDisplayRegressionTests {

        @Test
        @DisplayName("Assigned complaint correctly maps assignedAdminId, assignedAdminName, and displays full_name")
        void testAssignedComplaintDisplaysAdminFullName() {
            Complaint c = new Complaint();
            c.setComplaintId(201);
            c.setTrackingCode("CMP-2026-9001");
            c.setUserId(1);
            c.setTitle("Server Room AC Unit Leaking");
            c.setStatus("IN_PROGRESS");

            // Assign to Bob Officer (id = 20)
            Admin assignee = stubAdminDAO.findById(20);
            assertNotNull(assignee, "Assignee admin should exist in database");
            assertNotNull(assignee.getFullName(), "Assignee full_name should not be null");

            c.setAssignedAdminId(assignee.getAdminId());
            c.setAssignedAdminName(assignee.getFullName());
            c.setAssignedAt(new Timestamp(System.currentTimeMillis()));

            // Verify mapping properties
            assertEquals(Integer.valueOf(20), c.getAssignedAdminId());
            assertEquals(assignee.getFullName(), c.getAssignedAdminName());
            assertNotNull(c.getAssignedAt());

            // Verify dashboard display logic: displays the administrator's actual full_name, not ID or placeholder
            String displayName = (c.getAssignedAdminName() != null && !c.getAssignedAdminName().trim().isEmpty())
                    ? c.getAssignedAdminName() : "Not Assigned";
            assertEquals("Bob Officer", displayName);
            assertEquals(assignee.getFullName(), displayName);
            assertNotEquals("Not Assigned", displayName);
            assertNotEquals("20", displayName);
        }

        @Test
        @DisplayName("Unassigned complaint correctly resolves to 'Not Assigned'")
        void testUnassignedComplaintDisplaysNotAssigned() {
            Complaint c = new Complaint();
            c.setComplaintId(202);
            c.setTrackingCode("CMP-2026-9002");
            c.setUserId(2);
            c.setTitle("Unassigned Projector Issue");
            c.setStatus("OPEN");
            c.setAssignedAdminId(null);
            c.setAssignedAdminName(null);
            c.setAssignedAt(null);

            assertNull(c.getAssignedAdminId());
            assertNull(c.getAssignedAdminName());
            assertNull(c.getAssignedAt());

            String displayName = (c.getAssignedAdminName() != null && !c.getAssignedAdminName().trim().isEmpty())
                    ? c.getAssignedAdminName() : "Not Assigned";
            assertEquals("Not Assigned", displayName);
        }

        @Test
        @DisplayName("Blank or empty assignedAdminName correctly defaults to 'Not Assigned'")
        void testBlankAssignedNameDefaultsToNotAssigned() {
            Complaint c = new Complaint();
            c.setAssignedAdminName("   ");

            String displayName = (c.getAssignedAdminName() != null && !c.getAssignedAdminName().trim().isEmpty())
                    ? c.getAssignedAdminName() : "Not Assigned";
            assertEquals("Not Assigned", displayName);
        }

        @Test
        @DisplayName("ComplaintDAO ResultSet mapping accurately populates assignedAdminId and assignedAdminName")
        void testComplaintDAOMappingHelper() throws Exception {
            // Mock a ResultSet with actual admin join data
            java.sql.ResultSet mockRs = (java.sql.ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                    java.sql.ResultSet.class.getClassLoader(),
                    new Class<?>[]{java.sql.ResultSet.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("getInt".equals(methodName)) {
                            String col = (String) args[0];
                            if ("complaint_id".equals(col)) return 301;
                            if ("user_id".equals(col)) return 5;
                            if ("assigned_admin_id".equals(col)) return 10;
                            return 0;
                        }
                        if ("getString".equals(methodName)) {
                            String col = (String) args[0];
                            if ("tracking_code".equals(col)) return "CMP-2026-9003";
                            if ("title".equals(col)) return "Network Cable Damaged";
                            if ("description".equals(col)) return "Detailed description here";
                            if ("category".equals(col)) return "Infrastructure";
                            if ("priority".equals(col)) return "HIGH";
                            if ("status".equals(col)) return "IN_PROGRESS";
                            if ("complainant_name".equals(col)) return "Alice Johnson";
                            if ("complainant_email".equals(col)) return "alice@test.com";
                            if ("complainant_department".equals(col)) return "Computer Science";
                            if ("assigned_admin_name".equals(col)) return "Sarah Admin";
                            if ("resolution_notes".equals(col)) return null;
                            return null;
                        }
                        if ("getObject".equals(methodName)) {
                            String col = (String) args[0];
                            if ("assigned_admin_id".equals(col)) return Integer.valueOf(10);
                            return null;
                        }
                        if ("getTimestamp".equals(methodName)) {
                            return new Timestamp(System.currentTimeMillis());
                        }
                        if ("wasNull".equals(methodName)) {
                            return false;
                        }
                        return null;
                    }
            );

            Complaint mapped = new ComplaintDAO().mapComplaintWithComplainant(mockRs);
            assertNotNull(mapped);
            assertEquals(301, mapped.getComplaintId());
            assertEquals("CMP-2026-9003", mapped.getTrackingCode());
            assertEquals(Integer.valueOf(10), mapped.getAssignedAdminId());
            assertEquals("Sarah Admin", mapped.getAssignedAdminName());
            assertNotNull(mapped.getAssignedAt());

            String tableDisplay = (mapped.getAssignedAdminName() != null && !mapped.getAssignedAdminName().trim().isEmpty())
                    ? mapped.getAssignedAdminName() : "Not Assigned";
            assertEquals("Sarah Admin", tableDisplay);
        }
    }

    // -----------------------------------------------------------------------
    // 6. Timeline Rendering Classification Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Timeline Rendering Classification Tests")
    class TimelineRenderingClassificationTests {

        @Test
        @DisplayName("ComplaintHistory accurately classifies genuine status transitions vs assignment events")
        void testComplaintHistoryClassification() {
            // Initial submission (null -> OPEN) is a status transition, not an assignment
            ComplaintHistory initial = new ComplaintHistory(1, 101, null, "OPEN", "User", "Submitted", new Timestamp(System.currentTimeMillis()));
            assertTrue(initial.isStatusTransition(), "Initial submission is a status transition");
            assertFalse(initial.isAssignmentEvent(), "Initial submission is not an assignment event");
            assertEquals("—  →  OPEN", initial.getDisplayHeaderText("—"));
            assertEquals("SUBMITTED  →  OPEN", initial.getDisplayHeaderText("SUBMITTED"));

            // Genuine transition (OPEN -> IN_PROGRESS)
            ComplaintHistory transition = new ComplaintHistory(2, 101, "OPEN", "IN_PROGRESS", "Admin", "Investigation started", new Timestamp(System.currentTimeMillis()));
            assertTrue(transition.isStatusTransition(), "OPEN -> IN_PROGRESS is a genuine status transition");
            assertFalse(transition.isAssignmentEvent(), "Status update is not an assignment event");
            assertEquals("OPEN  →  IN_PROGRESS", transition.getDisplayHeaderText("—"));
            assertEquals("OPEN  →  IN_PROGRESS", transition.getDisplayHeaderText("SUBMITTED"));

            // Genuine transition with remarks mentioning "assigned" (e.g. CMP-2026-9724 entry 3)
            ComplaintHistory transitionWithAssignRemark = new ComplaintHistory(3, 101, "OPEN", "IN_PROGRESS", "Admin",
                    "Complaint assigned to the administrator. Initial inspection and troubleshooting are in progress.",
                    new Timestamp(System.currentTimeMillis()));
            assertTrue(transitionWithAssignRemark.isStatusTransition());
            assertEquals("OPEN  →  IN_PROGRESS", transitionWithAssignRemark.getDisplayHeaderText("—"),
                    "Genuine status transition must not be disguised as an assignment event even if remarks mention assign");

            // Assignment event (OPEN -> OPEN with note Assigned to...)
            ComplaintHistory assignment = new ComplaintHistory(4, 101, "OPEN", "OPEN", "Admin", "Assigned to System Administrator.", new Timestamp(System.currentTimeMillis()));
            assertFalse(assignment.isStatusTransition(), "Assignment with identical status is not a status transition");
            assertTrue(assignment.isAssignmentEvent(), "Assignment note and identical status is an assignment event");
            assertEquals("Staff Assigned", assignment.getDisplayHeaderText("—"));
            assertEquals("Staff Assigned", assignment.getDisplayHeaderText("SUBMITTED"));
            assertNotEquals("OPEN  →  OPEN", assignment.getDisplayHeaderText("—"));

            // Assignment event in IN_PROGRESS (IN_PROGRESS -> IN_PROGRESS)
            ComplaintHistory inProgressAssignment = new ComplaintHistory(5, 101, "IN_PROGRESS", "IN_PROGRESS", "Admin", "Assigned to Bob Officer", new Timestamp(System.currentTimeMillis()));
            assertFalse(inProgressAssignment.isStatusTransition());
            assertTrue(inProgressAssignment.isAssignmentEvent());
            assertEquals("Staff Assigned", inProgressAssignment.getDisplayHeaderText("—"));
            assertNotEquals("IN_PROGRESS  →  IN_PROGRESS", inProgressAssignment.getDisplayHeaderText("—"));

            // Unassignment event (IN_PROGRESS -> IN_PROGRESS with note Assignment removed)
            ComplaintHistory unassignment = new ComplaintHistory(6, 101, "IN_PROGRESS", "IN_PROGRESS", "Admin", "Assignment removed", new Timestamp(System.currentTimeMillis()));
            assertFalse(unassignment.isStatusTransition(), "Unassignment with identical status is not a status transition");
            assertTrue(unassignment.isAssignmentEvent(), "Assignment removed is an assignment event");
            assertTrue(unassignment.isUnassignmentEvent());
            assertEquals("Assignment Removed", unassignment.getDisplayHeaderText("—"));
            assertEquals("Assignment Removed", unassignment.getDisplayHeaderText("SUBMITTED"));
        }
    }
}
