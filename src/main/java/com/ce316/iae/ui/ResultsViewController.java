package com.ce316.iae.ui;

import com.ce316.iae.model.StudentResult;
import com.ce316.iae.model.SubmissionStatus;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Controller for the center/right results pane (ResultsView.fxml).
 *
 * Responsibilities (Dev 4 — Core Frontend Lead):
 *  - Display the TableView of student results (ID, Status, Error message)
 *  - Show a summary bar (pass / fail / error counts)
 *  - Render the diff TextArea when a row is clicked
 *  - Implement ResultsObserver so the ExecutionEngine can push live updates
 *
 * NO dummy / test data. All data arrives either from the observer callback
 * (during a live run) or from MainWindowController after a report is loaded.
 */
public class ResultsViewController implements ResultsObserver {

    // ---------------------------------------------------------------
    // FXML-injected nodes
    // ---------------------------------------------------------------

    @FXML private TableView<StudentResult>               resultsTable;
    @FXML private TableColumn<StudentResult, String>          studentIdColumn;
    @FXML private TableColumn<StudentResult, SubmissionStatus> statusColumn;
    @FXML private TableColumn<StudentResult, String>          errorColumn;

    /** One-line summary: "12 passed  •  3 failed  •  2 errors" */
    @FXML private Label summaryLabel;

    /** Shows the diff (expected vs. actual) for the selected row. */
    @FXML private TextArea diffTextArea;

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    @FXML
    public void initialize() {
        // Bind columns to StudentResult properties
        studentIdColumn.setCellValueFactory(new PropertyValueFactory<>("studentId"));
        statusColumn   .setCellValueFactory(new PropertyValueFactory<>("status"));
        errorColumn    .setCellValueFactory(new PropertyValueFactory<>("errorMessage"));

        // Status column — uses token-driven styleClasses (.status-pass / -fail / -error /
        // -pending) so colors come from theme.css, not hard-coded Color constants.
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(SubmissionStatus item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("status-pass", "status-fail",
                        "status-error", "status-pending");
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(prettyStatus(item));
                    switch (item) {
                        case SUCCESS       -> getStyleClass().add("status-pass");
                        case WRONG_OUTPUT  -> getStyleClass().add("status-fail");
                        case COMPILE_ERROR,
                             RUNTIME_ERROR -> getStyleClass().add("status-error");
                        default            -> getStyleClass().add("status-pending");
                    }
                }
            }
        });

        // Row-click: populate the diff area and update status styling
        resultsTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        displayDiff(newVal);
                    }
                });

        // Initialise labels to an empty state
        clearResults();
    }

    // ---------------------------------------------------------------
    // ResultsObserver — called by ExecutionEngine on a background thread
    // ---------------------------------------------------------------

    /**
     * Receives one StudentResult as it becomes available during the run.
     * Must always dispatch to the JavaFX Application Thread.
     */
    @Override
    public void onStudentProcessed(StudentResult result) {
        Platform.runLater(() -> {
            if (result != null) {
                resultsTable.getItems().add(result);
                updateSummary();
            }
        });
    }

    // ---------------------------------------------------------------
    // Public API used by MainWindowController
    // ---------------------------------------------------------------

    /**
     * Clears the table, diff area, and summary to prepare for a new run.
     * Must be called on the FX thread (MainWindowController calls it inside
     * Platform.runLater before starting the background task).
     */
    public void clearResults() {
        resultsTable.getItems().clear();
        diffTextArea.clear();
        diffTextArea.setPromptText("Select a row above to inspect expected vs. actual output.");
        summaryLabel.setText("No run yet");
    }

    /** Pretty enum names for the table: SUCCESS → "Passed", etc. */
    private String prettyStatus(SubmissionStatus s) {
        return switch (s) {
            case SUCCESS       -> "Passed";
            case WRONG_OUTPUT  -> "Wrong output";
            case COMPILE_ERROR -> "Compile error";
            case RUNTIME_ERROR -> "Runtime error";
            case SKIPPED       -> "Skipped";
        };
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    /**
     * Populates the diff TextArea from the selected StudentResult.
     * Shows the full diff string produced by OutputComparator.generateDiff().
     */
    private void displayDiff(StudentResult result) {
        String diff = result.getDiffText();
        if (diff == null || diff.isBlank()) {
            diffTextArea.setText(
                    result.getStatus() == SubmissionStatus.SUCCESS
                            ? "Output matches expected — no diff."
                            : "No diff available for status: " + result.getStatus());
        } else {
            diffTextArea.setText(diff);
        }
    }

    /**
     * Recomputes and updates the summary label from the current table items.
     */
    private void updateSummary() {
        long pass   = count(SubmissionStatus.SUCCESS);
        long fail   = count(SubmissionStatus.WRONG_OUTPUT);
        long errors = countErrors();
        long skip   = count(SubmissionStatus.SKIPPED);

        String text = pass + " passed  •  " + fail + " wrong output  •  "
                + errors + " error(s)";
        if (skip > 0) text += "  •  " + skip + " skipped";
        summaryLabel.setText(text);
    }

    private long count(SubmissionStatus status) {
        return resultsTable.getItems().stream()
                .filter(r -> r.getStatus() == status)
                .count();
    }

    private long countErrors() {
        return resultsTable.getItems().stream()
                .filter(r -> r.getStatus() == SubmissionStatus.COMPILE_ERROR
                        || r.getStatus() == SubmissionStatus.RUNTIME_ERROR)
                .count();
    }
}
