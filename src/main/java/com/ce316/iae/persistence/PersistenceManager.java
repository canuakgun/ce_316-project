package com.ce316.iae.persistence;

import com.ce316.iae.model.*;

import java.io.File;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Singleton. Manages %APPDATA%\IAE\iae.db and the exports/ + logs/ folders.
 * All queries use PreparedStatement. Transactions use setAutoCommit(false);
 * finally block always restores autoCommit.
 */
public class PersistenceManager {

    private static PersistenceManager instance;

    private final Path dbPath;
    private final Path exportsDir;
    private final Path logsDir;

    private PersistenceManager() {
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        Path base = Path.of(appData, "IAE");
        base.toFile().mkdirs();
        exportsDir = base.resolve("exports");
        logsDir    = base.resolve("logs");
        exportsDir.toFile().mkdirs();
        logsDir.toFile().mkdirs();
        dbPath = base.resolve("iae.db");
        initDb();
    }

    public static synchronized PersistenceManager getInstance() {
        if (instance == null) instance = new PersistenceManager();
        return instance;
    }

    public Path getExportsDir() { return exportsDir; }
    public Path getLogsDir()    { return logsDir; }

    // ------------------------------------------------------------------
    // DB bootstrap
    // ------------------------------------------------------------------

    private Connection openConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    private void initDb() {
        try (Connection conn = openConnection();
             Statement st = conn.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS configurations (
                    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
                    name                    TEXT    NOT NULL UNIQUE,
                    compiler_path           TEXT,
                    compile_args            TEXT,
                    file_to_compile         TEXT,
                    relative_executable_path TEXT,
                    interpreted             INTEGER NOT NULL DEFAULT 0
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS projects (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    name             TEXT    NOT NULL,
                    config_id        INTEGER,
                    submissions_dir  TEXT,
                    created_at       TEXT,
                    FOREIGN KEY (config_id) REFERENCES configurations(id) ON DELETE SET NULL
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS test_cases (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    project_id     INTEGER NOT NULL,
                    input_args     TEXT,
                    expected_output TEXT,
                    description    TEXT,
                    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS reports (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    project_id INTEGER NOT NULL,
                    timestamp  TEXT,
                    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS student_results (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    report_id     INTEGER NOT NULL,
                    student_id    TEXT,
                    status        TEXT,
                    actual_output TEXT,
                    error_message TEXT,
                    diff_text     TEXT,
                    FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
                )""");

            // --- Schema migrations (safe to run on every start) ---
            // runMigration() silently ignores "duplicate column" errors so each
            // ALTER TABLE is safe to execute on every application start regardless
            // of which schema version the existing database was created with.

            // configurations table
            runMigration(st, "ALTER TABLE configurations ADD COLUMN compiler_path TEXT");
            runMigration(st, "ALTER TABLE configurations ADD COLUMN compile_args TEXT");
            runMigration(st, "ALTER TABLE configurations ADD COLUMN file_to_compile TEXT");
            runMigration(st, "ALTER TABLE configurations ADD COLUMN relative_executable_path TEXT");
            runMigration(st, "ALTER TABLE configurations ADD COLUMN interpreted INTEGER NOT NULL DEFAULT 0");

            // projects table
            runMigration(st, "ALTER TABLE projects ADD COLUMN config_id INTEGER");
            runMigration(st, "ALTER TABLE projects ADD COLUMN submissions_dir TEXT");
            runMigration(st, "ALTER TABLE projects ADD COLUMN created_at TEXT");

            // test_cases table
            runMigration(st, "ALTER TABLE test_cases ADD COLUMN description TEXT");

            // student_results table
            runMigration(st, "ALTER TABLE student_results ADD COLUMN actual_output TEXT");
            runMigration(st, "ALTER TABLE student_results ADD COLUMN error_message TEXT");
            runMigration(st, "ALTER TABLE student_results ADD COLUMN diff_text TEXT");

        } catch (SQLException e) {
            throw new RuntimeException("DB init failed: " + e.getMessage(), e);
        }
    }

    /**
     * Runs a DDL migration statement, silently ignoring errors (e.g. "duplicate
     * column name") so that migrations are safe to re-run on every start.
     */
    private void runMigration(Statement st, String ddl) {
        try {
            st.execute(ddl);
        } catch (SQLException ignored) {
            // column already exists, or other benign schema conflict — skip
        }
    }

    // ------------------------------------------------------------------
    // Configuration CRUD
    // ------------------------------------------------------------------

    /** Inserts a new configuration. Sets config.id from generated key. */
    public void saveConfig(Configuration config) throws SQLException {
        String sql = "INSERT INTO configurations (name,compiler_path,compile_args,file_to_compile,relative_executable_path,interpreted) VALUES (?,?,?,?,?,?)";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindConfig(ps, config);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) config.setId(rs.getInt(1));
            }
        }
    }

    public void updateConfig(Configuration config) throws SQLException {
        String sql = "UPDATE configurations SET name=?,compiler_path=?,compile_args=?,file_to_compile=?,relative_executable_path=?,interpreted=? WHERE id=?";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindConfig(ps, config);
            ps.setInt(7, config.getId());
            ps.executeUpdate();
        }
    }

    public Optional<Configuration> loadConfig(int id) throws SQLException {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM configurations WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapConfig(rs));
            }
        }
        return Optional.empty();
    }

    public List<Configuration> listConfigs() throws SQLException {
        List<Configuration> list = new ArrayList<>();
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM configurations ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapConfig(rs));
        }
        return list;
    }

    /**
     * Deletes a configuration. Projects that reference it get config_id = NULL
     * via ON DELETE SET NULL (foreign key).
     */
    public void deleteConfig(int id) throws SQLException {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM configurations WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private void bindConfig(PreparedStatement ps, Configuration c) throws SQLException {
        ps.setString(1, c.getName());
        ps.setString(2, c.getCompilerPath());
        ps.setString(3, c.getCompileArgs());
        ps.setString(4, c.getFileToCompile());
        ps.setString(5, c.getRelativeExecutablePath());
        ps.setInt(6, c.isInterpreted() ? 1 : 0);
    }

    private Configuration mapConfig(ResultSet rs) throws SQLException {
        Configuration c = new Configuration();
        c.setId(rs.getInt("id"));
        c.setName(rs.getString("name"));
        c.setCompilerPath(rs.getString("compiler_path"));
        c.setCompileArgs(rs.getString("compile_args"));
        c.setFileToCompile(rs.getString("file_to_compile"));
        c.setRelativeExecutablePath(rs.getString("relative_executable_path"));
        c.setInterpreted(rs.getInt("interpreted") == 1);
        return c;
    }

    // ------------------------------------------------------------------
    // Project CRUD (transactions)
    // ------------------------------------------------------------------

    /** Inserts project + test cases in a single transaction. */
    public void saveProject(Project project) throws SQLException {
        project.stampCreatedAt();
        Connection conn = openConnection();
        conn.setAutoCommit(false);
        try {
            String sql = "INSERT INTO projects (name,config_id,submissions_dir,created_at) VALUES (?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bindProject(ps, project);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) project.setId(rs.getInt(1));
                }
            }
            insertTestCases(conn, project);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    /** Updates project metadata and replaces all test cases in a single transaction. */
    public void updateProject(Project project) throws SQLException {
        Connection conn = openConnection();
        conn.setAutoCommit(false);
        try {
            String sql = "UPDATE projects SET name=?,config_id=?,submissions_dir=? WHERE id=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, project.getName());
                Integer configId = project.getConfiguration() != null ? project.getConfiguration().getId() : null;
                if (configId != null) ps.setInt(2, configId); else ps.setNull(2, Types.INTEGER);
                ps.setString(3, project.getSubmissionsDirectory());
                ps.setInt(4, project.getId());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM test_cases WHERE project_id=?")) {
                ps.setInt(1, project.getId());
                ps.executeUpdate();
            }
            insertTestCases(conn, project);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    public Optional<Project> loadProject(int id) throws SQLException {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM projects WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Project p = mapProject(rs);
                    loadTestCasesInto(conn, p);
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    public List<Project> listProjects() throws SQLException {
        List<Project> list = new ArrayList<>();
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM projects ORDER BY id DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Project p = mapProject(rs);
                loadTestCasesInto(conn, p);
                list.add(p);
            }
        }
        return list;
    }

    /** Deletes project (cascades to test_cases and reports via FK). */
    public void deleteProject(int id) throws SQLException {
        Connection conn = openConnection();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM projects WHERE id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    private void bindProject(PreparedStatement ps, Project p) throws SQLException {
        ps.setString(1, p.getName());
        Integer configId = p.getConfiguration() != null ? p.getConfiguration().getId() : null;
        if (configId != null) ps.setInt(2, configId); else ps.setNull(2, Types.INTEGER);
        ps.setString(3, p.getSubmissionsDirectory());
        ps.setString(4, p.getCreatedAt());
    }

    private Project mapProject(ResultSet rs) throws SQLException {
        Project p = new Project();
        p.setId(rs.getInt("id"));
        p.setName(rs.getString("name"));
        p.setSubmissionsDirectory(rs.getString("submissions_dir"));
        p.setCreatedAt(rs.getString("created_at"));
        int configId = rs.getInt("config_id");
        if (!rs.wasNull()) {
            try { loadConfig(configId).ifPresent(p::setConfiguration); }
            catch (SQLException ignored) {}
        }
        return p;
    }

    private void insertTestCases(Connection conn, Project project) throws SQLException {
        String sql = "INSERT INTO test_cases (project_id,input_args,expected_output,description) VALUES (?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (TestCase tc : project.getTestCases()) {
                ps.setInt(1, project.getId());
                ps.setString(2, tc.getInputArgs());
                ps.setString(3, tc.getExpectedOutput());
                ps.setString(4, tc.getDescription());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) tc.setId(rs.getInt(1));
                }
            }
        }
    }

    private void loadTestCasesInto(Connection conn, Project project) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM test_cases WHERE project_id=? ORDER BY id")) {
            ps.setInt(1, project.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TestCase tc = new TestCase();
                    tc.setId(rs.getInt("id"));
                    tc.setInputArgs(rs.getString("input_args"));
                    tc.setExpectedOutput(rs.getString("expected_output"));
                    tc.setDescription(rs.getString("description"));
                    project.getTestCases().add(tc);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Report CRUD (transaction for batch student_results insert)
    // ------------------------------------------------------------------

    /** Inserts report header + all student results in one transaction. */
    public void saveReport(Report report) throws SQLException {
        Connection conn = openConnection();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO reports (project_id,timestamp) VALUES (?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, report.getProjectId());
                ps.setString(2, report.getTimestamp().toString());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) report.setId(rs.getInt(1));
                }
            }
            String srSql = "INSERT INTO student_results (report_id,student_id,status,actual_output,error_message,diff_text) VALUES (?,?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(srSql)) {
                for (StudentResult sr : report.getResults()) {
                    ps.setInt(1, report.getId());
                    ps.setString(2, sr.getStudentId());
                    ps.setString(3, sr.getStatus() != null ? sr.getStatus().name() : null);
                    ps.setString(4, sr.getActualOutput());
                    ps.setString(5, sr.getErrorMessage());
                    ps.setString(6, sr.getDiffText());
                    ps.executeUpdate();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    /** Loads the most recent report for a project (by highest report id). */
    public Optional<Report> loadLatestReport(int projectId) throws SQLException {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM reports WHERE project_id=? ORDER BY id DESC LIMIT 1")) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Report report = new Report();
                    report.setId(rs.getInt("id"));
                    report.setProjectId(rs.getInt("project_id"));
                    String ts = rs.getString("timestamp");
                    if (ts != null) report.setTimestamp(Instant.parse(ts));
                    loadStudentResultsInto(conn, report);
                    report.computeCounts();
                    return Optional.of(report);
                }
            }
        }
        return Optional.empty();
    }
    /**
     * Loads one report by id including all its student_results, and computes
     * total / success / fail counts from those results.
     *
     * NB: column names match the `reports` schema above — `project_id` and
     * `timestamp`. Counts are computed in-memory via {@link Report#computeCounts()}
     * because they are not stored as columns.
     */
    public Optional<Report> loadReportById(int reportId) throws SQLException {
        String sql = "SELECT id, project_id, timestamp FROM reports WHERE id = ?";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reportId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Report report = new Report();
                report.setId(rs.getInt("id"));
                report.setProjectId(rs.getInt("project_id"));
                String ts = rs.getString("timestamp");
                if (ts != null) report.setTimestamp(Instant.parse(ts));
                loadStudentResultsInto(conn, report);
                report.computeCounts();
                return Optional.of(report);
            }
        }
    }

    /**
     * Lists every report for a project, newest first. Each report is
     * lightweight (no student_results loaded) but its total/success counts are
     * populated via a per-row aggregate query against student_results — this
     * is what the run-history dropdown displays without paying the cost of
     * full result loading per row.
     */
    public List<Report> loadAllReports(int projectId) throws SQLException {
        List<Report> reports = new ArrayList<>();
        String listSql =
                "SELECT id, project_id, timestamp FROM reports " +
                "WHERE project_id = ? ORDER BY id DESC";
        String countSql =
                "SELECT COUNT(*) AS total, " +
                "SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS pass " +
                "FROM student_results WHERE report_id = ?";

        try (Connection conn = openConnection();
             PreparedStatement listPs = conn.prepareStatement(listSql)) {

            listPs.setInt(1, projectId);
            try (ResultSet rs = listPs.executeQuery()) {
                while (rs.next()) {
                    Report r = new Report();
                    r.setId(rs.getInt("id"));
                    r.setProjectId(rs.getInt("project_id"));
                    String ts = rs.getString("timestamp");
                    if (ts != null) r.setTimestamp(Instant.parse(ts));
                    reports.add(r);
                }
            }

            // Counts: separate aggregate query per report. The student_results
            // table doesn't store totals, so we aggregate on demand.
            try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                for (Report r : reports) {
                    countPs.setInt(1, r.getId());
                    try (ResultSet rs = countPs.executeQuery()) {
                        if (rs.next()) {
                            int total = rs.getInt("total");
                            int pass  = rs.getInt("pass");
                            r.setTotalCount(total);
                            r.setSuccessCount(pass);
                        }
                    }
                }
            }
        }
        return reports;
    }

    private void loadStudentResultsInto(Connection conn, Report report) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM student_results WHERE report_id=?")) {
            ps.setInt(1, report.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StudentResult sr = new StudentResult();
                    sr.setStudentId(rs.getString("student_id"));
                    String statusStr = rs.getString("status");
                    if (statusStr != null) sr.setStatus(SubmissionStatus.valueOf(statusStr));
                    sr.setActualOutput(rs.getString("actual_output"));
                    sr.setErrorMessage(rs.getString("error_message"));
                    sr.setDiffText(rs.getString("diff_text"));
                    report.getResults().add(sr);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Import: fresh AUTOINCREMENT id (ignore the id that came from the file)
    // ------------------------------------------------------------------

    /**
     * Saves an imported configuration, assigning a new AUTOINCREMENT id.
     * The id field on the object is overwritten with the new DB-assigned value.
     */
    public void importConfig(Configuration config) throws SQLException {
        config.setId(0); // clear any incoming id
        saveConfig(config);
    }
}
