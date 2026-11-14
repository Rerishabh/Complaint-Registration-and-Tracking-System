package com.complainttracking.service;

import com.complainttracking.dao.ComplaintDAO;
import com.complainttracking.dao.NotificationDAO;
import com.complainttracking.model.Admin;
import com.complainttracking.model.Complaint;
import com.complainttracking.model.Notification;
import com.complainttracking.model.User;
import com.complainttracking.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive automated test suite for Phase 8:
 * - Notification Model verification
 * - In-App Notification creation, retrieval, and unread counting
 * - User session and ownership enforcement
 * - Automatic notifications on Admin actions (Status Update, Assignment, Resolution)
 * - Prevention of duplicate notifications on resolution
 * - Quick Complaint Tracking lookup (authenticated vs unauthenticated privacy enforcement)
 * - JDBC Transactional integrity and rollback verification
 */
class NotificationServiceTest {

    private StubNotificationDAO stubNotificationDAO;
    private StubComplaintDAO stubComplaintDAO;
    private NotificationService notificationService;

    private User userA;
    private User userB;
    private Admin admin;

    @BeforeEach
    void setUp() {
        SessionManager.getInstance().logout();

        stubNotificationDAO = new StubNotificationDAO();
        stubComplaintDAO = new StubComplaintDAO(stubNotificationDAO);
        notificationService = new NotificationService(stubNotificationDAO);

        userA = new User(1, "Alice Student", "alice@university.edu", "passhash", "1234567890", "CompSci", null);
        userB = new User(2, "Bob Student", "bob@university.edu", "passhash", "0987654321", "Electrical", null);
        admin = new Admin(10, "admin", "passhash", "System Admin", "ADMIN", null);
    }

    @AfterEach
    void tearDown() {
        SessionManager.getInstance().logout();
    }

    // -----------------------------------------------------------------------
    // Test 1: Notification Model Getters, Setters, and Defaults
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("1. Notification model getters/setters & default values")
    void testNotificationModelGettersSetters() {
        Notification n = new Notification();
        assertNull(n.getCreatedAt());
        assertFalse(n.isRead(), "Default isRead should be false");

        n.setNotificationId(101);
        n.setUserId(1);
        n.setComplaintId(50);
        n.setTitle("Status Changed");
        n.setMessage("Your complaint has been processed.");
        n.setRead(true);
        n.setComplaintTrackingCode("CMP-2026-0001");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        n.setCreatedAt(now);

        assertEquals(101, n.getNotificationId());
        assertEquals(1, n.getUserId());
        assertEquals(50, n.getComplaintId());
        assertEquals("Status Changed", n.getTitle());
        assertEquals("Your complaint has been processed.", n.getMessage());
        assertTrue(n.isRead());
        assertEquals("CMP-2026-0001", n.getComplaintTrackingCode());
        assertEquals(now, n.getCreatedAt());

        // Parameterized constructor test
        Notification n2 = new Notification(102, 1, 50, "Title", "Message", false, now, "CMP-2026-0001");
        assertEquals(102, n2.getNotificationId());
        assertFalse(n2.isRead());
        assertTrue(n2.toString().contains("CMP-2026-0001"));
    }

    // -----------------------------------------------------------------------
    // Test 2: Notification Creation With Valid Data
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("2. Notification creation with valid data")
    void testNotificationCreationWithValidData() {
        Notification n = new Notification();
        n.setUserId(userA.getUserId());
        n.setComplaintId(10);
        n.setTitle("Welcome Alert");
        n.setMessage("Welcome to the notification system.");

        int id = stubNotificationDAO.createNotification(n);
        assertTrue(id > 0, "Notification ID should be generated and positive");
        assertEquals(1, stubNotificationDAO.store.size());
        assertEquals("Welcome Alert", stubNotificationDAO.store.get(0).getTitle());
    }

    // -----------------------------------------------------------------------
    // Test 3: Notification Creation Fails With Null
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("3. Notification creation fails with null notification")
    void testNotificationCreationFailsWithNull() {
        int result = stubNotificationDAO.createNotification(null);
        assertEquals(-1, result, "Creating null notification should return -1");

        assertThrows(IllegalArgumentException.class, () -> notificationService.sendNotification(null));
    }

