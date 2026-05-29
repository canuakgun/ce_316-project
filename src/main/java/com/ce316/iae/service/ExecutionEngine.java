package com.ce316.iae.service;

import com.ce316.iae.model.*;
import com.ce316.iae.ui.ResultsObserver;
import javafx.application.Platform;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Compiles and runs student submissions via {@link ProcessBuilder}.
 *
 * <h3>Observer pattern (SDD §2.3)</h3>
 * {@code ExecutionEngine} is the <em>Subject</em>. UI controllers register via
 * {@link #addObserver(ResultsObserver)} and receive an {@link ResultsObserver#onStudentProcessed}
 * notification — dispatched on the JavaFX Application Thread via
 * {@link Platform#runLater(Runnable)} — after every student is processed.
 *
 * <h3>10-second run timeout (SDD §3.3 / Q1 resolution)</h3>
 * Each test-case execution uses {@code rp.waitFor(10, TimeUnit.SECONDS)}.
 * If the process does not finish in time it is forcibly destroyed and the
 * result is {@link SubmissionStatus#RUNTIME_ERROR}.
 */
public class ExecutionEngine {

    // ------------------------------------------------------------------
    // Observer list (Subject side of Observer pattern)
    // ------------------------------------------------------------------

    private final List<ResultsObserver> observers = new ArrayList<>();

    /** Registers an observer. Ignored if already registered. */
    public void addObserver(ResultsObserver observer) {
        if (!observers.contains(observer)) observers.add(observer);
    }

    /** Removes a previously registered observer. */
    public void removeObserver(ResultsObserver observer) {
        observers.remove(observer);
    }

    /** Removes all registered observers (called after a run completes). */
    public void clearObservers() {
        observers.clear();
    }

    private void notifyObservers(StudentResult result) {
        for (ResultsObserver o : new ArrayList<>(observers)) {
            Platform.runLater(() -> o.onStudentProcessed(result));
        }
    }

    // ------------------------------------------------------------------
    // Inner record for compile outcome
    // ------------------------------------------------------------------

    /** Holds the raw compile result (exit code + merged stdout/stderr). */
    public record CompileResult(boolean success, String output) {}

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Compiles a submission using the language {@link Configuration}.
     * {@code {OUTPUT_PATH}} and {@code {SOURCE_FILE}} placeholders are resolved
     * relative to {@code workingDirectory} (SDD §3.3).
     *
     * <p>No timeout is applied on compilation per SDD.
     *
     * @param config           the language/tool configuration
     * @param workingDirectory the student's extracted directory
     * @return a {@link CompileResult} with success flag and merged stdout+stderr
     */
    public CompileResult compile(Configuration config, File workingDirectory) {
        try {
            // Resolve output-binary path relative to the working directory
            String outputPath = new File(workingDirectory,
                    config.getRelativeExecutablePath() != null
                            ? config.getRelativeExecutablePath() : "output")
                    .getAbsolutePath();

            // Find the source file (recursive search inside extractedDir)
            File sourceFile = findFile(workingDirectory, config.getFileToCompile());
            String sourcePath = sourceFile != null
                    ? sourceFile.getAbsolutePath()
                    : new File(workingDirectory, config.getFileToCompile()).getAbsolutePath();

            // Substitute placeholders in compileArgs
            String expandedArgs = (config.getCompileArgs() != null ? config.getCompileArgs() : "")
                    .replace("{OUTPUT_PATH}", outputPath)
                    .replace("{SOURCE_FILE}", sourcePath);

            // Build command: compiler + each arg token
            List<String> command = new ArrayList<>();
            command.add(config.getCompilerPath());
            if (!expandedArgs.isBlank()) {
                command.addAll(Arrays.asList(expandedArgs.split("\\s+")));
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDirectory);
            pb.redirectErrorStream(true); // merge stderr into stdout
            Process p = pb.start();

            String output = readAll(p.getInputStream());
            int exitCode = p.waitFor(); // no timeout on compilation

            return new CompileResult(exitCode == 0, output);

        } catch (Exception e) {
            return new CompileResult(false, e.getMessage());
        }
    }

    /**
     * Runs the compiled/interpreted program for one test case.
     *
     * <p>A 10-second timeout is enforced (SDD §3.3 / Q1). If exceeded, the process
     * is forcibly destroyed and {@code null} is returned (caller interprets as RUNTIME_ERROR).
     *
     * @param config           the language/tool configuration
     * @param workingDirectory the student's extracted directory
     * @param inputArgs        space-separated command-line arguments to pass
     * @return the program's stdout, or {@code null} on timeout/crash
     */
    public String run(Configuration config, File workingDirectory, String inputArgs) {
        try {
            List<String> command = new ArrayList<>();

            if (config.isInterpreted()) {
                String interpreter = config.getCompilerPath();
                if (interpreter == null || interpreter.isBlank()) {
                    interpreter = "python"; // sensible fallback
                }
                File sourceFile = findFile(workingDirectory, config.getFileToCompile());
                String sourcePath = sourceFile != null
                        ? sourceFile.getAbsolutePath()
                        : new File(workingDirectory, config.getFileToCompile()).getAbsolutePath();

                String args = config.getCompileArgs() != null ? config.getCompileArgs().trim() : "";

                if (!args.isEmpty() && args.contains("{SOURCE_FILE}")) {
                    // compileArgs contains {SOURCE_FILE} — use it to build the full command
                    // e.g. Prolog: "-g main -t halt -s {SOURCE_FILE}"
                    command.add(interpreter);
                    String expanded = args.replace("{SOURCE_FILE}", sourcePath);
                    command.addAll(Arrays.asList(expanded.split("\\s+")));
                } else if (!args.isEmpty()) {
                    // compileArgs has flags but no placeholder — put them before source file
                    command.add(interpreter);
                    command.addAll(Arrays.asList(args.split("\\s+")));
                    command.add(sourcePath);
                } else {
                    // Default: interpreter + sourceFile (Python, Scheme, Java single-file)
                    command.add(interpreter);
                    command.add(sourcePath);
                }
            } else {
                // For compiled languages: run the binary
                File binary = findFile(workingDirectory, config.getRelativeExecutablePath());
                if (binary == null) {
                    binary = new File(workingDirectory, config.getRelativeExecutablePath());
                }
                command.add(binary.getAbsolutePath());
            }

            // Append per-test-case command-line arguments
            if (inputArgs != null && !inputArgs.isBlank()) {
                command.addAll(Arrays.asList(inputArgs.trim().split("\\s+")));
            }

            ProcessBuilder rb = new ProcessBuilder(command);
            rb.directory(workingDirectory);
            rb.redirectErrorStream(true);
            Process rp = rb.start();

            boolean finished = rp.waitFor(10, TimeUnit.SECONDS); // Q1: 10-second timeout
            if (!finished) {
                rp.destroyForcibly();
                return null; // signals RUNTIME_ERROR to caller
            }

            return readAll(rp.getInputStream());

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Processes every student in the project's submissions directory.
     *
     * <p>Algorithm (SDD §3.2 + §3.4):
     * <ol>
     *   <li>List and extract all ZIPs via {@link ZipProcessor}.</li>
     *   <li>For each submission: compile (if not interpreted) → run each test case → compare output.</li>
     *   <li>Aggregate: a student is {@link SubmissionStatus#SUCCESS} only if every test case passes.</li>
     *   <li>An exception for one student never breaks the loop — all students are always processed.</li>
     *   <li>After each student, registered {@link ResultsObserver}s are notified on the FX thread.</li>
     * </ol>
     *
     * @param project the project to run
     * @return list of {@link StudentResult}, one per student ZIP found
     */
    public List<StudentResult> runAll(Project project) {
        List<StudentResult> results = new ArrayList<>();
        ZipProcessor zipProcessor = new ZipProcessor();
        OutputComparator comparator = new OutputComparator();
        Configuration config = project.getConfiguration();

        File submissionsDir = new File(project.getSubmissionsDirectory());
        List<StudentSubmission> submissions = zipProcessor.extractAll(submissionsDir);

        for (StudentSubmission submission : submissions) {
            StudentResult result = new StudentResult();
            result.setStudentId(submission.getStudentId());

            try {
                // ── SKIPPED: bad filename or failed extraction ───────────
                if (submission.getExtractedDir() == null) {
                    result.setStatus(SubmissionStatus.SKIPPED);
                    result.setErrorMessage(
                            "Could not derive student ID from filename — manual review required");
                    results.add(result);
                    notifyObservers(result);
                    continue;
                }

                // Require exactly a 9-digit student ID per the SDD; otherwise SKIPPED.
                if (submission.getStudentId() == null || !submission.getStudentId().matches("\\d{9}")) {
                    result.setStatus(SubmissionStatus.SKIPPED);
                    result.setErrorMessage(
                            "Filename does not encode a 9-digit student ID — manual review required");
                    results.add(result);
                    notifyObservers(result);
                    continue;
                }

                File extractedDir = submission.getExtractedDir();

                // ── COMPILE (compiled languages only) ────────────────────
                if (!config.isInterpreted()) {
                    CompileResult cr = compile(config, extractedDir);
                    if (!cr.success()) {
                        result.setStatus(SubmissionStatus.COMPILE_ERROR);
                        result.setErrorMessage(cr.output());
                        results.add(result);
                        notifyObservers(result);
                        continue;
                    }
                }

                // ── RUN each test case ───────────────────────────────────
                String firstFailActual = null;
                String firstFailDiff   = null;
                SubmissionStatus worstStatus = SubmissionStatus.SUCCESS;

                for (TestCase tc : project.getTestCases()) {
                    String actual = run(config, extractedDir, tc.getInputArgs());

                    if (actual == null) {
                        // null → timeout or crash
                        worstStatus = SubmissionStatus.RUNTIME_ERROR;
                        if (firstFailActual == null) {
                            firstFailActual = "";
                            firstFailDiff   = comparator.generateDiff("", tc.getExpectedOutput());
                        }
                        break; // first failure stops further test cases for this student
                    }

                    boolean passed = comparator.compareNormalized(actual, tc.getExpectedOutput());
                    if (!passed && worstStatus == SubmissionStatus.SUCCESS) {
                        worstStatus     = SubmissionStatus.WRONG_OUTPUT;
                        firstFailActual = actual;
                        firstFailDiff   = comparator.generateDiff(actual, tc.getExpectedOutput());
                    }
                }

                result.setStatus(worstStatus);
                if (firstFailActual != null) {
                    result.setActualOutput(firstFailActual);
                    result.setDiffText(firstFailDiff);
                }

            } catch (Exception e) {
                // Unexpected error for this student — never skip remaining students
                result.setStatus(SubmissionStatus.RUNTIME_ERROR);
                result.setErrorMessage(e.getMessage());
            }

            results.add(result);
            notifyObservers(result);
        }

        return results;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Reads all bytes from an {@link InputStream} and returns them as a UTF-8 string.
     * The stream is fully consumed but <em>not</em> closed (caller owns it).
     */
    private static String readAll(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Recursively searches {@code root} for a file whose name equals {@code filename}
     * (case-insensitive on Windows). Returns the first match or {@code null}.
     *
     * <p>Used to locate {@code main.c}, {@code Main.java}, etc. inside nested
     * ZIP structures (SDD §3.3 path resolution).
     */
    private static File findFile(File root, String filename) {
        if (root == null || filename == null || !root.isDirectory()) return null;
        File[] children = root.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isFile() && child.getName().equalsIgnoreCase(filename)) {
                return child;
            }
            if (child.isDirectory()) {
                File found = findFile(child, filename);
                if (found != null) return found;
            }
        }
        return null;
    }
}
