package com.complainttracking.model;

import java.sql.Timestamp;

/**
 * User model representing a record in the 'users' table.
 */
public class User {

    private int userId;
    private String fullName;
    private String email;
    private String passwordHash;
    private String phone;
    private String department;
    private Timestamp createdAt;

    /**
     * Default constructor.
     */
    public User() {
    }

    /**
     * Convenience constructor for registration.
     */
    public User(String fullName, String email, String passwordHash, String phone, String department) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.department = department;
    }

    /**
     * Full constructor for retrieving users from the database.
     */
    public User(int userId, String fullName, String email, String passwordHash, String phone, String department, Timestamp createdAt) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.department = department;
        this.createdAt = createdAt;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", department='" + department + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

