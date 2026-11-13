package com.complainttracking.service;

import com.complainttracking.dao.AdminDAO;
import com.complainttracking.dao.UserDAO;
import com.complainttracking.model.Admin;
import com.complainttracking.model.User;
import com.complainttracking.util.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthenticationService and SessionManager using in-memory DAO stubs.
 */
public class AuthenticationServiceTest {

    private AuthenticationService authService;
    private MockUserDAO mockUserDAO;
    private MockAdminDAO mockAdminDAO;

    @BeforeEach
    public void setUp() {
        SessionManager.getInstance().logout();
        mockUserDAO = new MockUserDAO();
        mockAdminDAO = new MockAdminDAO();
        authService = new AuthenticationService(mockUserDAO, mockAdminDAO);
    }

    // --- Mock DAOs for deterministic testing without external DB ---
    static class MockUserDAO extends UserDAO {
        private final Map<String, User> store = new HashMap<>();
        private int idCounter = 1;

        @Override
        public boolean registerUser(User user) {
            user.setUserId(idCounter++);
            store.put(user.getEmail().toLowerCase(), user);
            return true;
        }

        @Override
        public User findByEmail(String email) {
            return store.get(email.toLowerCase());
        }

        @Override
        public boolean emailExists(String email) {
            return store.containsKey(email.toLowerCase());
        }
    }

    static class MockAdminDAO extends AdminDAO {
        private final Map<String, Admin> store = new HashMap<>();

        public void addAdmin(Admin admin) {
            store.put(admin.getUsername().toLowerCase(), admin);
        }

        @Override
        public Admin findByUsername(String username) {
            return store.get(username.toLowerCase());
        }
    }

    // --- Registration Tests ---

    @Test
    public void testSuccessfulUserRegistration() {
        User user = authService.registerUser(
                "Jane Doe",
                "jane@example.com",
                "Password@123",
                "Password@123",
                "+1234567890",
                "Computer Science & Engineering"
        );

        assertNotNull(user);
        assertEquals(1, user.getUserId());
        assertEquals("Jane Doe", user.getFullName());
        assertEquals("jane@example.com", user.getEmail());
        assertNotNull(user.getPasswordHash());
        // Verify BCrypt hashing
        assertTrue(BCrypt.checkpw("Password@123", user.getPasswordHash()));
        // Plaintext password should not be stored
        assertNotEquals("Password@123", user.getPasswordHash());
    }

    @Test
    public void testRegistrationFailsOnMissingName() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                authService.registerUser("", "test@test.com", "pass123", "pass123", null, "IT"));
        assertTrue(ex.getMessage().contains("Full name is required"));
    }

    @Test
    public void testRegistrationFailsOnInvalidEmail() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                authService.registerUser("John Doe", "invalid-email", "pass123", "pass123", null, "IT"));
        assertTrue(ex.getMessage().contains("valid email address"));
    }

    @Test
    public void testRegistrationFailsOnDuplicateEmail() {
        authService.registerUser("John Doe", "john@example.com", "pass123", "pass123", null, "IT");

        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                authService.registerUser("Another John", "john@example.com", "pass456", "pass456", null, "IT"));
        assertTrue(ex.getMessage().contains("already registered"));
    }

    @Test
    public void testRegistrationFailsOnShortPassword() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                authService.registerUser("John Doe", "john2@example.com", "12345", "12345", null, "IT"));
        assertTrue(ex.getMessage().contains("at least 6 characters"));
    }

    @Test
    public void testRegistrationFailsOnPasswordMismatch() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                authService.registerUser("John Doe", "john3@example.com", "password123", "password456", null, "IT"));
        assertTrue(ex.getMessage().contains("do not match"));
    }

    @Test
    public void testRegistrationFailsOnUnselectedDepartment() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                authService.registerUser("John Doe", "john4@example.com", "password123", "password123", null, "Select Department"));
        assertTrue(ex.getMessage().contains("department"));
    }

    // --- User Login Tests ---

    @Test
    public void testSuccessfulUserLogin() {
        authService.registerUser("Alice Smith", "alice@test.com", "Secret@99", "Secret@99", null, "Administration");

        User loggedIn = authService.loginUser("alice@test.com", "Secret@99");
        assertNotNull(loggedIn);
        assertEquals("Alice Smith", loggedIn.getFullName());

        // Verify session
        SessionManager session = SessionManager.getInstance();
        assertTrue(session.isUserLoggedIn());
        assertFalse(session.isAdminLoggedIn());
        assertEquals("alice@test.com", session.getCurrentUser().getEmail());
    }

    @Test
    public void testUserLoginFailsWrongPassword() {
        authService.registerUser("Alice Smith", "alice@test.com", "Secret@99", "Secret@99", null, "Administration");

        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                authService.loginUser("alice@test.com", "WrongPassword"));
        assertTrue(ex.getMessage().contains("Invalid email or password"));
        assertFalse(SessionManager.getInstance().isUserLoggedIn());
    }

    @Test
    public void testUserLoginFailsNonexistentUser() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                authService.loginUser("nobody@test.com", "Password"));
        assertTrue(ex.getMessage().contains("Invalid email or password"));
    }

    // --- Admin Login Tests ---

    @Test
    public void testSuccessfulAdminLogin() {
        String adminHash = BCrypt.hashpw("Admin@123", BCrypt.gensalt(12));
        Admin admin = new Admin(1, "admin", adminHash, "System Administrator", "superadmin", null);
        mockAdminDAO.addAdmin(admin);

        Admin loggedIn = authService.loginAdmin("admin", "Admin@123");
        assertNotNull(loggedIn);
        assertEquals("System Administrator", loggedIn.getFullName());

        // Verify session
        SessionManager session = SessionManager.getInstance();
        assertTrue(session.isAdminLoggedIn());
        assertFalse(session.isUserLoggedIn());
        assertEquals("admin", session.getCurrentAdmin().getUsername());
    }

    @Test
    public void testAdminLoginFailsWrongPassword() {
        String adminHash = BCrypt.hashpw("Admin@123", BCrypt.gensalt(12));
        mockAdminDAO.addAdmin(new Admin(1, "admin", adminHash, "System Admin", "admin", null));

        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                authService.loginAdmin("admin", "WrongPass"));
        assertTrue(ex.getMessage().contains("Invalid username or password"));
        assertFalse(SessionManager.getInstance().isAdminLoggedIn());
    }

    // --- Session Manager Mutual Exclusion Tests ---

    @Test
    public void testSessionMutualExclusion() {
        SessionManager session = SessionManager.getInstance();

        User user = new User("User One", "user@test.com", "hash", "123", "IT");
        session.loginUser(user);
        assertTrue(session.isUserLoggedIn());
        assertFalse(session.isAdminLoggedIn());

        Admin admin = new Admin("admin1", "hash", "Admin One", "admin");
        session.loginAdmin(admin);
        assertFalse(session.isUserLoggedIn(), "User session should be invalidated when admin logs in");
        assertTrue(session.isAdminLoggedIn());

        session.logout();
        assertFalse(session.isLoggedIn());
    }
}

