package com.ce316.iae.service;

import com.ce316.iae.model.Configuration;
import com.ce316.iae.persistence.PersistenceManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD and import/export for {@link Configuration} (.iaeconfig).
 */
public class ConfigurationManager {

    private final PersistenceManager persistenceManager;
    private final List<Configuration> registry = new ArrayList<>();

    public ConfigurationManager(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public List<Configuration> listConfigurations() {
        return List.copyOf(registry);
    }

    public void register(Configuration configuration) {
        registry.removeIf(c -> c.getId().equals(configuration.getId()));
        registry.add(configuration);
    }

    public void delete(Configuration configuration) {
        registry.remove(configuration);
    }

    public void importConfig(Path file) {
        persistenceManager.loadConfiguration(file).ifPresent(this::register);
    }

    public void exportConfig(Configuration configuration, Path file) {
        persistenceManager.saveConfiguration(configuration, file);
    }
}
