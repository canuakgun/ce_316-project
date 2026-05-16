package com.ce316.iae.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

import com.ce316.iae.model.Report;

/**
 * Modal dialog shown while the grading pipeline runs.
 *
 * Displays:
 *  - A ProgressBar bound to the Task's progress (0–3 steps → 0–100 %)
 *  - A status Label bound to the Task's message ("Extracting…", "Compiling…", etc.)
 *  - A Cancel button that cancels the Task and closes the dialog
 *
 * Usage from MainWindowController (before starting the thread):
 * <pre>{@code
 *   RunProgressController ctrl = ...;          // loaded from FXML
 *   ctrl.bindToTask(task);                     // wire properties
 *   stage.show();                              // show non-blocking, OR
 *   // stage.showAndWait() — but the task won't start until showAndWait returns
 *   //   so use show() + let task.setOnSucceeded close the stage
 * }</pre>
 *
 * The dialog closes itself automatically when the task succeeds or fails.
 */
public class RunProgressController {

    // ---------------------------------------------------------------
    // FXML nodes
    // ---------------------------------------------------------------

    @FXML private ProgressBar progressBar;
    @FXML private Label       statusLabel;
    @FXML private Button      cancelButton;

    // ---------------------------------------------------------------
    // State
    // ---------------------------------------------------------------

    /** The background task this dialog monitors. */
    private Task<Report> task;

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    @FXML
    public void initialize() {
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        statusLabel.setText("Starting…");
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Binds the dialog's progress bar and status label to the given Task,
     * and registers close-on-finish handlers.
     *
     * Must be called on the JavaFX Application Thread before the stage is shown.
     *
     * @param task the JavaFX Task returned by {@code ProjectManager.buildRunTask()}
     */
    public void bindToTask(Task<Report> task) {
        this.task = task;

        // Bind UI to task properties (JavaFX properties are thread-safe for binding)
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        // Auto-close when the pipeline finishes (success or failure)
        task.setOnSucceeded(event -> Platform.runLater(this::closeDialog));
        task.setOnFailed(event -> Platform.runLater(() -> {
            // Unbind so we can write an error message manually
            statusLabel.textProperty().unbind();
            String msg = task.getException() != null
                    ? task.getException().getMessage() : "Unknown error";
            statusLabel.setText("Failed: " + msg);
            // Leave the dialog open so the user can read the error;
            // repurpose the Cancel button as a Close button
            cancelButton.setText("Close");
        }));

        task.setOnCancelled(event -> Platform.runLater(this::closeDialog));
    }

    // ---------------------------------------------------------------
    // Button handlers
    // ---------------------------------------------------------------

    /** Cancel / Close button. Cancels the task if still running, then closes. */
    @FXML
    private void handleCancel() {
        if (task != null && task.isRunning()) {
            task.cancel();
        }
        closeDialog();
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private void closeDialog() {
        // Unbind to avoid "bound property cannot be set" errors after close
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        getStage().close();
    }

    private Stage getStage() {
        return (Stage) progressBar.getScene().getWindow();
    }
}