package com.ce316.iae.ui;

import com.ce316.iae.model.Configuration;
import com.ce316.iae.model.Project;
import com.ce316.iae.model.TestCase;
import com.ce316.iae.service.ConfigurationManager;
import com.ce316.iae.service.ProjectManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

public class ProjectDialogController {

    // ---------------------------------------------------------------
    // FXML
    // ---------------------------------------------------------------

    @FXML private TextField nameField;
    @FXML private ComboBox<Configuration> configComboBox;
    @FXML private TextField submissionsDirField;

    @FXML private ListView<String> testCaseListView;

    @FXML private TextField inputArgsField;
    @FXML private TextField expectedOutputField;

    @FXML private Label errorLabel;

    // ---------------------------------------------------------------
    // STATE
    // ---------------------------------------------------------------

    private ProjectManager projectManager;
    private ConfigurationManager configurationManager;

    private Project existingProject;
    private Project committedProject;

    // ---------------------------------------------------------------
    // INIT FROM MAIN CONTROLLER
    // ---------------------------------------------------------------

    public void init(ProjectManager pm, ConfigurationManager cm, Project project) {
        this.projectManager = pm;
        this.configurationManager = cm;
        this.existingProject = project;

        loadConfigurations();

        if (existingProject != null) {
            populateFields(existingProject);
        }
    }

    // ---------------------------------------------------------------
    // FXML INIT
    // ---------------------------------------------------------------

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
        testCaseListView.setPlaceholder(new Label("No test cases yet."));
    }

    // ---------------------------------------------------------------
    // CONFIGURATIONS
    // ---------------------------------------------------------------

    private void loadConfigurations() {

        List<Configuration> configs = configurationManager.listConfigurations();

        configComboBox.getItems().clear();
        configComboBox.getItems().addAll(configs);

        configComboBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Configuration c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.getName());
            }
        });

        configComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Configuration c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.getName());
            }
        });

        if (!configs.isEmpty()) {
            configComboBox.getSelectionModel().selectFirst();
        }
    }

    // ---------------------------------------------------------------
    // ACTIONS
    // ---------------------------------------------------------------

    @FXML
    private void handleBrowseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Submissions Directory");

        File chosen = chooser.showDialog(getStage());
        if (chosen != null) {
            submissionsDirField.setText(chosen.getAbsolutePath());
        }
    }

    @FXML
    private void handleAddTestCase() {

        String args = inputArgsField.getText().trim();
        String expected = expectedOutputField.getText().trim();

        if (expected.isEmpty()) {
            showError("Expected output cannot be empty.");
            return;
        }

        testCaseListView.getItems().add(args + " → " + expected);

        inputArgsField.clear();
        expectedOutputField.clear();
        hideError();
    }

    @FXML
    private void handleRemoveTestCase() {
        int idx = testCaseListView.getSelectionModel().getSelectedIndex();
        if (idx >= 0) testCaseListView.getItems().remove(idx);
    }

    @FXML
    private void handleConfirm() {

        String name = nameField.getText().trim();
        String dir = submissionsDirField.getText().trim();
        Configuration config = configComboBox.getValue();

        if (name.isEmpty()) {
            showError("Project name required");
            return;
        }
        if (dir.isEmpty()) {
            showError("Directory required");
            return;
        }
        if (config == null) {
            showError("Select a configuration");
            return;
        }
        if (testCaseListView.getItems().isEmpty()) {
            showError("At least one test case required");
            return;
        }

        if (existingProject == null) {
            committedProject = projectManager.createProject(name, dir);
        } else {
            committedProject = existingProject;
            committedProject.setName(name);
            committedProject.setSubmissionsDirectory(dir);
            committedProject.getTestCases().clear();
        }

        committedProject.setConfiguration(config);

        for (String item : testCaseListView.getItems()) {
            TestCase tc = new TestCase();

            String[] parts = item.split("→");

            tc.setInputArgs(parts[0].trim());
            tc.setExpectedOutput(parts[1].trim());

            committedProject.getTestCases().add(tc);
        }

        if (existingProject != null) {
            projectManager.saveProject();
        }

        closeDialog();
    }

    @FXML
    private void handleCancel() {
        committedProject = null;
        closeDialog();
    }

    // ---------------------------------------------------------------
    // POPULATE EDIT MODE
    // ---------------------------------------------------------------

    private void populateFields(Project p) {

        nameField.setText(p.getName());
        submissionsDirField.setText(p.getSubmissionsDirectory());

        configComboBox.getSelectionModel().select(p.getConfiguration());

        testCaseListView.getItems().clear();

        for (TestCase tc : p.getTestCases()) {
            testCaseListView.getItems().add(
                    tc.getInputArgs() + " → " + tc.getExpectedOutput()
            );
        }
    }

    // ---------------------------------------------------------------
    // UTILS
    // ---------------------------------------------------------------

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
    }

    public Project getCommittedProject() {
        return committedProject;
    }

    private Stage getStage() {
        return (Stage) nameField.getScene().getWindow();
    }

    private void closeDialog() {
        getStage().close();
    }
}