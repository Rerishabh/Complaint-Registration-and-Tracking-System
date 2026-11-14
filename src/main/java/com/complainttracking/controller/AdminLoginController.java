package com.complainttracking.controller;

import com.complainttracking.model.Admin;
import com.complainttracking.service.AuthenticationService;
import com.complainttracking.util.NavigationHelper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for AdminLogin.fxml.
 * Handles administrator authentication, session establishment, and navigation to AdminDashboard.fxml.
 */
public class AdminLoginController {

    private static final Logger LOGGER = Logger.getLogger(AdminLoginController.class.getName());

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label statusLabel;

    private final AuthenticationService authService = new AuthenticationService();

    /**
     * Handles admin authentication.
     */
    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username == null || username.trim().isEmpty()) {
            showError("Please enter your admin username.");
            usernameField.requestFocus();
            return;
        }

        if (password == null || password.isEmpty()) {
            showError("Please enter your password.");
            passwordField.requestFocus();
            return;
        }

        loginButton.setDisable(true);
        setStatus("Authenticating administrator...", "status-checking");

        Thread authThread = new Thread(() -> {
            try {
                Admin admin = authService.loginAdmin(username, password);
                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    setStatus("Admin login successful! Loading dashboard...", "form-success-label");

                    // Navigate directly to AdminDashboard.fxml
                    Stage stage = (Stage) loginButton.getScene().getWindow();
                    NavigationHelper.navigateToStage(
                            stage,
                            "/fxml/AdminDashboard.fxml",
                            "Admin Dashboard – " + admin.getFullName() + " – CRTS"
                    );
                });
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    showError(e.getMessage());
                });
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Unexpected error during admin login", e);
                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    showError("An unexpected error occurred. Please verify database connectivity.");
                });
            }
        }, "admin-login-thread");
        authThread.setDaemon(true);
        authThread.start();
    }

    /**
     * Navigates back to the Welcome Screen.
     */
    @FXML
    private void handleBackToWelcome(ActionEvent event) {
        NavigationHelper.navigate(event, "/fxml/WelcomeScreen.fxml", "Complaint Registration and Tracking System");
    }

    private void showError(String message) {
        setStatus(message, "form-error-label");
    }

    private void setStatus(String message, String cssClass) {
        statusLabel.getStyleClass().removeAll(
                "status-checking", "status-ok", "status-error",
                "form-success-label", "form-error-label");
        statusLabel.setText(message);
        if (cssClass != null && !cssClass.isEmpty()) {
            statusLabel.getStyleClass().add(cssClass);
        }
    }
}
