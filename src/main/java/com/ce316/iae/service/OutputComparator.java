package com.ce316.iae.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares student stdout to expected output and produces a unified-style diff.
 *
 * <h3>Comparison pipeline (SDD §3.5)</h3>
 * <ol>
 *   <li>{@link #compareExact}   — byte-for-byte equality.</li>
 *   <li>{@link #compareTrimmed} — strips leading/trailing whitespace from both sides.</li>
 *   <li>{@link #compareNormalized} — additionally collapses runs of whitespace and
 *       normalises line endings ({@code \r\n} → {@code \n}).</li>
 * </ol>
 * The SDD states: <em>"Exact match is attempted first. If exact match fails, both strings
 * are trimmed and normalized."</em> Callers should use {@link #compareNormalized} as the
 * authoritative comparison; it subsumes the two simpler strategies.
 */
public class OutputComparator {

    // ------------------------------------------------------------------
    // Comparison strategies
    // ------------------------------------------------------------------

    /**
     * Exact, byte-for-byte comparison (SDD §3.5 strategy 1).
     *
     * @param actual   the program's stdout
     * @param expected the expected output from the test case
     * @return {@code true} if both strings are identical
     */
    public boolean compareExact(String actual, String expected) {
        if (actual == null || expected == null) return false;
        return actual.equals(expected);
    }

    /**
     * Trimmed comparison — strips leading/trailing whitespace before comparing
     * (SDD §3.5 strategy 2).
     *
     * @param actual   the program's stdout
     * @param expected the expected output from the test case
     * @return {@code true} if the trimmed strings are equal
     */
    public boolean compareTrimmed(String actual, String expected) {
        if (actual == null || expected == null) return false;
        return actual.trim().equals(expected.trim());
    }

    /**
     * Normalized comparison — normalises line endings and collapses runs of
     * whitespace before comparing (SDD §3.5 strategy 3).
     *
     * <p>Normalization steps applied to both sides:
     * <ol>
     *   <li>Convert {@code \r\n} → {@code \n} (Windows line endings).</li>
     *   <li>Trim the whole string.</li>
     *   <li>Collapse every sequence of spaces/tabs on a single line into one space.</li>
     * </ol>
     *
     * @param actual   the program's stdout
     * @param expected the expected output from the test case
     * @return {@code true} if the normalized strings are equal
     */
    public boolean compareNormalized(String actual, String expected) {
        if (actual == null || expected == null) return false;
        return normalize(actual).equals(normalize(expected));
    }

    // ------------------------------------------------------------------
    // Diff generation
    // ------------------------------------------------------------------

    /**
     * Produces a simple line-by-line unified diff between {@code actual} and
     * {@code expected} output (SDD §3.5 / UI requirement for the Detail pane).
     *
     * <p>Lines that exist only in {@code expected} are prefixed with {@code "- "}
     * (what was expected but not produced). Lines that exist only in {@code actual}
     * are prefixed with {@code "+ "} (what was produced but not expected). Lines
     * common to both are prefixed with {@code "  "}.
     *
     * <p>The algorithm is a straightforward LCS-based diff (patience variant via
     * dynamic programming), suitable for the short output strings typical of
     * student assignments.
     *
     * @param actual   the program's stdout (may be {@code null} or empty)
     * @param expected the expected output (may be {@code null} or empty)
     * @return a human-readable diff string; empty string if both inputs are equal
     */
    public String generateDiff(String actual, String expected) {
        String[] actualLines   = splitLines(actual   != null ? actual   : "");
        String[] expectedLines = splitLines(expected != null ? expected : "");

        int m = expectedLines.length;
        int n = actualLines.length;

        // Build LCS length table
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (expectedLines[i - 1].equals(actualLines[j - 1])) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // Back-track to build diff
        List<String> diffLines = new ArrayList<>();
        backtrack(dp, expectedLines, actualLines, m, n, diffLines);

        if (diffLines.isEmpty()) return ""; // identical

        StringBuilder sb = new StringBuilder();
        for (String line : diffLines) {
            sb.append(line).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Applies the SDD normalization: {@code \r\n} → {@code \n}, trim, collapse
     * intra-line whitespace.
     */
    private static String normalize(String s) {
        // Normalise Windows line endings
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        // Trim the whole string
        s = s.trim();
        // Collapse runs of spaces/tabs within each line
        String[] lines = s.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i].trim().replaceAll("[ \t]+", " "));
            if (i < lines.length - 1) sb.append('\n');
        }
        return sb.toString();
    }

    /** Splits {@code s} into lines, preserving empty trailing lines. */
    private static String[] splitLines(String s) {
        if (s.isEmpty()) return new String[0];
        // Normalise line endings before splitting
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        return s.split("\n", -1);
    }

    /** Recursive LCS back-tracker that appends {@code -}, {@code +}, or {@code   } prefixed lines. */
    private static void backtrack(int[][] dp, String[] exp, String[] act,
                                  int i, int j, List<String> out) {
        if (i == 0 && j == 0) return;
        if (i > 0 && j > 0 && exp[i - 1].equals(act[j - 1])) {
            backtrack(dp, exp, act, i - 1, j - 1, out);
            out.add("  " + exp[i - 1]);
        } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
            backtrack(dp, exp, act, i, j - 1, out);
            out.add("+ " + act[j - 1]);  // in actual but not expected
        } else {
            backtrack(dp, exp, act, i - 1, j, out);
            out.add("- " + exp[i - 1]);  // in expected but not actual
        }
    }
}
