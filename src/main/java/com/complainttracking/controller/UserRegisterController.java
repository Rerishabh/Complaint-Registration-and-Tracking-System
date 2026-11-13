package com.complainttracking.controller;

import com.complainttracking.model.User;
import com.complainttracking.service.AuthenticationService;
import com.complainttracking.util.NavigationHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for UserRegister.fxml.
 * Handles new user account registration, field validation, and navigation.
 */
public class UserRegisterController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(UserRegisterController.class.getName());

    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private ComboBox<String> departmentComboBox;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button registerButton;
    @FXML private Label statusLabel;

    private final AuthenticationService authService = new AuthenticationService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        departmentComboBox.setItems(FXCollections.observableArrayList(
                "Computer Science & Engineering",
                "Information Technology",
                "Electronics & Communication",
                "Electrical Engineering",
                "Mechanical Engineering",
                "Civil Engineering",
                "Administration",
                "Student Affairs",
                "Hostel & Facilities",
                "Finance & Accounts",
                "Other"
        ));
        departmentComboBox.setPromptText("Select Department");
    }

    /**
     * Handles user registration submission.
     */
    @FXML
    private void handleRegister(ActionEvent event) {
        String fullName = fullNameField.getText();
        String email = emailField.getText();
        String phone = phoneField.getText();
        String department = departmentComboBox.getValue();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        registerButton.setDisable(true);
        statusLabel.setText("Registering account...");
        statusLabel.getStyleClass().removeAll("status-error", "status-ok");
        statusLabel.getStyleClass().add("status-checking");

        Thread regThread = new Thread(() -> {
            try {
                User registeredUser = authService.registerUser(
                        fullName, email, password, confirmPassword, phone, department
                );

                Platform.runLater(() -> {
                    registerButton.setDisable(false);
                    statusLabel.getStyleClass().removeAll(
                            "status-checking", "status-error", "status-ok", "form-error-label");
                    statusLabel.setText("Registration successful!");
                    statusLabel.getStyleClass().add("form-success-label");

                    Alert alert = new Alert(AlertType.INFORMATION);
                    alert.setTitle("Registration Successful");
                    alert.setHeaderText("Account Created Successfully!");
                    alert.setContentText("Welcome, " + registeredUser.getFullName() +
                            "! Your account has been registered. You can now log in with your email.");
                    alert.showAndWait();

                    // Navigate to User Login
                    NavigationHelper.navigate(event, "/fxml/UserLogin.fxml", "User Login - CRTS");
                });

            } catch (IllegalArgumentException e) {
                Platform.runLater(() -> {
                    registerButton.setDisable(false);
                    showError(e.getMessage());
                });
            } catch (IllegalStateException e) {
                Platform.runLater(() -> {
                    registerButton.setDisable(false);
                    showError(e.getMessage());
                });
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Unexpected error during user registration", e);
                Platform.runLater(() -> {
                    registerButton.setDisable(false);
                    showError("An unexpected error occurred. Please verify database connectivity.");
                });
            }
        }, "user-reg-thread");
        regThread.setDaemon(true);
        regThread.start();
    }

    /**
     * Navigates to User Login.
     */
    @FXML
    private void handleGoToLogin(ActionEvent event) {
        NavigationHelper.navigate(event, "/fxml/UserLogin.fxml", "User Login - CRTS");
    }

    /**
     * Navigates back to Welcome Screen.
     */
    @FXML
    private void handleBackToWelcome(ActionEvent event) {
        NavigationHelper.navigate(event, "/fxml/WelcomeScreen.fxml", "Complaint Registration and Tracking System");
    }

    private void showError(String message) {
        statusLabel.getStyleClass().removeAll(
                "status-checking", "status-ok", "form-success-label");
        statusLabel.setText(message);
        statusLabel.getStyleClass().add("form-error-label");
    }
}

