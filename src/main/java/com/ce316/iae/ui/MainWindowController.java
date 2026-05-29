package com.ce316.iae.ui;

import com.ce316.iae.model.Configuration;
import com.ce316.iae.model.Project;
import com.ce316.iae.model.Report;
import com.ce316.iae.model.StudentResult;
import com.ce316.iae.persistence.PersistenceManager;
import com.ce316.iae.service.ConfigurationManager;
import com.ce316.iae.service.ProjectManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the main application window (MainWindow.fxml).
 *
 * Responsibilities:
 * - Drive the MenuBar and ToolBar
 * - Show all DB-persisted projects in the left ListView (click to select)
 * - Show details + latest results for the selected project
 * - Edit / Delete the selected project
 * - Run the grading pipeline on a background thread and stream results
 */
public class MainWindowController {

    // ---------------------------------------------------------------
    // FXML-injected UI nodes
    // ---------------------------------------------------------------

    /** Left panel top: list of all projects in the DB. */
    @FXML
    private ListView<Project> projectsListView;

    /** Left panel bottom: details (name/config/dir/tests) for the selection. */
    @FXML
    private ListView<String> projectInfoListView;

    /** Status bar label at the bottom of the window. */
    @FXML
    private Label statusLabel;

    /** Progress bar in the status bar (hidden when idle). */
    @FXML
    private ProgressBar statusProgressBar;

    /** Reference to the embedded ResultsViewController (fx:include). */
    @FXML
    private ResultsViewController resultsViewController;

    @FXML
    private ComboBox<String> reportHistoryComboBox;

    // ---------------------------------------------------------------
    // Service layer
    // ---------------------------------------------------------------

    private final PersistenceManager persistenceManager;
    private final ProjectManager projectManager;
    private final ConfigurationManager configurationManager;

    /** Currently selected/loaded project. */
    private Project currentProject;

    /** Guards against double-clicks on Run while a task is already executing. */
    private boolean runInProgress = false;

    // ---------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------

