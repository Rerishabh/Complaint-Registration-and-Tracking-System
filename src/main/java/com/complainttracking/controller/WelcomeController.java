package com.complainttracking.controller;

import com.complainttracking.database.DatabaseConnection;
import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WelcomeController – controller for WelcomeScreen.fxml.
 *
 * <p>Demonstrates JavaFX event handling as required by the academic
 * specification. All UI logic is kept separate from FXML and CSS.</p>
 */
public class WelcomeController implements Initializable {

    private static final Logger LOGGER =
            Logger.getLogger(WelcomeController.class.getName());

    // ---------------------------------------------------------------
    // FXML-injected nodes
    // ---------------------------------------------------------------
    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label statusLabel;
    @FXML private Button checkDbButton;
    @FXML private Button userLoginButton;
    @FXML private Button adminLoginButton;

    // ---------------------------------------------------------------
    // Initializable
    // ---------------------------------------------------------------
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        playFadeIn(titleLabel, 800);
        playFadeIn(subtitleLabel, 1200);
    }

    // ---------------------------------------------------------------
    // Event Handlers (JavaFX Event Handling demonstration)
    // ---------------------------------------------------------------

    /**
     * Handles the "Check Database" button click event.
     * Tests JDBC connectivity and updates the status label.
     */
    @FXML
    private void handleCheckDatabase(ActionEvent event) {
        checkDbButton.setDisable(true);
        statusLabel.setText("Checking database connection\u2026");
        statusLabel.getStyleClass().removeAll("status-ok", "status-error");
        statusLabel.getStyleClass().add("status-checking");

        // Run DB check on a background thread to keep UI responsive
        Thread dbCheckThread = new Thread(() -> {
            boolean connected = DatabaseConnection.testConnection();
            javafx.application.Platform.runLater(() -> {
                checkDbButton.setDisable(false);
                statusLabel.getStyleClass().removeAll("status-checking");

                if (connected) {
                    statusLabel.setText("\u2714  Database connected successfully.");
                    statusLabel.getStyleClass().add("status-ok");
                    LOGGER.log(Level.INFO, "DB connection check: SUCCESS");
                } else {
                    statusLabel.setText("\u2718  Database connection failed. Check MySQL and credentials.");
                    statusLabel.getStyleClass().add("status-error");
                    LOGGER.log(Level.WARNING, "DB connection check: FAILED");
                    showAlert(AlertType.WARNING,
                            "Database Not Connected",
                            "Could not connect to MySQL.",
                            "Ensure MySQL is running, the database 'complaint_tracking_db' exists, "
                            + "and credentials are set via DB_URL / DB_USER / DB_PASS environment variables.");
                }
            });
        }, "db-check-thread");
        dbCheckThread.setDaemon(true);
        dbCheckThread.start();
    }

    /**
     * Handles the "User Login" button click – navigates to User Login view.
     */
    @FXML
    private void handleUserLogin(ActionEvent event) {
        com.complainttracking.util.NavigationHelper.navigate(
                event, "/fxml/UserLogin.fxml", "User Login - CRTS"
        );
    }

    /**
     * Handles the "Admin Login" button click – navigates to Admin Login view.
     */
    @FXML
    private void handleAdminLogin(ActionEvent event) {
        com.complainttracking.util.NavigationHelper.navigate(
                event, "/fxml/AdminLogin.fxml", "Admin Login - CRTS"
        );
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void playFadeIn(javafx.scene.Node node, double delayMs) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(800), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.setDelay(Duration.millis(delayMs));
        ft.play();
    }

    private void showAlert(AlertType type, String title,
                           String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
