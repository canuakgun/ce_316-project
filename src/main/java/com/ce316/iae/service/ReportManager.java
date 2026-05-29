package com.ce316.iae.service;

import com.ce316.iae.model.Project;
import com.ce316.iae.model.Report;
import com.ce316.iae.model.StudentResult;
import com.ce316.iae.model.SubmissionStatus;
import com.ce316.iae.persistence.PersistenceManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Assembles {@link Report} objects from {@link StudentResult} lists produced by
 * {@link ExecutionEngine}, persists them to the DB via {@link PersistenceManager},
 * and exports them as CSV or HTML.
 */
public class ReportManager {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                             .withZone(ZoneId.systemDefault());

    private final PersistenceManager pm;

    public ReportManager(PersistenceManager pm) {
        this.pm = pm;
    }

    // -----------------------------------------------------------------------
    // Assembly
    // -----------------------------------------------------------------------

    /**
     * Creates an empty {@link Report} shell linked to the given project.
     *
     * @param project owning project (must not be {@code null})
     * @return empty report with {@code projectId} set
     */
    public Report createReport(Project project) {
        if (project == null) throw new IllegalArgumentException("project must not be null");
        Report report = new Report();
        report.setProjectId(project.getId());
        return report;
    }

    /**
     * Assembles a fully populated {@link Report} from the list of
     * {@link StudentResult} objects returned by {@link ExecutionEngine#runAll}.
     *
     * <p>Results are added in the order they are supplied. {@link Report#computeCounts()}
     * is <em>not</em> called here — the caller should call it after the report is
     * returned (so counts are computed from the final, complete list).
     *
     * @param project the owning project
     * @param results per-student results from the execution engine
     * @return a populated report ready for {@link #saveReport(Report)}
     */
    public Report buildReport(Project project, List<StudentResult> results) {
        Report report = createReport(project);
        if (results != null) {
            report.getResults().addAll(results);
        }
        return report;
    }

    // -----------------------------------------------------------------------
    // DB persistence (via PersistenceManager)
    // -----------------------------------------------------------------------

    /**
     * Persists a report to the DB (inserts report header + all student results
     * in a single transaction). Sets {@code report.id} from the AUTOINCREMENT key.
     *
     * @param report the report to persist (must not be {@code null})
     */
    public void saveReport(Report report) {
        if (report == null) return;
        try {
            pm.saveReport(report);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save report: " + e.getMessage(), e);
        }
    }

