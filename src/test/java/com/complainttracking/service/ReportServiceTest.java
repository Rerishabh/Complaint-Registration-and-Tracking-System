package com.complainttracking.service;

import com.complainttracking.dao.ReportDAO;
import com.complainttracking.database.DatabaseConnection;
import com.complainttracking.model.Admin;
import com.complainttracking.model.DepartmentStat;
import com.complainttracking.model.ReportSummary;
import com.complainttracking.model.StaffPerformance;
import com.complainttracking.model.User;
import com.complainttracking.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated test suite for Phase 9:
 * - KPI calculations (Total, Open, In Progress, Resolved, Closed, Rejected, Resolution Rate, Avg Resolution Time)
 * - Zero complaint edge case handling
 * - Analytical aggregations (Status, Category, Monthly Trends, Department, Staff Performance)
 * - Date filtering logic (Start date only, End date only, Full range, End-date inclusivity, Invalid range rejection)
 * - CSV report export formatting, escaping, and UTF-8 encoding
 * - Security & administrator authorization enforcement
 */
class ReportServiceTest {

    private StubReportDAO stubReportDAO;
    private ReportService reportService;
    private Admin testAdmin;
    private User testUser;

    @BeforeEach
    void setUp() {
        SessionManager.getInstance().logout();
        stubReportDAO = new StubReportDAO();
        reportService = new ReportService(stubReportDAO);

        testAdmin = new Admin(1, "admin", "hash", "System Admin", "ADMIN", null);
        testUser = new User(10, "Regular User", "user@test.com", "hash", "1234567890", "Computer Science", null);
    }

    @AfterEach
    void tearDown() {
        SessionManager.getInstance().logout();
    }

    // -----------------------------------------------------------------------
    // Security & Authorization Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Security & Administrator Authorization Tests")
    class SecurityTests {

        @Test
        @DisplayName("Reporting is permitted when an authenticated administrator session exists")
        void testReportAllowedForAdmin() {
            SessionManager.getInstance().loginAdmin(testAdmin);
            assertDoesNotThrow(() -> reportService.getReportSummary(null, null));
        }

        @Test
        @DisplayName("Reporting is rejected with SecurityException when no session exists")
        void testReportRejectedWithoutSession() {
            assertFalse(SessionManager.getInstance().isAdminLoggedIn());
            SecurityException ex = assertThrows(SecurityException.class,
                    () -> reportService.getReportSummary(null, null));
            assertTrue(ex.getMessage().contains("Administrative privilege required"));
        }

        @Test
        @DisplayName("Reporting is rejected with SecurityException when a normal user is logged in")
        void testReportRejectedForNormalUser() {
            SessionManager.getInstance().loginUser(testUser);
            assertFalse(SessionManager.getInstance().isAdminLoggedIn());
            assertThrows(SecurityException.class, () -> reportService.getReportSummary(null, null));
        }

        @Test
        @DisplayName("CSV Export is rejected with SecurityException when not logged in as admin")
        void testExportRejectedWithoutAdmin() {
            ReportSummary summary = new ReportSummary();
            File tempFile = new File(System.getProperty("java.io.tmpdir"), "test_export.csv");
            assertThrows(SecurityException.class, () -> reportService.exportReportToCsv(summary, tempFile));
        }
    }

    // -----------------------------------------------------------------------
    // Date Range Filtering Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Date Range Filtering Tests")
    class DateRangeTests {

        @BeforeEach
        void login() {
            SessionManager.getInstance().loginAdmin(testAdmin);
        }

        @Test
        @DisplayName("Invalid date range where startDate > endDate throws IllegalArgumentException")
        void testInvalidDateRangeThrows() {
            LocalDate start = LocalDate.of(2026, 11, 20);
            LocalDate end   = LocalDate.of(2026, 11, 10);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> reportService.getReportSummary(start, end));
            assertTrue(ex.getMessage().contains("cannot be after end date"));
        }

        @Test
        @DisplayName("Valid date range passes start and end dates to DAO correctly")
        void testValidFullDateRange() {
            LocalDate start = LocalDate.of(2026, 11, 1);
            LocalDate end   = LocalDate.of(2026, 11, 14);

            ReportSummary summary = reportService.getReportSummary(start, end);
            assertNotNull(summary);
            assertEquals(start, stubReportDAO.lastStartDate);
            assertEquals(end, stubReportDAO.lastEndDate);
            assertEquals(start, summary.getStartDate());
            assertEquals(end, summary.getEndDate());
        }

