package com.complainttracking;

import com.complainttracking.database.DatabaseConnection;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main – JavaFX application entry point.
 *
 * <p>Loads the welcome screen FXML, applies the global CSS stylesheet,
 * and shows the primary stage.</p>
 */
public class Main extends Application {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/WelcomeScreen.fxml"));
            Scene scene = new Scene(loader.load(), 900, 600);

            // Apply global stylesheet
            scene.getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());

            primaryStage.setTitle("Complaint Registration and Tracking System");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(550);
            primaryStage.centerOnScreen();
            primaryStage.show();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load WelcomeScreen.fxml", e);
        }
    }

    @Override
    public void stop() {
        // Gracefully close the database connection on exit
        DatabaseConnection.closeConnection();
        LOGGER.log(Level.INFO, "Application stopped.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
