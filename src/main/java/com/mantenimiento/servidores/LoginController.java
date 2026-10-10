package com.mantenimiento.servidores;

import java.io.IOException;
import java.lang.reflect.Constructor;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Modality;

public class LoginController {

    @FXML
    private TextField UsuarioField;

    @FXML
    private PasswordField PasswordField;

    private final ActiveDirectoryService activeDirectoryService =
            new ActiveDirectoryService();

    @FXML
    private void ingresar() {
        String usuario = UsuarioField.getText();
        String password = PasswordField.getText();
        boolean autenticado =
                activeDirectoryService.autenticar(usuario, password);
        if(autenticado) {
            abrirPanelPrincipal();
        } else {
            abrirPanelError();
        }
    }
    
    @FXML
   private void abrirPanelPrincipal() {
    try {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/mantenimiento/servidores/PanelPrincipal.fxml")
        );
        Scene scene = new Scene(loader.load());
        Stage stage = (Stage) UsuarioField.getScene().getWindow();
        stage.setScene(scene);
        stage.setTitle("Mantenimiento de Servidores");
        stage.centerOnScreen();
    } catch (IOException e) {
        e.printStackTrace();
    }
}

private void abrirPanelError() {
    try {
        FXMLLoader loader = new FXMLLoader(
            LoginController.class.getResource("/com/mantenimiento/servidores/PanelError.fxml")
        );

        Stage stage = new Stage();
        stage.setScene(new Scene(loader.load()));
        stage.show();

    } catch (IOException e) {
        e.printStackTrace();
    }
}

}