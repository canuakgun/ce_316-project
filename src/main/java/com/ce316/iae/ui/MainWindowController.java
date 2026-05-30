package com.ce316.iae.ui;

import com.ce316.iae.model.Configuration;
import com.ce316.iae.model.Project;
import com.ce316.iae.model.Report;
import com.ce316.iae.model.StudentResult;
import com.ce316.iae.persistence.PersistenceManager;
import com.ce316.iae.service.ConfigurationManager;
import com.ce316.iae.service.ProjectManager;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

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

    /** Hero title — large serif headline above the results canvas. */
    @FXML
    private Label heroTitleLabel;

    /** 8x8 pulse dot in the hero strip; pseudo-classes drive its color. */
    @FXML
    private Region statusDot;

    /** Floating accent CTA — disabled while a run is in flight. */
    @FXML
    private Button runButton;

    /** Sidebar container — used for the entry slide-in. */
    @FXML
    private VBox sidebarVBox;

    private static final PseudoClass PC_IDLE    = PseudoClass.getPseudoClass("idle");
    private static final PseudoClass PC_RUNNING = PseudoClass.getPseudoClass("running");
    private static final PseudoClass PC_OK      = PseudoClass.getPseudoClass("ok");
    private static final PseudoClass PC_ERROR   = PseudoClass.getPseudoClass("error");

    /** Indefinite scale pulse animation on the status dot while running. */
    private ScaleTransition dotPulse;

    // ---------------------------------------------------------------
    // Service layer
    // ---------------------------------------------------------------

    private final PersistenceManager persistenceManager;
    private final ProjectManager projectManager;
    private final ConfigurationManager configurationManager;

    /** Currently selected/loaded project. */
    private Project currentProject;

    /** Guards against accelerator-key double-fire while a task is running. */
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
        statusProgressBar.setManaged(false);
        setDotState(PC_IDLE);

        projectsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Project p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : "#" + p.getId() + "  " + p.getName());
            }
        });

        projectsListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldP, newP) -> {
                    if (newP != null) {
                        loadSelectedProject(newP.getId());
                    }
                });

        refreshProjectList();
        refreshProjectPanel();
        setStatus("Ready");

        // Pre-set the offstage state for the entry reveal so there's no flash
        // of the final layout before the animation begins.
        primeEntryState();

        runButton.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) Platform.runLater(this::playEntryReveal);
        });
    }

    /** Place the three reveal targets in their offstage positions. */
    private void primeEntryState() {
        heroTitleLabel.setOpacity(0);
        heroTitleLabel.setTranslateY(8);
        runButton.setOpacity(0);
        runButton.setScaleX(0.92);
        runButton.setScaleY(0.92);
        sidebarVBox.setOpacity(0);
        sidebarVBox.setTranslateX(-20);
    }

    /**
     * Editorial cascade — three beats:
     *   0ms   hero      fade + lift  (320ms)
     *   180ms run pill  fade + scale (220ms)
     *   320ms sidebar   fade + slide (260ms)
     */
    private void playEntryReveal() {
        // Beat 1 — hero rises
        FadeTransition heroFade = new FadeTransition(Duration.millis(320), heroTitleLabel);
        heroFade.setFromValue(0); heroFade.setToValue(1);
        heroFade.setInterpolator(Interpolator.EASE_OUT);
        TranslateTransition heroLift = new TranslateTransition(Duration.millis(320), heroTitleLabel);
        heroLift.setFromY(8); heroLift.setToY(0);
        heroLift.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(heroFade, heroLift).play();

        // Beat 2 — run pill scales in
        FadeTransition pillFade = new FadeTransition(Duration.millis(220), runButton);
        pillFade.setFromValue(0); pillFade.setToValue(1);
        pillFade.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition pillScale = new ScaleTransition(Duration.millis(220), runButton);
        pillScale.setFromX(0.92); pillScale.setFromY(0.92);
        pillScale.setToX(1.0);    pillScale.setToY(1.0);
        pillScale.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition pillIn = new ParallelTransition(pillFade, pillScale);
        pillIn.setDelay(Duration.millis(180));
        pillIn.play();

        // Beat 3 — sidebar slides in
        FadeTransition sideFade = new FadeTransition(Duration.millis(260), sidebarVBox);
        sideFade.setFromValue(0); sideFade.setToValue(1);
        sideFade.setInterpolator(Interpolator.EASE_OUT);
        TranslateTransition sideSlide = new TranslateTransition(Duration.millis(260), sidebarVBox);
        sideSlide.setFromX(-20); sideSlide.setToX(0);
        sideSlide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition sideIn = new ParallelTransition(sideFade, sideSlide);
        sideIn.setDelay(Duration.millis(320));
        sideIn.play();
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
        confirm.setHeaderText("Delete this project?");
        confirm.setContentText("Permanently delete \"" + currentProject.getName()
                + "\" and all its reports? This cannot be undone.");
        ThemeManager.apply(confirm);
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
        ThemeManager.apply(pick);
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

    /** Opens the bundled user manual via the styled HelpWindow shell. */
    @FXML
    private void handleUserManual() {
        try {
            URL manual = getClass().getResource("/manual/index.html");
            if (manual == null) {
                showInfo("User manual not bundled in this build.");
                return;
            }
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/HelpWindow.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("IAE — User Manual");
            stage.initOwner(getStage());
            Scene manualScene = new Scene(root);
            ThemeManager.apply(manualScene);
            stage.setScene(manualScene);
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
        ThemeManager.apply(about);
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
        statusProgressBar.setManaged(true);
        if (runButton != null) runButton.setDisable(true);
        setDotState(PC_RUNNING);
        startDotPulse();
        runInProgress = true;

        task.setOnSucceeded(event -> Platform.runLater(() -> {
            cleanupAfterRun(PC_OK);
            Report report = task.getValue();
            if (report != null) {
                setStatus(report.getSummary());
            }
            refreshProjectList();
        }));

        task.setOnFailed(event -> Platform.runLater(() -> {
            cleanupAfterRun(PC_ERROR);
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

    private void cleanupAfterRun(PseudoClass terminalState) {
        statusLabel.textProperty().unbind();
        statusProgressBar.progressProperty().unbind();
        statusProgressBar.setVisible(false);
        statusProgressBar.setManaged(false);
        stopDotPulse();
        setDotState(terminalState);
        if (runButton != null) runButton.setDisable(false);
        projectManager.getExecutionEngine().clearObservers();
        runInProgress = false;
    }

    /** Toggle exactly one custom pseudo-class on the status dot. */
    private void setDotState(PseudoClass active) {
        if (statusDot == null) return;
        statusDot.pseudoClassStateChanged(PC_IDLE,    active == PC_IDLE);
        statusDot.pseudoClassStateChanged(PC_RUNNING, active == PC_RUNNING);
        statusDot.pseudoClassStateChanged(PC_OK,      active == PC_OK);
        statusDot.pseudoClassStateChanged(PC_ERROR,   active == PC_ERROR);
    }

    /**
     * Soft, indefinite scale pulse on the status dot.
     * 1.0 → 1.5 → 1.0 over 900ms, eased — communicates aliveness without
     * the jitter of a hard blink.
     */
    private void startDotPulse() {
        if (statusDot == null) return;
        stopDotPulse();
        dotPulse = new ScaleTransition(Duration.millis(900), statusDot);
        dotPulse.setFromX(1.0);  dotPulse.setFromY(1.0);
        dotPulse.setToX(1.5);    dotPulse.setToY(1.5);
        dotPulse.setAutoReverse(true);
        dotPulse.setCycleCount(ScaleTransition.INDEFINITE);
        dotPulse.setInterpolator(Interpolator.EASE_BOTH);
        dotPulse.play();
    }

    private void stopDotPulse() {
        if (dotPulse != null) {
            dotPulse.stop();
            dotPulse = null;
        }
        if (statusDot != null) {
            statusDot.setScaleX(1.0);
            statusDot.setScaleY(1.0);
        }
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
     * Loads the most recent report for the project and pushes its results into
     * the table. Also rebuilds the run-history dropdown.
     *
     * NB: the setOnAction handler is detached before items are mutated; otherwise
     * the previous closure (capturing a stale `allReports` from the prior project)
     * would fire during selectFirst() and load the wrong report into the table.
     */
    private void displayLatestReport(int projectId) {
        if (resultsViewController == null) return;
        resultsViewController.clearResults();

        if (reportHistoryComboBox != null) {
            // Detach handler before mutating items so transient selection
            // changes during clear/selectFirst don't fire with stale data.
            reportHistoryComboBox.setOnAction(null);
            reportHistoryComboBox.getItems().clear();
        }

        List<Report> allReports = projectManager.getReportManager().loadAllReports(projectId);
        if (allReports.isEmpty()) {
            if (reportHistoryComboBox != null) {
                reportHistoryComboBox.setPromptText("No previous runs yet");
            }
            return;
        }

        if (reportHistoryComboBox != null) {
            for (Report r : allReports) {
                reportHistoryComboBox.getItems().add(formatReportLabel(r));
            }
            reportHistoryComboBox.getSelectionModel().selectFirst();
            // Re-attach handler AFTER the initial selection. Closure binds
            // this call's `allReports`, never the one from an earlier call.
            reportHistoryComboBox.setOnAction(e -> {
                int idx = reportHistoryComboBox.getSelectionModel().getSelectedIndex();
                if (idx >= 0 && idx < allReports.size()) {
                    loadReportById(allReports.get(idx).getId());
                }
            });
        }

        loadReportById(allReports.get(0).getId());
    }

    /** "2026-05-30 14:23:45  ·  12 / 15 passed" — readable, monospaced-friendly. */
    private String formatReportLabel(Report r) {
        String ts = r.getTimestamp().toString().replace("T", " ");
        if (ts.length() > 19) ts = ts.substring(0, 19);
        return ts + "  ·  " + r.getSuccessCount() + " / " + r.getTotalCount() + " passed";
    }

    private void loadReportById(int reportId) {
        if (resultsViewController == null) return;
        resultsViewController.clearResults();
        try {
            projectManager.getReportManager().loadReportById(reportId).ifPresent(report -> {
                for (StudentResult r : report.getResults()) {
                    resultsViewController.onStudentProcessed(r);
                }
            });
        } catch (Exception e) {
            setStatus("Could not load report: " + e.getMessage());
        }
    }

    /** Refreshes the left-panel detail rows + hero title for the currentProject. */
    private void refreshProjectPanel() {
        projectInfoListView.getItems().clear();
        if (currentProject == null) {
            projectInfoListView.getItems().add("— no project selected —");
            if (heroTitleLabel != null) heroTitleLabel.setText("Select a project");
            return;
        }
        if (heroTitleLabel != null) heroTitleLabel.setText(currentProject.getName());
        projectInfoListView.getItems().addAll(
                "Name   · " + currentProject.getName(),
                "Config · " + (currentProject.getConfiguration() != null
                        ? currentProject.getConfiguration().getName()
                        : "— none —"),
                "Dir    · " + currentProject.getSubmissionsDirectory(),
                "Tests  · " + currentProject.getTestCases().size() + " case(s)");
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
            Scene scene = new Scene(root);
            ThemeManager.apply(scene);
            dialog.setScene(scene);
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
            Scene scene = new Scene(root);
            ThemeManager.apply(scene);
            dialog.setScene(scene);
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
        alert.setHeaderText(title);
        alert.setContentText(message != null ? message : "Unknown error.");
        ThemeManager.apply(alert);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        ThemeManager.apply(alert);
        alert.showAndWait();
    }
}
