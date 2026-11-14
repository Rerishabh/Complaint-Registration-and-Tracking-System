package com.complainttracking.dao;

import com.complainttracking.database.DatabaseConnection;
import com.complainttracking.model.DepartmentStat;
import com.complainttracking.model.ReportSummary;
import com.complainttracking.model.StaffPerformance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ReportDAO - Data Access Object for administrative analytics, metrics, and chart datasets.
 */
public class ReportDAO {

    private static final Logger LOGGER = Logger.getLogger(ReportDAO.class.getName());

    /**
     * Generates a complete ReportSummary containing all KPIs, distributions, trends,
     * department statistics, and staff performance metrics within the specified date range.
     *
     * @param startDate optional start date (inclusive)
     * @param endDate   optional end date (inclusive)
     * @return fully populated ReportSummary
     */
    public ReportSummary generateReport(LocalDate startDate, LocalDate endDate) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            ReportSummary summary = new ReportSummary();
            summary.setStartDate(startDate);
            summary.setEndDate(endDate);

            populateKpis(conn, startDate, endDate, summary);
            summary.setStatusDistribution(getStatusDistribution(conn, startDate, endDate));
            summary.setCategoryDistribution(getCategoryDistribution(conn, startDate, endDate));
            summary.setMonthlyTrends(getMonthlyTrends(conn, startDate, endDate));
            summary.setDepartmentStats(getDepartmentStatistics(conn, startDate, endDate));
            summary.setStaffPerformance(getStaffPerformance(conn, startDate, endDate));

