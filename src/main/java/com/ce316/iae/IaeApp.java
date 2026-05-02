package com.ce316.iae;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class IaeApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                IaeApp.class.getResource("/fxml/MainWindow.fxml")));
        Scene scene = new Scene(loader.load(), 960, 640);
        stage.setTitle("IAE – Integrated Assignment Environment");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
