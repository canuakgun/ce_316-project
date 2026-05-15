package com.ce316.iae.ui;

import com.ce316.iae.model.StudentResult;

/**
 * Observer interface for the Subject/Observer pattern described in SDD §2.3.
 *
 * ExecutionEngine = Subject
 * UI Controllers = Observers
 */
public interface ResultsObserver {

    /**
     * Called whenever a student result is ready.
     */
    void onStudentProcessed(StudentResult result);
}