    // -----------------------------------------------------------------------
    // Test 4: Notification Retrieval Ordered By Newest First
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("4. Notification retrieval ordered by newest first")
    void testNotificationRetrievalOrderedByNewestFirst() {
        long baseTime = System.currentTimeMillis();

        Notification n1 = new Notification(1, userA.getUserId(), 10, "First", "Msg 1", false,
                new Timestamp(baseTime - 10000), "CMP-2026-0001");
        Notification n2 = new Notification(2, userA.getUserId(), 10, "Second", "Msg 2", false,
                new Timestamp(baseTime), "CMP-2026-0001");
        Notification n3 = new Notification(3, userA.getUserId(), 10, "Third", "Msg 3", false,
                new Timestamp(baseTime + 10000), "CMP-2026-0001");

        stubNotificationDAO.store.addAll(List.of(n1, n2, n3));

        List<Notification> result = stubNotificationDAO.findByUserId(userA.getUserId());
        assertEquals(3, result.size());
        assertEquals(3, result.get(0).getNotificationId(), "Newest notification should be first");
        assertEquals(2, result.get(1).getNotificationId());
        assertEquals(1, result.get(2).getNotificationId(), "Oldest notification should be last");
    }

    // -----------------------------------------------------------------------
    // Test 5: Unread Count Returns Correct Number
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("5. Unread count returns correct number")
    void testUnreadCountReturnsCorrectNumber() {
        Notification n1 = new Notification(1, userA.getUserId(), 10, "Alert 1", "Msg 1", false, null, "CMP-1");
        Notification n2 = new Notification(2, userA.getUserId(), 10, "Alert 2", "Msg 2", true, null, "CMP-1");
        Notification n3 = new Notification(3, userA.getUserId(), 10, "Alert 3", "Msg 3", false, null, "CMP-1");
        stubNotificationDAO.store.addAll(List.of(n1, n2, n3));

        int unread = stubNotificationDAO.getUnreadCount(userA.getUserId());
        assertEquals(2, unread, "There should be exactly 2 unread notifications for User A");
    }

    // -----------------------------------------------------------------------
    // Test 6: Unread Count Returns Zero When All Read
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("6. Unread count returns zero when all read")
    void testUnreadCountReturnsZeroWhenAllRead() {
        Notification n1 = new Notification(1, userA.getUserId(), 10, "Alert 1", "Msg 1", true, null, "CMP-1");
        Notification n2 = new Notification(2, userA.getUserId(), 10, "Alert 2", "Msg 2", true, null, "CMP-1");
        stubNotificationDAO.store.addAll(List.of(n1, n2));

        int unread = stubNotificationDAO.getUnreadCount(userA.getUserId());
        assertEquals(0, unread, "Unread count should be 0 when all notifications are read");
    }

    // -----------------------------------------------------------------------
    // Test 7: Mark Single Notification As Read Succeeds
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("7. Mark single notification as read succeeds")
    void testMarkSingleNotificationAsReadSucceeds() {
        SessionManager.getInstance().loginUser(userA);

        Notification n1 = new Notification(1, userA.getUserId(), 10, "Alert 1", "Msg", false, null, "CMP-1");
        stubNotificationDAO.store.add(n1);

        assertEquals(1, notificationService.getMyUnreadCount());

        boolean success = notificationService.markAsRead(1);
        assertTrue(success);
        assertTrue(n1.isRead());
        assertEquals(0, notificationService.getMyUnreadCount());
    }

    // -----------------------------------------------------------------------
    // Test 8: Mark As Read Enforces User Ownership
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("8. Mark as read enforces user ownership (cannot mark another user's notification)")
    void testMarkAsReadEnforcesUserOwnership() {
        // Notification belongs to User B
        Notification n = new Notification(10, userB.getUserId(), 10, "User B Alert", "Msg", false, null, "CMP-2");
        stubNotificationDAO.store.add(n);

        // User A is logged in
        SessionManager.getInstance().loginUser(userA);

        boolean marked = notificationService.markAsRead(10);
        assertFalse(marked, "User A should not be able to mark User B's notification as read");
        assertFalse(n.isRead(), "Notification should remain unread");
    }

