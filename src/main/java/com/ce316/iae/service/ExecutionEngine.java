package com.ce316.iae.service;

import com.ce316.iae.model.Configuration;
import com.ce316.iae.model.Project;
import com.ce316.iae.model.StudentResult;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Invokes compiler/interpreter and student process via {@link java.lang.ProcessBuilder}.
 */
public class ExecutionEngine {

    public record CompileResult(boolean success, String stdout, String stderr) { }

    public CompileResult compile(Configuration configuration, File workingDirectory) {
        return new CompileResult(false, "", "");
    }

    public String run(Configuration configuration, File workingDirectory, String inputArgs) {
        return "";
    }

    public List<StudentResult> runAll(Project project) {
        return Collections.emptyList();
    }
}
