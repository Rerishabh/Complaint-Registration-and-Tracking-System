# Complaint Registration and Tracking System (CRTS)

A professional Java desktop application for registering, tracking, and resolving complaints.  
Built with **Java 21**, **JavaFX 21**, **JDBC**, and **MySQL** as part of an academic Java project.

---

## Project Objectives

- Allow users to register, log in, and submit complaints with categorization and priority.
- Enable users to track complaint status using a unique tracking code.
- Allow administrators to log in securely, manage complaints, update statuses, and log resolutions.
- Maintain a full audit trail of all status changes.

---

## Technology Stack

| Layer        | Technology             | Version | Purpose |
|--------------|------------------------|---------|---------|
| Language     | Java (Eclipse Temurin) | 21      | Core application backend |
| GUI          | JavaFX + FXML          | 21      | Desktop user interface and layouts |
| Styling      | JavaFX CSS             | –       | Modern theme and component styling |
| Database     | MySQL                  | 8.x     | Relational data storage (InnoDB) |
| Connectivity | JDBC (MySQL Connector) | 8.3.0   | Database connectivity via PreparedStatement |
| Security     | BCrypt (jBCrypt)       | 0.4     | Salted password hashing (cost factor 12) |
| Build Tool   | Apache Maven           | 3.10.0  | Dependency management and build lifecycle |
| Testing      | JUnit Jupiter          | 5.10.2  | Unit and service validation testing |

---

## Architecture

This project strictly adheres to the **MVC (Model-View-Controller)** pattern with distinct layers:

```
com.complainttracking/
├── Main.java                       ← JavaFX application entry point
├── model/                          ← Domain entity models (Plain Java Objects)
│   ├── User.java
│   └── Admin.java
├── dao/                            ← Data Access Objects (JDBC PreparedStatement operations)
│   ├── UserDAO.java
│   └── AdminDAO.java
├── service/                        ← Business logic and validation layer
│   └── AuthenticationService.java
├── controller/                     ← JavaFX UI controllers (Event Handling)
│   ├── WelcomeController.java
│   ├── UserLoginController.java
│   ├── UserRegisterController.java
│   └── AdminLoginController.java
├── database/                       ← JDBC connection singleton
│   └── DatabaseConnection.java
└── util/                           ← Session management & navigation helpers
    ├── SessionManager.java
    └── NavigationHelper.java
```

---

## Project Folder Structure

```
Complaint-Registration-and-Tracking-System/
├── pom.xml
├── README.md
├── .gitignore
├── database/
│   └── schema.sql
└── src/
    ├── main/
    │   ├── java/com/complainttracking/
    │   │   ├── Main.java
    │   │   ├── controller/
    │   │   │   ├── WelcomeController.java
    │   │   │   ├── UserLoginController.java
    │   │   │   ├── UserRegisterController.java
    │   │   │   └── AdminLoginController.java
    │   │   ├── dao/
    │   │   │   ├── UserDAO.java
    │   │   │   └── AdminDAO.java
    │   │   ├── database/
    │   │   │   └── DatabaseConnection.java
    │   │   ├── model/
    │   │   │   ├── User.java
    │   │   │   └── Admin.java
    │   │   ├── service/
    │   │   │   └── AuthenticationService.java
    │   │   └── util/
    │   │       ├── SessionManager.java
    │   │       └── NavigationHelper.java
    │   └── resources/
    │       ├── css/
    │       │   └── styles.css
    │       ├── fxml/
    │       │   ├── WelcomeScreen.fxml
    │       │   ├── UserLogin.fxml
    │       │   ├── UserRegister.fxml
    │       │   └── AdminLogin.fxml
    │       └── images/
    └── test/
        └── java/com/complainttracking/
            └── service/
                └── AuthenticationServiceTest.java
```

---

## Database Setup & Admin Credentials

> **Prerequisites:** MySQL 8.x installed and running locally.

1. Open **MySQL Workbench** and connect to your local server.
2. Open `database/schema.sql` via **File → Open SQL Script**.
3. Execute the script with **Ctrl + Shift + Enter**.
4. Verify that `complaint_tracking_db` appears in your schemas list.

