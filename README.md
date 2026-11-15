# Complaint Registration and Tracking System (CRTS)

The **Complaint Registration and Tracking System (CRTS)** is a desktop application developed in Java and JavaFX for educational and institutional environments. The platform streamlines issue reporting, status lifecycle management, staff assignment, and complaint resolution.

Students and institutional users can register accounts, submit categorized complaints with specified priority levels, receive automatic tracking identifiers, monitor progress through an audit timeline, and review in-app status updates. Administrators can monitor system-wide key performance indicators (KPIs), inspect incoming complaints, enforce valid status transitions, assign tickets to staff, record resolution notes, review analytical charts, and export reports to CSV.

---

## Table of Contents

- [Key Features](#key-features)
  - [User Portal](#user-portal)
  - [Admin Portal](#admin-portal)
- [System Requirements](#system-requirements)
- [Technology Stack](#technology-stack)
- [Application Architecture](#application-architecture)
- [Application Screens and Workflow](#application-screens-and-workflow)
- [Complaint Status Lifecycle](#complaint-status-lifecycle)
- [Project Structure](#project-structure)
- [Database Configuration](#database-configuration)
- [Installation and Setup](#installation-and-setup)
- [Standalone Windows Application Packaging](#standalone-windows-application-packaging)
- [Automated Testing](#automated-testing)
- [Security Practices](#security-practices)
- [Future Enhancements](#future-enhancements)
- [Author](#author)

---

## Key Features

### User Portal

- **User Authentication**: Secure user registration and login with input validation and BCrypt password hashing.
- **Complaint Submission**: Form submission capturing title, category, priority, and detailed problem descriptions.
- **Unique Tracking Code Generation**: Generates persistent identifiers formatted as `CMP-YYYY-XXXX` upon successful submission.
- **Personal Dashboard**: Overview metrics displaying total submitted, open, in-progress, and resolved complaints.
- **Search & Filtering**: Search complaints by title, tracking code, or category, combined with dynamic status filtering (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `REJECTED`).
- **Audit History Timeline**: Interactive modal displaying the complete chronological audit trail, distinguishing staff assignments from genuine status changes.
- **Resolution Transparency**: Displays detailed resolution notes, resolving administrator name, and timestamp once resolved.
- **In-App Notifications**: Notification badge with unread counters, mark-as-read controls, and one-click navigation to referenced complaints.
- **Public Tracking Lookup**: Unauthenticated tracking lookup from the Welcome Screen that enforces privacy by masking complainant details and internal administrator remarks.

### Admin Portal

- **Administrative Authentication**: Secure role-based login (`ADMIN`, `STAFF`, `SUPERADMIN`) enforcing isolated administrative sessions.
- **System-wide KPI Overview**: System-wide metric counters displaying Total, Open, In Progress, Resolved, and Rejected complaints.
- **Complaint Management**: Centralized table view of all institutional complaints with complainant details, category, priority, status, and assigned staff member.
- **Search & Multi-Criteria Filtering**: Filter complaints across status, category, and priority dropdowns, alongside real-time search across titles, tracking codes, complainant names, and emails.
- **Strict Status Lifecycle Enforcement**: State machine validation governing transitions between `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, and `REJECTED`. Rejects invalid transitions and no-op updates (e.g., `OPEN → OPEN`).
- **Staff Assignment**: Assign or reassign complaints to available administrators or staff members, recording audit history without altering ticket status.
- **Resolution Recording**: Enforces mandatory resolution notes (minimum 10 characters) and records the resolving administrator within an atomic database transaction.
- **Analytical Reports & Charts**:
  - Category distribution (`PieChart`).
  - Status frequency breakdown (`BarChart`).
  - Department complaint volume comparison (`BarChart`).
  - Monthly registration trends over time (`LineChart`).
- **Date-Range Filtering**: Dynamic start-date and end-date pickers to scope analytical reports to custom time windows.
- **Staff Performance Metrics**: Table displaying assigned volume, resolved count, resolution rate percentage, and average resolution time per staff member.
- **CSV Report Export**: Generates RFC 4180-compliant CSV reports with UTF-8 BOM encoding for direct spreadsheet compatibility.

---

## System Requirements

| Requirement | Minimum Supported Version | Notes |
| :--- | :--- | :--- |
| **Java JDK** | 21 | Eclipse Temurin or Oracle OpenJDK 21 |
| **JavaFX** | 21 | Configured via Maven dependencies (`javafx-controls`, `javafx-fxml`) |
| **MySQL Server** | 8.0+ | InnoDB storage engine with `utf8mb4` character set |
| **Apache Maven** | 3.8+ | Used for dependency resolution, compilation, testing, and packaging |
| **Operating System** | Windows 10 / 11 | Primary documented environment for setup scripts and PowerShell commands |

---

## Technology Stack

The application dependencies and build plugins are defined in `pom.xml`:

| Component | Technology | Version | Purpose |
| :--- | :--- | :--- | :--- |
| **Language** | Java (JDK) | 21 | Core backend and application logic |
| **UI Framework** | JavaFX Controls & FXML | 21 | Modular desktop graphical interface |
| **UI Styling** | JavaFX CSS | — | Cohesive custom enterprise styling (`styles.css`) |
| **Database** | MySQL | 8.x | Relational database storage (InnoDB engine) |
| **Driver / Connectivity** | MySQL Connector/J | 8.3.0 | JDBC database driver |
| **Password Hashing** | jBCrypt | 0.4 | Salted password hashing (cost factor 12) |
| **Build Tool** | Apache Maven | 3.8+ | Build lifecycle and dependency management |
| **Testing Framework** | JUnit Jupiter | 5.10.2 | Unit testing and business logic test execution |

---

## Application Architecture

The project follows a layered MVC-based architecture, separating JavaFX presentation, business logic, database persistence, and shared utilities:

| Layer | Responsibility |
| :--- | :--- |
| **Controller** | Handles JavaFX UI interactions, user input, dialogs, and view navigation |
| **Service** | Contains business logic, validation, state transition rules, and authorization checks |
| **DAO** | Handles database persistence, SQL queries, and transactional updates through JDBC |
| **Model** | Represents domain entities and data transfer objects mapping database records |
| **Database** | Manages MySQL JDBC connectivity, credentials resolution, and connection lifecycle |
| **Util** | Provides in-memory session management, navigation helpers, and shared utilities |

---

## Application Screens and Workflow

### Core Workflow

```
[User Registration / Login]
           │
           ▼
[Submit Complaint] ────► [System Generates CMP-YYYY-XXXX Tracking Code]
                                     │
                                     ▼
                    [Admin Reviews & Assigns Staff]
                                     │
                                     ▼
                     [Admin Updates Status to IN_PROGRESS]
                                     │
                                     ▼
                     [Admin Resolves Ticket with Notes]
                                     │
                                     ▼
            [User Views Updated Status, Timeline & Resolution]
```

1. **User Registration & Login**: The user registers with their name, email, department, phone, and password, then authenticates.
2. **Complaint Submission**: The user submits a ticket. Category choices include *Infrastructure*, *IT & Network*, *Academic*, *Hostel & Facilities*, *Administration*, and *Other*.
3. **Tracking Code Generation**: A unique tracking identifier (`CMP-YYYY-XXXX`) is assigned immediately.
4. **Staff Assignment**: An administrator inspects the ticket in the Admin Dashboard and assigns an investigator.
5. **Status Update**: The ticket status is updated to `IN_PROGRESS` with mandatory administrator remarks.
6. **Resolution**: Upon resolving the issue, the administrator provides detailed resolution notes, transitioning the ticket to `RESOLVED`.
7. **Audit & Notifications**: The user receives in-app notifications and can inspect the full status history timeline.

### Application Screens

- **Welcome Screen (`WelcomeScreen.fxml`)**: Main entry screen offering quick navigation to User Login, Admin Login, and a public Quick Complaint Tracking dialog.
- **User Login & Registration (`UserLogin.fxml`, `UserRegister.fxml`)**: Authentication forms featuring validation feedback.
- **User Dashboard (`UserDashboard.fxml`)**: Overview cards, complaint registration form, filterable complaint list, history inspection dialog, and the In-App Notification Center.
- **Admin Login (`AdminLogin.fxml`)**: Administrative authentication portal.
- **Admin Dashboard (`AdminDashboard.fxml`)**: System management console containing KPI counters, complaint data table, status update and assignment dialogs, resolution modals, analytical charts, date filtering, staff performance metrics, and CSV report export.

---

## Complaint Status Lifecycle

Complaint status changes are strictly validated by `ResolutionService.isValidStatusTransition(...)` and guarded against no-op updates by `AdminComplaintService`:

| Status | Description | Permitted Next Statuses | Prerequisites / Triggers |
| :--- | :--- | :--- | :--- |
| `OPEN` | Newly registered complaint awaiting initial administrator review | `IN_PROGRESS`, `REJECTED` | Awaiting administrator review; can be moved to `IN_PROGRESS` or `REJECTED` through an authorized status update. |
| `IN_PROGRESS` | Complaint actively under investigation or troubleshooting by staff | `RESOLVED`, `REJECTED`, `OPEN` | Requires detailed resolution notes (&ge; 10 characters) to resolve, rejection remarks to reject, or can be re-queued to `OPEN`. |
| `RESOLVED` | Issue resolved by staff with documented resolution notes | `CLOSED`, `IN_PROGRESS` | Closed when verified; can be reopened to `IN_PROGRESS` if issue recurs. |
| `CLOSED` | Formally verified and closed; terminal state | *(None)* | Cannot transition to any other status. |
| `REJECTED` | Marked as invalid, duplicate, or unserviceable | `OPEN` | Can be reopened through authorized administrative reconsideration, subject to implemented transition rules. |

### Status Transition Rules

- **No-Op Prevention**: Updating a complaint to its existing status (e.g., `OPEN → OPEN` or `IN_PROGRESS → IN_PROGRESS`) is rejected before database execution with an `IllegalArgumentException("Complaint is already in status <STATUS>.")`.
- **Forbidden Transitions**: Direct jumps that bypass investigation (`OPEN → CLOSED`), modifying closed tickets (`CLOSED → IN_PROGRESS`), and resolving rejected tickets directly (`REJECTED → RESOLVED`) are strictly blocked.
- **Staff Assignment Separation**: Staff assignment is tracked independently from status transitions. Assigning or unassigning staff updates ticket assignment metadata without changing complaint status or creating false status transitions.

---

## Project Structure

```text
Complaint-Registration-and-Tracking-System/
├── database/                      # SQL schema and migration scripts
├── src/
│   ├── main/
│   │   ├── java/com/complainttracking/
│   │   │   ├── Main.java          # JavaFX application entry point
│   │   │   ├── controller/        # JavaFX FXML controller event handlers
│   │   │   ├── dao/               # Data Access Objects (JDBC PreparedStatement operations)
│   │   │   ├── database/          # Database connection singleton manager
│   │   │   ├── model/             # Domain entities and data models
│   │   │   ├── service/           # Business logic, state validation, and authorization
│   │   │   └── util/              # Session management and navigation helpers
│   │   └── resources/
│   │       ├── css/styles.css     # JavaFX enterprise stylesheet
│   │       └── fxml/              # FXML UI layout definitions
│   └── test/java/com/complainttracking/service/ # Automated unit tests
├── pom.xml                        # Maven project descriptor and dependencies
├── .gitignore                     # Git ignore rules for build and environment artifacts
└── README.md                      # Project documentation
```

### Main Package Responsibilities

- `controller/`: Interacts with FXML controls, manages UI state, handles user events, and renders dialogs.
- `dao/`: Executes SQL queries and updates using JDBC `PreparedStatement` within transactional boundaries.
- `database/`: Manages singleton connection lifecycle and resolves environment credentials.
- `model/`: Encapsulates plain Java objects mapping database records and analytical projections.
- `service/`: Enforces business logic, input validation, role-based authorization, and status lifecycle state transitions.
- `util/`: Manages authenticated sessions (`SessionManager`) and stage scene transitions (`NavigationHelper`).

---

## Database Configuration

The application operates on a MySQL database named `complaint_tracking_db`.

### 1. Database Scripts Purpose & Execution Order

Execute the SQL scripts located in the `database/` folder in the following order:

1. **`database/schema.sql`**: Base database initialization script. Automatically creates the `complaint_tracking_db` database if it does not exist, sets `utf8mb4` encoding, creates initial tables (`users`, `admins`, `complaints`, `resolutions`, `complaint_history`), adds indexes, and provisions an initial administrator account.
2. **`database/migration_phase7.sql`**: Schema migration script. Adds staff assignment columns (`assigned_admin_id`, `assigned_at`) and foreign keys to `complaints`, and standardizes resolution columns.
3. **`database/migration_phase8.sql`**: Schema migration script. Creates the `notifications` table with foreign key constraints and indexes for in-app user notifications.

### 2. Execution Commands (Windows PowerShell)

Run each script sequentially using the MySQL client:

```powershell
# Step 1: Base schema and tables
mysql -u root -p -e "source database/schema.sql;"

# Step 2: Staff assignment & resolution migrations
mysql -u root -p -e "source database/migration_phase7.sql;"

# Step 3: In-app notification migrations
mysql -u root -p -e "source database/migration_phase8.sql;"
```

*Note: Alternatively, you can use PowerShell redirection:*
```powershell
Get-Content .\database\schema.sql | mysql -u root -p
Get-Content .\database\migration_phase7.sql | mysql -u root -p
Get-Content .\database\migration_phase8.sql | mysql -u root -p
```

### 3. Verify Database Setup

Verify that all six expected tables exist in `complaint_tracking_db`:

```powershell
mysql -u root -p -e "USE complaint_tracking_db; SHOW TABLES;"
```

Expected output tables:
- `admins`
- `complaint_history`
- `complaints`
- `notifications`
- `resolutions`
- `users`

### 4. Initial Administrator Provisioning

An initial administrator account is provisioned during database setup. Change the default credentials before using the application in a production environment.

### 5. Secure Credential Configuration

`DatabaseConnection.java` retrieves credentials from environment variables at runtime. Set these variables in your terminal session before launching the application:

| Variable | Description | Default Fallback |
| :--- | :--- | :--- |
| `DB_URL` | JDBC database URL | `jdbc:mysql://localhost:3306/complaint_tracking_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| `DB_USER` | MySQL user | `root` |
| `DB_PASS` | MySQL password | `""` *(empty)* |

Configure environment variables in PowerShell as follows:

```powershell
$env:DB_USER = "root"
$env:DB_PASS = "<your_mysql_password>"
$env:DB_URL  = "jdbc:mysql://localhost:3306/complaint_tracking_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
```

---

## Installation and Setup

### Setup Instructions (Windows PowerShell)

1. **Clone the repository**:
   ```powershell
   git clone https://github.com/Rerishabh/Complaint-Registration-and-Tracking-System.git
   cd Complaint-Registration-and-Tracking-System
   ```

2. **Initialize the MySQL database**:
   Follow the step-by-step instructions in the [Database Configuration](#database-configuration) section to execute the database scripts in order (`schema.sql`, `migration_phase7.sql`, and `migration_phase8.sql`) and verify that all 6 tables are created.

3. **Configure database environment variables**:
   ```powershell
   $env:DB_USER = "root"
   $env:DB_PASS = "<your_mysql_password>"
   $env:DB_URL  = "jdbc:mysql://localhost:3306/complaint_tracking_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
   ```

4. **Compile the application**:
   ```powershell
   mvn clean compile
   ```

5. **Launch the JavaFX application**:
   ```powershell
   mvn javafx:run
   ```

---

## Standalone Windows Application Packaging

CRTS can be packaged as a self-contained Windows application using JDK 21 `jpackage`. The generated package bundles its own dedicated Java runtime and JavaFX libraries, allowing teammates to run the application without installing JDK, JavaFX SDK, or Maven.

### 1. Packaging Availability & Requirements

- **Tested Format**: Windows Standalone Application Directory (`--type app-image`), verified on Windows 10/11. The resulting executable is located at `dist/ComplaintTrackingSystem/ComplaintTrackingSystem.exe`.
- **Planned Single-File Installer**: Generating a single-file `.exe` or `.msi` Windows setup wizard requires [WiX Toolset v3.11+](https://wixtoolset.org/) installed and added to `PATH`. On systems without WiX, `jpackage` produces the fully functional standalone application directory (`app-image`).
- **MySQL Prerequisite**: Each teammate must have MySQL Server 8.0+ running locally on port 3306.

### 2. Database Initialization for Teammates

Before launching the standalone application, create and populate the local database:

1. Open PowerShell in the project root.
2. Execute the database scripts in order:
   ```powershell
   mysql -u root -p -e "source database/schema.sql;"
   mysql -u root -p -e "source database/migration_phase7.sql;"
   mysql -u root -p -e "source database/migration_phase8.sql;"
   ```
   *(Each teammate maintains independent local complaint records.)*

### 3. Database Credential Configuration

The standalone executable resolves credentials in the following order:
1. Windows Environment Variables (`DB_USER`, `DB_PASS`, `DB_URL`).
2. Optional external `db.properties` file placed in the application root directory or user home (`~/.crts/db.properties`).
3. Default compiled fallback (`root` user, empty password, `localhost:3306/complaint_tracking_db`).

To configure via Windows User Environment Variables:
```powershell
[System.Environment]::SetEnvironmentVariable("DB_USER", "root", "User")
[System.Environment]::SetEnvironmentVariable("DB_PASS", "<your_mysql_password>", "User")
```

Alternatively, create a `db.properties` file in `dist/ComplaintTrackingSystem/`:
```properties
db.user=root
db.pass=<your_mysql_password>
db.url=jdbc:mysql://localhost:3306/complaint_tracking_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

### 4. Build and Package Commands

To stage dependencies and build the standalone Windows application:

```powershell
# Step 1: Compile, run tests, and stage dependencies to target/jpackage-input/
mvn clean package

# Step 2: Generate the self-contained Windows application image
jpackage --name ComplaintTrackingSystem `
  --app-version 1.0.0 `
  --vendor "CRTS Development Team" `
  --dest dist `
  --input target/jpackage-input `
  --main-jar complaint-tracking-system-1.0-SNAPSHOT.jar `
  --main-class com.complainttracking.Launcher `
  --type app-image

# (Optional) If WiX Toolset v3.11+ is installed, build an .exe installer:
# jpackage --name ComplaintTrackingSystem --app-version 1.0.0 --vendor "CRTS Development Team" --dest dist --input target/jpackage-input --main-jar complaint-tracking-system-1.0-SNAPSHOT.jar --main-class com.complainttracking.Launcher --type exe --win-shortcut --win-menu
```

### 5. Launch the Application

Run the generated executable directly from Windows Explorer or PowerShell:

```powershell
.\dist\ComplaintTrackingSystem\ComplaintTrackingSystem.exe
```

---

## Automated Testing

### Latest Test Execution Summary

The test suite was executed using `mvn clean test` with the following verified results:

| Metric | Result |
| :--- | :--- |
| **Total Tests** | 119 |
| **Passed** | 119 |
| **Failed** | 0 |
| **Errors** | 0 |
| **Skipped** | 0 |

### Running the Test Suite

```powershell
mvn clean test
```

### Test Classes Overview

| Test Class | Focus Areas |
| :--- | :--- |
| `AuthenticationServiceTest` | User registration, credential validation, BCrypt verification, duplicate email checks |
| `ComplaintServiceTest` | Complaint validation, priority mapping, tracking code generation, ownership rules |
| `AdminComplaintServiceTest` | Dashboard statistics, multi-criteria filtering, search matching, authorization checks |
| `ResolutionServiceTest` | Status transition rules, no-op prevention (`OPEN → OPEN`), assignment history preservation, resolution validation |
| `NotificationServiceTest` | Unread badge counts, mark-as-read updates, user ownership checks |
| `ReportServiceTest` | Date range boundary checks, KPI calculations, CSV export formatting, administrator authorization |

> [!NOTE]
> Automated unit tests utilize in-memory DAO stubs (`StubComplaintDAO`, `StubHistoryDAO`, `StubResolutionDAO`, `StubAdminDAO`) to verify business logic, state transitions, validation boundaries, and authorization rules in isolation. These unit tests do not replace end-to-end integration testing against a live MySQL database.

---

## Security Practices

- **Salted Password Hashing**: Passwords for users and administrators are hashed using BCrypt (cost factor 12) before persistence. Plaintext passwords are never stored.
- **SQL Injection Prevention**: All database queries and updates in `dao` classes utilize JDBC `PreparedStatement` with parameterized placeholders.
- **Transaction Atomicity**: Critical operations (such as status updates, staff assignments, and resolutions) run within explicit JDBC transactions (`conn.setAutoCommit(false)`) with automatic rollback (`conn.rollback()`) on failure.
- **Session Enforcement**: `SessionManager` maintains logged-in state in memory. Service methods enforce authorization via `requireAdminSession()` and session validation.
- **Data Privacy**: Public tracking lookups exclude complainant identities (name, email, phone) and internal administrative notes, displaying only complaint identifiers and status history.
- **Safe CSV Handling**: CSV export in `ReportService` implements RFC 4180 escaping and does not export password hashes or authentication secrets.

---

## Future Enhancements

The following features represent planned future enhancements:

- **Email & SMS Notifications**: External SMTP or SMS gateway integration for real-time delivery outside the desktop client.
- **File & Media Attachments**: Support for attaching photos, error logs, and documentation to complaints.
- **PDF Export**: Direct generation of formatted PDF analytical reports alongside CSV.
- **Theme Customization**: User-selectable visual themes (Dark Mode and Light Mode).
- **Internationalization (i18n)**: Multi-language resource bundles for UI localization.

---

## Author

- **Rishabh Paira**
- **GitHub**: [https://github.com/Rerishabh](https://github.com/Rerishabh)
