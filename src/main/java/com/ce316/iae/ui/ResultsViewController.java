package com.ce316.iae.ui;

import com.ce316.iae.model.StudentResult;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class ResultsViewController implements ResultsObserver {

    @FXML
    private TableView<StudentResult> resultsTable;

    @FXML
    private TableColumn<StudentResult, String> studentIdColumn;

    @FXML
    private TableColumn<StudentResult, String> statusColumn;

    @FXML
    private TextArea diffTextArea;

    @FXML
    public void initialize() {

        studentIdColumn.setCellValueFactory(
                new PropertyValueFactory<>("studentId")
        );

        statusColumn.setCellValueFactory(
                new PropertyValueFactory<>("status")
        );

        resultsTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {

                    if (newVal != null) {
                        diffTextArea.setText(newVal.getDiffText());
                    }
                });

        // TEMP DATA
        loadDummyResults();
    }

    @Override
    public void onResultsUpdated(List<StudentResult> results) {

        Platform.runLater(() -> {

            resultsTable.getItems().clear();

            if (results != null) {
                resultsTable.getItems().addAll(results);
            }
        });
    }

    private void loadDummyResults() {

        StudentResult r1 = new StudentResult();
        r1.setStudentId("Alice");
        r1.setDiffText("Expected: Hello\nActual: Hello");

        StudentResult r2 = new StudentResult();
        r2.setStudentId("Bob");
        r2.setDiffText("Expected: 42\nActual: 41");

        StudentResult r3 = new StudentResult();
        r3.setStudentId("Charlie");
        r3.setDiffText("Compile error");

        resultsTable.getItems().addAll(r1, r2, r3);
    }
}