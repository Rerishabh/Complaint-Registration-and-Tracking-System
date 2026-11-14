-- ============================================================
-- Complaint Registration and Tracking System (CRTS)
-- Migration Script: Phase 7 - Complaint Assignment & Resolutions
-- Non-destructive schema enhancement
-- ============================================================

USE complaint_tracking_db;

-- 1. Ensure resolution_notes column exists in resolutions table
-- (Renames legacy 'remarks' column to 'resolution_notes' if present)
SET @remarks_col = (
    SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'complaint_tracking_db' 
      AND TABLE_NAME = 'resolutions' 
      AND COLUMN_NAME = 'remarks'
);

SET @sql_rename = IF(@remarks_col > 0, 
    'ALTER TABLE resolutions CHANGE COLUMN remarks resolution_notes TEXT NOT NULL', 
    'SELECT 1');
PREPARE stmt_rename FROM @sql_rename;
EXECUTE stmt_rename;
DEALLOCATE PREPARE stmt_rename;

-- 2. Add assigned_admin_id and assigned_at columns to complaints if not present
SET @assigned_col = (
    SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'complaint_tracking_db' 
      AND TABLE_NAME = 'complaints' 
      AND COLUMN_NAME = 'assigned_admin_id'
);

SET @sql_assign = IF(@assigned_col = 0, 
    'ALTER TABLE complaints ADD COLUMN assigned_admin_id INT NULL AFTER user_id, ADD COLUMN assigned_at TIMESTAMP NULL AFTER assigned_admin_id, ADD CONSTRAINT fk_complaints_assigned_admin FOREIGN KEY (assigned_admin_id) REFERENCES admins (admin_id) ON DELETE SET NULL ON UPDATE CASCADE, ADD INDEX idx_complaints_assigned_admin (assigned_admin_id)', 
    'SELECT 1');
PREPARE stmt_assign FROM @sql_assign;
EXECUTE stmt_assign;
DEALLOCATE PREPARE stmt_assign;

