package com.ce316.iae.model;

/**
 * Outcome of processing a single student submission.
 */
public enum SubmissionStatus {
    SUCCESS,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    WRONG_OUTPUT,
    TIMEOUT,
    SKIPPED,
    INVALID_ZIP
}
