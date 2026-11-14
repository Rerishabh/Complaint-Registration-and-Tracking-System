package com.complainttracking.service;

import com.complainttracking.dao.ComplaintDAO;
import com.complainttracking.dao.ComplaintHistoryDAO;
import com.complainttracking.model.Admin;
import com.complainttracking.model.Complaint;
import com.complainttracking.model.ComplaintHistory;
import com.complainttracking.util.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AdminComplaintService and admin complaint management.
 * Uses in-memory DAO stubs without requiring a live database connection.
 */
class AdminComplaintServiceTest {

    private AdminComplaintService adminService;
    private StubAdminComplaintDAO stubComplaintDAO;
    private StubAdminHistoryDAO stubHistoryDAO;
    private Admin testAdmin;

    // -----------------------------------------------------------------------
    // DAO Stubs
    // -----------------------------------------------------------------------

    static class StubAdminComplaintDAO extends ComplaintDAO {
        final List<Complaint> memoryStore = new ArrayList<>();

        @Override
        public List<Complaint> findAllComplaints() {
            return new ArrayList<>(memoryStore);
        }

        @Override
        public Map<String, Integer> getAdminDashboardStats() {
            Map<String, Integer> stats = new LinkedHashMap<>();
            int total = memoryStore.size();
            int open = 0, inProg = 0, resolved = 0, rejected = 0;

            for (Complaint c : memoryStore) {
                switch (c.getStatus()) {
                    case Complaint.STATUS_OPEN -> open++;
                    case Complaint.STATUS_IN_PROGRESS -> inProg++;
                    case Complaint.STATUS_RESOLVED -> resolved++;
                    case Complaint.STATUS_REJECTED -> rejected++;
                }
            }

            stats.put("TOTAL", total);
            stats.put("OPEN", open);
            stats.put("IN_PROGRESS", inProg);
            stats.put("RESOLVED", resolved);
            stats.put("REJECTED", rejected);
            return stats;
        }

        @Override
        public List<Complaint> searchComplaints(String keyword) {
            if (keyword == null || keyword.trim().isEmpty()) {
                return findAllComplaints();
            }
            String lower = keyword.trim().toLowerCase();
            List<Complaint> results = new ArrayList<>();
            for (Complaint c : memoryStore) {
                boolean match = (c.getTrackingCode() != null && c.getTrackingCode().toLowerCase().contains(lower))
                        || (c.getTitle() != null && c.getTitle().toLowerCase().contains(lower))
                        || (c.getComplainantName() != null && c.getComplainantName().toLowerCase().contains(lower))
                        || (c.getComplainantEmail() != null && c.getComplainantEmail().toLowerCase().contains(lower));
                if (match) {
                    results.add(c);
                }
            }
            return results;
        }

        @Override
        public List<Complaint> filterComplaints(String status, String category, String priority) {
            List<Complaint> results = new ArrayList<>();
            for (Complaint c : memoryStore) {
                boolean matchStatus = (status == null || "All".equalsIgnoreCase(status)
                        || "All Statuses".equalsIgnoreCase(status) || c.getStatus().equalsIgnoreCase(status));
                boolean matchCategory = (category == null || "All".equalsIgnoreCase(category)
                        || "All Categories".equalsIgnoreCase(category) || c.getCategory().equalsIgnoreCase(category));
                boolean matchPriority = (priority == null || "All".equalsIgnoreCase(priority)
                        || "All Priorities".equalsIgnoreCase(priority) || c.getPriority().equalsIgnoreCase(priority));

                if (matchStatus && matchCategory && matchPriority) {
                    results.add(c);
                }
            }
            return results;
        }
    }

    static class StubAdminHistoryDAO extends ComplaintHistoryDAO {
        final Map<Integer, List<ComplaintHistory>> historyStore = new HashMap<>();

        @Override
        public List<ComplaintHistory> findByComplaintId(int complaintId) {
            return historyStore.getOrDefault(complaintId, Collections.emptyList());
        }
    }

    // -----------------------------------------------------------------------
    // Setup
    // -----------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        SessionManager.getInstance().logout();
        stubComplaintDAO = new StubAdminComplaintDAO();
        stubHistoryDAO = new StubAdminHistoryDAO();
        adminService = new AdminComplaintService(stubComplaintDAO, stubHistoryDAO);

        testAdmin = new Admin(1, "admin", "hash", "System Admin", "superadmin", null);
        SessionManager.getInstance().loginAdmin(testAdmin);

        // Populate sample complaints with complainant details
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Complaint c1 = new Complaint(101, "CMP-2026-0001", 1, "WiFi outage in library",
                "Full description of wifi issue", "IT & Network", "HIGH", "OPEN", now, now,
                "John Doe", "john@college.edu", "Computer Science");

        Complaint c2 = new Complaint(102, "CMP-2026-0002", 2, "Broken window latch",
                "Full description of broken window", "Hostel & Facilities", "MEDIUM", "IN_PROGRESS", now, now,
                "Jane Smith", "jane@college.edu", "Civil Engineering");

        Complaint c3 = new Complaint(103, "CMP-2026-0003", 3, "Fee receipt discrepancy",
                "Full description of fee discrepancy", "Administration", "LOW", "RESOLVED", now, now,
                "Alex Brown", "alex@college.edu", "Administration");

        Complaint c4 = new Complaint(104, "CMP-2026-0004", 4, "Invalid complaint example",
                "Spam or invalid submission details", "Other", "LOW", "REJECTED", now, now,
                "Sam Wilson", "sam@college.edu", "Mechanical");

        stubComplaintDAO.memoryStore.addAll(List.of(c1, c2, c3, c4));