        @Test
        @DisplayName("Start-date-only filter is supported and passed to DAO")
        void testStartDateOnlyFilter() {
            LocalDate start = LocalDate.of(2026, 11, 1);
            ReportSummary summary = reportService.getReportSummary(start, null);
            assertEquals(start, stubReportDAO.lastStartDate);
            assertNull(stubReportDAO.lastEndDate);
            assertEquals(start, summary.getStartDate());
            assertNull(summary.getEndDate());
        }

        @Test
        @DisplayName("End-date-only filter is supported and passed to DAO")
        void testEndDateOnlyFilter() {
            LocalDate end = LocalDate.of(2026, 11, 30);
            ReportSummary summary = reportService.getReportSummary(null, end);
            assertNull(stubReportDAO.lastStartDate);
            assertEquals(end, stubReportDAO.lastEndDate);
            assertNull(summary.getStartDate());
            assertEquals(end, summary.getEndDate());
        }

        @Test
        @DisplayName("Null date bounds returns all available history")
        void testNullDateBounds() {
            ReportSummary summary = reportService.getReportSummary(null, null);
            assertNull(stubReportDAO.lastStartDate);
            assertNull(stubReportDAO.lastEndDate);
            assertNull(summary.getStartDate());
            assertNull(summary.getEndDate());
        }
    }

    // -----------------------------------------------------------------------
    // KPI Calculations & Analytical Aggregations Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("KPI Calculations & Aggregations Tests")
    class KpiAndAggregationTests {

        @BeforeEach
        void login() {
            SessionManager.getInstance().loginAdmin(testAdmin);
        }

        @Test
        @DisplayName("Resolution rate calculation formula: (Resolved / Total) * 100")
        void testResolutionRateFormula() {
            assertEquals(75.0, ReportSummary.calculateResolutionRate(3, 4));
            assertEquals(50.0, ReportSummary.calculateResolutionRate(1, 2));
            assertEquals(100.0, ReportSummary.calculateResolutionRate(10, 10));
            assertEquals(33.33, ReportSummary.calculateResolutionRate(1, 3));
        }

        @Test
        @DisplayName("Resolution rate safely returns 0.0 when total complaints is zero (avoids division by zero)")
        void testResolutionRateZeroComplaints() {
            assertEquals(0.0, ReportSummary.calculateResolutionRate(0, 0));
            assertEquals(0.0, ReportSummary.calculateResolutionRate(5, 0));
            assertEquals(0.0, ReportSummary.calculateResolutionRate(0, -1));
        }

        @Test
        @DisplayName("Pending complaints is the sum of open and in-progress complaints")
        void testPendingComplaintsCalculation() {
            ReportSummary summary = new ReportSummary();
            summary.setOpenComplaints(4);
            summary.setInProgressComplaints(3);
            assertEquals(7, summary.getPendingComplaints());
        }

        @Test
        @DisplayName("Status distribution contains all 5 supported statuses: OPEN, IN_PROGRESS, RESOLVED, CLOSED, REJECTED")
        void testStatusDistributionContainsAllStatuses() {
            ReportSummary summary = reportService.getReportSummary(null, null);
            Map<String, Integer> dist = summary.getStatusDistribution();

            assertTrue(dist.containsKey("OPEN"));
            assertTrue(dist.containsKey("IN_PROGRESS"));
            assertTrue(dist.containsKey("RESOLVED"));
            assertTrue(dist.containsKey("CLOSED"));
            assertTrue(dist.containsKey("REJECTED"));
            assertEquals(5, dist.size());
        }

        @Test
        @DisplayName("Category distribution aggregates complaints per category")
        void testCategoryDistribution() {
            ReportSummary summary = reportService.getReportSummary(null, null);
            Map<String, Integer> catDist = summary.getCategoryDistribution();

            assertNotNull(catDist);
            assertEquals(12, catDist.get("Hardware"));
            assertEquals(8, catDist.get("Network"));
            assertEquals(4, catDist.get("Facility"));
        }

        @Test
        @DisplayName("Monthly trends aggregate complaint registrations chronologically")
        void testMonthlyTrends() {
            ReportSummary summary = reportService.getReportSummary(null, null);
            Map<String, Integer> trends = summary.getMonthlyTrends();

            assertNotNull(trends);
            List<String> months = new ArrayList<>(trends.keySet());
            assertEquals("2026-09", months.get(0));
            assertEquals("2026-10", months.get(1));
            assertEquals("2026-11", months.get(2));
        }

        @Test
        @DisplayName("Department statistics contain total, resolved, and pending counts per department")
        void testDepartmentStatistics() {
            ReportSummary summary = reportService.getReportSummary(null, null);
            List<DepartmentStat> deptStats = summary.getDepartmentStats();

            assertNotNull(deptStats);
            assertEquals(2, deptStats.size());

            DepartmentStat cs = deptStats.get(0);
            assertEquals("Computer Science", cs.getDepartmentName());
            assertEquals(15, cs.getTotalComplaints());
            assertEquals(10, cs.getResolvedComplaints());
            assertEquals(5, cs.getPendingComplaints());
        }

        @Test
        @DisplayName("Staff performance metrics include assigned, resolved, rate, and avg resolution time")
        void testStaffPerformance() {
            ReportSummary summary = reportService.getReportSummary(null, null);
            List<StaffPerformance> staffList = summary.getStaffPerformance();

            assertNotNull(staffList);
            assertEquals(2, staffList.size());

            StaffPerformance sp1 = staffList.get(0);
            assertEquals("Alice Admin", sp1.getStaffName());
            assertEquals("ADMIN", sp1.getRole());
            assertEquals(10, sp1.getAssignedComplaints());
            assertEquals(8, sp1.getResolvedComplaints());
            assertEquals(80.0, sp1.getResolutionRate());
            assertEquals(12.5, sp1.getAvgResolutionTimeHours());
        }
    }

    // -----------------------------------------------------------------------
    // CSV Export Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("CSV Export Formatting & Escaping Tests")
    class CsvExportTests {

        @BeforeEach
        void login() {
            SessionManager.getInstance().loginAdmin(testAdmin);
        }

        @Test
        @DisplayName("CSV export produces UTF-8 encoded file with all required report sections")
        void testExportReportToCsv() throws IOException {
            ReportSummary summary = reportService.getReportSummary(null, null);
            File tempFile = File.createTempFile("crts_test_report_", ".csv");
            tempFile.deleteOnExit();

            reportService.exportReportToCsv(summary, tempFile);

            assertTrue(tempFile.exists());
            assertTrue(tempFile.length() > 0);

            String content = Files.readString(tempFile.toPath(), StandardCharsets.UTF_8);

            // Verify Headers and Sections
            assertTrue(content.contains("CRTS ADMINISTRATIVE ANALYTICAL REPORT"));
            assertTrue(content.contains("Reporting Period,All Time (Complete History)"));
            assertTrue(content.contains("--- OVERALL KPI SUMMARY ---"));
            assertTrue(content.contains("Total Complaints,24"));
            assertTrue(content.contains("Overall Resolution Rate (%),62.50%"));
            assertTrue(content.contains("Average Resolution Time (hours),15.30 hrs"));
            assertTrue(content.contains("--- COMPLAINTS BY STATUS ---"));
            assertTrue(content.contains("OPEN,4"));
            assertTrue(content.contains("IN_PROGRESS,3"));
            assertTrue(content.contains("RESOLVED,10"));
            assertTrue(content.contains("CLOSED,5"));
            assertTrue(content.contains("REJECTED,2"));
            assertTrue(content.contains("--- CATEGORY-WISE DISTRIBUTION ---"));
            assertTrue(content.contains("Hardware,12"));
            assertTrue(content.contains("--- MONTHLY REGISTRATION TRENDS ---"));
            assertTrue(content.contains("2026-11,14"));
            assertTrue(content.contains("--- DEPARTMENT-WISE STATISTICS ---"));
            assertTrue(content.contains("Computer Science,15,10,5"));
            assertTrue(content.contains("--- STAFF PERFORMANCE METRICS ---"));
            assertTrue(content.contains("Alice Admin,ADMIN,10,8,80.00%,12.50"));
        }

        @Test
        @DisplayName("CSV escaping properly handles commas, quotation marks, and line breaks")
        void testCsvEscaping() {
            assertEquals("Simple", reportService.escapeCsv("Simple"));
            assertEquals("\"Hello, World\"", reportService.escapeCsv("Hello, World"));
            assertEquals("\"He said \"\"Yes\"\"\"", reportService.escapeCsv("He said \"Yes\""));
            assertEquals("\"Line 1\nLine 2\"", reportService.escapeCsv("Line 1\nLine 2"));
            assertEquals("", reportService.escapeCsv(null));
        }

        @Test
        @DisplayName("Export with null summary or destination throws IllegalArgumentException")
        void testExportValidation() {
            File tempFile = new File(System.getProperty("java.io.tmpdir"), "test.csv");
            assertThrows(IllegalArgumentException.class, () -> reportService.exportReportToCsv(null, tempFile));
            assertThrows(IllegalArgumentException.class, () -> reportService.exportReportToCsv(new ReportSummary(), null));
        }
    }

    // -----------------------------------------------------------------------
    // Database Integration Tests (Non-Destructive)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Database Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Direct ReportDAO execution against MySQL (if reachable) without modifying any data")
        void testLiveDatabaseReportGeneration() {
            if (!DatabaseConnection.testConnection()) {
                System.out.println("Live database not reachable, skipping live DB integration test.");
                return;
            }

            ReportDAO liveDAO = new ReportDAO();
            ReportSummary summary = liveDAO.generateReport(null, null);

            assertNotNull(summary);
            assertTrue(summary.getTotalComplaints() >= 0);
            assertTrue(summary.getResolutionRate() >= 0.0 && summary.getResolutionRate() <= 100.0);
            assertNotNull(summary.getStatusDistribution());
            assertEquals(5, summary.getStatusDistribution().size());
            assertNotNull(summary.getCategoryDistribution());
            assertNotNull(summary.getMonthlyTrends());
            assertNotNull(summary.getDepartmentStats());
            assertNotNull(summary.getStaffPerformance());
        }
    }

    // -----------------------------------------------------------------------
    // In-Memory Stub DAO for Isolated Unit Testing
    // -----------------------------------------------------------------------

    private static class StubReportDAO extends ReportDAO {
        LocalDate lastStartDate;
        LocalDate lastEndDate;

        @Override
        public ReportSummary generateReport(LocalDate startDate, LocalDate endDate) {
            this.lastStartDate = startDate;
            this.lastEndDate = endDate;

            ReportSummary summary = new ReportSummary();
            summary.setStartDate(startDate);
            summary.setEndDate(endDate);

            summary.setTotalComplaints(24);
            summary.setOpenComplaints(4);
            summary.setInProgressComplaints(3);
            summary.setResolvedComplaints(10);
            summary.setClosedComplaints(5);
            summary.setRejectedComplaints(2);
            summary.setResolutionRate(ReportSummary.calculateResolutionRate(15, 24)); // (15 / 24) * 100 = 62.5%
            summary.setAvgResolutionTimeHours(15.3);

            Map<String, Integer> statusMap = new LinkedHashMap<>();
            statusMap.put("OPEN", 4);
            statusMap.put("IN_PROGRESS", 3);
            statusMap.put("RESOLVED", 10);
            statusMap.put("CLOSED", 5);
            statusMap.put("REJECTED", 2);
            summary.setStatusDistribution(statusMap);

            Map<String, Integer> catMap = new LinkedHashMap<>();
            catMap.put("Hardware", 12);
            catMap.put("Network", 8);
            catMap.put("Facility", 4);
            summary.setCategoryDistribution(catMap);

            Map<String, Integer> trendMap = new LinkedHashMap<>();
            trendMap.put("2026-09", 4);
            trendMap.put("2026-10", 6);
            trendMap.put("2026-11", 14);
            summary.setMonthlyTrends(trendMap);

            List<DepartmentStat> deptStats = new ArrayList<>();
            deptStats.add(new DepartmentStat("Computer Science", 15, 10, 5));
            deptStats.add(new DepartmentStat("Electrical Engineering", 9, 5, 4));
            summary.setDepartmentStats(deptStats);

            List<StaffPerformance> staffList = new ArrayList<>();
            staffList.add(new StaffPerformance(1, "Alice Admin", "ADMIN", 10, 8, 80.0, 12.5));
            staffList.add(new StaffPerformance(2, "Bob Support", "STAFF", 14, 7, 50.0, 18.2));
            summary.setStaffPerformance(staffList);

            return summary;
        }
    }
}