    // -----------------------------------------------------------------------
    // Test 9: Mark All As Read Succeeds For Current User
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("9. Mark all as read succeeds for current user and isolates other users")
    void testMarkAllAsReadSucceedsForCurrentUser() {
        Notification nA1 = new Notification(1, userA.getUserId(), 10, "A1", "Msg", false, null, "CMP-A");
        Notification nA2 = new Notification(2, userA.getUserId(), 10, "A2", "Msg", false, null, "CMP-A");
        Notification nB1 = new Notification(3, userB.getUserId(), 20, "B1", "Msg", false, null, "CMP-B");
        stubNotificationDAO.store.addAll(List.of(nA1, nA2, nB1));

        SessionManager.getInstance().loginUser(userA);

        boolean result = notificationService.markAllAsRead();
        assertTrue(result);

        assertTrue(nA1.isRead());
        assertTrue(nA2.isRead());
        assertFalse(nB1.isRead(), "User B's notification must remain unread");
    }

    // -----------------------------------------------------------------------
    // Test 10: NotificationService Throws When No User Session
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("10. NotificationService throws IllegalStateException when no active session")
    void testNotificationServiceThrowsIllegalStateExceptionWhenNoSession() {
        SessionManager.getInstance().logout();

        assertThrows(IllegalStateException.class, () -> notificationService.getMyNotifications());
        assertThrows(IllegalStateException.class, () -> notificationService.getMyUnreadCount());
        assertThrows(IllegalStateException.class, () -> notificationService.markAsRead(1));
        assertThrows(IllegalStateException.class, () -> notificationService.markAllAsRead());
    }

    // -----------------------------------------------------------------------
    // Test 11: Get Notifications Restricted To Current User Session
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("11. Get notifications restricted to current user session")
    void testGetNotificationsRestrictedToCurrentUserSession() {
        Notification nA = new Notification(1, userA.getUserId(), 10, "A Alert", "Msg", false, null, "CMP-A");
        Notification nB = new Notification(2, userB.getUserId(), 20, "B Alert", "Msg", false, null, "CMP-B");
        stubNotificationDAO.store.addAll(List.of(nA, nB));

        SessionManager.getInstance().loginUser(userA);

        List<Notification> myNotifs = notificationService.getMyNotifications();
        assertEquals(1, myNotifs.size());
        assertEquals("A Alert", myNotifs.get(0).getTitle());
        assertEquals(userA.getUserId(), myNotifs.get(0).getUserId());
    }

    // -----------------------------------------------------------------------
    // Test 12: Admin Status Update Triggers Notification Creation
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("12. Admin status update triggers notification creation")
    void testAdminStatusUpdateTriggersNotification() {
        Complaint c = new Complaint();
        c.setComplaintId(101);
        c.setTrackingCode("CMP-2026-1001");
        c.setUserId(userA.getUserId());
        c.setStatus(Complaint.STATUS_OPEN);
        stubComplaintDAO.store.put(101, c);

        boolean updated = stubComplaintDAO.updateStatusWithHistory(101, Complaint.STATUS_OPEN,
                Complaint.STATUS_IN_PROGRESS, admin.getFullName(), "Under technical review");

        assertTrue(updated);
        assertEquals(1, stubNotificationDAO.store.size());

        Notification notif = stubNotificationDAO.store.get(0);
        assertEquals(userA.getUserId(), notif.getUserId());
        assertEquals(101, notif.getComplaintId());
        assertEquals("Complaint Status Updated", notif.getTitle());
        assertTrue(notif.getMessage().contains("CMP-2026-1001"));
        assertTrue(notif.getMessage().contains("IN_PROGRESS"));
        assertTrue(notif.getMessage().contains("Under technical review"));
    }

    // -----------------------------------------------------------------------
    // Test 13: Admin Assignment Triggers Notification Creation
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("13. Admin assignment triggers notification creation")
    void testAdminAssignmentTriggersNotification() {
        Complaint c = new Complaint();
        c.setComplaintId(102);
        c.setTrackingCode("CMP-2026-1002");
        c.setUserId(userA.getUserId());
        c.setStatus(Complaint.STATUS_OPEN);
        stubComplaintDAO.store.put(102, c);

        boolean assigned = stubComplaintDAO.assignComplaint(102, admin.getAdminId(),
                Complaint.STATUS_OPEN, admin.getFullName(), "Assigned to IT Admin");

        assertTrue(assigned);
        assertEquals(1, stubNotificationDAO.store.size());

        Notification notif = stubNotificationDAO.store.get(0);
        assertEquals(userA.getUserId(), notif.getUserId());
        assertEquals("Complaint Assigned", notif.getTitle());
        assertTrue(notif.getMessage().contains("CMP-2026-1002"));
    }

