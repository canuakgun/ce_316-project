package com.ce316.iae.ui;

import javafx.fxml.FXML;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Objects;

/**
 * Controller for the Help window (HelpWindow.fxml).
 *
 * Opens the bundled index.html user manual inside a JavaFX WebView.
 * Triggered by Help > User Manual and the toolbar Help button,
 * both wired to MainWindowController#handleUserManual.
 */
public class HelpWindowController {

    @FXML
    private WebView helpWebView;

    @FXML
    public void initialize() {
        URL manualUrl = getClass().getResource("/manual/index.html");
        if (manualUrl != null) {
            helpWebView.getEngine().load(manualUrl.toExternalForm());
        } else {
            helpWebView.getEngine().loadContent(
                    "<html><body style='font-family:sans-serif;padding:2rem'>"
                            + "<h2>Manual not found</h2>"
                            + "<p>Could not locate <code>/help/index.html</code> on the classpath.</p>"
                            + "</body></html>");
        }
    }

    @FXML
    private void handleClose() {
        ((Stage) helpWebView.getScene().getWindow()).close();
    }
}