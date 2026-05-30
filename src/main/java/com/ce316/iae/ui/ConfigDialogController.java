package com.ce316.iae.ui;

import com.ce316.iae.model.Configuration;
import com.ce316.iae.persistence.PersistenceManager;
import com.ce316.iae.service.ConfigurationManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Dialog: full CRUD for Configuration objects + import / export.
 *
 * Layout (see ConfigDialog.fxml):
 *  Left  — ListView of saved configurations
 *  Right — Form fields: name, compiler path, compile args,
 *          file to compile, executable path, interpreted checkbox
 *  Bottom — New | Save | Delete | Import | Export | Close buttons
 *
 * The dialog is self-contained: it owns its own ConfigurationManager
 * instance so it can operate independently of any open project.
 */
public class ConfigDialogController {

    // ---------------------------------------------------------------
    // FXML nodes
    // ---------------------------------------------------------------

    @FXML private ListView<String> configListView;

    @FXML private TextField nameField;
    @FXML private TextField compilerPathField;
    @FXML private TextField compileArgsField;
    @FXML private TextField fileToCompileField;
    @FXML private TextField executablePathField;
    @FXML private CheckBox  interpretedCheckBox;

    @FXML private Label     statusLabel;

    // ---------------------------------------------------------------
    // State
    // ---------------------------------------------------------------

    private ConfigurationManager configurationManager;

    /** Index in configListView that is currently loaded into the form; -1 if none. */
    private int selectedIndex = -1;

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    @FXML
    public void initialize() {
        // Build the manager with the singleton PersistenceManager
        this.configurationManager = new ConfigurationManager(PersistenceManager.getInstance());

        statusLabel.setText("");

        // Clicking a list item populates the form
        configListView.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldVal, newVal) -> {
                    selectedIndex = newVal.intValue();
                    loadSelectedIntoForm();
                });

        refreshList();
    }

    // ---------------------------------------------------------------
    // Button handlers
    // ---------------------------------------------------------------

    /** New — clears the form so the user can type a fresh configuration. */
    @FXML
    private void handleNew() {
        clearForm();
        selectedIndex = -1;
        configListView.getSelectionModel().clearSelection();
        setStatus("Fill in the fields and click Save.");
    }

    /**
     * Save — creates a new configuration or updates the selected one,
     * then persists via ConfigurationManager.
     */
    @FXML
    private void handleSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) { setStatus("Name is required."); return; }

        String compilerPath = compilerPathField.getText().trim();
        String fileToCompile = fileToCompileField.getText().trim();
        if (fileToCompile.isEmpty()) { setStatus("File to compile is required."); return; }

        Configuration config;
        List<Configuration> all = configurationManager.listConfigurations();

        if (selectedIndex >= 0 && selectedIndex < all.size()) {
            // Update existing
            config = all.get(selectedIndex);
        } else {
            // New
            config = new Configuration();
        }

        config.setName(name);
        config.setCompilerPath(compilerPath);
        config.setCompileArgs(compileArgsField.getText().trim());
        config.setFileToCompile(fileToCompile);
        config.setRelativeExecutablePath(executablePathField.getText().trim());
        config.setInterpreted(interpretedCheckBox.isSelected());

        try {
            if (config.getId() == 0) {
                configurationManager.create(config);
            } else {
                configurationManager.update(config);
            }
            refreshList();
            setStatus("Configuration saved: " + name);
        } catch (Exception e) {
            setStatus("Save failed: " + e.getMessage());
        }
    }

    /** Delete — removes the selected configuration after confirmation. */
    @FXML
    private void handleDelete() {
        List<Configuration> all = configurationManager.listConfigurations();
        if (selectedIndex < 0 || selectedIndex >= all.size()) {
            setStatus("Select a configuration to delete."); return;
        }
        Configuration target = all.get(selectedIndex);

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Configuration");
        confirm.setHeaderText("Delete this configuration?");
        confirm.setContentText("Delete \"" + target.getName() + "\"? Projects using it will lose the link.");
        ThemeManager.apply(confirm);
        if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) return;

        try {
            configurationManager.delete(target.getId());
            clearForm();
            selectedIndex = -1;
            refreshList();
            setStatus("Deleted: " + target.getName());
        } catch (Exception e) {
            setStatus("Delete failed: " + e.getMessage());
        }
    }

    /** Import — reads a .iaeconfig JSON file via ConfigurationManager. */
    @FXML
    private void handleImport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Configuration");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("IAE Config (*.iaeconfig)", "*.iaeconfig"));
        File file = chooser.showOpenDialog(getStage());
        if (file == null) return;

        try {
            configurationManager.importConfig(file.toPath());
            refreshList();
            setStatus("Imported: " + file.getName());
        } catch (Exception e) {
            setStatus("Import failed: " + e.getMessage());
        }
    }

    /** Export — serialises the selected configuration to a .iaeconfig file. */
    @FXML
    private void handleExport() {
        List<Configuration> all = configurationManager.listConfigurations();
        if (selectedIndex < 0 || selectedIndex >= all.size()) {
            setStatus("Select a configuration to export."); return;
        }
        Configuration target = all.get(selectedIndex);

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Configuration");
        chooser.setInitialFileName(target.getName() + ".iaeconfig");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("IAE Config (*.iaeconfig)", "*.iaeconfig"));
        File dest = chooser.showSaveDialog(getStage());
        if (dest == null) return;

        try {
            configurationManager.exportConfig(target, Path.of(dest.getAbsolutePath()));
            setStatus("Exported: " + dest.getName());
        } catch (Exception e) {
            setStatus("Export failed: " + e.getMessage());
        }
    }

    /** Close button — just closes the dialog window. */
    @FXML
    private void handleClose() {
        getStage().close();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Reloads the left-hand ListView from the ConfigurationManager. */
    private void refreshList() {
        configListView.getItems().clear();
        configurationManager.listConfigurations()
                .forEach(c -> configListView.getItems().add(c.getName()));
    }

    /** Populates the form fields from whichever config is selected. */
    private void loadSelectedIntoForm() {
        List<Configuration> all = configurationManager.listConfigurations();
        if (selectedIndex < 0 || selectedIndex >= all.size()) return;
        Configuration c = all.get(selectedIndex);

        nameField.setText(c.getName() != null ? c.getName() : "");
        compilerPathField.setText(c.getCompilerPath() != null ? c.getCompilerPath() : "");
        compileArgsField.setText(c.getCompileArgs() != null ? c.getCompileArgs() : "");
        fileToCompileField.setText(c.getFileToCompile() != null ? c.getFileToCompile() : "");
        executablePathField.setText(c.getRelativeExecutablePath() != null
                ? c.getRelativeExecutablePath() : "");
        interpretedCheckBox.setSelected(c.isInterpreted());
        statusLabel.setText("");
    }

    private void clearForm() {
        nameField.clear();
        compilerPathField.clear();
        compileArgsField.clear();
        fileToCompileField.clear();
        executablePathField.clear();
        interpretedCheckBox.setSelected(false);
        statusLabel.setText("");
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
    }

    private Stage getStage() {
        return (Stage) nameField.getScene().getWindow();
    }
}