package com.ce316.iae.service;

/**
 * Compares student stdout to expected output; optional normalization and diff text.
 */
public class OutputComparator {

    public boolean compareExact(String actual, String expected) {
        return actual != null && actual.equals(expected);
    }

    public boolean compareTrimmed(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return actual.trim().equals(expected.trim());
    }

    public String generateDiff(String actual, String expected) {
        return "";
    }
}