        // Sample history for c1
        ComplaintHistory h1 = new ComplaintHistory(1, 101, null, "OPEN", "John Doe", "Submitted", now);
        stubHistoryDAO.historyStore.put(101, List.of(h1));
    }

    // -----------------------------------------------------------------------
    // Session Validation Tests
    // -----------------------------------------------------------------------

    @Test
    void requireAdminSessionSucceedsWhenAdminLoggedIn() {
        assertDoesNotThrow(() -> adminService.requireAdminSession());
        assertEquals("admin", adminService.requireAdminSession().getUsername());
    }

    @Test
    void allAdminOperationsFailWithoutAdminSession() {
        SessionManager.getInstance().logout();

        assertThrows(IllegalStateException.class, () -> adminService.getDashboardStats());
        assertThrows(IllegalStateException.class, () -> adminService.getAllComplaints());
        assertThrows(IllegalStateException.class, () -> adminService.getRecentComplaints(5));
        assertThrows(IllegalStateException.class, () -> adminService.searchComplaints("WiFi"));
        assertThrows(IllegalStateException.class, () -> adminService.filterComplaints("OPEN", "IT & Network", "HIGH"));
        assertThrows(IllegalStateException.class, () -> adminService.getComplaintHistory(101));
    }

    // -----------------------------------------------------------------------
    // Dashboard Stats Tests
    // -----------------------------------------------------------------------

    @Test
    void getDashboardStatsReturnsCorrectCounts() {
        Map<String, Integer> stats = adminService.getDashboardStats();

        assertEquals(4, stats.get("TOTAL"));
        assertEquals(1, stats.get("OPEN"));
        assertEquals(1, stats.get("IN_PROGRESS"));
        assertEquals(1, stats.get("RESOLVED"));
        assertEquals(1, stats.get("REJECTED"));
    }

    // -----------------------------------------------------------------------
    // Complaint Retrieval Tests
    // -----------------------------------------------------------------------

    @Test
    void getAllComplaintsReturnsAllRecordsWithComplainant() {
        List<Complaint> all = adminService.getAllComplaints();
        assertEquals(4, all.size());
        assertEquals("John Doe", all.get(0).getComplainantName());
        assertEquals("john@college.edu", all.get(0).getComplainantEmail());
        assertEquals("Computer Science", all.get(0).getComplainantDepartment());
    }

    @Test
    void getRecentComplaintsRespectsLimit() {
        List<Complaint> recent2 = adminService.getRecentComplaints(2);
        assertEquals(2, recent2.size());

        List<Complaint> recent10 = adminService.getRecentComplaints(10);
        assertEquals(4, recent10.size());
    }

    // -----------------------------------------------------------------------
    // Search Functionality Tests
    // -----------------------------------------------------------------------

    @Test
    void searchByTrackingId() {
        List<Complaint> results = adminService.searchComplaints("CMP-2026-0001");
        assertEquals(1, results.size());
        assertEquals("WiFi outage in library", results.get(0).getTitle());
    }

    @Test
    void searchByTitleKeyword() {
        List<Complaint> results = adminService.searchComplaints("window");
        assertEquals(1, results.size());
        assertEquals("CMP-2026-0002", results.get(0).getTrackingCode());
    }

    @Test
    void searchByComplainantName() {
        List<Complaint> results = adminService.searchComplaints("Alex");
        assertEquals(1, results.size());
        assertEquals("Fee receipt discrepancy", results.get(0).getTitle());
    }

    @Test
    void searchByComplainantEmail() {
        List<Complaint> results = adminService.searchComplaints("sam@college.edu");
        assertEquals(1, results.size());
        assertEquals("Invalid complaint example", results.get(0).getTitle());
    }

    @Test
    void searchWithEmptyStringReturnsAll() {
        List<Complaint> results = adminService.searchComplaints("");
        assertEquals(4, results.size());
    }

    // -----------------------------------------------------------------------
    // Filter Functionality Tests
    // -----------------------------------------------------------------------

    @Test
    void filterByStatus() {
        List<Complaint> open = adminService.filterComplaints("OPEN", "All", "All");
        assertEquals(1, open.size());
        assertEquals("CMP-2026-0001", open.get(0).getTrackingCode());

        List<Complaint> rejected = adminService.filterComplaints("REJECTED", "All", "All");
        assertEquals(1, rejected.size());
        assertEquals("CMP-2026-0004", rejected.get(0).getTrackingCode());
    }

    @Test
    void filterByCategory() {
        List<Complaint> it = adminService.filterComplaints("All", "IT & Network", "All");
        assertEquals(1, it.size());
        assertEquals("WiFi outage in library", it.get(0).getTitle());
    }

    @Test
    void filterByPriority() {
        List<Complaint> high = adminService.filterComplaints("All", "All", "HIGH");
        assertEquals(1, high.size());
        assertEquals("CMP-2026-0001", high.get(0).getTrackingCode());
    }

    @Test
    void filterByCombinedCriteria() {
        List<Complaint> match = adminService.filterComplaints("IN_PROGRESS", "Hostel & Facilities", "MEDIUM");
        assertEquals(1, match.size());
        assertEquals("CMP-2026-0002", match.get(0).getTrackingCode());

        List<Complaint> noMatch = adminService.filterComplaints("RESOLVED", "IT & Network", "HIGH");
        assertTrue(noMatch.isEmpty());
    }

    // -----------------------------------------------------------------------
    // History Retrieval Test
    // -----------------------------------------------------------------------

    @Test
    void getComplaintHistoryReturnsTimeline() {
        List<ComplaintHistory> history = adminService.getComplaintHistory(101);
        assertEquals(1, history.size());
        assertEquals("OPEN", history.get(0).getNewStatus());
        assertEquals("John Doe", history.get(0).getChangedBy());
    }
}

