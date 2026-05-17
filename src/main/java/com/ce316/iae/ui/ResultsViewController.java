package com.ce316.iae.ui;

import com.ce316.iae.model.StudentResult;
import com.ce316.iae.model.SubmissionStatus;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;

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

        // Colour-code the Status column — item is now SubmissionStatus enum
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(SubmissionStatus item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.name());
                    switch (item) {
                        case SUCCESS       -> setTextFill(Color.GREEN);
                        case WRONG_OUTPUT  -> setTextFill(Color.ORANGE);
                        case COMPILE_ERROR,
                             RUNTIME_ERROR -> setTextFill(Color.RED);
                        default            -> setTextFill(Color.GRAY);
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
        diffTextArea.setPromptText("Click a row to see the diff.");
        summaryLabel.setText("No results yet.");
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
