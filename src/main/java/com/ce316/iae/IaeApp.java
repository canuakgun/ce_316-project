package com.ce316.iae;

import com.ce316.iae.persistence.PersistenceManager;
import com.ce316.iae.service.ConfigurationManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class IaeApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Bootstrap the DB (creates %APPDATA%\IAE\ + 5 tables) and seed defaults
        new ConfigurationManager(PersistenceManager.getInstance()).seedDefaultsIfEmpty();

        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                IaeApp.class.getResource("/fxml/MainWindow.fxml")));
        Scene scene = new Scene(loader.load(), 1100, 700);
        stage.setTitle("IAE – Integrated Assignment Environment");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
