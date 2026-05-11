package com.ce316.iae.model;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * One student's ZIP file and its extraction directory.
 */
public class StudentSubmission {

    private String studentId;
    private File zipFile;
    /** Null signals that this submission should be SKIPPED (bad zip name or extraction error). */
    private File extractedDir;

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public File getZipFile() { return zipFile; }
    public void setZipFile(File zipFile) { this.zipFile = zipFile; }

    public File getExtractedDir() { return extractedDir; }
    public void setExtractedDir(File extractedDir) { this.extractedDir = extractedDir; }

    /**
     * Recursively collects all regular files under {@link #extractedDir}.
     */
    public List<File> getSourceFiles() {
        List<File> files = new ArrayList<>();
        if (extractedDir == null || !extractedDir.exists()) return files;
        Queue<File> queue = new LinkedList<>();
        queue.add(extractedDir);
        while (!queue.isEmpty()) {
            File dir = queue.poll();
            File[] children = dir.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) queue.add(child);
                else files.add(child);
            }
        }
        return files;
    }
}