            return summary;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error generating report summary", e);
            throw new RuntimeException("Database error while generating analytical report.", e);
        }
    }

    /**
     * Populates overall KPI metrics: total, status-specific counts, resolution rate,
     * and average resolution time in hours.
     */
    public void populateKpis(Connection conn, LocalDate startDate, LocalDate endDate, ReportSummary summary) throws SQLException {
        StringBuilder countSql = new StringBuilder(
            "SELECT " +
            "  COUNT(*) AS total, " +
            "  SUM(CASE WHEN c.status = 'OPEN'        THEN 1 ELSE 0 END) AS open_count, " +
            "  SUM(CASE WHEN c.status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress_count, " +
            "  SUM(CASE WHEN c.status = 'RESOLVED'    THEN 1 ELSE 0 END) AS resolved_count, " +
            "  SUM(CASE WHEN c.status = 'CLOSED'      THEN 1 ELSE 0 END) AS closed_count, " +
            "  SUM(CASE WHEN c.status = 'REJECTED'    THEN 1 ELSE 0 END) AS rejected_count " +
            "FROM complaints c WHERE 1=1"
        );
        appendDateFilter(countSql, "c.created_at", startDate, endDate);

        try (PreparedStatement ps = conn.prepareStatement(countSql.toString())) {
            bindDateFilter(ps, 1, startDate, endDate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    int open = rs.getInt("open_count");
                    int inProgress = rs.getInt("in_progress_count");
                    int resolved = rs.getInt("resolved_count");
                    int closed = rs.getInt("closed_count");
                    int rejected = rs.getInt("rejected_count");

                    summary.setTotalComplaints(total);
                    summary.setOpenComplaints(open);
                    summary.setInProgressComplaints(inProgress);
                    summary.setResolvedComplaints(resolved);
                    summary.setClosedComplaints(closed);
                    summary.setRejectedComplaints(rejected);
                    summary.setResolutionRate(ReportSummary.calculateResolutionRate(resolved, total));
                }
            }
        }

        // Average Resolution Time (hours): (resolved_at - created_at) for resolved complaints
        StringBuilder avgSql = new StringBuilder(
            "SELECT AVG(TIMESTAMPDIFF(SECOND, c.created_at, r.resolved_at)) / 3600.0 AS avg_res_hours " +
            "FROM complaints c " +
            "JOIN resolutions r ON c.complaint_id = r.complaint_id " +
            "WHERE 1=1"
        );
        appendDateFilter(avgSql, "c.created_at", startDate, endDate);

        try (PreparedStatement ps = conn.prepareStatement(avgSql.toString())) {
            bindDateFilter(ps, 1, startDate, endDate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double avgHours = rs.getDouble("avg_res_hours");
                    if (rs.wasNull()) {
                        summary.setAvgResolutionTimeHours(0.0);
                    } else {
                        summary.setAvgResolutionTimeHours(Math.round(avgHours * 100.0) / 100.0);
                    }
                } else {
                    summary.setAvgResolutionTimeHours(0.0);
                }
            }
        }
    }

    /**
     * Returns distribution across all 5 statuses: OPEN, IN_PROGRESS, RESOLVED, CLOSED, REJECTED.
     */
    public Map<String, Integer> getStatusDistribution(Connection conn, LocalDate startDate, LocalDate endDate) throws SQLException {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("OPEN", 0);
        distribution.put("IN_PROGRESS", 0);
        distribution.put("RESOLVED", 0);
        distribution.put("CLOSED", 0);
        distribution.put("REJECTED", 0);

        StringBuilder sql = new StringBuilder(
            "SELECT c.status, COUNT(*) AS cnt " +
            "FROM complaints c WHERE 1=1"
        );
        appendDateFilter(sql, "c.created_at", startDate, endDate);
        sql.append(" GROUP BY c.status");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindDateFilter(ps, 1, startDate, endDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    int count = rs.getInt("cnt");
                    if (status != null) {
                        distribution.put(status.toUpperCase(), count);
                    }
                }
            }
        }
        return distribution;
    }

    /**
     * Returns category-wise complaint counts ordered by count DESC.
     */
    public Map<String, Integer> getCategoryDistribution(Connection conn, LocalDate startDate, LocalDate endDate) throws SQLException {
        Map<String, Integer> distribution = new LinkedHashMap<>();

        StringBuilder sql = new StringBuilder(
            "SELECT c.category, COUNT(*) AS cnt " +
            "FROM complaints c WHERE 1=1"
        );
        appendDateFilter(sql, "c.created_at", startDate, endDate);
        sql.append(" GROUP BY c.category ORDER BY cnt DESC, c.category ASC");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindDateFilter(ps, 1, startDate, endDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String category = rs.getString("category");
                    int count = rs.getInt("cnt");
                    if (category != null) {
                        distribution.put(category, count);
                    }
                }
            }
        }
        return distribution;
    }

    /**
     * Returns monthly registration trends (YYYY-MM -> count) in chronological order.
     */
    public Map<String, Integer> getMonthlyTrends(Connection conn, LocalDate startDate, LocalDate endDate) throws SQLException {
        Map<String, Integer> trends = new LinkedHashMap<>();

        StringBuilder sql = new StringBuilder(
            "SELECT DATE_FORMAT(c.created_at, '%Y-%m') AS month_key, COUNT(*) AS cnt " +
            "FROM complaints c WHERE 1=1"
        );
        appendDateFilter(sql, "c.created_at", startDate, endDate);
        sql.append(" GROUP BY DATE_FORMAT(c.created_at, '%Y-%m') ORDER BY month_key ASC");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindDateFilter(ps, 1, startDate, endDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String monthKey = rs.getString("month_key");
                    int count = rs.getInt("cnt");
                    if (monthKey != null) {
                        trends.put(monthKey, count);
                    }
                }
            }
        }
        return trends;
    }

    /**
     * Returns department-wise complaint statistics (Total, Resolved, Pending).
     */
    public List<DepartmentStat> getDepartmentStatistics(Connection conn, LocalDate startDate, LocalDate endDate) throws SQLException {
        List<DepartmentStat> list = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
            "SELECT " +
            "  COALESCE(NULLIF(TRIM(u.department), ''), 'Unassigned') AS dept_name, " +
            "  COUNT(*) AS total, " +
            "  SUM(CASE WHEN c.status IN ('RESOLVED', 'CLOSED') THEN 1 ELSE 0 END) AS resolved_count, " +
            "  SUM(CASE WHEN c.status IN ('OPEN', 'IN_PROGRESS') THEN 1 ELSE 0 END) AS pending_count " +
            "FROM complaints c " +
            "JOIN users u ON c.user_id = u.user_id " +
            "WHERE 1=1"
        );
        appendDateFilter(sql, "c.created_at", startDate, endDate);
        sql.append(" GROUP BY COALESCE(NULLIF(TRIM(u.department), ''), 'Unassigned') ORDER BY total DESC, dept_name ASC");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindDateFilter(ps, 1, startDate, endDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String dept = rs.getString("dept_name");
                    int total = rs.getInt("total");
                    int resolved = rs.getInt("resolved_count");
                    int pending = rs.getInt("pending_count");
                    list.add(new DepartmentStat(dept, total, resolved, pending));
                }
            }
        }
        return list;
    }

    /**
     * Returns staff-wise performance metrics (assigned, resolved, resolution rate, avg resolution time).
     */
    public List<StaffPerformance> getStaffPerformance(Connection conn, LocalDate startDate, LocalDate endDate) throws SQLException {
        List<StaffPerformance> list = new ArrayList<>();

        StringBuilder onClause = new StringBuilder("ON a.admin_id = c.assigned_admin_id");
        if (startDate != null) {
            onClause.append(" AND c.created_at >= ?");
        }
        if (endDate != null) {
            onClause.append(" AND c.created_at < ?");
        }

        String sql =
            "SELECT " +
            "  a.admin_id, " +
            "  a.full_name, " +
            "  a.role, " +
            "  COUNT(DISTINCT c.complaint_id) AS assigned_count, " +
            "  COUNT(DISTINCT CASE WHEN c.status IN ('RESOLVED', 'CLOSED') THEN c.complaint_id ELSE NULL END) AS resolved_count, " +
            "  AVG(CASE WHEN r.resolved_at IS NOT NULL THEN TIMESTAMPDIFF(SECOND, c.created_at, r.resolved_at) / 3600.0 ELSE NULL END) AS avg_res_time " +
            "FROM admins a " +
            "LEFT JOIN complaints c " + onClause.toString() + " " +
            "LEFT JOIN resolutions r ON c.complaint_id = r.complaint_id " +
            "GROUP BY a.admin_id, a.full_name, a.role " +
            "ORDER BY assigned_count DESC, a.full_name ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDateFilter(ps, 1, startDate, endDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int adminId = rs.getInt("admin_id");
                    String name = rs.getString("full_name");
                    String role = rs.getString("role");
                    int assigned = rs.getInt("assigned_count");
                    int resolved = rs.getInt("resolved_count");
                    double avgTime = rs.getDouble("avg_res_time");
                    if (rs.wasNull()) {
                        avgTime = 0.0;
                    } else {
                        avgTime = Math.round(avgTime * 100.0) / 100.0;
                    }

                    double rate = ReportSummary.calculateResolutionRate(resolved, assigned);
                    list.add(new StaffPerformance(adminId, name, role, assigned, resolved, rate, avgTime));
                }
            }
        }
        return list;
    }

    // -----------------------------------------------------------------------
    // Date Filtering Helpers
    // -----------------------------------------------------------------------

    private void appendDateFilter(StringBuilder sql, String column, LocalDate startDate, LocalDate endDate) {
        if (startDate != null) {
            sql.append(" AND ").append(column).append(" >= ?");
        }
        if (endDate != null) {
            sql.append(" AND ").append(column).append(" < ?");
        }
    }

    private int bindDateFilter(PreparedStatement ps, int startIndex, LocalDate startDate, LocalDate endDate) throws SQLException {
        int idx = startIndex;
        if (startDate != null) {
            ps.setTimestamp(idx++, Timestamp.valueOf(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            // Include records through the end of the selected day
            ps.setTimestamp(idx++, Timestamp.valueOf(endDate.plusDays(1).atStartOfDay()));
        }
        return idx;
    }
}
