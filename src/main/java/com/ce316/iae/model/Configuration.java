package com.ce316.iae.model;

/**
 * Defines how a language/assignment is compiled and run.
 * Gson requires a no-arg constructor (provided below).
 */
public class Configuration {

    private int id;
    private String name;
    private String compilerPath;
    /** Supports {SOURCE_FILE} and {OUTPUT_PATH} placeholders. */
    private String compileArgs;
    private String fileToCompile;
    /** Relative path from the student's working dir to the compiled binary (e.g. "main" or "a.out"). */
    private String relativeExecutablePath;
    private boolean interpreted;

    public Configuration() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCompilerPath() { return compilerPath; }
    public void setCompilerPath(String compilerPath) { this.compilerPath = compilerPath; }

    public String getCompileArgs() { return compileArgs; }
    public void setCompileArgs(String compileArgs) { this.compileArgs = compileArgs; }

    public String getFileToCompile() { return fileToCompile; }
    public void setFileToCompile(String fileToCompile) { this.fileToCompile = fileToCompile; }

    public String getRelativeExecutablePath() { return relativeExecutablePath; }
    public void setRelativeExecutablePath(String relativeExecutablePath) {
        this.relativeExecutablePath = relativeExecutablePath;
    }

    public boolean isInterpreted() { return interpreted; }
    public void setInterpreted(boolean interpreted) { this.interpreted = interpreted; }

    @Override
    public String toString() { return name != null ? name : "(unnamed)"; }
}
