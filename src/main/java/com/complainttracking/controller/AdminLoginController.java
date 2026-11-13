package com.complainttracking.controller;

import com.complainttracking.model.Admin;
import com.complainttracking.service.AuthenticationService;
import com.complainttracking.util.NavigationHelper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for AdminLogin.fxml.
 * Handles administrator authentication and session creation.
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
        statusLabel.setText("Authenticating administrator...");
        statusLabel.getStyleClass().removeAll("status-error", "status-ok");
        statusLabel.getStyleClass().add("status-checking");

        Thread authThread = new Thread(() -> {
            try {
                Admin admin = authService.loginAdmin(username, password);
                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    statusLabel.setText("Admin login successful!");
                    statusLabel.getStyleClass().removeAll("status-checking", "status-error");
                    statusLabel.getStyleClass().add("status-ok");

                    showConfirmationAlert(admin);
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
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-checking", "status-ok");
        statusLabel.getStyleClass().add("status-error");
    }

    private void showConfirmationAlert(Admin admin) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Admin Authentication Successful");
        alert.setHeaderText("Welcome, " + admin.getFullName() + "!");
        alert.setContentText(
                "Name: " + admin.getFullName() + "\n" +
                "Username: " + admin.getUsername() + "\n" +
                "Role: " + admin.getRole() + "\n\n" +
                "Your administrator session is active. Admin Dashboard functionality will be available in Phase 10."
        );
        alert.showAndWait();
    }
}

