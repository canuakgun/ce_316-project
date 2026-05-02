package com.ce316.iae.persistence;

import com.ce316.iae.model.Configuration;
import com.ce316.iae.model.Project;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Serialize/deserialize {@link Project} (.iaeproj) and {@link Configuration} (.iaeconfig).
 */
public class PersistenceManager {

    public Optional<Project> loadProject(Path path) {
        return Optional.empty();
    }

    public void saveProject(Project project, Path path) {
    }

    public Optional<Configuration> loadConfiguration(Path path) {
        return Optional.empty();
    }

    public void saveConfiguration(Configuration configuration, Path path) {
    }
}
