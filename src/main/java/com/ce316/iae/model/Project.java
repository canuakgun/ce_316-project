package com.ce316.iae.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Central entity: configuration, submissions folder, test cases, last report.
 */
public class Project {

    private int id;
    private String name;
    /** Resolved configuration in memory; persistence may use {@link #configurationName} only. */
    private Configuration configuration;
    /** Name or id used when saving if {@link #configuration} is not embedded. */
    private String configurationName;
    private String submissionsDirectory;
    private final List<TestCase> testCases = new ArrayList<>();
    private Report report;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public String getConfigurationName() {
        return configurationName;
    }

    public void setConfigurationName(String configurationName) {
        this.configurationName = configurationName;
    }

    public String getSubmissionsDirectory() {
        return submissionsDirectory;
    }

    public void setSubmissionsDirectory(String submissionsDirectory) {
        this.submissionsDirectory = submissionsDirectory;
    }

    public List<TestCase> getTestCases() {
        return testCases;
    }

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }
}
