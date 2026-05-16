package com.ce316.iae.ui;

import com.ce316.iae.model.Configuration;
import com.ce316.iae.model.Project;
import com.ce316.iae.model.Report;
import com.ce316.iae.persistence.PersistenceManager;
import com.ce316.iae.service.ConfigurationManager;
import com.ce316.iae.service.ProjectManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the main application window (MainWindow.fxml).
 *
 * Responsibilities (Dev 4 — Core Frontend Lead):
 *  - Drive the MenuBar: File / Configuration / Help menus
 *  - Drive the ToolBar: New, Open, Save, Run, Help buttons
 *  - Populate the left-panel ListView with project information labels
 *  - Delegate all business operations to ProjectManager / ConfigurationManager
 *  - Hand off results to ResultsViewController via the ResultsObserver interface
 *  - Update the status bar during pipeline execution
 */
public class MainWindowController {

    // ---------------------------------------------------------------
    // FXML-injected UI nodes
    // ---------------------------------------------------------------

    /** Left panel: shows project name, config, dir, test-case count. */
    @FXML private ListView<String> projectInfoListView;

    /** Status bar label at the bottom of the window. */
    @FXML private Label statusLabel;

    /** Progress bar in the status bar (hidden when idle). */
    @FXML private ProgressBar statusProgressBar;

    /** Reference to the embedded ResultsViewController (fx:include). */
    @FXML private ResultsViewController resultsViewController;

    // ---------------------------------------------------------------
    // Service layer
    // ---------------------------------------------------------------

    private final PersistenceManager    persistenceManager;
    private final ProjectManager        projectManager;
    private final ConfigurationManager  configurationManager;

    /** Currently loaded project (may be null before the user opens/creates one). */
    private Project currentProject;

    // ---------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------

    public MainWindowController() {
        this.persistenceManager   = PersistenceManager.getInstance();
        this.projectManager       = new ProjectManager(persistenceManager);
        this.configurationManager = new ConfigurationManager(persistenceManager);
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    @FXML
    public void initialize() {
        // Hide progress bar until a run starts
        statusProgressBar.setVisible(false);
        setStatus("Ready.");
    }

    // ---------------------------------------------------------------
    // FILE MENU
    // ---------------------------------------------------------------

    /** File → New Project… : opens the ProjectDialog to create a project. */
    @FXML
    private void handleNewProject() {
        openProjectDialog(null);
    }

    /**
     * File → Open Project… : lists all projects stored in the DB and lets
     * the user pick one by name. Uses {@link ProjectManager#listProjects()}
     * and {@link ProjectManager#openProject(int)} — the actual API exposed
     * by the ProjectManager (projects are DB-backed, not file-backed).
     */
    @FXML
    private void handleOpenProject() {
        List<Project> allProjects = projectManager.listProjects();
        if (allProjects.isEmpty()) {
            showInfo("No projects found in the database. Create a new project first.");
            return;
        }

        // Build a choice list of "id — name" strings
        List<String> choices = allProjects.stream()
                .map(p -> p.getId() + " — " + p.getName())
                .collect(java.util.stream.Collectors.toList());

        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.setTitle("Open Project");
        dialog.setHeaderText("Select a project to open:");
        dialog.setContentText("Project:");
        Optional<String> chosen = dialog.showAndWait();
        if (chosen.isEmpty()) return;

        // Parse the id from the selected string ("42 — My Project")
        int projectId = Integer.parseInt(chosen.get().split(" — ")[0].trim());

        try {
            currentProject = projectManager.openProject(projectId);
            if (currentProject == null) {
                showError("Open failed", "Project not found in database (id=" + projectId + ").");
                return;
            }
            // Sync ProjectManager's internal reference
            projectManager.setCurrentProject(currentProject);
            refreshProjectPanel();
            setStatus("Project loaded: " + currentProject.getName());
        } catch (Exception e) {
            showError("Failed to open project", e.getMessage());
        }
    }

    /** File → Save Project : persists the current project. */
    @FXML
    private void handleSaveProject() {
        if (currentProject == null) {
            showInfo("No project is open. Please create or open a project first.");
            return;
        }
        try {
            projectManager.saveProject();
            setStatus("Project saved: " + currentProject.getName());
        } catch (Exception e) {
            showError("Failed to save project", e.getMessage());
        }
    }

    /** File → Exit */
    @FXML
    private void handleExit() {
        Platform.exit();
    }

    // ---------------------------------------------------------------
    // CONFIGURATION MENU
    // ---------------------------------------------------------------

    /** Configuration → Manage Configurations : opens ConfigDialog. */
    @FXML
    private void handleManageConfigurations() {
        openConfigDialog();
    }

    /** Configuration → Import Configuration… */
    @FXML
    private void handleImportConfiguration() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Configuration");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("IAE Config Files (*.iaeconfig)", "*.iaeconfig"));
        File selected = chooser.showOpenDialog(getStage());
        if (selected == null) return;

        try {
            configurationManager.importConfig(selected.toPath());
            setStatus("Configuration imported: " + selected.getName());
        } catch (Exception e) {
            showError("Import failed", e.getMessage());
        }
    }

