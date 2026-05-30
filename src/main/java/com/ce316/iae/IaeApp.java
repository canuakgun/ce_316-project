package com.ce316.iae;

import com.ce316.iae.persistence.PersistenceManager;
import com.ce316.iae.service.ConfigurationManager;
import com.ce316.iae.ui.ThemeManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class IaeApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        new ConfigurationManager(PersistenceManager.getInstance()).seedDefaultsIfEmpty();

        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                IaeApp.class.getResource("/fxml/MainWindow.fxml")));
        Scene scene = new Scene(loader.load(), 1180, 760);
        ThemeManager.apply(scene);

        stage.setTitle("IAE — Integrated Assignment Environment");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
