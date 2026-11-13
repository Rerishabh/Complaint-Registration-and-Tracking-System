package com.complainttracking.controller;

import com.complainttracking.model.User;
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
 * Controller for UserLogin.fxml.
 * On successful login, navigates directly to UserDashboard.fxml.
 */
public class UserLoginController {

    private static final Logger LOGGER = Logger.getLogger(UserLoginController.class.getName());

    @FXML private TextField    emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button        loginButton;
    @FXML private Label         statusLabel;

    private final AuthenticationService authService = new AuthenticationService();

    @FXML
    private void handleLogin(ActionEvent event) {
        String email    = emailField.getText();
        String password = passwordField.getText();

        if (email == null || email.trim().isEmpty()) {
            showError("Please enter your email address.");
            emailField.requestFocus();
            return;
        }
        if (password == null || password.isEmpty()) {
            showError("Please enter your password.");
            passwordField.requestFocus();
            return;
        }

        loginButton.setDisable(true);
        setStatus("Authenticating…", "status-checking");

        Thread authThread = new Thread(() -> {
            try {
                User user = authService.loginUser(email, password);
                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    setStatus("Login successful! Loading dashboard…", "form-success-label");
                    // Navigate to UserDashboard
                    Stage stage = (Stage) loginButton.getScene().getWindow();
                    NavigationHelper.navigateToStage(
                            stage,
                            "/fxml/UserDashboard.fxml",
                            "Dashboard – " + user.getFullName() + " – CRTS");
                });
            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    showError(e.getMessage());
                });
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Unexpected error during user login", e);
                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    showError("An unexpected error occurred. Please check database connection.");
                });
            }
        }, "user-login-thread");
        authThread.setDaemon(true);
        authThread.start();
    }

    @FXML
    private void handleGoToRegister(ActionEvent event) {
        NavigationHelper.navigate(event, "/fxml/UserRegister.fxml", "User Registration - CRTS");
    }

    @FXML
    private void handleBackToWelcome(ActionEvent event) {
        NavigationHelper.navigate(event, "/fxml/WelcomeScreen.fxml",
                "Complaint Registration and Tracking System");
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
