package com.ce316.iae.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Central entity: ties together a configuration, a submissions directory,
 * test cases, and the latest report.
 */
public class Project {

    private int id;
    private String name;
    private Configuration configuration;
    private String submissionsDirectory;
    private final List<TestCase> testCases = new ArrayList<>();
    private Report report;
    /** ISO-8601 creation timestamp, set once via {@link #stampCreatedAt()}. */
    private String createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Configuration getConfiguration() { return configuration; }
    public void setConfiguration(Configuration configuration) { this.configuration = configuration; }

    public String getSubmissionsDirectory() { return submissionsDirectory; }
    public void setSubmissionsDirectory(String submissionsDirectory) {
        this.submissionsDirectory = submissionsDirectory;
    }

    public List<TestCase> getTestCases() { return testCases; }

    public Report getReport() { return report; }
    public void setReport(Report report) { this.report = report; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    /** Sets createdAt to now if it has not been set yet. */
    public void stampCreatedAt() {
        if (createdAt == null || createdAt.isBlank()) {
            createdAt = Instant.now().toString();
        }
    }

    @Override
    public String toString() { return name != null ? name : "(unnamed project)"; }
}
