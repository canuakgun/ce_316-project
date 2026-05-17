package com.ce316.iae.service;

import com.ce316.iae.model.Project;
import com.ce316.iae.model.Report;
import com.ce316.iae.model.StudentResult;
import com.ce316.iae.model.StudentSubmission;
import com.ce316.iae.model.TestCase;
import com.ce316.iae.persistence.PersistenceManager;

import javafx.concurrent.Task;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Project lifecycle: create, open, save, and run the grading pipeline.
 *
 * <p>All DB operations go through the {@link PersistenceManager} singleton.
 * {@link #buildRunTask()} returns a JavaFX {@link Task}{@code <Report>} so the
 * pipeline runs on a background thread without blocking the UI thread.
 *
 * <p>Pipeline order:
 * <ol>
 *   <li>{@link ZipProcessor}    – list &amp; extract student ZIP archives</li>
 *   <li>{@link ExecutionEngine} – compile &amp; run each submission</li>
 *   <li>{@link ReportManager}   – assemble results, persist to DB</li>
 * </ol>
 */
public class ProjectManager {

    private final PersistenceManager pm;
    private final ZipProcessor       zipProcessor    = new ZipProcessor();
    private final ExecutionEngine    executionEngine = new ExecutionEngine();
    private final ReportManager      reportManager;

    /** The project currently loaded in the UI; {@code null} when none is open. */
    private Project currentProject;

    public ProjectManager(PersistenceManager pm) {
        this.pm            = pm;
        this.reportManager = new ReportManager(pm);
    }

    // -----------------------------------------------------------------------
    // Creation / loading pipeline
    // -----------------------------------------------------------------------

    /**
     * Creates a new empty project shell (in memory only — caller must attach
     * a configuration and test cases, then call {@link #persistNewProject(Project)}).
     */
    public Project createProject(String name, String submissionsDirectory) {
        Project project = new Project();
        project.setName(name);
        project.setSubmissionsDirectory(submissionsDirectory);
        project.stampCreatedAt();
        currentProject = project;
        return project;
    }

    /**
     * Persists a fully populated new project (with its configuration and test
     * cases attached) to the DB in a single transaction. The DB assigns an id
     * which is set back onto the project.
     */
    public void persistNewProject(Project project) {
        if (project == null) throw new IllegalArgumentException("project must not be null");
        project.stampCreatedAt();
        try {
            pm.saveProject(project); // INSERT project + INSERT test_cases (1 tx)
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create project: " + e.getMessage(), e);
        }
        currentProject = project;
    }

    /**
     * Loads a project from the DB by id and makes it the current project.
     *
     * @param projectId DB id of the project to load
     * @return the loaded project, or {@code null} if not found
     */
    public Project openProject(int projectId) {
        try {
            currentProject = pm.loadProject(projectId).orElse(null);
        } catch (SQLException e) {
            System.err.println("ProjectManager.openProject: " + e.getMessage());
            currentProject = null;
        }
        return currentProject;
    }

    /**
     * Returns all projects from the DB (for a "recent projects" list).
     */
    public List<Project> listProjects() {
        try {
            return pm.listProjects();
        } catch (SQLException e) {
            System.err.println("ProjectManager.listProjects: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Persists changes to the current project back to the DB.
     * Updates project metadata and replaces all test cases in one transaction.
     */
    public void saveProject() {
        if (currentProject == null) return;
        try {
            pm.updateProject(currentProject);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save project: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes the current project from the DB (cascades to test_cases and reports).
     */
    public void deleteProject() {
        if (currentProject == null) return;
        try {
            pm.deleteProject(currentProject.getId());
            currentProject = null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete project: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // runProject — JavaFX Task
    // -----------------------------------------------------------------------

    /**
     * Builds and returns a JavaFX {@link Task}{@code <Report>} that runs the
     * complete grading pipeline on a background thread.
     *
     * <p>The task publishes progress (0–3) and a human-readable message at each
     * step so the UI can bind a {@link javafx.scene.control.ProgressBar} and
     * status label directly.
     *
     * <p>Typical controller usage:
     * <pre>{@code
     * Task<Report> task = projectManager.buildRunTask();
     * progressBar.progressProperty().bind(task.progressProperty());
     * statusLabel.textProperty().bind(task.messageProperty());
     * task.setOnSucceeded(e -> showResults(task.getValue()));
     * task.setOnFailed(e -> showError(task.getException().getMessage()));
     * new Thread(task, "run-pipeline").start();
     * }</pre>
     *
     * @return a configured, ready-to-submit {@link Task}
     * @throws IllegalStateException if no project is loaded or it is not runnable
     */
    public Task<Report> buildRunTask() {
        Project project = requireCurrentProject();
        validateRunnable(project);

        // Capture the state needed by the lambda (effectively final)
        final PersistenceManager capturedPm          = pm;
        final ZipProcessor       capturedZip         = zipProcessor;
        final ExecutionEngine    capturedEngine      = executionEngine;
        final ReportManager      capturedReportMgr   = reportManager;

        return new Task<>() {
            @Override
            protected Report call() throws Exception {

                // Step 1 – extract student ZIP archives
                updateMessage("Extracting student submissions…");
                updateProgress(0, 3);

                java.io.File submissionsDir = new java.io.File(project.getSubmissionsDirectory());
                List<StudentSubmission> submissions = capturedZip.extractAll(submissionsDir);

                // Step 2 – compile and run each submission against all test cases
                updateMessage("Compiling and running submissions ("
                        + submissions.size() + " found)…");
                updateProgress(1, 3);

                List<StudentResult> results = capturedEngine.runAll(project);

                // Step 3 – assemble report and persist it to the DB
                updateMessage("Assembling and saving report…");
                updateProgress(2, 3);

                Report report = capturedReportMgr.buildReport(project, results);
                report.computeCounts();
                project.setReport(report);

                // Persist the report (inserts header + student_results in one transaction)
                capturedReportMgr.saveReport(report);

                // Update project in DB to reflect that a run has completed
                capturedPm.updateProject(project);

                updateProgress(3, 3);
                updateMessage("Done — " + report.getSummary());
                return report;
            }
        };
    }

    // -----------------------------------------------------------------------
    // Test-case helpers
    // -----------------------------------------------------------------------

    /**
     * Adds a test case to the current project and persists via
     * {@link #saveProject()}.
     */
    public void addTestCase(TestCase testCase) {
        if (testCase == null) throw new IllegalArgumentException("testCase must not be null");
        requireCurrentProject().getTestCases().add(testCase);
        saveProject();
    }

    /**
     * Removes a test case from the current project and persists via
     * {@link #saveProject()}.
     */
    public void removeTestCase(TestCase testCase) {
        requireCurrentProject().getTestCases().remove(testCase);
        saveProject();
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public Project getCurrentProject() { return currentProject; }

    public void setCurrentProject(Project project) { this.currentProject = project; }

    public ZipProcessor    getZipProcessor()    { return zipProcessor;    }
    public ExecutionEngine getExecutionEngine() { return executionEngine; }
    public ReportManager   getReportManager()   { return reportManager;   }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Project requireCurrentProject() {
        if (currentProject == null)
            throw new IllegalStateException("No project is currently loaded.");
        return currentProject;
    }

    private void validateRunnable(Project project) {
        if (project.getSubmissionsDirectory() == null
                || project.getSubmissionsDirectory().isBlank())
            throw new IllegalStateException(
                    "Project has no submissions directory configured.");
        if (project.getConfiguration() == null)
            throw new IllegalStateException(
                    "Project has no language configuration set.");
        if (project.getTestCases().isEmpty())
            throw new IllegalStateException(
                    "Project has no test cases defined.");
    }
}