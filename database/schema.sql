-- ============================================================
-- Complaint Registration and Tracking System
-- Database Schema
-- MySQL 8.x | InnoDB | UTF8MB4
-- ============================================================
-- INSTRUCTIONS:
-- 1. Open MySQL Workbench and connect to your local server.
-- 2. Open this file via File > Open SQL Script.
-- 3. Execute with Ctrl+Shift+Enter or the lightning bolt button.
-- ============================================================

-- Create and select database
CREATE DATABASE IF NOT EXISTS complaint_tracking_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE complaint_tracking_db;

-- ============================================================
-- TABLE: users
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    user_id       INT             NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(100)    NOT NULL,
    email         VARCHAR(150)    NOT NULL,
    password_hash VARCHAR(255)    NOT NULL,
    phone         VARCHAR(20)     DEFAULT NULL,
    department    VARCHAR(100)    DEFAULT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: admins
-- ============================================================
CREATE TABLE IF NOT EXISTS admins (
    admin_id      INT             NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)     NOT NULL,
    password_hash VARCHAR(255)    NOT NULL,
    full_name     VARCHAR(100)    NOT NULL,
    role          VARCHAR(50)     NOT NULL DEFAULT 'admin',
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_admins PRIMARY KEY (admin_id),
    CONSTRAINT uq_admins_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: complaints
-- ============================================================
CREATE TABLE IF NOT EXISTS complaints (
    complaint_id  INT             NOT NULL AUTO_INCREMENT,
    tracking_code VARCHAR(20)     NOT NULL,
    user_id       INT             NOT NULL,
    title         VARCHAR(200)    NOT NULL,
    description   TEXT            NOT NULL,
    category      VARCHAR(100)    NOT NULL,
    priority      ENUM('LOW','MEDIUM','HIGH','CRITICAL')
                                  NOT NULL DEFAULT 'MEDIUM',
    status        ENUM('OPEN','IN_PROGRESS','RESOLVED','CLOSED','REJECTED')
                                  NOT NULL DEFAULT 'OPEN',
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_complaints PRIMARY KEY (complaint_id),
    CONSTRAINT uq_complaints_tracking_code UNIQUE (tracking_code),
    CONSTRAINT fk_complaints_user FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: resolutions
-- ============================================================
CREATE TABLE IF NOT EXISTS resolutions (
    resolution_id INT             NOT NULL AUTO_INCREMENT,
    complaint_id  INT             NOT NULL,
    admin_id      INT             NOT NULL,
    remarks       TEXT            NOT NULL,
    resolved_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_resolutions PRIMARY KEY (resolution_id),
    CONSTRAINT fk_resolutions_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints (complaint_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_resolutions_admin FOREIGN KEY (admin_id)
        REFERENCES admins (admin_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: complaint_history
-- ============================================================
CREATE TABLE IF NOT EXISTS complaint_history (
    history_id    INT             NOT NULL AUTO_INCREMENT,
    complaint_id  INT             NOT NULL,
    old_status    VARCHAR(50)     DEFAULT NULL,
    new_status    VARCHAR(50)     NOT NULL,
    changed_by    VARCHAR(100)    NOT NULL,
    note          TEXT            DEFAULT NULL,
    changed_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_complaint_history PRIMARY KEY (history_id),
    CONSTRAINT fk_history_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints (complaint_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- INDEXES for frequently searched fields
-- ============================================================
CREATE INDEX idx_complaints_status       ON complaints (status);
CREATE INDEX idx_complaints_priority     ON complaints (priority);
CREATE INDEX idx_complaints_user_id      ON complaints (user_id);
CREATE INDEX idx_complaints_created_at   ON complaints (created_at);
CREATE INDEX idx_history_complaint_id    ON complaint_history (complaint_id);
CREATE INDEX idx_resolutions_complaint   ON resolutions (complaint_id);

-- ============================================================
-- SEED: Default admin account
-- Password: Admin@123 (BCrypt hashed)
-- Change this password immediately after first login.
-- ============================================================
INSERT IGNORE INTO admins (username, password_hash, full_name, role)
VALUES (
    'admin',
    '$2a$12$yGaascJlePP97TzEbfS7beejCGhBYdcBeRPBnPGwjEfue/j6R7zle',
    'System Administrator',
    'superadmin'
);
