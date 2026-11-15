package com.complainttracking;

/**
 * Launcher - Entry point for packaged non-modular JavaFX runtime.
 *
 * <p>Bypasses the JavaFX runtime check performed by the JVM launcher
 * when the main class directly extends {@link javafx.application.Application}.</p>
 */
public class Launcher {

    public static void main(String[] args) {
        Main.main(args);
    }
}
