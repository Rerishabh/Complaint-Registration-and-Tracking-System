package com.complainttracking.service;

import com.complainttracking.dao.ReportDAO;
import com.complainttracking.model.DepartmentStat;
import com.complainttracking.model.ReportSummary;
import com.complainttracking.model.StaffPerformance;
import com.complainttracking.util.SessionManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ReportService - Business logic and authorization layer for analytical reports and CSV exports.
 */
public class ReportService {

    private static final Logger LOGGER = Logger.getLogger(ReportService.class.getName());
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReportDAO reportDAO;

    public ReportService() {
        this(new ReportDAO());
    }

    public ReportService(ReportDAO reportDAO) {
        this.reportDAO = reportDAO;
    }

    /**
     * Enforces active administrator session requirement.
     *
     * @throws SecurityException if an admin is not currently logged in
     */
    private void requireAdminSession() {
        if (!SessionManager.getInstance().isAdminLoggedIn()) {
            throw new SecurityException("Unauthorized: Administrative privilege required to access analytical reports.");
        }
    }

    /**
     * Validates that start date does not exceed end date.
     *
     * @throws IllegalArgumentException if startDate is after endDate
     */
    public void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Invalid date range: Start date (" + startDate + ") cannot be after end date (" + endDate + ").");
        }
    }

    /**
     * Generates a complete ReportSummary within the given date bounds.
     *
     * @param startDate optional start date (inclusive)
     * @param endDate   optional end date (inclusive)
     * @return populated ReportSummary
     * @throws SecurityException        if unauthorized
     * @throws IllegalArgumentException if date range is invalid
     */
    public ReportSummary getReportSummary(LocalDate startDate, LocalDate endDate) {
        requireAdminSession();
        validateDateRange(startDate, endDate);

        LOGGER.log(Level.INFO, "Generating analytical report summary for period: {0} to {1}",
                new Object[]{startDate != null ? startDate : "Beginning", endDate != null ? endDate : "Present"});

        return reportDAO.generateReport(startDate, endDate);
    }

    /**
     * Exports the given ReportSummary to a properly formatted, UTF-8 encoded CSV file.
     *
     * @param summary     populated ReportSummary to export
     * @param destination target file chosen by administrator
     * @throws IOException       if writing to destination fails
     * @throws SecurityException if unauthorized
     */
    public void exportReportToCsv(ReportSummary summary, File destination) throws IOException {
        requireAdminSession();

        if (summary == null) {
            throw new IllegalArgumentException("Report summary cannot be null for export.");
        }
        if (destination == null) {
            throw new IllegalArgumentException("Destination file cannot be null.");
        }

        LOGGER.log(Level.INFO, "Exporting analytical report to CSV file: {0}", destination.getAbsolutePath());

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(destination), StandardCharsets.UTF_8))) {

            // UTF-8 BOM for Microsoft Excel compatibility
            writer.write('\ufeff');

            // 1. Report Title & Header
            writer.write("CRTS ADMINISTRATIVE ANALYTICAL REPORT\n");
            writer.write("Generated At," + escapeCsv(summary.getGeneratedAt() != null ? summary.getGeneratedAt().toString() : "") + "\n");

            String periodStr;
            if (summary.getStartDate() != null && summary.getEndDate() != null) {
                periodStr = summary.getStartDate().format(DATE_FMT) + " to " + summary.getEndDate().format(DATE_FMT);
            } else if (summary.getStartDate() != null) {
                periodStr = "From " + summary.getStartDate().format(DATE_FMT) + " onwards";
            } else if (summary.getEndDate() != null) {
                periodStr = "Up to " + summary.getEndDate().format(DATE_FMT);
            } else {
                periodStr = "All Time (Complete History)";
            }
            writer.write("Reporting Period," + escapeCsv(periodStr) + "\n\n");

            // 2. Executive KPI Metrics
            writer.write("--- OVERALL KPI SUMMARY ---\n");
            writer.write("Metric,Value\n");
            writer.write("Total Complaints," + summary.getTotalComplaints() + "\n");
            writer.write("Open Complaints," + summary.getOpenComplaints() + "\n");
            writer.write("In Progress Complaints," + summary.getInProgressComplaints() + "\n");
            writer.write("Pending Complaints (Open + In Progress)," + summary.getPendingComplaints() + "\n");
            writer.write("Resolved Complaints," + summary.getResolvedComplaints() + "\n");
            writer.write("Closed Complaints," + summary.getClosedComplaints() + "\n");
            writer.write("Rejected Complaints," + summary.getRejectedComplaints() + "\n");
            writer.write("Overall Resolution Rate (%)," + String.format("%.2f%%", summary.getResolutionRate()) + "\n");
            writer.write("Average Resolution Time (hours)," + String.format("%.2f hrs", summary.getAvgResolutionTimeHours()) + "\n\n");

            // 3. Status Distribution
            writer.write("--- COMPLAINTS BY STATUS ---\n");
            writer.write("Status,Count\n");
            for (Map.Entry<String, Integer> entry : summary.getStatusDistribution().entrySet()) {
                writer.write(escapeCsv(entry.getKey()) + "," + entry.getValue() + "\n");
            }
            writer.write("\n");

            // 4. Category Distribution
            writer.write("--- CATEGORY-WISE DISTRIBUTION ---\n");
            writer.write("Category,Count\n");
            for (Map.Entry<String, Integer> entry : summary.getCategoryDistribution().entrySet()) {
                writer.write(escapeCsv(entry.getKey()) + "," + entry.getValue() + "\n");
            }
            writer.write("\n");

            // 5. Monthly Registration Trends
            writer.write("--- MONTHLY REGISTRATION TRENDS ---\n");
            writer.write("Month (YYYY-MM),Registrations\n");
            for (Map.Entry<String, Integer> entry : summary.getMonthlyTrends().entrySet()) {
                writer.write(escapeCsv(entry.getKey()) + "," + entry.getValue() + "\n");
            }
            writer.write("\n");

            // 6. Department-Wise Statistics
            writer.write("--- DEPARTMENT-WISE STATISTICS ---\n");
            writer.write("Department,Total Complaints,Resolved Complaints,Pending Complaints\n");
            for (DepartmentStat stat : summary.getDepartmentStats()) {
                writer.write(escapeCsv(stat.getDepartmentName()) + "," +
                        stat.getTotalComplaints() + "," +
                        stat.getResolvedComplaints() + "," +
                        stat.getPendingComplaints() + "\n");
            }
            writer.write("\n");

            // 7. Staff Performance Metrics
            writer.write("--- STAFF PERFORMANCE METRICS ---\n");
            writer.write("Staff Name,Role,Assigned Complaints,Resolved Complaints,Resolution Rate (%),Avg Resolution Time (hours)\n");
            for (StaffPerformance sp : summary.getStaffPerformance()) {
                writer.write(escapeCsv(sp.getStaffName()) + "," +
                        escapeCsv(sp.getRole()) + "," +
                        sp.getAssignedComplaints() + "," +
                        sp.getResolvedComplaints() + "," +
                        String.format("%.2f%%", sp.getResolutionRate()) + "," +
                        String.format("%.2f", sp.getAvgResolutionTimeHours()) + "\n");
            }
            writer.write("\n");
        }
    }

    /**
     * Escapes standard CSV fields: quotes strings containing commas, quotes, or newlines,
     * and doubles any existing double-quotes.
     */
    public String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
