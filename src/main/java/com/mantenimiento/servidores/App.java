package com.mantenimiento.servidores;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader loader = new FXMLLoader(
                App.class.getResource("/com/mantenimiento/servidores/login.fxml")
        );

        Scene scene = new Scene(loader.load());

        stage.setTitle("Mantenimiento de Servidores");
        stage.setResizable(true);
        stage.sceneProperty().addListener((observable, anterior, nueva) -> ajustarMinimos(stage, nueva));
        stage.setScene(scene);
        stage.show();
    }

    private static void ajustarMinimos(Stage stage, Scene scene) {
        if (scene.getRoot() instanceof javafx.scene.layout.Region root) {
            // Reservar el marco de Windows y respetar el minimo propio de cada panel.
            double ancho = root.getMinWidth() > 0 ? root.getMinWidth() : root.prefWidth(-1);
            double alto = root.getMinHeight() > 0 ? root.getMinHeight() : root.prefHeight(-1);
            stage.setMinWidth(Math.max(320, ancho) + 16);
            stage.setMinHeight(Math.max(200, alto) + 39);
        }
    }

    public static void main(String[] args) {
        launch();
    }
}