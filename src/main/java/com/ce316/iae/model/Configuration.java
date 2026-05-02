package com.ce316.iae.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Defines how a language/assignment is compiled and run.
 */
public class Configuration {

    private String id;
    private String name;
    private String compilerPath;
    private String compileArgs;
    private String runCommand;
    private String fileToCompile;
    private boolean interpreted;

    public Configuration() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCompilerPath() {
        return compilerPath;
    }

    public void setCompilerPath(String compilerPath) {
        this.compilerPath = compilerPath;
    }

    public String getCompileArgs() {
        return compileArgs;
    }

    public void setCompileArgs(String compileArgs) {
        this.compileArgs = compileArgs;
    }

    public String getRunCommand() {
        return runCommand;
    }

    public void setRunCommand(String runCommand) {
        this.runCommand = runCommand;
    }

    public String getFileToCompile() {
        return fileToCompile;
    }

    public void setFileToCompile(String fileToCompile) {
        this.fileToCompile = fileToCompile;
    }

    public boolean isInterpreted() {
        return interpreted;
    }

    public void setInterpreted(boolean interpreted) {
        this.interpreted = interpreted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Configuration that = (Configuration) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