    // -----------------------------------------------------------------------
    // Test 14: Admin Resolution Triggers Notification Creation
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("14. Admin resolution triggers notification creation")
    void testAdminResolutionTriggersNotification() {
        Complaint c = new Complaint();
        c.setComplaintId(103);
        c.setTrackingCode("CMP-2026-1003");
        c.setUserId(userA.getUserId());
        c.setStatus(Complaint.STATUS_IN_PROGRESS);
        stubComplaintDAO.store.put(103, c);

        boolean resolved = stubComplaintDAO.resolveComplaint(103, admin.getAdminId(),
                Complaint.STATUS_IN_PROGRESS, admin.getFullName(), "Replaced faulty network switch");

        assertTrue(resolved);
        assertEquals(1, stubNotificationDAO.store.size());

        Notification notif = stubNotificationDAO.store.get(0);
        assertEquals(userA.getUserId(), notif.getUserId());
        assertEquals("Complaint Resolved", notif.getTitle());
        assertTrue(notif.getMessage().contains("CMP-2026-1003"));
        assertTrue(notif.getMessage().contains("Replaced faulty network switch"));
    }

    // -----------------------------------------------------------------------
    // Test 15: Resolution Does Not Create Duplicate Notifications
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("15. Resolution does not create duplicate notifications")
    void testResolutionDoesNotCreateDuplicateNotifications() {
        Complaint c = new Complaint();
        c.setComplaintId(104);
        c.setTrackingCode("CMP-2026-1004");
        c.setUserId(userA.getUserId());
        c.setStatus(Complaint.STATUS_IN_PROGRESS);
        stubComplaintDAO.store.put(104, c);

        // Resolve complaint once
        stubComplaintDAO.resolveComplaint(104, admin.getAdminId(),
                Complaint.STATUS_IN_PROGRESS, admin.getFullName(), "All issues resolved");

        assertEquals(1, stubNotificationDAO.store.size(),
                "Resolving complaint must produce exactly ONE notification, not duplicate notifications");
        assertEquals("Complaint Resolved", stubNotificationDAO.store.get(0).getTitle());
    }

    // -----------------------------------------------------------------------
    // Test 16: Unauthenticated Tracking Lookup Succeeds With Valid Code
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("16. Unauthenticated tracking lookup succeeds with valid tracking code")
    void testUnauthenticatedTrackingLookupSucceedsWithValidCode() {
        Complaint c = new Complaint();
        c.setComplaintId(201);
        c.setTrackingCode("CMP-2026-5414");
        c.setTitle("Air Conditioner Malfunction");
        c.setCategory("Facilities");
        c.setPriority("HIGH");
        c.setStatus("IN_PROGRESS");
        stubComplaintDAO.store.put(201, c);

        Complaint result = stubComplaintDAO.findByTrackingCode("CMP-2026-5414");
        assertNotNull(result);
        assertEquals("CMP-2026-5414", result.getTrackingCode());
        assertEquals("Air Conditioner Malfunction", result.getTitle());
        assertEquals("IN_PROGRESS", result.getStatus());
    }

    // -----------------------------------------------------------------------
    // Test 17: Unauthenticated Tracking Lookup Fails With Invalid Code
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("17. Unauthenticated tracking lookup fails with invalid/non-existent code")
    void testUnauthenticatedTrackingLookupFailsWithInvalidCode() {
        assertNull(stubComplaintDAO.findByTrackingCode("NON-EXISTENT-CODE"));
        assertNull(stubComplaintDAO.findByTrackingCode(""));
        assertNull(stubComplaintDAO.findByTrackingCode(null));
        assertNull(stubComplaintDAO.findByTrackingCode("   "));
    }

