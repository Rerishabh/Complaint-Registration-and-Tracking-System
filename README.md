# Complaint Registration and Tracking System (CRTS)

A professional Java desktop application for registering, tracking, assigning, and resolving complaints.  
Built with **Java 21**, **JavaFX 21**, **JDBC**, and **MySQL** as part of an academic Java project.

---

## Project Objectives

- Allow users to register, log in, submit complaints with categorization and priority, and track status with a unique tracking code.
- Allow administrators to log in securely, monitor key operational KPIs, filter complaints, update statuses with strict lifecycle enforcement, assign complaints to specific staff, and record detailed resolutions.
- Provide users with full visibility into the assigned staff member, resolution records, and audit timeline.
- Maintain a complete audit trail of all status transitions, staff assignments, and resolutions.

---

## Technology Stack

| Layer        | Technology             | Version | Purpose |
|--------------|------------------------|---------|---------|
| Language     | Java (Eclipse Temurin) | 21      | Core application backend |
| GUI          | JavaFX + FXML          | 21      | Desktop user interface and layouts |
| Styling      | JavaFX CSS             | –       | Modern dark-navy & clean enterprise theme |
| Database     | MySQL                  | 8.x     | Relational data storage (InnoDB engine) |
| Connectivity | JDBC (MySQL Connector) | 8.3.0   | Database connectivity via PreparedStatement |
| Security     | BCrypt (jBCrypt)       | 0.4     | Salted password hashing (cost factor 12) |
| Build Tool   | Apache Maven           | 3.10.0  | Dependency management and build lifecycle |
| Testing      | JUnit Jupiter          | 5.10.2  | Automated unit and integration testing (70 tests) |

---

## Architecture

This project strictly adheres to the **MVC (Model-View-Controller)** pattern with distinct layers:

```
com.complainttracking/
├── Main.java                       ← JavaFX application entry point
├── model/                          ← Domain entity models (Plain Java Objects)
│   ├── User.java
│   ├── Admin.java
│   ├── Complaint.java              ← Full complaint entity with assignee & resolution info
│   ├── ComplaintHistory.java       ← Audit trail event entity
│   └── Resolution.java             ← Resolution notes and resolver metadata entity
├── dao/                            ← Data Access Objects (JDBC PreparedStatement operations)
│   ├── UserDAO.java
│   ├── AdminDAO.java
│   ├── ComplaintDAO.java           ← Multi-table queries, transactions (status, assign, resolve)
│   ├── ComplaintHistoryDAO.java
│   └── ResolutionDAO.java          ← Resolution storage and retrieval
├── service/                        ← Business logic, validation, and transaction orchestrators
│   ├── AuthenticationService.java
│   ├── ComplaintService.java       ← User complaint creation & ownership validation
│   ├── AdminComplaintService.java  ← Admin stats, filtering, assignees, status updates
│   └── ResolutionService.java      ← State transition validation, resolution rules & transactions
├── controller/                     ← JavaFX UI controllers (Event Handling)
│   ├── WelcomeController.java
│   ├── UserLoginController.java
│   ├── UserRegisterController.java
│   ├── AdminLoginController.java
│   ├── UserDashboardController.java   ← User portal: submit, track, view assignee & resolution
│   └── AdminDashboardController.java  ← Admin portal: KPIs, status update, assign, resolve dialogs
├── database/                       ← JDBC connection singleton
│   └── DatabaseConnection.java
└── util/                           ← Session management & navigation helpers
    ├── SessionManager.java
    └── NavigationHelper.java
```

---

## Database Schema & Migrations

The database consists of five relational tables designed with foreign key constraints, indexes, and full referential integrity:

1. **`users`**: User profile, contact information, department, and BCrypt salted password hash.
2. **`admins`**: Administrator/staff credentials, role (`ADMIN`, `STAFF`, `SUPERADMIN`), and password hash.
3. **`complaints`**: Core complaint data with `tracking_code`, `status`, `assigned_admin_id`, and timestamps.
4. **`complaint_history`**: Immutable status transition and assignment audit log (`old_status`, `new_status`, `changed_by`, `note`, `changed_at`).
5. **`resolutions`**: Detailed resolution records (`resolution_id`, `complaint_id`, `admin_id`, `resolution_notes`, `resolved_at`).

### Phase 7 Migration Script (`database/migration_phase7.sql`)
Executed to align column naming and establish complaint assignments:
```sql
-- 1. Standardize resolution notes column
ALTER TABLE resolutions CHANGE COLUMN remarks resolution_notes TEXT NOT NULL;

-- 2. Add staff assignment tracking columns and foreign keys
ALTER TABLE complaints ADD COLUMN assigned_admin_id INT NULL AFTER updated_at;
ALTER TABLE complaints ADD COLUMN assigned_at TIMESTAMP NULL AFTER assigned_admin_id;
ALTER TABLE complaints ADD CONSTRAINT fk_complaints_assigned_admin 
    FOREIGN KEY (assigned_admin_id) REFERENCES admins(admin_id) ON DELETE SET NULL;
CREATE INDEX idx_complaints_assigned_admin ON complaints(assigned_admin_id);
```

---

## Status Transition Lifecycle & Business Rules

All status changes are strictly controlled through `ResolutionService.isValidStatusTransition(...)` and committed inside single JDBC transactions (`conn.setAutoCommit(false)`):