    public MainWindowController() {
        this.persistenceManager = PersistenceManager.getInstance();
        this.projectManager = new ProjectManager(persistenceManager);
        this.configurationManager = new ConfigurationManager(persistenceManager);
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    @FXML
    public void initialize() {
        statusProgressBar.setVisible(false);

        // Render projects as "id — name"
        projectsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Project p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : "#" + p.getId() + "  " + p.getName());
            }
        });

        // Selection in the project list drives currentProject + detail pane
        projectsListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldP, newP) -> {
                    if (newP != null) {
                        loadSelectedProject(newP.getId());
                    }
                });

        refreshProjectList();
        refreshProjectPanel();
        setStatus("Ready.");
    }

    // ---------------------------------------------------------------
    // FILE MENU
    // ---------------------------------------------------------------

    @FXML
    private void handleNewProject() {
        openProjectDialog(null);
    }

    @FXML
    private void handleOpenProject() {
        // The sidebar already lists projects — this menu item just focuses it.
        List<Project> all = projectManager.listProjects();
        if (all.isEmpty()) {
            showInfo("No projects in the database. Click ⊕ New to create one.");
            return;
        }
        refreshProjectList();
        projectsListView.requestFocus();
        if (!projectsListView.getItems().isEmpty()) {
            projectsListView.getSelectionModel().selectFirst();
        }
    }

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

    @FXML
    private void handleEditProject() {
        if (currentProject == null) {
            showInfo("Select a project in the sidebar first.");
            return;
        }
        openProjectDialog(currentProject);
    }

    @FXML
    private void handleDeleteProject() {
        if (currentProject == null) {
            showInfo("Select a project in the sidebar first.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Project");
        confirm.setHeaderText(null);
        confirm.setContentText("Permanently delete \"" + currentProject.getName()
                + "\" and all its reports?");
        if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isEmpty())
            return;

        try {
            projectManager.setCurrentProject(currentProject);
            projectManager.deleteProject();
            String name = currentProject.getName();
            currentProject = null;
            if (resultsViewController != null)
                resultsViewController.clearResults();
            refreshProjectList();
            refreshProjectPanel();
            setStatus("Deleted: " + name);
        } catch (Exception e) {
            showError("Delete failed", e.getMessage());
        }
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }

    // ---------------------------------------------------------------
    // CONFIGURATION MENU
    // ---------------------------------------------------------------

    @FXML
    private void handleManageConfigurations() {
        openConfigDialog();
    }

    @FXML
    private void handleImportConfiguration() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Configuration");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("IAE Config Files (*.iaeconfig)", "*.iaeconfig"));
        File selected = chooser.showOpenDialog(getStage());
        if (selected == null)
            return;

        try {
            configurationManager.importConfig(selected.toPath());
            setStatus("Configuration imported: " + selected.getName());
        } catch (Exception e) {
            showError("Import failed", e.getMessage());
        }
    }

    @FXML
    private void handleExportConfiguration() {
        List<Configuration> configs = configurationManager.listConfigurations();
        if (configs.isEmpty()) {
            showInfo("No configurations available to export.");
            return;
        }
        ChoiceDialog<String> pick = new ChoiceDialog<>(
                configs.get(0).getName(),
                configs.stream().map(Configuration::getName).toArray(String[]::new));
        pick.setTitle("Export Configuration");
        pick.setHeaderText("Select the configuration to export:");
        Optional<String> chosen = pick.showAndWait();
        if (chosen.isEmpty())
            return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Configuration As");
        chooser.setInitialFileName(chosen.get() + ".iaeconfig");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("IAE Config Files (*.iaeconfig)", "*.iaeconfig"));
        File dest = chooser.showSaveDialog(getStage());
        if (dest == null)
            return;

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

    /** Opens the bundled user manual inside a WebView window. */
    @FXML
    private void handleUserManual() {
        try {
            URL manual = getClass().getResource("/manual/index.html");
            if (manual == null) {
                showInfo("User manual not bundled in this build.");
                return;
            }
            WebView view = new WebView();
            view.getEngine().load(manual.toExternalForm());
            Stage stage = new Stage();
            stage.setTitle("IAE — User Manual");
            stage.initOwner(getStage());
            stage.setScene(new Scene(view, 900, 650));
            stage.show();
        } catch (Exception e) {
            showError("Cannot open manual", e.getMessage());
        }
    }

    @FXML
    private void handleAbout() {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle("About IAE");
        about.setHeaderText("Integrated Assignment Environment");
        about.setContentText(
                "IAE automates grading of student code submissions.\n\n"
                        + "Compile → Run → Compare pipeline with SQLite persistence.\n\n"
                        + "CE316 Project — 2025");
        about.showAndWait();
    }

    // ---------------------------------------------------------------
    // TOOLBAR ACTIONS
    // ---------------------------------------------------------------

    @FXML
    private void handleRunTests() {
        if (runInProgress) {
            // Guard against accidental double-click while pipeline is running
            return;
        }
        if (currentProject == null) {
            showInfo("Please open or create a project before running tests.");
            return;
        }
        if (currentProject.getConfiguration() == null) {
            showError("Cannot run", "Project has no language configuration. Edit the project to attach one.");
            return;
        }
        if (currentProject.getTestCases().isEmpty()) {
            showError("Cannot run", "Project has no test cases. Edit the project to add one.");
            return;
        }
        File dir = new File(currentProject.getSubmissionsDirectory() != null
                ? currentProject.getSubmissionsDirectory()
                : "");
        if (!dir.isDirectory()) {
            showError("Cannot run", "Submissions directory does not exist:\n" + dir);
            return;
        }

        if (resultsViewController != null) {
            resultsViewController.clearResults();
        }

        projectManager.setCurrentProject(currentProject);

        Task<Report> task = projectManager.buildRunTask();

        // Stream live results into the ResultsView as each student is processed
        projectManager.getExecutionEngine().clearObservers();
        if (resultsViewController != null) {
            projectManager.getExecutionEngine().addObserver(resultsViewController);
        }

        statusLabel.textProperty().bind(task.messageProperty());
        statusProgressBar.progressProperty().bind(task.progressProperty());
        statusProgressBar.setVisible(true);
        runInProgress = true;

        task.setOnSucceeded(event -> Platform.runLater(() -> {
            cleanupAfterRun();
            Report report = task.getValue();
            if (report != null) {
                setStatus(report.getSummary());
            }
            refreshProjectList(); // refresh so the latest report is reflected
        }));

        task.setOnFailed(event -> Platform.runLater(() -> {
            cleanupAfterRun();
            String msg = task.getException() != null
                    ? task.getException().getMessage()
                    : "Unknown error";
            setStatus("Run failed: " + msg);
            showError("Execution error", msg);
        }));

        Thread t = new Thread(task, "run-pipeline");
        t.setDaemon(true);
        t.start();
    }

    private void cleanupAfterRun() {
        statusLabel.textProperty().unbind();
        statusProgressBar.progressProperty().unbind();
        statusProgressBar.setVisible(false);
        projectManager.getExecutionEngine().clearObservers();
        runInProgress = false;
    }

    @FXML
    private void handleRefresh() {
        refreshProjectList();
        refreshProjectPanel();
        setStatus("Refreshed.");
    }

    // ---------------------------------------------------------------
    // INTERNAL HELPERS
    // ---------------------------------------------------------------

    /**
     * Reloads the sidebar project list from the DB, preserving selection if
     * possible.
     */
    private void refreshProjectList() {
        int previouslySelectedId = currentProject != null ? currentProject.getId() : -1;
        List<Project> all = projectManager.listProjects();
        ObservableList<Project> items = FXCollections.observableArrayList(all);
        projectsListView.setItems(items);
        if (previouslySelectedId > 0) {
            for (Project p : items) {
                if (p.getId() == previouslySelectedId) {
                    projectsListView.getSelectionModel().select(p);
                    break;
                }
            }
        }
    }

    /** Loads the selected project (with test cases + latest report) from the DB. */
    private void loadSelectedProject(int projectId) {
        try {
            Project loaded = projectManager.openProject(projectId);
            if (loaded == null) {
                setStatus("Project not found in DB (id=" + projectId + ").");
                return;
            }
            currentProject = loaded;
            projectManager.setCurrentProject(loaded);
            refreshProjectPanel();
            displayLatestReport(loaded.getId());
            setStatus("Loaded: " + loaded.getName());
        } catch (Exception e) {
            showError("Failed to open project", e.getMessage());
        }
    }

    /**
     * Loads the most recent report for the project and pushes its results into the
     * table.
     */
    private void displayLatestReport(int projectId) {
        if (resultsViewController == null) return;
        resultsViewController.clearResults();

        List<Report> allReports = projectManager.getReportManager().loadAllReports(projectId);
        if (allReports.isEmpty()) return;

        if (reportHistoryComboBox != null) {
            reportHistoryComboBox.getItems().clear();
            for (Report r : allReports) {
                reportHistoryComboBox.getItems().add(
                        r.getTimestamp().toString().replace("T", " ").substring(0, 19)
                                + "  (" + r.getSuccessCount() + "/" + r.getTotalCount() + " passed)"
                );
            }
            reportHistoryComboBox.getSelectionModel().selectFirst();
            reportHistoryComboBox.setOnAction(e -> {
                int idx = reportHistoryComboBox.getSelectionModel().getSelectedIndex();
                if (idx >= 0 && idx < allReports.size()) {
                    loadReportById(allReports.get(idx).getId());
                }
            });
        }

        loadReportById(allReports.get(0).getId());
    }

    private void loadReportById(int reportId) {
        if (resultsViewController == null) return;
        resultsViewController.clearResults();
        projectManager.getReportManager().loadReportById(reportId).ifPresent(report -> {
            for (StudentResult r : report.getResults()) {
                resultsViewController.onStudentProcessed(r);
            }
        });
    }

    /** Refreshes the left-panel detail rows for the currentProject. */
    private void refreshProjectPanel() {
        projectInfoListView.getItems().clear();
        if (currentProject == null) {
            projectInfoListView.getItems().add("No project selected.");
            return;
        }
        projectInfoListView.getItems().addAll(
                "Name   : " + currentProject.getName(),
                "Config : " + (currentProject.getConfiguration() != null
                        ? currentProject.getConfiguration().getName()
                        : "— none —"),
                "Dir    : " + currentProject.getSubmissionsDirectory(),
                "Tests  : " + currentProject.getTestCases().size() + " test case(s)");
    }

    /** Opens the ProjectDialogController in a modal window. */
    private void openProjectDialog(Project existing) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/ProjectDialog.fxml"));
            Parent root = loader.load();
            ProjectDialogController ctrl = loader.getController();

            ctrl.init(projectManager, configurationManager, existing);

            Stage dialog = new Stage();
            dialog.setTitle(existing == null ? "New Project" : "Edit Project");
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(getStage());
            dialog.setScene(new Scene(root));
            dialog.showAndWait();

            Project committed = ctrl.getCommittedProject();
            if (committed != null) {
                currentProject = committed;
                projectManager.setCurrentProject(currentProject);
                refreshProjectList();
                // Re-select the project in the list so details refresh
                for (Project p : projectsListView.getItems()) {
                    if (p.getId() == committed.getId()) {
                        projectsListView.getSelectionModel().select(p);
                        break;
                    }
                }
                refreshProjectPanel();
                setStatus("Project ready: " + currentProject.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Throwable root = e;
            while (root.getCause() != null)
                root = root.getCause();
            showError("FXML Error", root.getClass().getName() + "\n" + root.getMessage());
        }
    }

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

    private void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    private Stage getStage() {
        return (Stage) projectsListView.getScene().getWindow();
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