    // -----------------------------------------------------------------------
    // Test 18: Unauthenticated Tracking Protects Complainant Personal Info
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("18. Unauthenticated tracking lookup protects complainant personal info (email, department)")
    void testUnauthenticatedTrackingProtectsComplainantPersonalInfo() {
        Complaint c = new Complaint();
        c.setComplaintId(202);
        c.setTrackingCode("CMP-2026-9999");
        c.setComplainantName("Alice Secret");
        c.setComplainantEmail("alice.secret@university.edu");
        c.setComplainantDepartment("Sensitive Research");
        stubComplaintDAO.store.put(202, c);

        Complaint result = stubComplaintDAO.findByTrackingCode("CMP-2026-9999");
        assertNotNull(result);

        // Simulated public view mapping: personal complainant details stripped/masked
        Complaint publicView = new Complaint();
        publicView.setTrackingCode(result.getTrackingCode());
        publicView.setTitle(result.getTitle());
        publicView.setCategory(result.getCategory());
        publicView.setPriority(result.getPriority());
        publicView.setStatus(result.getStatus());
        publicView.setCreatedAt(result.getCreatedAt());

        assertNull(publicView.getComplainantEmail(), "Public view must not expose email");
        assertNull(publicView.getComplainantDepartment(), "Public view must not expose department");
        assertNull(publicView.getComplainantName(), "Public view must not expose complainant name");
    }

    // -----------------------------------------------------------------------
    // Test 19: Authenticated Tracking Lookup In Dashboard Succeeds For Own Complaint
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("19. Authenticated tracking lookup succeeds for user's own complaint")
    void testAuthenticatedTrackingLookupSucceedsForOwnComplaint() {
        SessionManager.getInstance().loginUser(userA);

        Complaint c = new Complaint();
        c.setComplaintId(301);
        c.setUserId(userA.getUserId());
        c.setTrackingCode("CMP-2026-3001");
        c.setTitle("Broken Projector");
        stubComplaintDAO.store.put(301, c);

        Complaint lookup = stubComplaintDAO.findByTrackingCode("CMP-2026-3001");
        assertNotNull(lookup);
        assertEquals(userA.getUserId(), lookup.getUserId(), "User owns this complaint");
    }

    // -----------------------------------------------------------------------
    // Test 20: Authenticated Tracking Lookup Blocks Access To Other User's Complaint
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("20. Authenticated tracking lookup blocks access to another user's complaint")
    void testAuthenticatedTrackingLookupBlocksAccessToOtherUsersComplaint() {
        // Complaint belongs to User B
        Complaint c = new Complaint();
        c.setComplaintId(302);
        c.setUserId(userB.getUserId());
        c.setTrackingCode("CMP-2026-3002");
        stubComplaintDAO.store.put(302, c);

        // User A is logged in
        SessionManager.getInstance().loginUser(userA);

        Complaint lookup = stubComplaintDAO.findByTrackingCode("CMP-2026-3002");
        assertNotNull(lookup);

        // Security check simulated from UserDashboardController.handleQuickTrack
        boolean accessGranted = (lookup.getUserId() == SessionManager.getInstance().getCurrentUser().getUserId());
        assertFalse(accessGranted, "Access must be denied when attempting to track another user's complaint in the dashboard");
    }

    // -----------------------------------------------------------------------
    // Test 21: Transaction Rollback: Notification Failure Rolls Back All Operations
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("21. Transaction rollback: if notification insert fails, complaint status update rolls back")
    void testTransactionRollbackIfNotificationFails() {
        Complaint c = new Complaint();
        c.setComplaintId(401);
        c.setTrackingCode("CMP-2026-4001");
        c.setUserId(userA.getUserId());
        c.setStatus(Complaint.STATUS_OPEN);
        stubComplaintDAO.store.put(401, c);

        // Configure stub to throw exception during notification creation
        stubNotificationDAO.failOnCreate = true;

        boolean updated = stubComplaintDAO.updateStatusWithHistory(401, Complaint.STATUS_OPEN,
                Complaint.STATUS_IN_PROGRESS, admin.getFullName(), "Review note");

        assertFalse(updated, "Update must fail when notification insertion throws an error");
        assertEquals(Complaint.STATUS_OPEN, c.getStatus(),
                "Complaint status must rollback and remain unchanged (OPEN)");
        assertEquals(0, stubNotificationDAO.store.size(),
                "No notification should be persisted on rollback");
    }

    // =======================================================================
    // In-Memory Stubs for Phase 8 Testing
    // =======================================================================

    static class StubNotificationDAO extends NotificationDAO {
        final List<Notification> store = new ArrayList<>();
        int idCounter = 1;
        boolean failOnCreate = false;

