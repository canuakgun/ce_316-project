package com.ce316.iae.ui;

import com.ce316.iae.model.Configuration;
import com.ce316.iae.model.Report;
import com.ce316.iae.persistence.PersistenceManager;
import com.ce316.iae.service.ConfigurationManager;
import com.ce316.iae.service.ProjectManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;

import java.util.List;

public class MainWindowController {

    // UI components
    @FXML
    private ListView<String> resultsListView;

    @FXML
    private ComboBox<String> languageComboBox;

    // Managers
    private final ProjectManager projectManager;
    private final ConfigurationManager configurationManager;

    public MainWindowController() {

        PersistenceManager pm = PersistenceManager.getInstance();

        this.projectManager = new ProjectManager(pm);
        this.configurationManager = new ConfigurationManager(pm);
    }

    @FXML
    public void initialize() {

        Platform.runLater(() -> {
            loadConfigurations();
            loadStudents();
            setupListeners();
        });
    }

    // ---------------------------
    // INIT DATA
    // ---------------------------

    private void loadConfigurations() {

        List<Configuration> configs =
                configurationManager.listConfigurations();

        for (Configuration config : configs) {
            languageComboBox.getItems().add(config.getName());
        }

        if (!configs.isEmpty()) {
            languageComboBox.setValue(configs.get(0).getName());
        }
    }

    private void loadStudents() {

        resultsListView.getItems().addAll(
                "Student A",
                "Student B",
                "Student C"
        );
    }

    // ---------------------------
    // EVENTS
    // ---------------------------

    private void setupListeners() {

        // Click student
        resultsListView.setOnMouseClicked(event -> {

            String selected =
                    resultsListView.getSelectionModel().getSelectedItem();

            if (selected != null) {
                System.out.println("Selected student: " + selected);
            }
        });

        // Change language
        languageComboBox.setOnAction(event -> {

            String selectedConfig =
                    languageComboBox.getValue();

            System.out.println("Selected config: " + selectedConfig);
        });
    }

    // ---------------------------
    // BUTTON ACTIONS (FXML)
    // ---------------------------

    @FXML
    private void handleRunTests() {

        System.out.println("Running tests...");

        Task<Report> task = projectManager.buildRunTask();

        task.setOnSucceeded(event -> {

            Report report = task.getValue();

            System.out.println("Report generated:");
            System.out.println(report.getSummary());

        });

        task.setOnFailed(event -> {

            System.out.println(
                    "Error: " + task.getException().getMessage()
            );

        });

        new Thread(task).start();
    }

    @FXML
    private void handleRefresh() {

        System.out.println("Refreshing UI...");

        resultsListView.getItems().clear();
        loadStudents();
    }
}