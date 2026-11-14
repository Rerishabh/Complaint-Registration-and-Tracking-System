-- ============================================================
-- Complaint Registration and Tracking System (CRTS)
-- Migration Script: Phase 8 - In-App Notifications
-- Non-destructive schema enhancement
-- ============================================================

USE complaint_tracking_db;

-- 1. Create notifications table if not exists
CREATE TABLE IF NOT EXISTS notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT          NOT NULL,
    complaint_id    INT          NOT NULL,
    title           VARCHAR(150) NOT NULL,
    message         TEXT         NOT NULL,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_notifications_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints (complaint_id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_notifications_user_read (user_id, is_read),
    INDEX idx_notifications_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
