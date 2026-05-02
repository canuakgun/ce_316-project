package com.ce316.iae.service;

import com.ce316.iae.model.Project;
import com.ce316.iae.persistence.PersistenceManager;

import java.nio.file.Path;

/**
 * Project lifecycle: create, open, save, run pipeline.
 */
public class ProjectManager {

    private final ZipProcessor zipProcessor = new ZipProcessor();
    private final ExecutionEngine executionEngine = new ExecutionEngine();
    private final ReportManager reportManager = new ReportManager();
    private final PersistenceManager persistenceManager;

    private Project currentProject;

    public ProjectManager(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public Project getCurrentProject() {
        return currentProject;
    }

    public void setCurrentProject(Project currentProject) {
        this.currentProject = currentProject;
    }

    public Project openProject(Path path) {
        currentProject = persistenceManager.loadProject(path).orElse(null);
        return currentProject;
    }

    public void saveProject() {
        if (currentProject != null) {
            // Path typically comes from "Save As" or last opened file — set by UI later
        }
    }

    public void saveProjectAs(Path path) {
        if (currentProject != null) {
            persistenceManager.saveProject(currentProject, path);
        }
    }

    public void runProject() {
        if (currentProject == null) {
            return;
        }
        // Orchestrate: zipProcessor -> executionEngine -> report
    }

    public ZipProcessor getZipProcessor() {
        return zipProcessor;
    }

    public ExecutionEngine getExecutionEngine() {
        return executionEngine;
    }

    public ReportManager getReportManager() {
        return reportManager;
    }
}