    /**
     * Loads the most recent report for a project from the DB.
     *
     * @param projectId the owning project's DB id
     * @return the latest report, or {@link Optional#empty()} if none exists
     */
    public Optional<Report> loadLatestReport(int projectId) {
        try {
            return pm.loadLatestReport(projectId);
        } catch (SQLException e) {
            System.err.println("ReportManager.loadLatestReport: " + e.getMessage());
            return Optional.empty();
        }
    }
    public List<Report> loadAllReports(int projectId) {
        try {
            return pm.loadAllReports(projectId);
        } catch (SQLException e) {
            System.err.println("ReportManager.loadAllReports: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    public Optional<Report> loadReportById(int reportId) {
        try {
            return pm.loadReportById(reportId); // geçici — aşağıda açıkladım
        } catch (SQLException e) {
            System.err.println("ReportManager.loadReportById: " + e.getMessage());
            return Optional.empty();
        }
    }

    // -----------------------------------------------------------------------
    // CSV export
    // -----------------------------------------------------------------------

    /**
     * Exports the report as a CSV file.
     *
     * <p>Columns: {@code studentId,status,actualOutput,errorMessage,diffText}
     * A header row is always written first. Values containing commas, double
     * quotes, or newlines are wrapped in double quotes per RFC 4180.
     *
     * @param report the report to export
     * @param path   destination CSV file (created or overwritten)
     * @throws IOException if the file cannot be written
     */
    public void exportCsv(Report report, Path path) throws IOException {
        if (report == null || path == null) return;
        report.computeCounts();

        StringWriter sw = new StringWriter();
        PrintWriter  pw = new PrintWriter(sw);

        pw.println("studentId,status,actualOutput,errorMessage,diffText");

        for (StudentResult r : report.getResults()) {
            pw.printf("%s,%s,%s,%s,%s%n",
                    csvEscape(r.getStudentId()),
                    csvEscape(r.getStatus() != null ? r.getStatus().name() : ""),
                    csvEscape(r.getActualOutput()),
                    csvEscape(r.getErrorMessage()),
                    csvEscape(r.getDiffText()));
        }

        pw.println();
        pw.println("# " + report.getSummary());

        Files.writeString(path, sw.toString(), StandardCharsets.UTF_8);
    }

    // -----------------------------------------------------------------------
    // HTML export
    // -----------------------------------------------------------------------

    /**
     * Exports the report as a self-contained HTML file with colour-coded status
     * cells (green = SUCCESS, red = COMPILE/RUNTIME/WRONG_OUTPUT, grey = SKIPPED).
     *
     * @param report the report to export
     * @param path   destination HTML file (created or overwritten)
     * @throws IOException if the file cannot be written
     */
    public void exportHtml(Report report, Path path) throws IOException {
        if (report == null || path == null) return;
        report.computeCounts();
        Files.writeString(path, buildHtml(report), StandardCharsets.UTF_8);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private String buildHtml(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head>")
          .append("<meta charset=\"UTF-8\">")
          .append("<title>IAE Grading Report</title>")
          .append("<style>")
          .append("body{font-family:sans-serif;margin:2rem;color:#1a1a1a}")
          .append("h1{margin-bottom:.25rem}")
          .append(".meta{color:#555;margin-bottom:1.5rem;font-size:.95rem}")
          .append("table{border-collapse:collapse;width:100%;font-size:.9rem}")
          .append("th,td{border:1px solid #ccc;padding:.4rem .8rem;text-align:left;vertical-align:top}")
          .append("th{background:#f0f0f0;font-weight:600}")
          .append("tr:nth-child(even){background:#fafafa}")
          .append(".SUCCESS{color:#15803d;font-weight:600}")
          .append(".COMPILE_ERROR,.RUNTIME_ERROR,.WRONG_OUTPUT{color:#b91c1c;font-weight:600}")
          .append(".SKIPPED{color:#6b7280;font-weight:600}")
          .append("pre{margin:0;white-space:pre-wrap;word-break:break-all;font-size:.85rem}")
          .append("</style></head><body>")
          .append("<h1>IAE Grading Report</h1>")
          .append("<p class=\"meta\">")
          .append("<strong>Generated:</strong> ")
          .append(DISPLAY_FMT.format(report.getTimestamp()))
          .append(" &nbsp;|&nbsp; <strong>Project ID:</strong> ").append(report.getProjectId())
          .append(" &nbsp;|&nbsp; <strong>Summary:</strong> ")
          .append(htmlEscape(report.getSummary()))
          .append("</p>")
          .append("<table><thead><tr>")
          .append("<th>#</th><th>Student ID</th><th>Status</th>")
          .append("<th>Actual Output</th><th>Error Message</th><th>Diff</th>")
          .append("</tr></thead><tbody>");

        List<StudentResult> results = report.getResults();
        for (int i = 0; i < results.size(); i++) {
            StudentResult r      = results.get(i);
            String        status = r.getStatus() != null ? r.getStatus().name() : "UNKNOWN";

            sb.append("<tr>")
              .append("<td>").append(i + 1).append("</td>")
              .append("<td>").append(htmlEscape(r.getStudentId())).append("</td>")
              .append("<td class=\"").append(status).append("\">").append(status).append("</td>")
              .append("<td><pre>").append(htmlEscape(r.getActualOutput())).append("</pre></td>")
              .append("<td><pre>").append(htmlEscape(r.getErrorMessage())).append("</pre></td>")
              .append("<td><pre>").append(htmlEscape(r.getDiffText())).append("</pre></td>")
              .append("</tr>");
        }

        sb.append("</tbody></table></body></html>");
        return sb.toString();
    }

    /** RFC 4180 CSV field escaping. */
    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")
                || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String htmlEscape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}