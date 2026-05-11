package com.ce316.iae.model;

/**
 * One test: command-line args to pass and the expected stdout.
 */
public class TestCase {

    private int id;
    private String inputArgs;
    private String expectedOutput;
    private String description;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getInputArgs() { return inputArgs; }
    public void setInputArgs(String inputArgs) { this.inputArgs = inputArgs; }

    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /**
     * Returns true if {@code actual} matches expected output.
     * Tries exact match first, then trimmed comparison as fallback.
     */
    public boolean compare(String actual) {
        if (actual == null || expectedOutput == null) return false;
        if (actual.equals(expectedOutput)) return true;
        return actual.trim().equals(expectedOutput.trim());
    }
}
