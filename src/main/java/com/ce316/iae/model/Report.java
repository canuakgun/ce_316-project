package com.ce316.iae.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates all {@link StudentResult} entries for one project run.
 */
public class Report {

    private int projectId;
    private Instant timestamp = Instant.now();
    private final List<StudentResult> results = new ArrayList<>();

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public List<StudentResult> getResults() {
        return results;
    }

    public String getSummary() {
        long pass = results.stream()
                .filter(r -> r.getStatus() == SubmissionStatus.SUCCESS)
                .count();
        long fail = results.size() - pass;
        return pass + " passed, " + fail + " failed or errors";
    }
}