### Initial Administrator Account
The schema seeds a default administrative account with a verified BCrypt hash:
- **Username:** `admin`
- **Default Password:** `Admin@123`
- **Role:** `superadmin`

> **Note on Initial Admin Password:**  
> The BCrypt hash seeded in MySQL for `Admin@123` is:  
> `$2a$12$yGaascJlePP97TzEbfS7beejCGhBYdcBeRPBnPGwjEfue/j6R7zle`  
> If you already executed the database script previously, update the hash with:
> ```sql
> USE complaint_tracking_db;
> UPDATE admins 
> SET password_hash = '$2a$12$yGaascJlePP97TzEbfS7beejCGhBYdcBeRPBnPGwjEfue/j6R7zle' 
> WHERE username = 'admin';
> ```

---

## Environment Variables (Database Credentials)

Set these environment variables before running the application:

| Variable  | Description                        | Example                                                    |
|-----------|------------------------------------|------------------------------------------------------------|
| `DB_URL`  | Full JDBC connection string        | `jdbc:mysql://localhost:3306/complaint_tracking_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| `DB_USER` | MySQL username                     | `root`                                                     |
| `DB_PASS` | MySQL password                     | `your_password`                                            |

**On Windows (PowerShell):**
```powershell
$env:DB_USER = "root"
$env:DB_PASS = "your_mysql_password"
$env:DB_URL  = "jdbc:mysql://localhost:3306/complaint_tracking_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
```

---

## Building and Running

### Compile & Run Tests
```powershell
mvn clean test
```

### Launch the Application
```powershell
mvn javafx:run
```

---

## Implemented Authentication Features (Phase 4)

- **User Registration (`UserRegister.fxml` & `UserRegisterController`):**
  - Validation: Full name, valid email regex format, phone format, mandatory department selection.
  - Password strength check (min 6 characters) and password confirmation match.
  - Duplicate email prevention query via `UserDAO.emailExists(...)`.
  - Passwords hashed using **BCrypt (cost factor 12)** prior to storage in MySQL.
- **User Login (`UserLogin.fxml` & `UserLoginController`):**
  - Email and password validation.
  - Case-insensitive email lookup via `UserDAO.findByEmail(...)`.
  - BCrypt verification via `BCrypt.checkpw(...)`.
  - Threaded authentication with responsive loading state.
  - Displays authenticated user confirmation alert on success.
- **Admin Login (`AdminLogin.fxml` & `AdminLoginController`):**
  - Username and password validation.
  - Query via `AdminDAO.findByUsername(...)`.
  - BCrypt verification and session initialization.
  - Displays authenticated admin confirmation alert with role details on success.
- **Session Management (`SessionManager`):**
  - Enforces mutual exclusion between user and admin sessions.
  - Thread-safe singleton storing active user/admin object.
- **Navigation (`NavigationHelper`):**
  - Smooth scene switching between Welcome, User Login, User Registration, and Admin Login views.

---

## Development Roadmap

| Phase | Description                              | Status        |
|-------|------------------------------------------|---------------|
| 1     | Project architecture & folder structure  | ✅ Complete   |
| 2     | Maven configuration                      | ✅ Complete   |
| 3     | MySQL database schema design             | ✅ Complete   |
| 4     | User & Admin authentication              | ✅ Complete   |
| 5     | Complaint registration (User)            | 🔲 Planned    |
| 6     | Complaint tracking (User)                | 🔲 Planned    |
| 7     | Admin dashboard & complaint management   | 🔲 Planned    |
| 8     | Resolution management                    | 🔲 Planned    |
| 9     | Complaint history & audit trail          | 🔲 Planned    |

---

## Academic Marking Criteria Alignment

> **Category:** "Project with Event Handling, JavaFX and JDBC with MySQL" (Maximum Marks: 15)

Key implementations verified:
- **JavaFX Event Handling:** FXML actions, background threads with `Platform.runLater()`, asynchronous UI status updates.
- **JavaFX & FXML:** Modular FXML views, custom CSS theme, consistent typography and colors.
- **JDBC & MySQL:** `PreparedStatement` parameterized queries, transactions, connection management.
- **Core Java & Security:** Salted BCrypt password hashing, session singleton, MVC separation.
