package com.complainttracking.controller;

import com.complainttracking.dao.ComplaintDAO;
import com.complainttracking.dao.ComplaintHistoryDAO;
import com.complainttracking.database.DatabaseConnection;
import com.complainttracking.model.Complaint;
import com.complainttracking.model.ComplaintHistory;
import com.complainttracking.util.NavigationHelper;
import com.complainttracking.util.SessionManager;
import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
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
    @FXML private Button trackComplaintButton;

    // Sidebar navigation buttons and main scrollpane
    @FXML private ScrollPane welcomeScrollPane;
    @FXML private Button navHomeBtn;
    @FXML private Button navComplaintsBtn;
    @FXML private Button navAccountBtn;
    @FXML private Button navReportsBtn;

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd MMM yyyy, hh:mm a");

    // ---------------------------------------------------------------
    // Initializable
    // ---------------------------------------------------------------
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (titleLabel != null) {
            titleLabel.setOpacity(1.0);
            titleLabel.setVisible(true);
            titleLabel.setManaged(true);
        }
        if (subtitleLabel != null) {
            subtitleLabel.setOpacity(1.0);
            subtitleLabel.setVisible(true);
            subtitleLabel.setManaged(true);
        }
        checkInitialDatabaseConnection();
    }

    // ---------------------------------------------------------------
    // Sidebar Navigation Event Handlers
    // ---------------------------------------------------------------

    /**
     * Handles the "Home" navigation button click.
     * Displays the Welcome Screen overview and scrolls to top.
     */
    @FXML
    private void handleNavHome(ActionEvent event) {
        if (welcomeScrollPane != null) {
            welcomeScrollPane.setVvalue(0.0);
        }
        if (titleLabel != null) {
            titleLabel.setOpacity(1.0);
            titleLabel.setVisible(true);
        }
        if (subtitleLabel != null) {
            subtitleLabel.setOpacity(1.0);
            subtitleLabel.setVisible(true);
        }
        setActiveNav(navHomeBtn);
    }

    /**
     * Handles the "Complaints" navigation button click.
     * Navigates to User Login screen, or active dashboard if authenticated.
     */
    @FXML
    private void handleNavComplaints(ActionEvent event) {
        if (SessionManager.getInstance().isUserLoggedIn()) {
            NavigationHelper.navigate(event, "/fxml/UserDashboard.fxml", "User Dashboard - CRTS");
        } else if (SessionManager.getInstance().isAdminLoggedIn()) {
            NavigationHelper.navigate(event, "/fxml/AdminDashboard.fxml", "Admin Dashboard - CRTS");
        } else {
            NavigationHelper.navigate(event, "/fxml/UserLogin.fxml", "User Login - CRTS");
        }
    }

    /**
     * Handles the "My Account" navigation button click.
     * Navigates to User Login screen, or active dashboard if authenticated.
     */
    @FXML
    private void handleNavAccount(ActionEvent event) {
        if (SessionManager.getInstance().isUserLoggedIn()) {
            NavigationHelper.navigate(event, "/fxml/UserDashboard.fxml", "User Dashboard - CRTS");
        } else if (SessionManager.getInstance().isAdminLoggedIn()) {
            NavigationHelper.navigate(event, "/fxml/AdminDashboard.fxml", "Admin Dashboard - CRTS");
        } else {
            NavigationHelper.navigate(event, "/fxml/UserLogin.fxml", "User Login - CRTS");
        }
    }

    /**
     * Handles the "Reports" navigation button click.
     * Navigates to Admin Login screen, or Admin Dashboard if authenticated.
     */
    @FXML
    private void handleNavReports(ActionEvent event) {
        if (SessionManager.getInstance().isAdminLoggedIn()) {
            NavigationHelper.navigate(event, "/fxml/AdminDashboard.fxml", "Admin Dashboard - CRTS");
        } else {
            NavigationHelper.navigate(event, "/fxml/AdminLogin.fxml", "Admin Login - CRTS");
        }
    }

    private void setActiveNav(Button activeBtn) {
        Button[] navButtons = {navHomeBtn, navComplaintsBtn, navAccountBtn, navReportsBtn};
        for (Button btn : navButtons) {
            if (btn != null) {
                btn.getStyleClass().remove("nav-btn-active");
            }
        }
        if (activeBtn != null && !activeBtn.getStyleClass().contains("nav-btn-active")) {
            activeBtn.getStyleClass().add("nav-btn-active");
        }
    }

    private void checkInitialDatabaseConnection() {
        Thread dbThread = new Thread(() -> {
            boolean connected = DatabaseConnection.testConnection();
            javafx.application.Platform.runLater(() -> {
                if (statusLabel != null) {
                    statusLabel.getStyleClass().removeAll("status-checking", "status-ok", "status-error");
                    if (connected) {
                        statusLabel.setText("\u2714  Database connected successfully.");
                        statusLabel.getStyleClass().add("status-ok");
                    } else {
                        statusLabel.setText("Database not connected. Click below to verify.");
                    }
                }
            });
        }, "initial-db-check");
        dbThread.setDaemon(true);
        dbThread.start();
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
     * Handles the "User Login" button click – navigates to User Login view,
     * or User Dashboard if already logged in.
     */
    @FXML
    private void handleUserLogin(ActionEvent event) {
        if (SessionManager.getInstance().isUserLoggedIn()) {
            NavigationHelper.navigate(event, "/fxml/UserDashboard.fxml", "User Dashboard - CRTS");
        } else {
            NavigationHelper.navigate(event, "/fxml/UserLogin.fxml", "User Login - CRTS");
        }
    }

    /**
     * Handles the "Admin Login" button click – navigates to Admin Login view,
     * or Admin Dashboard if already logged in.
     */
    @FXML
    private void handleAdminLogin(ActionEvent event) {
        if (SessionManager.getInstance().isAdminLoggedIn()) {
            NavigationHelper.navigate(event, "/fxml/AdminDashboard.fxml", "Admin Dashboard - CRTS");
        } else {
            NavigationHelper.navigate(event, "/fxml/AdminLogin.fxml", "Admin Login - CRTS");
        }
    }

    /**
     * Handles the "Track Complaint" button click event.
     * Prompts for tracking code and performs a public tracking lookup
     * while protecting sensitive complainant personal details.
     */
    @FXML
    private void handleTrackComplaint(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Track Complaint");
        dialog.setHeaderText("Quick Complaint Tracking Lookup");
        dialog.setContentText("Enter Tracking Code (e.g. CMP-2026-5414):");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().trim().isEmpty()) {
            return;
        }

        String trackingCode = result.get().trim().toUpperCase();
        ComplaintDAO complaintDAO = new ComplaintDAO();
        Complaint complaint = complaintDAO.findByTrackingCode(trackingCode);

        if (complaint == null) {
            showAlert(AlertType.INFORMATION, "Complaint Not Found",
                    "No complaint matches code: " + trackingCode,
                    "Please double-check your tracking ID and try again.");
            return;
        }

        ComplaintHistoryDAO historyDAO = new ComplaintHistoryDAO();
        List<ComplaintHistory> history = historyDAO.findByComplaintId(complaint.getComplaintId());

        showPublicTrackingDialog(complaint, history);
    }

    /**
     * Displays public tracking details and status history timeline.
     * Enforces privacy: complainant email and contact info are withheld.
     */
    private void showPublicTrackingDialog(Complaint c, List<ComplaintHistory> history) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Tracking Status - " + c.getTrackingCode());
        dialog.setHeaderText("Complaint Status: " + c.getStatus());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.setPrefWidth(520);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);

        addGridRow(grid, 0, "Tracking ID", c.getTrackingCode());
        addGridRow(grid, 1, "Title", c.getTitle());
        addGridRow(grid, 2, "Category", c.getCategory());
        addGridRow(grid, 3, "Priority", c.getPriority());
        addGridRow(grid, 4, "Current Status", c.getStatus());
        addGridRow(grid, 5, "Submitted On", c.getCreatedAt() != null ? DATE_FMT.format(c.getCreatedAt()) : "—");
        addGridRow(grid, 6, "Last Updated", c.getUpdatedAt() != null ? DATE_FMT.format(c.getUpdatedAt()) : "—");

        Label privacyNotice = new Label("Complainant details and private resolution notes are protected. Log in to User Portal for full access.");
        privacyNotice.setWrapText(true);
        privacyNotice.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B; -fx-font-style: italic;");

        Separator sep = new Separator();
        Label histHead = new Label("Status Timeline");
        histHead.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1E293B;");

        VBox histItems = new VBox(8);
        if (history == null || history.isEmpty()) {
            histItems.getChildren().add(new Label("No timeline records available."));
        } else {
            for (ComplaintHistory h : history) {
                VBox item = new VBox(3);
                String headerText;
                if (h.getOldStatus() == null) {
                    headerText = "SUBMITTED  \u2192  " + h.getNewStatus();
                } else if (h.isAssignmentEvent()) {
                    if (h.getNote() != null && h.getNote().toLowerCase().contains("removed")) {
                        headerText = "Assignment Removed";
                    } else {
                        headerText = "Staff Assigned";
                    }
                } else if (h.getOldStatus().equalsIgnoreCase(h.getNewStatus())) {
                    headerText = "Status: " + h.getNewStatus();
                } else {
                    headerText = h.getOldStatus() + "  \u2192  " + h.getNewStatus();
                }

                Label statusLine = new Label(headerText);
                statusLine.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1D4ED8;");

                String meta = (h.getChangedAt() != null ? DATE_FMT.format(h.getChangedAt()) : "");
                Label metaLine = new Label(meta);
                metaLine.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

                item.getChildren().addAll(statusLine, metaLine);
                item.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; "
                            + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 12;");
                histItems.getChildren().add(item);
            }
        }

        content.getChildren().addAll(grid, privacyNotice, sep, histHead, histItems);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(480);
        scroll.setStyle("-fx-background-color: transparent;");

        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    private void addGridRow(GridPane g, int row, String key, String value) {
        Label k = new Label(key);
        k.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #64748B;");
        Label v = new Label(value != null ? value : "—");
        v.setStyle("-fx-font-size: 12px; -fx-text-fill: #0F172A;");
        g.add(k, 0, row);
        g.add(v, 1, row);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void showAlert(AlertType type, String title,
                           String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
