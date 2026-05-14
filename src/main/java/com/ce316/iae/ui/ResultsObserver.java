package com.ce316.iae.ui;

import com.ce316.iae.model.StudentResult;

/**
 * Observer interface for the Subject/Observer pattern described in SDD §2.3.
 *
 * <p>{@link com.ce316.iae.service.ExecutionEngine} is the Subject; UI controllers
 * ({@link RunProgressController}, {@link ResultsViewController}) implement this
 * interface and are notified after each student is processed.
 *
 * <p>Implementations <em>must</em> dispatch UI updates via
 * {@code Platform.runLater()} because notifications arrive from a background thread.
 */
public interface ResultsObserver {

    /**
     * Called by {@link com.ce316.iae.service.ExecutionEngine} once per student,
     * immediately after that student's compile-run-compare pipeline completes.
     *
     * @param result the fully populated result for one student (never {@code null})
     */
    void onStudentProcessed(StudentResult result);
}
