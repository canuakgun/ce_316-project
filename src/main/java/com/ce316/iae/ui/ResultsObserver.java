package com.ce316.iae.ui;

import java.util.List;

import com.ce316.iae.model.StudentResult;

public interface ResultsObserver {
    void onResultsUpdated(List<StudentResult> results);
}
