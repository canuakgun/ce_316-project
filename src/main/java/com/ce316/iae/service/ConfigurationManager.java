package com.ce316.iae.service;

import com.ce316.iae.model.Configuration;
import com.ce316.iae.persistence.PersistenceManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * CRUD and import/export for {@link Configuration} (.iaeconfig).
 *
 * <p>All persistence is delegated to the {@link PersistenceManager} singleton
 * (SQLite via JDBC). Import/export uses Gson; the {@code id} field is
 * intentionally stripped on export so that importing on another machine always
 * receives a fresh DB-assigned id.
 */
public class ConfigurationManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final PersistenceManager pm;

    public ConfigurationManager(PersistenceManager pm) {
        this.pm = pm;
    }

    // -----------------------------------------------------------------------
    // DB pass-throughs
    // -----------------------------------------------------------------------

    /**
     * Returns all configurations stored in the DB, ordered by name.
     */
    public List<Configuration> listConfigurations() {
        try {
            return pm.listConfigs();
        } catch (SQLException e) {
            System.err.println("ConfigurationManager.listConfigurations: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Finds a configuration by its DB id.
     */
    public Optional<Configuration> findById(int id) {
        try {
            return pm.loadConfig(id);
        } catch (SQLException e) {
            System.err.println("ConfigurationManager.findById: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Seeds the four built-in language configurations (C, C++, Python 3,
     * Java single-file) the first time the application is launched. No-op if
     * the DB already contains any configurations.
     */
    public void seedDefaultsIfEmpty() {
        try {
            if (!pm.listConfigs().isEmpty()) return;
        } catch (SQLException e) {
            System.err.println("ConfigurationManager.seedDefaultsIfEmpty: " + e.getMessage());
            return;
        }

        // C
        Configuration c = new Configuration();
        c.setName("C Language");
        c.setCompilerPath("gcc");
        c.setCompileArgs("-o {OUTPUT_PATH} {SOURCE_FILE}");
        c.setFileToCompile("main.c");
        c.setRelativeExecutablePath(isWindows() ? "output.exe" : "output");
        c.setInterpreted(false);
        trySeed(c);

        // C++
        Configuration cpp = new Configuration();
        cpp.setName("C++ Language");
        cpp.setCompilerPath("g++");
        cpp.setCompileArgs("-o {OUTPUT_PATH} {SOURCE_FILE}");
        cpp.setFileToCompile("main.cpp");
        cpp.setRelativeExecutablePath(isWindows() ? "output.exe" : "output");
        cpp.setInterpreted(false);
        trySeed(cpp);

        // Python 3
        Configuration py = new Configuration();
        py.setName("Python 3");
        py.setCompilerPath(isWindows() ? "python" : "python3");
        py.setCompileArgs("");
        py.setFileToCompile("main.py");
        py.setRelativeExecutablePath("");
        py.setInterpreted(true);
        trySeed(py);

        // Java single-file (Java 11+ supports running .java directly)
        Configuration java = new Configuration();
        java.setName("Java (Single-File)");
        java.setCompilerPath("java");
        java.setCompileArgs("");
        java.setFileToCompile("Main.java");
        java.setRelativeExecutablePath("");
        java.setInterpreted(true);
        trySeed(java);
        // Scheme (Racket)
        Configuration scheme = new Configuration();
        scheme.setName("Scheme (Racket)");
        scheme.setCompilerPath("racket");
        scheme.setCompileArgs("");
        scheme.setFileToCompile("main.rkt");
        scheme.setRelativeExecutablePath("");
        scheme.setInterpreted(true);
        trySeed(scheme);

        // Prolog (SWI-Prolog)
        Configuration prolog = new Configuration();
        prolog.setName("Prolog (SWI-Prolog)");
        prolog.setCompilerPath("swipl");
        prolog.setCompileArgs("-g main -t halt -s {SOURCE_FILE}");
        prolog.setFileToCompile("main.pl");
        prolog.setRelativeExecutablePath("");
        prolog.setInterpreted(true);
        trySeed(prolog);
    }

    private void trySeed(Configuration c) {
        try { pm.saveConfig(c); }
        catch (SQLException e) { System.err.println("Seed failed for " + c.getName() + ": " + e.getMessage()); }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Inserts a new configuration. The DB assigns the id and sets it on the
     * object before returning.
     *
     * @param configuration configuration to insert (id is overwritten by DB)
     */
    public void create(Configuration configuration) {
        if (configuration == null) throw new IllegalArgumentException("configuration must not be null");
        try {
            pm.saveConfig(configuration);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing configuration in the DB (matched by id).
     *
     * @param configuration configuration carrying the updated values
     */
    public void update(Configuration configuration) {
        if (configuration == null) throw new IllegalArgumentException("configuration must not be null");
        try {
            pm.updateConfig(configuration);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a configuration by id. Projects that reference it get
     * {@code config_id = NULL} via ON DELETE SET NULL in the DB schema.
     *
     * @param id id of the configuration to remove
     */
    public void delete(int id) {
        try {
            pm.deleteConfig(id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete configuration: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Gson import / export  (.iaeconfig)
    // -----------------------------------------------------------------------

    /**
     * Exports a configuration to a {@code .iaeconfig} file as pretty-printed JSON.
     *
     * <p>The {@code id} field is <em>not</em> written to the file so that the
     * configuration can be imported on any machine and get a fresh DB-assigned id.
     *
     * @param configuration the configuration to export
     * @param file          target {@code .iaeconfig} path
     * @throws IOException if the file cannot be written
     */
    public void exportConfig(Configuration configuration, Path file) throws IOException {
        if (configuration == null || file == null) return;

        // Build a JsonObject manually so we control which fields appear
        JsonObject json = new JsonObject();
        // id intentionally omitted
        json.addProperty("name",                   configuration.getName());
        json.addProperty("compilerPath",           configuration.getCompilerPath());
        json.addProperty("compileArgs",            configuration.getCompileArgs());
        json.addProperty("fileToCompile",          configuration.getFileToCompile());
        json.addProperty("relativeExecutablePath", configuration.getRelativeExecutablePath());
        json.addProperty("interpreted",            configuration.isInterpreted());

        Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
    }

    /**
     * Imports a configuration from a {@code .iaeconfig} JSON file, inserts it
     * into the DB (which assigns a fresh id), and returns the persisted object.
     *
     * <p>Any {@code id} field present in the file is silently ignored.
     *
     * @param file path to the {@code .iaeconfig} file
     * @return the imported configuration with its new DB-assigned id
     * @throws IOException if the file cannot be read or is not valid JSON
     */
    public Configuration importConfig(Path file) throws IOException {
        if (file == null) throw new IllegalArgumentException("file must not be null");

        String raw = Files.readString(file, StandardCharsets.UTF_8);

        // Parse via Gson; id field is ignored — PersistenceManager.importConfig()
        // resets it to 0 before calling saveConfig() to get a new AUTOINCREMENT id.
        Configuration config = GSON.fromJson(raw, Configuration.class);
        if (config == null) throw new IOException("Could not parse configuration from: " + file);

        try {
            pm.importConfig(config); // clears id, then INSERT to get new DB id
        } catch (SQLException e) {
            throw new IOException("Failed to persist imported configuration: " + e.getMessage(), e);
        }
        return config;
    }
}