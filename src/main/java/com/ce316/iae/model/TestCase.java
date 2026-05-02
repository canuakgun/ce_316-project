package com.ce316.iae.model;

/**
 * One test: command-line args to the student program and expected stdout.
 */
public class TestCase {

    private String inputArgs;
    private String expectedOutput;

    public String getInputArgs() {
        return inputArgs;
    }

    public void setInputArgs(String inputArgs) {
        this.inputArgs = inputArgs;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }
}
