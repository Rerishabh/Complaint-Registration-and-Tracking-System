package com.complainttracking.util;

import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NavigationHelper - Utility for transitioning between FXML views smoothly.
 */
public final class NavigationHelper {

    private static final Logger LOGGER = Logger.getLogger(NavigationHelper.class.getName());
    private static final String STYLESHEET_PATH = "/css/styles.css";

    private NavigationHelper() {
    }

    /**
     * Transitions the current stage (obtained from any triggering event) to the given FXML view.
     *
     * @param event    JavaFX action event
     * @param fxmlPath classpath to the target FXML file (e.g. "/fxml/UserLogin.fxml")
     * @param title    window title
     */
    public static void navigate(Event event, String fxmlPath, String title) {
        if (event == null || event.getSource() == null) {
            LOGGER.log(Level.WARNING, "Cannot navigate: event or event source is null");
            return;
        }
        Node sourceNode = (Node) event.getSource();
        Stage stage = (Stage) sourceNode.getScene().getWindow();
        navigateToStage(stage, fxmlPath, title);
    }

    /**
     * Transitions the specified stage directly to the given FXML view.
     *
     * @param stage    target JavaFX Stage
     * @param fxmlPath classpath to the target FXML file
     * @param title    window title
     */
    public static void navigateToStage(Stage stage, String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(NavigationHelper.class.getResource(fxmlPath));
            Parent root = loader.load();

            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(root, 1200, 760);
                String css = NavigationHelper.class.getResource(STYLESHEET_PATH).toExternalForm();
                scene.getStylesheets().add(css);
                stage.setScene(scene);
            } else {
                scene.setRoot(root);
                String css = NavigationHelper.class.getResource(STYLESHEET_PATH).toExternalForm();
                if (!scene.getStylesheets().contains(css)) {
                    scene.getStylesheets().add(css);
                }
            }

            stage.setMinWidth(1024);
            stage.setMinHeight(680);

            // Ensure window expands to at least 1200x760 for dashboards
            if (fxmlPath.contains("Dashboard")) {
                if (stage.getWidth() < 1200) {
                    stage.setWidth(1200);
                }
                if (stage.getHeight() < 760) {
                    stage.setHeight(760);
                }
                stage.centerOnScreen();
            }

            if (title != null && !title.isEmpty()) {
                stage.setTitle(title);
            }

            stage.show();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load view: " + fxmlPath, e);
        }
    }
}

