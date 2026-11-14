package com.complainttracking.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ReportSummary - Consolidated analytics and executive KPIs for administrative reporting.
 */
public class ReportSummary {

    private int totalComplaints;
    private int openComplaints;
    private int inProgressComplaints;
    private int resolvedComplaints;
    private int closedComplaints;
    private int rejectedComplaints;
    private double resolutionRate;
    private double avgResolutionTimeHours;

    private Map<String, Integer> statusDistribution = new LinkedHashMap<>();
    private Map<String, Integer> categoryDistribution = new LinkedHashMap<>();
    private Map<String, Integer> monthlyTrends = new LinkedHashMap<>();
    private List<DepartmentStat> departmentStats = new ArrayList<>();
    private List<StaffPerformance> staffPerformance = new ArrayList<>();

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime generatedAt = LocalDateTime.now();

    public ReportSummary() {
    }

    /**
     * Calculates resolution rate using:
     * Resolution Rate = (Resolved Complaints / Total Complaints) * 100
     * Safely handles zero total complaints without division by zero.
     */
    public static double calculateResolutionRate(int resolvedComplaints, int totalComplaints) {
        if (totalComplaints <= 0) {
            return 0.0;
        }
        double rate = ((double) resolvedComplaints / (double) totalComplaints) * 100.0;
        return Math.round(rate * 100.0) / 100.0; // round to 2 decimal places
    }

    public int getPendingComplaints() {
        return openComplaints + inProgressComplaints;
    }

    public int getTotalComplaints() {
        return totalComplaints;
    }

    public void setTotalComplaints(int totalComplaints) {
        this.totalComplaints = totalComplaints;
    }

    public int getOpenComplaints() {
        return openComplaints;
    }

    public void setOpenComplaints(int openComplaints) {
        this.openComplaints = openComplaints;
    }

    public int getInProgressComplaints() {
        return inProgressComplaints;
    }

    public void setInProgressComplaints(int inProgressComplaints) {
        this.inProgressComplaints = inProgressComplaints;
    }

    public int getResolvedComplaints() {
        return resolvedComplaints;
    }

    public void setResolvedComplaints(int resolvedComplaints) {
        this.resolvedComplaints = resolvedComplaints;
    }

    public int getClosedComplaints() {
        return closedComplaints;
    }

    public void setClosedComplaints(int closedComplaints) {
        this.closedComplaints = closedComplaints;
    }

    public int getRejectedComplaints() {
        return rejectedComplaints;
    }

    public void setRejectedComplaints(int rejectedComplaints) {
        this.rejectedComplaints = rejectedComplaints;
    }

    public double getResolutionRate() {
        return resolutionRate;
    }

    public void setResolutionRate(double resolutionRate) {
        this.resolutionRate = resolutionRate;
    }

    public double getAvgResolutionTimeHours() {
        return avgResolutionTimeHours;
    }

    public void setAvgResolutionTimeHours(double avgResolutionTimeHours) {
        this.avgResolutionTimeHours = avgResolutionTimeHours;
    }

    public Map<String, Integer> getStatusDistribution() {
        return statusDistribution;
    }

    public void setStatusDistribution(Map<String, Integer> statusDistribution) {
        this.statusDistribution = statusDistribution != null ? statusDistribution : new LinkedHashMap<>();
    }

    public Map<String, Integer> getCategoryDistribution() {
        return categoryDistribution;
    }

    public void setCategoryDistribution(Map<String, Integer> categoryDistribution) {
        this.categoryDistribution = categoryDistribution != null ? categoryDistribution : new LinkedHashMap<>();
    }

    public Map<String, Integer> getMonthlyTrends() {
        return monthlyTrends;
    }

    public void setMonthlyTrends(Map<String, Integer> monthlyTrends) {
        this.monthlyTrends = monthlyTrends != null ? monthlyTrends : new LinkedHashMap<>();
    }

    public List<DepartmentStat> getDepartmentStats() {
        return departmentStats;
    }

    public void setDepartmentStats(List<DepartmentStat> departmentStats) {
        this.departmentStats = departmentStats != null ? departmentStats : new ArrayList<>();
    }

    public List<StaffPerformance> getStaffPerformance() {
        return staffPerformance;
    }

    public void setStaffPerformance(List<StaffPerformance> staffPerformance) {
        this.staffPerformance = staffPerformance != null ? staffPerformance : new ArrayList<>();
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    @Override
    public String toString() {
        return "ReportSummary{" +
                "totalComplaints=" + totalComplaints +
                ", openComplaints=" + openComplaints +
                ", inProgressComplaints=" + inProgressComplaints +
                ", resolvedComplaints=" + resolvedComplaints +
                ", closedComplaints=" + closedComplaints +
                ", rejectedComplaints=" + rejectedComplaints +
                ", resolutionRate=" + resolutionRate +
                ", avgResolutionTimeHours=" + avgResolutionTimeHours +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }
}