| Current Status | Allowed Next Status | Trigger / Description |
|----------------|---------------------|-----------------------|
| `OPEN`         | `IN_PROGRESS`       | Admin assigns staff or begins investigation. |
| `OPEN`         | `REJECTED`          | Invalid, spam, or duplicate request (requires remarks). |
| `IN_PROGRESS`  | `RESOLVED`          | Root cause resolved (requires resolution notes ≥ 10 chars). |
| `IN_PROGRESS`  | `REJECTED`          | Cannot be serviced after initial investigation. |
| `IN_PROGRESS`  | `OPEN`              | Re-queued to open pool. |
| `RESOLVED`     | `CLOSED`            | Confirmed resolved and formally closed. |
| `RESOLVED`     | `IN_PROGRESS`       | Reopened by admin upon user feedback or recurring defect. |
| `REJECTED`     | `OPEN`              | Reconsidered by admin upon user appeal. |
| `CLOSED`       | *(None)*            | Terminal status; no transitions allowed. |

### Forbidden Transitions
- `OPEN` ➔ `CLOSED` (Cannot bypass investigation and resolution)
- `CLOSED` ➔ `IN_PROGRESS` (Closed tickets cannot be modified directly)
- `REJECTED` ➔ `RESOLVED` (Must be reconsidered to `OPEN` first)
- Transitioning to the exact same status

---

## Assignment & Resolution Workflow

### Staff Assignment
- Administrators can assign any open or in-progress complaint to an available administrator/staff member via the **Assign Staff** button in the Admin Dashboard.
- Optional handoff instructions or notes are recorded directly into `complaint_history`.
- Unassigning (`assigned_admin_id = NULL`) is fully supported.

### Resolution Management
- When resolving a complaint, the administrator provides detailed resolution notes (minimum 10 characters).
- The operation runs in a single transaction:
  1. Inserts into `resolutions (complaint_id, admin_id, resolution_notes, resolved_at)`.
  2. Updates `complaints (status = 'RESOLVED', updated_at = NOW())`.
  3. Inserts into `complaint_history` recording the transition with the resolving admin's name.
- Both the Admin inspection dialog and User details dialog display a prominent **Resolution Information** card highlighting the notes, resolving admin, and resolution timestamp.

---

## Initial Administrator Account

- **Username:** `admin`
- **Default Password:** `Admin@123`
- **Role:** `superadmin`

BCrypt hash seeded in MySQL for `Admin@123`:
```
$2a$12$yGaascJlePP97TzEbfS7beejCGhBYdcBeRPBnPGwjEfue/j6R7zle
```

---

## Environment Variables (Database Credentials)

Set the following environment variables before launching:

| Variable  | Description                        | Example |
|-----------|------------------------------------|---------|
| `DB_URL`  | Full JDBC connection string        | `jdbc:mysql://localhost:3306/complaint_tracking_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| `DB_USER` | MySQL username                     | `root` |
| `DB_PASS` | MySQL password                     | `your_password` |

**PowerShell Example:**
```powershell
$env:DB_USER = "root"
$env:DB_PASS = "r15k3P@5"
$env:DB_URL  = "jdbc:mysql://localhost:3306/complaint_tracking_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
```

---

## Building, Testing, and Running

### Run Automated Unit Tests (70 tests)
```powershell
mvn test
```

### Clean & Compile
```powershell
mvn clean compile
```

### Launch the Application
```powershell
mvn javafx:run
```

---

## Manual Verification & SQL Queries

To verify operations directly in MySQL Workbench:

```sql
USE complaint_tracking_db;

-- 1. Inspect recent status transitions and assignment logs
SELECT h.history_id, c.tracking_code, h.old_status, h.new_status, h.changed_by, h.note, h.changed_at
FROM complaint_history h
JOIN complaints c ON h.complaint_id = c.complaint_id
ORDER BY h.changed_at DESC
LIMIT 10;

-- 2. Inspect recorded resolutions
SELECT r.resolution_id, c.tracking_code, a.full_name AS resolver_name, r.resolution_notes, r.resolved_at
FROM resolutions r
JOIN complaints c ON r.complaint_id = c.complaint_id
JOIN admins a ON r.admin_id = a.admin_id
ORDER BY r.resolved_at DESC;

-- 3. Check complaint assignments
SELECT c.tracking_code, c.title, c.status, a.full_name AS assigned_to, c.assigned_at
FROM complaints c
LEFT JOIN admins a ON c.assigned_admin_id = a.admin_id
ORDER BY c.created_at DESC;
```

---

## Academic Marking Criteria Alignment

> **Category:** "Project with Event Handling, JavaFX and JDBC with MySQL" (Maximum Marks: 15)

Key implementations verified:
- **JavaFX Event Handling:** Action events, modal dialogs, background threads off the JavaFX Application Thread with `Platform.runLater()`.
- **JavaFX & FXML:** Modular FXML views, dynamic table columns (`Assigned Staff`), responsive layouts, and unified CSS styling.
- **JDBC & MySQL:** Parameterized `PreparedStatement` queries, multi-statement transactional updates (`conn.setAutoCommit(false)`), rollback handling, and foreign key referential integrity.
- **Core Java & Architecture:** Clean MVC architecture, BCrypt password hashing, session singleton, thorough unit testing with in-memory test stubs.
