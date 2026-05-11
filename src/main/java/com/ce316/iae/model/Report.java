package com.ce316.iae.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates all {@link StudentResult} entries from one project run.
 */
public class Report {

    private int id;
    private int projectId;
    private Instant timestamp = Instant.now();
    private final List<StudentResult> results = new ArrayList<>();

    private int totalCount;
    private int successCount;
    private int failCount;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProjectId() { return projectId; }
    public void setProjectId(int projectId) { this.projectId = projectId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public List<StudentResult> getResults() { return results; }

    public int getTotalCount() { return totalCount; }
    public int getSuccessCount() { return successCount; }
    public int getFailCount() { return failCount; }

    /** Recalculates totalCount, successCount, failCount from current results list. */
    public void computeCounts() {
        totalCount = results.size();
        successCount = (int) results.stream()
                .filter(r -> r.getStatus() == SubmissionStatus.SUCCESS)
                .count();
        failCount = totalCount - successCount;
    }

    public String getSummary() {
        computeCounts();
        return successCount + " / " + totalCount + " passed  (" + failCount + " failed or error)";
    }
}
