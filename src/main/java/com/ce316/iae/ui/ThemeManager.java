package com.ce316.iae.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.util.Objects;

/**
 * Single source of truth for stylesheet wiring. Every Scene built
 * by the app (main window, dialogs, modal popups) must run through
 * {@link #apply(Scene)} so the Oxide tokens load uniformly.
 */
public final class ThemeManager {

    private static final String THEME_CSS =
            Objects.requireNonNull(
                    ThemeManager.class.getResource("/css/theme.css"),
                    "theme.css missing from resources/css/"
            ).toExternalForm();

    private ThemeManager() {}

    /** Attach the theme stylesheet and tag the root with `.app-root`. */
    public static void apply(Scene scene) {
        if (!scene.getStylesheets().contains(THEME_CSS)) {
            scene.getStylesheets().add(THEME_CSS);
        }
        Parent root = scene.getRoot();
        if (root != null && !root.getStyleClass().contains("app-root")) {
            root.getStyleClass().add("app-root");
        }
    }

    /** Theme a built-in {@link Dialog} (Alert, ChoiceDialog, …). */
    public static void apply(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        if (!pane.getStylesheets().contains(THEME_CSS)) {
            pane.getStylesheets().add(THEME_CSS);
        }
        if (!pane.getStyleClass().contains("app-root")) {
            pane.getStyleClass().add("app-root");
        }
    }
}
