package com.complainttracking.controller;

import com.complainttracking.model.Admin;
import com.complainttracking.model.Complaint;
import com.complainttracking.model.ComplaintHistory;
import com.complainttracking.model.DepartmentStat;
import com.complainttracking.model.ReportSummary;
import com.complainttracking.model.Resolution;
import com.complainttracking.model.StaffPerformance;
import com.complainttracking.service.AdminComplaintService;
import com.complainttracking.service.ComplaintService;
import com.complainttracking.service.ReportService;
import com.complainttracking.service.ResolutionService;
import com.complainttracking.util.NavigationHelper;
import com.complainttracking.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for AdminDashboard.fxml.
 * Manages Administrator KPI overview, full complaint listing, search, multi-criteria filtering,
 * complaint status management, assignment, resolution tracking, and comprehensive audit history.
 */
public class AdminDashboardController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(AdminDashboardController.class.getName());
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd MMM yyyy, hh:mm a");

    // ---- Header ----
    @FXML private Label headerAdminName;
    @FXML private Label headerAdminRole;

    // ---- Sidebar nav buttons ----
    @FXML private Button navOverviewBtn;
    @FXML private Button navAllComplaintsBtn;
    @FXML private Button navReportsBtn;

    // ---- Content sections ----
    @FXML private VBox overviewSection;
    @FXML private VBox allComplaintsSection;
    @FXML private VBox reportsSection;

    // ---- Overview KPI labels ----
    @FXML private Label kpiTotal;
    @FXML private Label kpiOpen;
    @FXML private Label kpiInProgress;
    @FXML private Label kpiResolved;
    @FXML private Label kpiRejected;

    // ---- Overview Recent Table ----
    @FXML private TableView<Complaint> recentTable;
    @FXML private TableColumn<Complaint, String> recentTrackingCol;
    @FXML private TableColumn<Complaint, String> recentTitleCol;
    @FXML private TableColumn<Complaint, String> recentComplainantCol;
    @FXML private TableColumn<Complaint, String> recentCategoryCol;
    @FXML private TableColumn<Complaint, String> recentPriorityCol;
    @FXML private TableColumn<Complaint, String> recentStatusCol;
    @FXML private TableColumn<Complaint, String> recentDateCol;

    // ---- All Complaints Section Controls ----
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private ComboBox<String> categoryFilterCombo;
    @FXML private ComboBox<String> priorityFilterCombo;

    @FXML private TableView<Complaint> complaintsTable;
    @FXML private TableColumn<Complaint, Number> colId;
    @FXML private TableColumn<Complaint, String> colTracking;
    @FXML private TableColumn<Complaint, String> colComplainant;
    @FXML private TableColumn<Complaint, String> colCategory;
    @FXML private TableColumn<Complaint, String> colTitle;
    @FXML private TableColumn<Complaint, String> colPriority;
    @FXML private TableColumn<Complaint, String> colStatus;
    @FXML private TableColumn<Complaint, String> colAssigned;
    @FXML private TableColumn<Complaint, String> colDate;

    // ---- Reports Filter Toolbar ----
    @FXML private DatePicker reportStartDatePicker;
    @FXML private DatePicker reportEndDatePicker;
    @FXML private Button btnApplyReportFilter;
    @FXML private Button btnResetReportFilter;
    @FXML private Button btnExportReportCsv;
    @FXML private Label reportStatusLabel;

    // ---- Reports Executive KPI Cards ----
    @FXML private Label kpiReportTotal;
    @FXML private Label kpiReportResolutionRate;
    @FXML private Label kpiReportResolved;
    @FXML private Label kpiReportPending;
    @FXML private Label kpiReportAvgTime;

    // ---- Reports Charts ----
    @FXML private PieChart categoryPieChart;
    @FXML private BarChart<String, Number> statusBarChart;
    @FXML private BarChart<String, Number> departmentBarChart;
    @FXML private LineChart<String, Number> monthlyTrendsChart;

    @FXML private CategoryAxis statusXAxis;
    @FXML private CategoryAxis departmentXAxis;
    @FXML private CategoryAxis monthlyTrendsXAxis;

    // ---- Staff Performance Table ----
    @FXML private TableView<StaffPerformance> staffPerformanceTable;
    @FXML private TableColumn<StaffPerformance, String> colStaffName;
    @FXML private TableColumn<StaffPerformance, String> colStaffRole;
    @FXML private TableColumn<StaffPerformance, Number> colStaffAssigned;
    @FXML private TableColumn<StaffPerformance, Number> colStaffResolved;
    @FXML private TableColumn<StaffPerformance, String> colStaffRate;
    @FXML private TableColumn<StaffPerformance, String> colStaffAvgTime;

    // ---- Phase 9 Service & Cache ----
    private final ReportService reportService = new ReportService();
    private ReportSummary currentReportSummary;

    // ---- Service & Data ----
    private final AdminComplaintService adminService = new AdminComplaintService();
    private final ObservableList<Complaint> allComplaints = FXCollections.observableArrayList();
    private FilteredList<Complaint> filteredComplaints;

    // -----------------------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------------------

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Admin admin = SessionManager.getInstance().getCurrentAdmin();
        if (admin == null) {
            LOGGER.log(Level.SEVERE, "AdminDashboard accessed without active admin session.");
            return;
        }

        // Header info
        headerAdminName.setText(admin.getFullName());
        headerAdminRole.setText("Role: " + admin.getRole().toUpperCase());

        // Setup filter dropdowns
        statusFilterCombo.setItems(FXCollections.observableArrayList(
                "All Statuses", "OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "REJECTED"));
        statusFilterCombo.setValue("All Statuses");

        ObservableList<String> categories = FXCollections.observableArrayList("All Categories");
        categories.addAll(ComplaintService.CATEGORIES);
        categoryFilterCombo.setItems(categories);
        categoryFilterCombo.setValue("All Categories");

        priorityFilterCombo.setItems(FXCollections.observableArrayList(
                "All Priorities", "LOW", "MEDIUM", "HIGH", "CRITICAL"));
        priorityFilterCombo.setValue("All Priorities");

        // Setup Tables
        setupRecentTable();
        setupComplaintsTable();
        setupStaffPerformanceTable();
        setupCharts();

        // Setup FilteredList
        filteredComplaints = new FilteredList<>(allComplaints, c -> true);
        complaintsTable.setItems(filteredComplaints);

        // Wire filter listeners
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
        statusFilterCombo.valueProperty().addListener((obs, o, n) -> applyFilter());
        categoryFilterCombo.valueProperty().addListener((obs, o, n) -> applyFilter());
        priorityFilterCombo.valueProperty().addListener((obs, o, n) -> applyFilter());

        // Load initial data
        refreshDashboardData();

        // Default to overview section
        showSection(overviewSection, navOverviewBtn);
    }

    // -----------------------------------------------------------------------
    // Navigation Handlers
    // -----------------------------------------------------------------------

    @FXML
    private void handleNavOverview(ActionEvent e) {
        showSection(overviewSection, navOverviewBtn);
        refreshDashboardData();
    }

    @FXML
    private void handleNavAllComplaints(ActionEvent e) {
        showSection(allComplaintsSection, navAllComplaintsBtn);
        refreshAllComplaints();
    }

    @FXML
    private void handleNavReports(ActionEvent e) {
        showSection(reportsSection, navReportsBtn);
        if (currentReportSummary == null) {
            refreshReportsData();
        }
    }

    @FXML
    private void handleLogout(ActionEvent e) {
        SessionManager.getInstance().logout();
        Stage stage = (Stage) navOverviewBtn.getScene().getWindow();
        NavigationHelper.navigateToStage(stage, "/fxml/WelcomeScreen.fxml",
                "Complaint Registration and Tracking System");
    }

    @FXML
    private void handleResetFilters(ActionEvent e) {
        searchField.clear();
        statusFilterCombo.setValue("All Statuses");
        categoryFilterCombo.setValue("All Categories");
        priorityFilterCombo.setValue("All Priorities");
    }

    // -----------------------------------------------------------------------
    // Phase 7 Actions: Update Status, Assign, Resolve, View Details
    // -----------------------------------------------------------------------

    @FXML
    private void handleUpdateStatus(ActionEvent e) {
        Complaint selected = complaintsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection",
                    "Please select a complaint from the table to update its status.");
            return;
        }

        if ("CLOSED".equalsIgnoreCase(selected.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Terminal Status",
                    "This complaint is CLOSED and cannot transition to any other status.");
            return;
        }

        // Determine valid transitions
        List<String> nextStatuses = new ArrayList<>();
        for (String s : new String[]{"OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "REJECTED"}) {
            if (ResolutionService.isValidStatusTransition(selected.getStatus(), s)) {
                nextStatuses.add(s);
            }
        }

        if (nextStatuses.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "No Transitions Available",
                    "No valid status transitions are available from the current status (" + selected.getStatus() + ").");
            return;
        }

        // Build Dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Update Complaint Status");
        dialog.setHeaderText("Update status for [" + selected.getTrackingCode() + "] " + selected.getTitle());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.setPrefWidth(420);

        Label currentLbl = new Label("Current Status: " + selected.getStatus());
        currentLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B;");

        Label targetLbl = new Label("New Target Status *:");
        targetLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        ComboBox<String> statusCombo = new ComboBox<>(FXCollections.observableArrayList(nextStatuses));
        statusCombo.setValue(nextStatuses.get(0));
        statusCombo.setMaxWidth(Double.MAX_VALUE);

        Label remarksLbl = new Label("Remarks / Reason * (required for Rejection / Reopening):");
        remarksLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        TextArea remarksArea = new TextArea();
        remarksArea.setPromptText("Enter reasons or remarks regarding this status change...");
        remarksArea.setPrefRowCount(3);
        remarksArea.setWrapText(true);

        box.getChildren().addAll(currentLbl, targetLbl, statusCombo, remarksLbl, remarksArea);
        dialog.getDialogPane().setContent(box);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String newStatus = statusCombo.getValue();
            String remarks = remarksArea.getText() != null ? remarksArea.getText().trim() : "";

            if (newStatus == null || newStatus.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Validation Error", "Please select a valid new status.");
                return;
            }

            // Enforce remarks for REJECTED or Reopening
            if ("REJECTED".equalsIgnoreCase(newStatus) && remarks.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Validation Error", "A remark/reason is mandatory when rejecting a complaint.");
                return;
            }

            // Execute in background
            Thread t = new Thread(() -> {
                try {
                    boolean ok = adminService.updateComplaintStatus(selected.getComplaintId(), newStatus, remarks);
                    Platform.runLater(() -> {
                        if (ok) {
                            showAlert(Alert.AlertType.INFORMATION, "Success",
                                    "Complaint [" + selected.getTrackingCode() + "] status updated to " + newStatus + ".");
                            refreshAllComplaints();
                            refreshDashboardData();
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Error",
                                    "Failed to update complaint status. Please verify the transition rules and try again.");
                        }
                    });
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Cannot Update Status", ex.getMessage()));
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Unexpected error updating complaint status", ex);
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error",
                            "An unexpected error occurred: " + ex.getMessage()));
                }
            }, "admin-status-update");
            t.setDaemon(true);
            t.start();
        }
    }

    @FXML
    private void handleAssignComplaint(ActionEvent e) {
        Complaint selected = complaintsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection",
                    "Please select a complaint from the table to assign staff.");
            return;
        }

        // Load available admins
        Thread t = new Thread(() -> {
            List<Admin> admins = adminService.getAvailableAssignees();
            Platform.runLater(() -> showAssignDialog(selected, admins));
        }, "admin-fetch-assignees");
        t.setDaemon(true);
        t.start();
    }

    private void showAssignDialog(Complaint c, List<Admin> admins) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Assign Complaint");
        dialog.setHeaderText("Assign Staff Member to [" + c.getTrackingCode() + "]");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.setPrefWidth(440);

        String currentAssigned = (c.getAssignedAdminName() != null && !c.getAssignedAdminName().trim().isEmpty())
                ? c.getAssignedAdminName() : "Currently Unassigned";
        Label curLbl = new Label("Current Assignment: " + currentAssigned);
        curLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B;");

        Label selectLbl = new Label("Select Staff Member:");
        selectLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");

        ComboBox<Admin> adminCombo = new ComboBox<>(FXCollections.observableArrayList(admins));
        adminCombo.setMaxWidth(Double.MAX_VALUE);
        adminCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Admin item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getFullName() + " (" + item.getRole() + ")");
            }
        });
        adminCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Admin item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getFullName() + " (" + item.getRole() + ")");
            }
        });

        // Pre-select if currently assigned
        if (c.getAssignedAdminId() != null) {
            for (Admin a : admins) {
                if (a.getAdminId() == c.getAssignedAdminId()) {
                    adminCombo.setValue(a);
                    break;
                }
            }
        }

        CheckBox unassignCheck = new CheckBox("Unassign (Clear currently assigned staff)");
        unassignCheck.selectedProperty().addListener((obs, oldV, isChecked) -> {
            adminCombo.setDisable(isChecked);
        });

        Label noteLbl = new Label("Assignment Note / Instructions (Optional):");
        noteLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        TextArea noteArea = new TextArea();
        noteArea.setPromptText("Enter any special instructions or handoff notes...");
        noteArea.setPrefRowCount(3);
        noteArea.setWrapText(true);

        box.getChildren().addAll(curLbl, selectLbl, adminCombo, unassignCheck, noteLbl, noteArea);
        dialog.getDialogPane().setContent(box);

        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            Integer targetAdminId = null;
            String targetAdminName = "Unassigned";

            if (!unassignCheck.isSelected()) {
                Admin chosen = adminCombo.getValue();
                if (chosen == null) {
                    showAlert(Alert.AlertType.WARNING, "No Selection",
                            "Please select an administrator to assign or check 'Unassign'.");
                    return;
                }
                targetAdminId = chosen.getAdminId();
                targetAdminName = chosen.getFullName();
            }

            final Integer finalAdminId = targetAdminId;
            final String finalName = targetAdminName;
            final String note = noteArea.getText() != null ? noteArea.getText().trim() : "";

            Thread assignThread = new Thread(() -> {
                boolean ok = adminService.assignComplaint(c.getComplaintId(), finalAdminId, note);
                Platform.runLater(() -> {
                    if (ok) {
                        showAlert(Alert.AlertType.INFORMATION, "Success",
                                "Complaint [" + c.getTrackingCode() + "] assigned to: " + finalName);
                        refreshAllComplaints();
                        refreshDashboardData();
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Error",
                                "Failed to update complaint assignment. Please try again.");
                    }
                });
            }, "admin-assign-worker");
            assignThread.setDaemon(true);
            assignThread.start();
        }
    }

    @FXML
    private void handleResolveComplaint(ActionEvent e) {
        Complaint selected = complaintsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection",
                    "Please select a complaint from the table to resolve.");
            return;
        }

        if ("RESOLVED".equalsIgnoreCase(selected.getStatus()) || "CLOSED".equalsIgnoreCase(selected.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Already Resolved",
                    "This complaint is already in " + selected.getStatus() + " status.");
            return;
        }

        if ("REJECTED".equalsIgnoreCase(selected.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Invalid Status",
                    "A REJECTED complaint cannot be directly resolved. It must be reconsidered to OPEN first.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Resolve Complaint");
        dialog.setHeaderText("Provide Resolution for [" + selected.getTrackingCode() + "]");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.setPrefWidth(460);

        Label infoLbl = new Label("Title: " + selected.getTitle() + "\nCategory: " + selected.getCategory()
                + "  |  Current Status: " + selected.getStatus());
        infoLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");

        Label notesLbl = new Label("Resolution Notes * (minimum 10 characters):");
        notesLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B;");

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Describe the corrective actions taken, root cause resolution, and instructions for the user...");
        notesArea.setPrefRowCount(5);
        notesArea.setWrapText(true);

        box.getChildren().addAll(infoLbl, notesLbl, notesArea);
        dialog.getDialogPane().setContent(box);

        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            String notes = notesArea.getText() != null ? notesArea.getText().trim() : "";
            if (notes.length() < 10) {
                showAlert(Alert.AlertType.ERROR, "Validation Error",
                        "Resolution notes must be at least 10 characters long describing the resolution.");
                return;
            }

            Thread resThread = new Thread(() -> {
                boolean ok = adminService.resolveComplaint(selected.getComplaintId(), notes);
                Platform.runLater(() -> {
                    if (ok) {
                        showAlert(Alert.AlertType.INFORMATION, "Success",
                                "Complaint [" + selected.getTrackingCode() + "] has been successfully marked as RESOLVED.");
                        refreshAllComplaints();
                        refreshDashboardData();
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Resolution Failed",
                                "Failed to resolve complaint. Ensure the status allows resolution and try again.");
                    }
                });
            }, "admin-resolve-worker");
            resThread.setDaemon(true);
            resThread.start();
        }
    }

    @FXML
    private void handleViewDetails(ActionEvent e) {
        Complaint selected = complaintsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection",
                    "Please select a complaint from the table to view details.");
            return;
        }
        showComplaintDetailsDialog(selected);
    }

    // -----------------------------------------------------------------------
    // Data Loading & Refresh
    // -----------------------------------------------------------------------

    private void refreshDashboardData() {
        Thread t = new Thread(() -> {
            try {
                Map<String, Integer> stats = adminService.getDashboardStats();
                List<Complaint> recent = adminService.getRecentComplaints(5);

                Platform.runLater(() -> {
                    kpiTotal.setText(String.valueOf(stats.getOrDefault("TOTAL", 0)));
                    kpiOpen.setText(String.valueOf(stats.getOrDefault("OPEN", 0)));
                    kpiInProgress.setText(String.valueOf(stats.getOrDefault("IN_PROGRESS", 0)));
                    kpiResolved.setText(String.valueOf(stats.getOrDefault("RESOLVED", 0)));
                    kpiRejected.setText(String.valueOf(stats.getOrDefault("REJECTED", 0)));

                    recentTable.setItems(FXCollections.observableArrayList(recent));
                    if (!recentTable.getColumns().isEmpty()) {
                        recentTable.scrollToColumn(recentTable.getColumns().get(0));
                    }
                });
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error loading admin dashboard stats", ex);
            }
        }, "admin-stats-thread");
        t.setDaemon(true);
        t.start();
    }

    private void refreshAllComplaints() {
        Thread t = new Thread(() -> {
            try {
                List<Complaint> list = adminService.getAllComplaints();
                Platform.runLater(() -> {
                    allComplaints.setAll(list);
                    if (!complaintsTable.getColumns().isEmpty()) {
                        complaintsTable.scrollToColumn(complaintsTable.getColumns().get(0));
                    }
                });
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error loading all complaints", ex);
            }
        }, "admin-complaints-thread");
        t.setDaemon(true);
        t.start();
    }

    // -----------------------------------------------------------------------
    // Filtering Logic
    // -----------------------------------------------------------------------

    private void applyFilter() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String status  = statusFilterCombo.getValue();
        String category = categoryFilterCombo.getValue();
        String priority = priorityFilterCombo.getValue();

        filteredComplaints.setPredicate(c -> {
            // Status match
            boolean matchStatus = (status == null || "All Statuses".equalsIgnoreCase(status)
                    || c.getStatus().equalsIgnoreCase(status));

            // Category match
            boolean matchCategory = (category == null || "All Categories".equalsIgnoreCase(category)
                    || c.getCategory().equalsIgnoreCase(category));

            // Priority match
            boolean matchPriority = (priority == null || "All Priorities".equalsIgnoreCase(priority)
                    || c.getPriority().equalsIgnoreCase(priority));

            // Keyword match (Tracking ID, Title, Complainant Name, Email)
            boolean matchKeyword = keyword.isEmpty()
                    || (c.getTrackingCode() != null && c.getTrackingCode().toLowerCase().contains(keyword))
                    || (c.getTitle() != null && c.getTitle().toLowerCase().contains(keyword))
                    || (c.getComplainantName() != null && c.getComplainantName().toLowerCase().contains(keyword))
                    || (c.getComplainantEmail() != null && c.getComplainantEmail().toLowerCase().contains(keyword));

            return matchStatus && matchCategory && matchPriority && matchKeyword;
        });
    }

    // -----------------------------------------------------------------------
    // Table Setup
    // -----------------------------------------------------------------------

    private void setupRecentTable() {
        recentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        recentTrackingCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTrackingCode()));
        recentTitleCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));
        recentComplainantCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getComplainantName() != null ? d.getValue().getComplainantName() : "User #" + d.getValue().getUserId()));
        recentCategoryCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategory()));
        recentPriorityCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPriority()));
        recentStatusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        recentDateCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCreatedAt() != null ? DATE_FMT.format(d.getValue().getCreatedAt()) : ""));
    }

    private void setupComplaintsTable() {
        complaintsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        colId.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getComplaintId()));
        colTracking.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTrackingCode()));
        colComplainant.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getComplainantName() != null ? d.getValue().getComplainantName() : "User #" + d.getValue().getUserId()));
        colCategory.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategory()));
        colTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));
        colPriority.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPriority()));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        colAssigned.setCellValueFactory(d -> {
            String name = d.getValue().getAssignedAdminName();
            return new SimpleStringProperty((name != null && !name.trim().isEmpty()) ? name : "Not Assigned");
        });
        colDate.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCreatedAt() != null ? DATE_FMT.format(d.getValue().getCreatedAt()) : ""));
    }

    // -----------------------------------------------------------------------
    // Complaint Details & History Dialog
    // -----------------------------------------------------------------------

    private void showComplaintDetailsDialog(Complaint c) {
        Thread t = new Thread(() -> {
            List<ComplaintHistory> history = adminService.getComplaintHistory(c.getComplaintId());
            Resolution res = adminService.getResolutionDetails(c.getComplaintId());
            Platform.runLater(() -> buildDetailsDialog(c, history, res).showAndWait());
        }, "admin-history-thread");
        t.setDaemon(true);
        t.start();
    }

    private Dialog<Void> buildDetailsDialog(Complaint c, List<ComplaintHistory> history, Resolution res) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Complaint Inspection – Admin Portal");
        dialog.setHeaderText("Tracking ID: " + c.getTrackingCode());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.setPrefWidth(580);

        // Complainant & Complaint Metadata
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(8);

        addRow(grid, 0, "Complaint ID", String.valueOf(c.getComplaintId()));
        addRow(grid, 1, "Tracking ID", c.getTrackingCode());
        addRow(grid, 2, "Title", c.getTitle());
        addRow(grid, 3, "Category", c.getCategory());
        addRow(grid, 4, "Priority", c.getPriority());
        addRow(grid, 5, "Status", c.getStatus());
        addRow(grid, 6, "Assigned Staff", (c.getAssignedAdminName() != null && !c.getAssignedAdminName().trim().isEmpty())
                ? c.getAssignedAdminName() : "Unassigned");
        addRow(grid, 7, "Assigned Date", c.getAssignedAt() != null ? DATE_FMT.format(c.getAssignedAt()) : "—");
        addRow(grid, 8, "Complainant Name", c.getComplainantName() != null ? c.getComplainantName() : "User #" + c.getUserId());
        addRow(grid, 9, "Email", c.getComplainantEmail() != null ? c.getComplainantEmail() : "—");
        addRow(grid, 10, "Department", c.getComplainantDepartment() != null ? c.getComplainantDepartment() : "—");
        addRow(grid, 11, "Submission Date", c.getCreatedAt() != null ? DATE_FMT.format(c.getCreatedAt()) : "—");
        addRow(grid, 12, "Last Updated", c.getUpdatedAt() != null ? DATE_FMT.format(c.getUpdatedAt()) : "—");

        Label descHead = new Label("Description");
        descHead.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0F172A;");
        Label descText = new Label(c.getDescription());
        descText.setWrapText(true);
        descText.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155; -fx-background-color: #F1F5F9; " +
                "-fx-padding: 10 12 10 12; -fx-background-radius: 6;");

        content.getChildren().addAll(grid, descHead, descText);

        // If resolution exists, show prominent resolution box
        if (res != null || (c.getResolutionNotes() != null && !c.getResolutionNotes().isEmpty())) {
            Separator sepRes = new Separator();
            Label resHead = new Label("Resolution Details");
            resHead.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #16A34A;");

            VBox resBox = new VBox(6);
            resBox.setStyle("-fx-background-color: #F0FDF4; -fx-border-color: #86EFAC; -fx-border-radius: 6; " +
                    "-fx-background-radius: 6; -fx-padding: 12;");

            String resolver = (res != null && res.getAdminName() != null) ? res.getAdminName() : "Staff Admin";
            String resolvedAtStr = (res != null && res.getResolvedAt() != null) ? DATE_FMT.format(res.getResolvedAt()) : "—";
            Label resMeta = new Label("Resolved By: " + resolver + "  ·  Date: " + resolvedAtStr);
            resMeta.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #166534;");

            String notes = res != null ? res.getResolutionNotes() : c.getResolutionNotes();
            Label resNotes = new Label(notes);
            resNotes.setWrapText(true);
            resNotes.setStyle("-fx-font-size: 13px; -fx-text-fill: #14532D;");

            resBox.getChildren().addAll(resMeta, resNotes);
            content.getChildren().addAll(sepRes, resHead, resBox);
        }

        Separator sep = new Separator();

        Label histHead = new Label("Status History Timeline");
        histHead.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        VBox histItems = new VBox(8);
        if (history == null || history.isEmpty()) {
            histItems.getChildren().add(new Label("No status change records found."));
        } else {
            for (ComplaintHistory h : history) {
                VBox item = new VBox(3);
                String headerText;
                if (h.getOldStatus() == null) {
                    headerText = "—  →  " + h.getNewStatus();
                } else if (h.isAssignmentEvent()) {
                    if (h.getNote() != null && h.getNote().toLowerCase().contains("removed")) {
                        headerText = "Assignment Removed";
                    } else {
                        headerText = "Staff Assigned";
                    }
                } else if (h.getOldStatus().equalsIgnoreCase(h.getNewStatus())) {
                    headerText = "Status: " + h.getNewStatus();
                } else {
                    headerText = h.getOldStatus() + "  →  " + h.getNewStatus();
                }
                Label statusLine = new Label(headerText);
                statusLine.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1D4ED8;");

                String meta = "By: " + h.getChangedBy() + (h.getChangedAt() != null ? "  ·  " + DATE_FMT.format(h.getChangedAt()) : "");
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

                item.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; " +
                        "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 12 8 12;");
                histItems.getChildren().add(item);
            }
        }

        content.getChildren().addAll(sep, histHead, histItems);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(520);
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
    // Section Switching & Alert Helper
    // -----------------------------------------------------------------------

    private void showSection(VBox section, Button activeNav) {
        overviewSection.setVisible(false);
        overviewSection.setManaged(false);
        allComplaintsSection.setVisible(false);
        allComplaintsSection.setManaged(false);
        reportsSection.setVisible(false);
        reportsSection.setManaged(false);

        section.setVisible(true);
        section.setManaged(true);

        for (Button b : new Button[]{navOverviewBtn, navAllComplaintsBtn, navReportsBtn}) {
            if (b != null) {
                b.getStyleClass().remove("nav-btn-active");
            }
        }
        if (activeNav != null) {
            activeNav.getStyleClass().add("nav-btn-active");
        }
    }

    // -----------------------------------------------------------------------
    // Phase 9: Analytical Reports & Charts Handlers
    // -----------------------------------------------------------------------

    private void setupStaffPerformanceTable() {
        staffPerformanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        colStaffName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStaffName()));
        colStaffRole.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRole() != null ? d.getValue().getRole().toUpperCase() : ""));
        colStaffAssigned.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getAssignedComplaints()));
        colStaffResolved.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getResolvedComplaints()));
        colStaffRate.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.1f%%", d.getValue().getResolutionRate())));
        colStaffAvgTime.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.1f hrs", d.getValue().getAvgResolutionTimeHours())));
    }

    private void setupCharts() {
        if (statusBarChart != null) {
            statusBarChart.setAnimated(false);
            statusBarChart.setCategoryGap(24);
            statusBarChart.setBarGap(4);
        }
        if (departmentBarChart != null) {
            departmentBarChart.setAnimated(false);
            departmentBarChart.setCategoryGap(24);
            departmentBarChart.setBarGap(4);
        }
        if (monthlyTrendsChart != null) {
            monthlyTrendsChart.setAnimated(false);
        }
        if (categoryPieChart != null) {
            categoryPieChart.setAnimated(false);
        }

        if (statusXAxis != null) {
            statusXAxis.setAnimated(false);
            statusXAxis.setGapStartAndEnd(true);
        }
        if (departmentXAxis != null) {
            departmentXAxis.setAnimated(false);
            departmentXAxis.setGapStartAndEnd(true);
            departmentXAxis.setTickLabelRotation(-15);
        }
        if (monthlyTrendsXAxis != null) {
            monthlyTrendsXAxis.setAnimated(false);
            monthlyTrendsXAxis.setGapStartAndEnd(true);
            monthlyTrendsXAxis.setTickLabelRotation(-20);
        }
    }

    @FXML
    private void handleApplyReportFilter(ActionEvent e) {
        refreshReportsData();
    }

    @FXML
    private void handleResetReportFilter(ActionEvent e) {
        if (reportStartDatePicker != null) reportStartDatePicker.setValue(null);
        if (reportEndDatePicker != null)   reportEndDatePicker.setValue(null);
        refreshReportsData();
    }

    @FXML
    private void handleExportReportCsv(ActionEvent e) {
        if (currentReportSummary == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Data", "Please generate a report first before exporting.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Analytical Report to CSV");
        String defaultFileName = "CRTS_Analytical_Report_" + LocalDate.now() + ".csv";
        fileChooser.setInitialFileName(defaultFileName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        Stage stage = (Stage) (navReportsBtn != null && navReportsBtn.getScene() != null ?
                navReportsBtn.getScene().getWindow() : navOverviewBtn.getScene().getWindow());

        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                reportService.exportReportToCsv(currentReportSummary, file);
                showAlert(Alert.AlertType.INFORMATION, "Export Successful",
                        "Analytical report successfully exported to:\n" + file.getAbsolutePath());
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to export CSV report", ex);
                showAlert(Alert.AlertType.ERROR, "Export Failed",
                        "Could not export CSV file: " + ex.getMessage());
            }
        }
    }

    private void refreshReportsData() {
        LocalDate start = reportStartDatePicker != null ? reportStartDatePicker.getValue() : null;
        LocalDate end   = reportEndDatePicker != null ? reportEndDatePicker.getValue() : null;

        if (start != null && end != null && start.isAfter(end)) {
            showAlert(Alert.AlertType.WARNING, "Invalid Date Range",
                    "Start date (" + start + ") cannot be after end date (" + end + ").");
            if (reportStatusLabel != null) {
                reportStatusLabel.setText("Error: Start date cannot be after end date.");
            }
            return;
        }

        if (reportStatusLabel != null) {
            reportStatusLabel.setText("Generating reports...");
        }

        Thread t = new Thread(() -> {
            try {
                ReportSummary summary = reportService.getReportSummary(start, end);

                Platform.runLater(() -> {
                    currentReportSummary = summary;
                    renderReportsUI(summary);
                    if (reportStatusLabel != null) {
                        reportStatusLabel.setText("Updated: " + summary.getTotalComplaints() + " complaints analyzed.");
                    }
                });
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error loading analytical report data", ex);
                Platform.runLater(() -> {
                    if (reportStatusLabel != null) {
                        reportStatusLabel.setText("Failed to load report data.");
                    }
                    showAlert(Alert.AlertType.ERROR, "Report Error",
                            "Could not generate analytical report: " + ex.getMessage());
                });
            }
        }, "report-loading-thread");
        t.setDaemon(true);
        t.start();
    }

    private void renderReportsUI(ReportSummary summary) {
        // 1. Executive KPI Cards
        kpiReportTotal.setText(String.valueOf(summary.getTotalComplaints()));
        kpiReportResolutionRate.setText(String.format("%.1f%%", summary.getResolutionRate()));
        kpiReportResolved.setText(String.valueOf(summary.getResolvedComplaints()));
        kpiReportPending.setText(String.valueOf(summary.getPendingComplaints()));
        kpiReportAvgTime.setText(String.format("%.1f hrs", summary.getAvgResolutionTimeHours()));

        // 2. Category PieChart
        categoryPieChart.getData().clear();
        for (Map.Entry<String, Integer> entry : summary.getCategoryDistribution().entrySet()) {
            if (entry.getValue() > 0) {
                categoryPieChart.getData().add(new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
            }
        }

        // 3. Status BarChart
        statusBarChart.getData().clear();
        CategoryAxis sAxis = (statusXAxis != null) ? statusXAxis : (CategoryAxis) statusBarChart.getXAxis();
        ObservableList<String> statusCategories = FXCollections.observableArrayList(
                "OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "REJECTED"
        );
        sAxis.setCategories(statusCategories);

        XYChart.Series<String, Number> statusSeries = new XYChart.Series<>();
        statusSeries.setName("Status");
        Map<String, Integer> statusDist = summary.getStatusDistribution();
        for (String status : statusCategories) {
            int count = (statusDist != null) ? statusDist.getOrDefault(status, 0) : 0;
            statusSeries.getData().add(new XYChart.Data<>(status, count));
        }
        statusBarChart.getData().add(statusSeries);

        // 4. Department BarChart
        departmentBarChart.getData().clear();
        CategoryAxis dAxis = (departmentXAxis != null) ? departmentXAxis : (CategoryAxis) departmentBarChart.getXAxis();
        ObservableList<String> deptCategories = FXCollections.observableArrayList();
        List<DepartmentStat> deptStats = summary.getDepartmentStats();
        if (deptStats != null && !deptStats.isEmpty()) {
            for (DepartmentStat stat : deptStats) {
                String name = (stat.getDepartmentName() != null && !stat.getDepartmentName().trim().isEmpty())
                        ? stat.getDepartmentName().trim() : "Unassigned";
                if (!deptCategories.contains(name)) {
                    deptCategories.add(name);
                }
            }
        }
        if (deptCategories.isEmpty()) {
            deptCategories.add("No Data");
        }
        dAxis.setCategories(deptCategories);

        XYChart.Series<String, Number> deptSeries = new XYChart.Series<>();
        deptSeries.setName("Department");
        if (deptStats != null && !deptStats.isEmpty()) {
            for (DepartmentStat stat : deptStats) {
                String name = (stat.getDepartmentName() != null && !stat.getDepartmentName().trim().isEmpty())
                        ? stat.getDepartmentName().trim() : "Unassigned";
                deptSeries.getData().add(new XYChart.Data<>(name, stat.getTotalComplaints()));
            }
        } else {
            deptSeries.getData().add(new XYChart.Data<>("No Data", 0));
        }
        departmentBarChart.getData().add(deptSeries);

        // 5. Monthly Trends LineChart
        monthlyTrendsChart.getData().clear();
        CategoryAxis mAxis = (monthlyTrendsXAxis != null) ? monthlyTrendsXAxis : (CategoryAxis) monthlyTrendsChart.getXAxis();
        ObservableList<String> monthCategories = FXCollections.observableArrayList();
        Map<String, Integer> monthlyTrends = summary.getMonthlyTrends();
        if (monthlyTrends != null && !monthlyTrends.isEmpty()) {
            monthCategories.addAll(monthlyTrends.keySet());
        }
        if (monthCategories.isEmpty()) {
            monthCategories.add("No Data");
        }
        mAxis.setCategories(monthCategories);

        XYChart.Series<String, Number> trendSeries = new XYChart.Series<>();
        trendSeries.setName("New Registrations");
        if (monthlyTrends != null && !monthlyTrends.isEmpty()) {
            for (Map.Entry<String, Integer> entry : monthlyTrends.entrySet()) {
                trendSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
            }
        } else {
            trendSeries.getData().add(new XYChart.Data<>("No Data", 0));
        }
        monthlyTrendsChart.getData().add(trendSeries);

        // 6. Staff Performance Table
        staffPerformanceTable.setItems(FXCollections.observableArrayList(summary.getStaffPerformance()));
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }
}
