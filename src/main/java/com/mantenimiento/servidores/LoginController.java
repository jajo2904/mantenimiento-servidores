package com.mantenimiento.servidores;

import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

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

        System.out.println("Usuario: " + autenticado);
        System.out.println("Autenticado: " + autenticado);
    }
}