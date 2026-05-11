package com.ce316.iae.model;

/**
 * Compile/run/compare result for one student (one row per student, not per test case).
 */
public class StudentResult {

    private String studentId;
    private SubmissionStatus status;
    private String actualOutput;
    private String errorMessage;
    private String diffText;

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public SubmissionStatus getStatus() { return status; }
    public void setStatus(SubmissionStatus status) { this.status = status; }

    public String getActualOutput() { return actualOutput; }
    public void setActualOutput(String actualOutput) { this.actualOutput = actualOutput; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getDiffText() { return diffText; }
    public void setDiffText(String diffText) { this.diffText = diffText; }
}
