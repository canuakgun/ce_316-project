package com.ce316.iae.service;

import com.ce316.iae.model.StudentSubmission;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lists and extracts student ZIP files using zip4j (no reliance on system
 * unzip).
 *
 * <p>
 * Student ID derivation follows the SDD §2.2 algorithm:
 * <ol>
 * <li>Strip the {@code .zip} extension from the filename.</li>
 * <li>Apply the regex {@code \d{9}}.</li>
 * <li>Exactly one match → use it as the student ID.</li>
 * <li>No match (or more than one match) → student ID =
 * filename-without-extension;
 * the submission is flagged as SKIPPED.</li>
 * </ol>
 */
public class ZipProcessor {

    /** Exactly 9 consecutive digits — SDD §2.2 student-ID pattern. */
    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("\\d{9}");

    /** Root temp directory where all extracted submissions land. */
    private static final File EXTRACT_ROOT = new File(System.getProperty("java.io.tmpdir"), "IAE_extractions");

    // ------------------------------------------------------------------
    // Public API (SDD §2.2)
    // ------------------------------------------------------------------

    /**
     * Returns all {@code .zip} files inside {@code directory} (non-recursive).
     */
    public List<File> listZips(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return Collections.emptyList();
        }
        File[] zips = directory.listFiles(
                f -> f.isFile() && f.getName().toLowerCase().endsWith(".zip"));
        return zips != null ? Arrays.asList(zips) : Collections.emptyList();
    }

    /**
     * Extracts a single ZIP file into a fresh sub-directory under
     * {@link #EXTRACT_ROOT}.
     *
     * <p>
     * The returned {@link StudentSubmission} always has a {@code studentId}
     * (derived
     * from the filename). If extraction fails, {@code extractedDir} is
     * {@code null},
     * signalling the caller to mark the submission as SKIPPED.
     *
     * @param zip the ZIP file to extract
     * @return a populated {@link StudentSubmission}; never {@code null}
     */
    public StudentSubmission extractOne(File zip) {
        StudentSubmission submission = new StudentSubmission();
        submission.setZipFile(zip);

        String filenameWithoutExt = stripExtension(zip.getName());
        submission.setStudentId(deriveStudentId(filenameWithoutExt));

        // Prepare a unique extraction target directory
        File extractTarget = new File(EXTRACT_ROOT, filenameWithoutExt + "_" + System.nanoTime());
        extractTarget.mkdirs();

        try (ZipFile zipFile = new ZipFile(zip)) {
            zipFile.extractAll(extractTarget.getAbsolutePath());
            submission.setExtractedDir(extractTarget);
        } catch (ZipException e) {
            // Corrupt or unreadable ZIP → extractedDir stays null → caller skips it
            submission.setExtractedDir(null);
        } catch (IOException e) {
            // ZipFile.close() raised an IOException — treat the same as a bad ZIP
            submission.setExtractedDir(null);
        }

        return submission;
    }

    /**
     * Convenience method: extracts every ZIP found in {@code submissionsDir}.
     *
     * @param submissionsDir directory containing student ZIPs
     * @return list of submissions (some may have {@code extractedDir == null} if
     *         corrupt)
     */
    public List<StudentSubmission> extractAll(File submissionsDir) {
        List<File> zips = listZips(submissionsDir);
        List<StudentSubmission> submissions = new ArrayList<>(zips.size());
        for (File zip : zips) {
            submissions.add(extractOne(zip));
        }
        return submissions;
    }

    // ------------------------------------------------------------------
    // Student-ID derivation (SDD §2.2)
    // ------------------------------------------------------------------

    /**
     * Derives a student ID from a filename (without extension).
     *
     * <ul>
     * <li>If the name contains exactly one 9-digit sequence, that sequence is
     * returned.</li>
     * <li>Otherwise the full name-without-extension is returned (submission will be
     * SKIPPED
     * by the caller because it does not match a known student ID pattern).</li>
     * </ul>
     *
     * @param filenameWithoutExt the ZIP filename with its {@code .zip} suffix
     *                           already removed
     * @return the derived student ID string
     */
    public String deriveStudentId(String filenameWithoutExt) {
        if (filenameWithoutExt == null || filenameWithoutExt.isBlank()) {
            return filenameWithoutExt;
        }
        Matcher m = STUDENT_ID_PATTERN.matcher(filenameWithoutExt);
        List<String> matches = new ArrayList<>();
        while (m.find()) {
            matches.add(m.group());
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        // Zero or multiple 9-digit sequences → fall back to raw name; caller sets
        // SKIPPED
        return filenameWithoutExt;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Removes the last {@code .xxx} extension (case-insensitive) from a filename.
     */
    private static String stripExtension(String filename) {
        if (filename == null)
            return "";
        int dot = filename.lastIndexOf('.');
        return (dot > 0) ? filename.substring(0, dot) : filename;
    }
}
