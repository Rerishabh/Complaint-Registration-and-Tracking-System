package com.complainttracking.model;

/**
 * DepartmentStat - Represents aggregated complaint statistics for a department.
 */
public class DepartmentStat {

    private String departmentName;
    private int totalComplaints;
    private int resolvedComplaints;
    private int pendingComplaints;

    public DepartmentStat() {
    }

    public DepartmentStat(String departmentName, int totalComplaints, int resolvedComplaints, int pendingComplaints) {
        this.departmentName = departmentName;
        this.totalComplaints = totalComplaints;
        this.resolvedComplaints = resolvedComplaints;
        this.pendingComplaints = pendingComplaints;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public int getTotalComplaints() {
        return totalComplaints;
    }

    public void setTotalComplaints(int totalComplaints) {
        this.totalComplaints = totalComplaints;
    }

    public int getResolvedComplaints() {
        return resolvedComplaints;
    }

    public void setResolvedComplaints(int resolvedComplaints) {
        this.resolvedComplaints = resolvedComplaints;
    }

    public int getPendingComplaints() {
        return pendingComplaints;
    }

    public void setPendingComplaints(int pendingComplaints) {
        this.pendingComplaints = pendingComplaints;
    }

    @Override
    public String toString() {
        return "DepartmentStat{" +
                "departmentName='" + departmentName + '\'' +
                ", totalComplaints=" + totalComplaints +
                ", resolvedComplaints=" + resolvedComplaints +
                ", pendingComplaints=" + pendingComplaints +
                '}';
    }
}
