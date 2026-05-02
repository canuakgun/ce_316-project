package com.ce316.iae.model;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * One student's ZIP and extraction directory.
 */
public class StudentSubmission {

    private String studentId;
    private File zipFile;
    private File extractedDir;

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public File getZipFile() {
        return zipFile;
    }

    public void setZipFile(File zipFile) {
        this.zipFile = zipFile;
    }

    public File getExtractedDir() {
        return extractedDir;
    }

    public void setExtractedDir(File extractedDir) {
        this.extractedDir = extractedDir;
    }

    public void extract() {
        // Implemented via ZipProcessor / Java ZIP API
    }

    public List<File> getSourceFiles() {
        return Collections.emptyList();
    }
}
