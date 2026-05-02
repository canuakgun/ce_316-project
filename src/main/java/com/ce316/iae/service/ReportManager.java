package com.ce316.iae.service;

import com.ce316.iae.model.Project;
import com.ce316.iae.model.Report;

import java.nio.file.Path;

/**
 * Builds and optionally persists {@link Report} (often embedded in project file).
 */
public class ReportManager {

    public Report createReport(Project project) {
        Report report = new Report();
        report.setProjectId(project.getId());
        return report;
    }

    public void saveReport(Report report, Path path) {
    }

    public Report loadReport(Path path) {
        return new Report();
    }
}
