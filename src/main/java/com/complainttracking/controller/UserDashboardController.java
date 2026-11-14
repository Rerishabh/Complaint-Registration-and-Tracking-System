package com.complainttracking.controller;

import com.complainttracking.dao.ComplaintDAO;
import com.complainttracking.model.Complaint;
import com.complainttracking.model.ComplaintHistory;
import com.complainttracking.model.Notification;
import com.complainttracking.model.User;
import com.complainttracking.service.ComplaintService;
import com.complainttracking.service.NotificationService;
import com.complainttracking.util.NavigationHelper;
import com.complainttracking.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UserDashboardController – controller for UserDashboard.fxml.
 *
 * <p>Manages three logical views rendered inside a single content pane:</p>
 * <ol>
 *   <li>Overview – KPI cards + recent complaints</li>
 *   <li>Submit Complaint – validated submission form</li>
 *   <li>My Complaints – searchable, filterable TableView with history dialog</li>
 * </ol>
 */
public class UserDashboardController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(UserDashboardController.class.getName());
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd MMM yyyy, hh:mm a");

    // ---- Header ----
    @FXML private Label headerUserName;
    @FXML private Label headerDepartment;

    // ---- Sidebar nav buttons ----
    @FXML private Button navOverviewBtn;
    @FXML private Button navSubmitBtn;
    @FXML private Button navMyComplaintsBtn;
    @FXML private Button navNotificationsBtn;
    @FXML private Button navTrackBtn;

    // ---- Content sections (toggled visible) ----
    @FXML private VBox overviewSection;
    @FXML private VBox submitSection;
    @FXML private VBox myComplaintsSection;
    @FXML private VBox notificationsSection;

    // ---- Notification section controls ----
    @FXML private VBox notificationsListContainer;
    @FXML private Button markAllReadBtn;

    // ---- Overview KPI labels ----
    @FXML private Label kpiTotal;
    @FXML private Label kpiOpen;
    @FXML private Label kpiInProgress;
    @FXML private Label kpiResolved;

    // ---- Overview recent table ----
    @FXML private TableView<Complaint> recentTable;
    @FXML private TableColumn<Complaint, String> recentTrackingCol;
    @FXML private TableColumn<Complaint, String> recentTitleCol;
    @FXML private TableColumn<Complaint, String> recentStatusCol;
    @FXML private TableColumn<Complaint, String> recentDateCol;

    // ---- Submit form ----
    @FXML private TextField        titleField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private ComboBox<String> priorityCombo;
    @FXML private TextField        locationField;
    @FXML private TextArea         descriptionArea;
    @FXML private Button           submitButton;
    @FXML private Label            submitStatusLabel;

    // ---- My Complaints ----
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private TableView<Complaint>            complaintsTable;
    @FXML private TableColumn<Complaint, String> colTracking;
    @FXML private TableColumn<Complaint, String> colTitle;
    @FXML private TableColumn<Complaint, String> colCategory;
    @FXML private TableColumn<Complaint, String> colPriority;
    @FXML private TableColumn<Complaint, String> colStatus;
    @FXML private TableColumn<Complaint, String> colAssigned;
    @FXML private TableColumn<Complaint, String> colDate;

    // ---- Data ----
    private final ComplaintService complaintService = new ComplaintService();
    private final NotificationService notificationService = new NotificationService();
    private final ObservableList<Complaint> allComplaints = FXCollections.observableArrayList();
    private FilteredList<Complaint> filteredComplaints;

    // -----------------------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------------------

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null) {
            LOGGER.log(Level.SEVERE, "Dashboard opened without active session – this is a bug.");
            return;
        }

        // Header
        headerUserName.setText(user.getFullName());
        headerDepartment.setText(user.getDepartment() != null ? user.getDepartment() : "");

        // Seed dropdowns
        categoryCombo.setItems(FXCollections.observableArrayList(ComplaintService.CATEGORIES));
        categoryCombo.setPromptText("Select Category");
        priorityCombo.setItems(FXCollections.observableArrayList(ComplaintService.PRIORITY_LABELS));
        priorityCombo.setPromptText("Select Priority");
        statusFilterCombo.setItems(FXCollections.observableArrayList(
                "All", "OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "REJECTED"));
        statusFilterCombo.setValue("All");

        // Wire up table columns
        setupRecentTable();
        setupComplaintsTable();

        // Filtered list
        filteredComplaints = new FilteredList<>(allComplaints, c -> true);
        complaintsTable.setItems(filteredComplaints);

        // Filter listeners
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
        statusFilterCombo.valueProperty().addListener((obs, o, n) -> applyFilter());

        // Load data on background thread
        refreshData();

        // Show overview by default
        showSection(overviewSection, navOverviewBtn);
    }

    // -----------------------------------------------------------------------
    // Navigation Handlers
    // -----------------------------------------------------------------------

    @FXML private void handleNavOverview(ActionEvent e)      { showSection(overviewSection,      navOverviewBtn);      }
    @FXML private void handleNavSubmit(ActionEvent e)        { showSection(submitSection,        navSubmitBtn);        }
    @FXML private void handleNavMyComplaints(ActionEvent e)  {
        showSection(myComplaintsSection, navMyComplaintsBtn);
        if (allComplaints.isEmpty()) {
            refreshData();
        } else if (!complaintsTable.getColumns().isEmpty()) {
            complaintsTable.scrollToColumn(complaintsTable.getColumns().get(0));
        }
    }
    @FXML private void handleNavNotifications(ActionEvent e) { showSection(notificationsSection, navNotificationsBtn); refreshNotifications(); }

    @FXML
    private void handleQuickTrack(ActionEvent e) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Track Complaint");
        dialog.setHeaderText("Quick Complaint Tracking Lookup");
        dialog.setContentText("Enter Tracking Code (e.g. CMP-2026-5414):");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().trim().isEmpty()) {
            return;
        }
        String code = result.get().trim().toUpperCase();
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) return;

        ComplaintDAO dao = new ComplaintDAO();
        Complaint c = dao.findByTrackingCode(code);
        if (c == null) {
            showAlert(Alert.AlertType.INFORMATION, "Not Found", "No complaint found with tracking code: " + code);
            return;
        }
        // Enforce ownership: user can only track their own complaints inside User Portal
        if (c.getUserId() != currentUser.getUserId()) {
            showAlert(Alert.AlertType.WARNING, "Access Denied",
                    "This complaint belongs to another user. In the User Portal, you can only track your own complaints.");
            return;
        }
        showHistoryDialog(c);
    }

    @FXML
    private void handleLogout(ActionEvent e) {
        SessionManager.getInstance().logout();
        Stage stage = (Stage) navOverviewBtn.getScene().getWindow();
        NavigationHelper.navigateToStage(stage, "/fxml/WelcomeScreen.fxml",
                "Complaint Registration and Tracking System");
    }

    // -----------------------------------------------------------------------
    // Submit Complaint
    // -----------------------------------------------------------------------

    @FXML
    private void handleSubmitComplaint(ActionEvent e) {
        String title       = titleField.getText();
        String category    = categoryCombo.getValue();
        String priority    = priorityCombo.getValue();
        String description = descriptionArea.getText();

        submitButton.setDisable(true);
        setSubmitStatus("", null);

        Thread t = new Thread(() -> {
            try {
                Complaint c = complaintService.submitComplaint(
                        title, category, priority, description);

                Platform.runLater(() -> {
                    submitButton.setDisable(false);
                    setSubmitStatus(
                        "Complaint submitted! Tracking ID: " + c.getTrackingCode(),
                        "form-success-label");
                    clearSubmitForm();
                    allComplaints.add(0, c);
                    updateKpisFromComplaints();
                });
            } catch (IllegalArgumentException | IllegalStateException ex) {
                Platform.runLater(() -> {
                    submitButton.setDisable(false);
                    setSubmitStatus(ex.getMessage(), "form-error-label");
                });
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Unexpected error submitting complaint", ex);
                Platform.runLater(() -> {
                    submitButton.setDisable(false);
                    setSubmitStatus(
                        "Unexpected error. Please check database connectivity.",
                        "form-error-label");
                });
            }
        }, "submit-complaint-thread");
        t.setDaemon(true);
        t.start();
    }

    // -----------------------------------------------------------------------
    // View History
    // -----------------------------------------------------------------------

    @FXML
    private void handleViewHistory(ActionEvent e) {
        Complaint selected = complaintsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION,
                    "No Selection", "Please select a complaint from the table first.");
            return;
        }
        showHistoryDialog(selected);
    }

    // -----------------------------------------------------------------------
    // Data Refresh
    // -----------------------------------------------------------------------

    private void refreshData() {
        Thread t = new Thread(() -> {
            try {
                List<Complaint> complaints = complaintService.getMyComplaints();
                int unread = notificationService.getMyUnreadCount();

                // Compute KPI statistics in-memory directly from complaints.
                // Eliminates redundant DB queries and avoids thread collisions on the JDBC connection.
                int total = complaints.size();
                int open = 0;
                int inProgress = 0;
                int resolved = 0;
                for (Complaint c : complaints) {
                    String status = c.getStatus();
                    if (Complaint.STATUS_OPEN.equalsIgnoreCase(status)) {
                        open++;
                    } else if (Complaint.STATUS_IN_PROGRESS.equalsIgnoreCase(status)) {
                        inProgress++;
                    } else if (Complaint.STATUS_RESOLVED.equalsIgnoreCase(status)
                            || Complaint.STATUS_CLOSED.equalsIgnoreCase(status)) {
                        resolved++;
                    }
                }

                final int fOpen = open;
                final int fInProgress = inProgress;
                final int fResolved = resolved;

                Platform.runLater(() -> {
                    allComplaints.setAll(complaints);
                    kpiTotal.setText(String.valueOf(total));
                    kpiOpen.setText(String.valueOf(fOpen));
                    kpiInProgress.setText(String.valueOf(fInProgress));
                    kpiResolved.setText(String.valueOf(fResolved));

                    // Recent = top 5
                    recentTable.setItems(FXCollections.observableArrayList(
                            complaints.subList(0, Math.min(5, complaints.size()))));

                    if (!recentTable.getColumns().isEmpty()) {
                        recentTable.scrollToColumn(recentTable.getColumns().get(0));
                    }
                    updateNotificationBadgeText(unread);
                });
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error loading dashboard data", ex);
            }
        }, "dashboard-refresh-thread");
        t.setDaemon(true);
        t.start();
    }

    private void refreshKpis() {
        updateKpisFromComplaints();
    }

    private void updateKpisFromComplaints() {
        int total = allComplaints.size();
        int open = 0;
        int inProgress = 0;
        int resolved = 0;
        for (Complaint c : allComplaints) {
            String status = c.getStatus();
            if (Complaint.STATUS_OPEN.equalsIgnoreCase(status)) {
                open++;
            } else if (Complaint.STATUS_IN_PROGRESS.equalsIgnoreCase(status)) {
                inProgress++;
            } else if (Complaint.STATUS_RESOLVED.equalsIgnoreCase(status)
                    || Complaint.STATUS_CLOSED.equalsIgnoreCase(status)) {
                resolved++;
            }
        }
        kpiTotal.setText(String.valueOf(total));
        kpiOpen.setText(String.valueOf(open));
        kpiInProgress.setText(String.valueOf(inProgress));
        kpiResolved.setText(String.valueOf(resolved));

        recentTable.setItems(FXCollections.observableArrayList(
                allComplaints.subList(0, Math.min(5, allComplaints.size()))));
    }

    // -----------------------------------------------------------------------
    // Table Setup
    // -----------------------------------------------------------------------

    private void setupRecentTable() {
        recentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        recentTrackingCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTrackingCode()));
        recentTitleCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));
        recentStatusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        recentDateCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCreatedAt() != null
                        ? DATE_FMT.format(d.getValue().getCreatedAt()) : ""));
    }

    private void setupComplaintsTable() {
        complaintsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        colTracking.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTrackingCode()));
        colTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));
        colCategory.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategory()));
        colPriority.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPriority()));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        colAssigned.setCellValueFactory(d -> {
            String name = d.getValue().getAssignedAdminName();
            return new SimpleStringProperty((name != null && !name.trim().isEmpty()) ? name : "Not Assigned");
        });
        colDate.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCreatedAt() != null
                        ? DATE_FMT.format(d.getValue().getCreatedAt()) : ""));
    }

    // -----------------------------------------------------------------------
    // Filtering
    // -----------------------------------------------------------------------

    private void applyFilter() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String status  = statusFilterCombo.getValue();

        filteredComplaints.setPredicate(c -> {
            boolean matchStatus = "All".equals(status) || c.getStatus().equalsIgnoreCase(status);
            boolean matchKeyword = keyword.isEmpty()
                    || c.getTitle().toLowerCase().contains(keyword)
                    || c.getTrackingCode().toLowerCase().contains(keyword)
                    || c.getCategory().toLowerCase().contains(keyword);
            return matchStatus && matchKeyword;
        });
    }

    // -----------------------------------------------------------------------
    // History Dialog
    // -----------------------------------------------------------------------

    private void showHistoryDialog(Complaint complaint) {
        Thread t = new Thread(() -> {
            List<ComplaintHistory> history = complaintService.getHistory(complaint.getComplaintId());
            Platform.runLater(() -> buildHistoryDialog(complaint, history).showAndWait());
        }, "history-fetch-thread");
        t.setDaemon(true);
        t.start();
    }

    private Dialog<Void> buildHistoryDialog(Complaint c, List<ComplaintHistory> history) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Complaint Details");
        dialog.setHeaderText("Tracking ID: " + c.getTrackingCode());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.setPrefWidth(540);

        // Details section
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);
        addRow(grid, 0, "Title",          c.getTitle());
        addRow(grid, 1, "Category",       c.getCategory());
        addRow(grid, 2, "Priority",       c.getPriority());
        addRow(grid, 3, "Status",         c.getStatus());
        addRow(grid, 4, "Assigned Staff", (c.getAssignedAdminName() != null && !c.getAssignedAdminName().trim().isEmpty())
                ? c.getAssignedAdminName() : "Not Assigned");
        addRow(grid, 5, "Submitted",      c.getCreatedAt() != null ? DATE_FMT.format(c.getCreatedAt()) : "");
        addRow(grid, 6, "Last Updated",   c.getUpdatedAt() != null ? DATE_FMT.format(c.getUpdatedAt()) : "");

        Label descHead = new Label("Description");
        descHead.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label descText = new Label(c.getDescription());
        descText.setWrapText(true);
        descText.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155;");

        content.getChildren().addAll(grid, descHead, descText);

        // Resolution section if resolved or notes exist
        if (c.getResolutionNotes() != null && !c.getResolutionNotes().isEmpty()) {
            Separator sepRes = new Separator();
            Label resHead = new Label("Resolution Information");
            resHead.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #16A34A;");

            VBox resBox = new VBox(6);
            resBox.setStyle("-fx-background-color: #F0FDF4; -fx-border-color: #86EFAC; -fx-border-radius: 6; "
                    + "-fx-background-radius: 6; -fx-padding: 10;");

            Label notes = new Label(c.getResolutionNotes());
            notes.setWrapText(true);
            notes.setStyle("-fx-font-size: 13px; -fx-text-fill: #14532D;");

            resBox.getChildren().add(notes);
            content.getChildren().addAll(sepRes, resHead, resBox);
        }

        Separator sep = new Separator();

        Label histHead = new Label("Status History");
        histHead.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        VBox histItems = new VBox(8);
        if (history.isEmpty()) {
            histItems.getChildren().add(new Label("No history records found."));
        } else {
            for (ComplaintHistory h : history) {
                VBox item = new VBox(2);
                String arrow = (h.getOldStatus() == null ? "—" : h.getOldStatus())
                             + "  →  " + h.getNewStatus();
                Label statusLine = new Label(arrow);
                statusLine.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1D4ED8;");
                String meta = "By " + h.getChangedBy()
                        + (h.getChangedAt() != null ? "  ·  " + DATE_FMT.format(h.getChangedAt()) : "");
                Label metaLine = new Label(meta);
                metaLine.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
                if (h.getNote() != null && !h.getNote().isEmpty()) {
                    Label noteLine = new Label(h.getNote());
                    noteLine.setWrapText(true);
                    noteLine.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");
                    item.getChildren().addAll(statusLine, metaLine, noteLine);
                } else {
                    item.getChildren().addAll(statusLine, metaLine);
                }
                item.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; "
                            + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 12 8 12;");
                histItems.getChildren().add(item);
            }
        }

        content.getChildren().addAll(sep, histHead, histItems);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(500);
        scroll.setStyle("-fx-background-color: transparent;");

        dialog.getDialogPane().setContent(scroll);
        return dialog;
    }

    private void addRow(GridPane g, int row, String key, String value) {
        Label k = new Label(key);
        k.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #64748B;");
        Label v = new Label(value != null ? value : "—");
        v.setStyle("-fx-font-size: 12px; -fx-text-fill: #0F172A;");
        g.add(k, 0, row);
        g.add(v, 1, row);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void showSection(VBox section, Button activeNav) {
        overviewSection.setVisible(false);       overviewSection.setManaged(false);
        submitSection.setVisible(false);         submitSection.setManaged(false);
        myComplaintsSection.setVisible(false);   myComplaintsSection.setManaged(false);
        notificationsSection.setVisible(false);  notificationsSection.setManaged(false);

        section.setVisible(true);
        section.setManaged(true);

        for (Button b : new Button[]{navOverviewBtn, navSubmitBtn, navMyComplaintsBtn, navNotificationsBtn, navTrackBtn}) {
            if (b != null) {
                b.getStyleClass().removeAll("nav-btn-active");
            }
        }
        if (activeNav != null) {
            activeNav.getStyleClass().add("nav-btn-active");
        }
    }

    // -----------------------------------------------------------------------
    // Notifications Management
    // -----------------------------------------------------------------------

    @FXML
    private void handleMarkAllNotificationsRead(ActionEvent e) {
        Thread t = new Thread(() -> {
            try {
                boolean success = notificationService.markAllAsRead();
                if (success) {
                    List<Notification> list = notificationService.getMyNotifications();
                    int unread = notificationService.getMyUnreadCount();
                    Platform.runLater(() -> {
                        renderNotificationList(list);
                        updateNotificationBadgeText(unread);
                    });
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error marking all notifications as read", ex);
            }
        }, "mark-all-read-thread");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void handleRefreshNotifications(ActionEvent e) {
        refreshNotifications();
    }

    private void updateNotificationBadgeText(int unread) {
        if (navNotificationsBtn != null) {
            if (unread > 0) {
                navNotificationsBtn.setText("\uD83D\uDD14  Notifications (" + unread + ")");
            } else {
                navNotificationsBtn.setText("\uD83D\uDD14  Notifications");
            }
        }
    }

    private void refreshNotificationBadge() {
        Thread t = new Thread(() -> {
            try {
                int unread = notificationService.getMyUnreadCount();
                Platform.runLater(() -> updateNotificationBadgeText(unread));
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error loading unread count", ex);
            }
        }, "notif-count-thread");
        t.setDaemon(true);
        t.start();
    }

    private void refreshNotifications() {
        Thread t = new Thread(() -> {
            try {
                List<Notification> list = notificationService.getMyNotifications();
                int unread = notificationService.getMyUnreadCount();
                Platform.runLater(() -> {
                    renderNotificationList(list);
                    updateNotificationBadgeText(unread);
                });
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error loading notifications", ex);
            }
        }, "notif-list-thread");
        t.setDaemon(true);
        t.start();
    }

    private void renderNotificationList(List<Notification> list) {
        notificationsListContainer.getChildren().clear();
        if (list == null || list.isEmpty()) {
            Label empty = new Label("No notifications yet.");
            empty.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748B; -fx-padding: 20 0 0 4;");
            notificationsListContainer.getChildren().add(empty);
            return;
        }

        for (Notification n : list) {
            VBox card = new VBox(8);
            card.getStyleClass().add(n.isRead() ? "notification-card-read" : "notification-card-unread");

            // Header row
            HBox topRow = new HBox(10);
            topRow.setAlignment(Pos.CENTER_LEFT);

            Label titleLbl = new Label(n.getTitle());
            titleLbl.getStyleClass().add("notification-title");
            topRow.getChildren().add(titleLbl);

            if (!n.isRead()) {
                Label unreadBadge = new Label("NEW");
                unreadBadge.getStyleClass().add("badge-unread");
                topRow.getChildren().add(unreadBadge);
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label timeLbl = new Label(n.getCreatedAt() != null ? DATE_FMT.format(n.getCreatedAt()) : "");
            timeLbl.getStyleClass().add("notification-time");

            topRow.getChildren().addAll(spacer, timeLbl);

            // Message
            Label bodyLbl = new Label(n.getMessage());
            bodyLbl.getStyleClass().add("notification-body");
            bodyLbl.setWrapText(true);

            // Actions row
            HBox actionsRow = new HBox(8);
            actionsRow.setAlignment(Pos.CENTER_RIGHT);

            if (!n.isRead()) {
                Button markBtn = new Button("Mark as Read");
                markBtn.getStyleClass().add("btn-small-outline");
                markBtn.setOnAction(evt -> {
                    Thread th = new Thread(() -> {
                        try {
                            boolean ok = notificationService.markAsRead(n.getNotificationId());
                            if (ok) {
                                List<Notification> updatedList = notificationService.getMyNotifications();
                                int unread = notificationService.getMyUnreadCount();
                                Platform.runLater(() -> {
                                    renderNotificationList(updatedList);
                                    updateNotificationBadgeText(unread);
                                });
                            }
                        } catch (Exception ex) {
                            LOGGER.log(Level.WARNING, "Error marking notification as read", ex);
                        }
                    }, "mark-read-thread");
                    th.setDaemon(true);
                    th.start();
                });
                actionsRow.getChildren().add(markBtn);
            }

            Button viewBtn = new Button("View Complaint");
            viewBtn.getStyleClass().add("btn-small-primary");
            viewBtn.setOnAction(evt -> {
                showSection(myComplaintsSection, navMyComplaintsBtn);
                openComplaintById(n.getComplaintId());
            });
            actionsRow.getChildren().add(viewBtn);

            card.getChildren().addAll(topRow, bodyLbl, actionsRow);
            notificationsListContainer.getChildren().add(card);
        }
    }

    private void openComplaintById(int complaintId) {
        for (Complaint c : allComplaints) {
            if (c.getComplaintId() == complaintId) {
                complaintsTable.getSelectionModel().select(c);
                showHistoryDialog(c);
                return;
            }
        }
        Thread t = new Thread(() -> {
            try {
                ComplaintDAO dao = new ComplaintDAO();
                Complaint c = dao.findById(complaintId);
                if (c != null) {
                    Platform.runLater(() -> {
                        complaintsTable.getSelectionModel().select(c);
                        showHistoryDialog(c);
                    });
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error opening complaint by ID: " + complaintId, ex);
            }
        }, "open-complaint-thread");
        t.setDaemon(true);
        t.start();
    }

    private void setSubmitStatus(String msg, String cssClass) {
        submitStatusLabel.getStyleClass().removeAll("form-success-label", "form-error-label", "status-checking");
        submitStatusLabel.setText(msg);
        if (cssClass != null) submitStatusLabel.getStyleClass().add(cssClass);
    }

    private void clearSubmitForm() {
        titleField.clear();
        categoryCombo.setValue(null);
        priorityCombo.setValue(null);
        locationField.clear();
        descriptionArea.clear();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }
}

