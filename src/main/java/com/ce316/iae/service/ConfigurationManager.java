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