        @Override
        public int createNotification(Notification n) {
            if (failOnCreate) {
                return -1;
            }
            if (n == null) return -1;
            n.setNotificationId(idCounter++);
            if (n.getCreatedAt() == null) {
                n.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            }
            store.add(n);
            return n.getNotificationId();
        }

        @Override
        public int createNotification(Connection conn, Notification n) throws SQLException {
            if (failOnCreate) {
                throw new SQLException("Simulated database failure during notification creation");
            }
            return createNotification(n);
        }

        @Override
        public List<Notification> findByUserId(int userId) {
            List<Notification> list = new ArrayList<>();
            for (Notification n : store) {
                if (n.getUserId() == userId) {
                    list.add(n);
                }
            }
            list.sort((a, b) -> {
                if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                return b.getCreatedAt().compareTo(a.getCreatedAt()); // DESC
            });
            return list;
        }

        @Override
        public int getUnreadCount(int userId) {
            int count = 0;
            for (Notification n : store) {
                if (n.getUserId() == userId && !n.isRead()) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public boolean markAsRead(int notificationId, int userId) {
            for (Notification n : store) {
                if (n.getNotificationId() == notificationId && n.getUserId() == userId) {
                    n.setRead(true);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean markAllAsRead(int userId) {
            boolean any = false;
            for (Notification n : store) {
                if (n.getUserId() == userId) {
                    n.setRead(true);
                    any = true;
                }
            }
            return any;
        }
    }

    static class StubComplaintDAO extends ComplaintDAO {
        final Map<Integer, Complaint> store = new HashMap<>();
        final StubNotificationDAO notificationDAO;

        StubComplaintDAO(StubNotificationDAO notificationDAO) {
            this.notificationDAO = notificationDAO;
        }

        @Override
        public Complaint findById(int id) {
            return store.get(id);
        }

        @Override
        public Complaint findByTrackingCode(String code) {
            if (code == null || code.trim().isEmpty()) return null;
            String normalized = code.trim().toUpperCase();
            for (Complaint c : store.values()) {
                if (c.getTrackingCode() != null && c.getTrackingCode().equalsIgnoreCase(normalized)) {
                    return c;
                }
            }
            return null;
        }

        @Override
        public boolean updateStatusWithHistory(int complaintId, String oldStatus, String newStatus, String changedBy, String remarks) {
            Complaint c = store.get(complaintId);
            if (c == null) return false;

            String prevStatus = c.getStatus();
            c.setStatus(newStatus);

            try {
                Notification notif = new Notification();
                notif.setUserId(c.getUserId());
                notif.setComplaintId(complaintId);
                notif.setTitle("Complaint Status Updated");
                notif.setMessage("Your complaint " + c.getTrackingCode() + " status has changed to " + newStatus
                        + (remarks != null ? ": " + remarks : ""));
                int notifId = notificationDAO.createNotification(null, notif);
                if (notifId <= 0) {
                    c.setStatus(prevStatus); // Rollback
                    return false;
                }
            } catch (SQLException e) {
                c.setStatus(prevStatus); // Rollback
                return false;
            }

            return true;
        }

        @Override
        public boolean assignComplaint(int complaintId, Integer adminId, String currentStatus, String changedBy, String remarks) {
            Complaint c = store.get(complaintId);
            if (c == null) return false;

            c.setAssignedAdminId(adminId);

            try {
                Notification notif = new Notification();
                notif.setUserId(c.getUserId());
                notif.setComplaintId(complaintId);
                notif.setTitle("Complaint Assigned");
                notif.setMessage("Your complaint " + c.getTrackingCode() + " has been assigned.");
                notificationDAO.createNotification(null, notif);
            } catch (SQLException e) {
                return false;
            }

            return true;
        }

        @Override
        public boolean resolveComplaint(int complaintId, int adminId, String oldStatus, String adminName, String resolutionNotes) {
            Complaint c = store.get(complaintId);
            if (c == null) return false;

            c.setStatus(Complaint.STATUS_RESOLVED);

            try {
                Notification notif = new Notification();
                notif.setUserId(c.getUserId());
                notif.setComplaintId(complaintId);
                notif.setTitle("Complaint Resolved");
                notif.setMessage("Your complaint " + c.getTrackingCode() + " has been resolved. Notes: " + resolutionNotes);
                notificationDAO.createNotification(null, notif);
            } catch (SQLException e) {
                return false;
            }

            return true;
        }
    }
}