    /** Configuration → Export Configuration… */
    @FXML
    private void handleExportConfiguration() {
        List<Configuration> configs = configurationManager.listConfigurations();
        if (configs.isEmpty()) {
            showInfo("No configurations available to export.");
            return;
        }
        // Let the user pick which configuration to export
        ChoiceDialog<String> pick = new ChoiceDialog<>(
                configs.get(0).getName(),
                configs.stream().map(Configuration::getName).toArray(String[]::new));
        pick.setTitle("Export Configuration");
        pick.setHeaderText("Select the configuration to export:");
        Optional<String> chosen = pick.showAndWait();
        if (chosen.isEmpty()) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Configuration As");
        chooser.setInitialFileName(chosen.get() + ".iaeconfig");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("IAE Config Files (*.iaeconfig)", "*.iaeconfig"));
        File dest = chooser.showSaveDialog(getStage());
        if (dest == null) return;

        configs.stream()
                .filter(c -> c.getName().equals(chosen.get()))
                .findFirst()
                .ifPresent(c -> {
                    try {
                        configurationManager.exportConfig(c, Path.of(dest.getAbsolutePath()));
                        setStatus("Configuration exported: " + dest.getName());
                    } catch (Exception e) {
                        showError("Export failed", e.getMessage());
                    }
                });
    }

    // ---------------------------------------------------------------
    // HELP MENU
    // ---------------------------------------------------------------

    /** Help → User Manual */
    @FXML
    private void handleUserManual() {
        showInfo("User manual is located in the docs/ folder of the installation directory.");
    }

    /** Help → About */
    @FXML
    private void handleAbout() {
        showInfo("IAE — Integrated Assignment Environment\nVersion 1.0\n\nCE 316 Software Engineering Project");
    }

    // ---------------------------------------------------------------
    // TOOLBAR ACTIONS
    // ---------------------------------------------------------------

    /** Toolbar → Run button (also reachable from a menu action). */
    @FXML
    private void handleRunTests() {
        if (currentProject == null) {
            showInfo("Please open or create a project before running tests.");
            return;
        }

        // Clear previous results
        if (resultsViewController != null) {
            resultsViewController.clearResults();
        }

        // ProjectManager.buildRunTask() uses its own internal currentProject —
        // make sure it is in sync with ours.
        projectManager.setCurrentProject(currentProject);

        // Build the background Task (no parameter — PM uses its internal state)
        Task<Report> task = projectManager.buildRunTask();

        // Bind the status bar label to the task's live message updates
        // (the PM publishes "Extracting…", "Compiling…", "Done — 12 passed…")
        statusLabel.textProperty().bind(task.messageProperty());

        // Bind the progress bar to the task's progress (0–3 steps → 0.0–1.0)
        statusProgressBar.progressProperty().bind(task.progressProperty());
        statusProgressBar.setVisible(true);

        task.setOnSucceeded(event -> Platform.runLater(() -> {
            // Unbind so we can set text manually afterwards
            statusLabel.textProperty().unbind();
            statusProgressBar.progressProperty().unbind();
            statusProgressBar.setVisible(false);

            Report report = task.getValue();
            if (report != null) {
                setStatus(report.getSummary());
                // Push every StudentResult into the ResultsViewController
                if (resultsViewController != null) {
                    for (var result : report.getResults()) {
                        resultsViewController.onStudentProcessed(result);
                    }
                }
            }
        }));

        task.setOnFailed(event -> Platform.runLater(() -> {
            statusLabel.textProperty().unbind();
            statusProgressBar.progressProperty().unbind();
            statusProgressBar.setVisible(false);
            String msg = task.getException() != null
                    ? task.getException().getMessage() : "Unknown error";
            setStatus("Run failed: " + msg);
            showError("Execution error", msg);
        }));

        Thread t = new Thread(task, "run-pipeline");
        t.setDaemon(true);
        t.start();
    }

    /** Toolbar → Refresh: reloads the project panel without re-running tests. */
    @FXML
    private void handleRefresh() {
        refreshProjectPanel();
        setStatus("Panel refreshed.");
    }

    // ---------------------------------------------------------------
    // INTERNAL HELPERS
    // ---------------------------------------------------------------

    /**
     * Refreshes the left-panel ListView to reflect the currentProject state.
     * Shows project name, configuration name, submissions directory, and
     * the number of defined test cases.
     */
    private void refreshProjectPanel() {
        projectInfoListView.getItems().clear();
        if (currentProject == null) {
            projectInfoListView.getItems().add("No project loaded.");
            return;
        }
        projectInfoListView.getItems().addAll(
                "📁  Project : " + currentProject.getName(),
                "⚙   Config  : " + (currentProject.getConfiguration() != null
                        ? currentProject.getConfiguration().getName()
                        : "— none —"),
                "📂  Dir     : " + currentProject.getSubmissionsDirectory(),
                "🧪  Tests   : " + currentProject.getTestCases().size() + " test case(s)"
        );
    }

    /** Opens the ProjectDialogController in a modal window. */
    private void openProjectDialog(Project existing) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/ProjectDialog.fxml"));
            Parent root = loader.load();
            ProjectDialogController ctrl = loader.getController();

            // Pass the managers so the dialog can create/update via the service layer
            ctrl.init(projectManager, configurationManager, existing);

            Stage dialog = new Stage();
            dialog.setTitle(existing == null ? "New Project" : "Edit Project");
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(getStage());
            dialog.setScene(new Scene(root));
            dialog.showAndWait();

            // After dialog closes, pick up whatever project the dialog committed
            Project committed = ctrl.getCommittedProject();
            if (committed != null) {
                currentProject = committed;
                projectManager.setCurrentProject(currentProject);
                refreshProjectPanel();
                setStatus("Project ready: " + currentProject.getName());
            }
        } catch (Exception e) {

            e.printStackTrace();

            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }

            showError(
                    "FXML Error",
                    root.getClass().getName() + "\n" + root.getMessage()
            );
        }
    }

    /** Opens the ConfigDialogController in a modal window. */
    private void openConfigDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/ConfigDialog.fxml"));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.setTitle("Manage Configurations");
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(getStage());
            dialog.setScene(new Scene(root));
            dialog.showAndWait();
        } catch (IOException e) {
            showError("Cannot open Configuration dialog", e.getMessage());
        }
    }

    /** Convenience: update the status bar label (always on FX thread). */
    private void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    /** Grab the primary Stage from any FXML node. */
    private Stage getStage() {
        return (Stage) projectInfoListView.getScene().getWindow();
    }

    // ---------------------------------------------------------------
    // ALERT HELPERS
    // ---------------------------------------------------------------

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "Unknown error.");
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}