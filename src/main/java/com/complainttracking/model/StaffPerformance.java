package com.complainttracking.model;

/**
 * StaffPerformance - Represents performance and resolution metrics for an admin/staff member.
 */
public class StaffPerformance {

    private int adminId;
    private String staffName;
    private String role;
    private int assignedComplaints;
    private int resolvedComplaints;
    private double resolutionRate;
    private double avgResolutionTimeHours;

    public StaffPerformance() {
    }

    public StaffPerformance(int adminId, String staffName, String role,
                            int assignedComplaints, int resolvedComplaints,
                            double resolutionRate, double avgResolutionTimeHours) {
        this.adminId = adminId;
        this.staffName = staffName;
        this.role = role;
        this.assignedComplaints = assignedComplaints;
        this.resolvedComplaints = resolvedComplaints;
        this.resolutionRate = resolutionRate;
        this.avgResolutionTimeHours = avgResolutionTimeHours;
    }

    public int getAdminId() {
        return adminId;
    }

    public void setAdminId(int adminId) {
        this.adminId = adminId;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public int getAssignedComplaints() {
        return assignedComplaints;
    }

    public void setAssignedComplaints(int assignedComplaints) {
        this.assignedComplaints = assignedComplaints;
    }

    public int getResolvedComplaints() {
        return resolvedComplaints;
    }

    public void setResolvedComplaints(int resolvedComplaints) {
        this.resolvedComplaints = resolvedComplaints;
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

    @Override
    public String toString() {
        return "StaffPerformance{" +
                "adminId=" + adminId +
                ", staffName='" + staffName + '\'' +
                ", role='" + role + '\'' +
                ", assignedComplaints=" + assignedComplaints +
                ", resolvedComplaints=" + resolvedComplaints +
                ", resolutionRate=" + resolutionRate +
                ", avgResolutionTimeHours=" + avgResolutionTimeHours +
                '}';
    }
}
