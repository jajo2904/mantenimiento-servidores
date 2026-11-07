package com.mantenimiento.servidores;

import java.io.IOException;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class MenuController {
    @FXML private Label usuarioLabel;
    private String usuario;

    public void setUsuario(String usuario) {
        this.usuario = usuario;
        usuarioLabel.setText("Usuario: " + usuario);
    }

    @FXML private void abrirPortales() { navegar("PanelPrincipal.fxml"); }
    @FXML private void cerrarSesion() { navegar("login.fxml"); }

    @FXML private void abrirSima() { new SimaWindow().mostrar(); }
    @FXML private void abrirPendiente2() { navegar("Pendiente.fxml", "FunciÃ³n 2"); }
    @FXML private void abrirPendiente3() { navegar("Pendiente.fxml", "FunciÃ³n 3"); }
    private void navegar(String recurso) { navegar(recurso, ""); }
    private void navegar(String recurso, String titulo) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(recurso));
            Scene scene = new Scene(loader.load());
            if (loader.getController() instanceof PanelPrincipalController controller) {
                controller.setUsuario(usuario);
            }
            if (loader.getController() instanceof PendienteController pendiente) pendiente.configurar(titulo, usuario);
            Stage stage = (Stage) usuarioLabel.getScene().getWindow();
            stage.setScene(scene);
            stage.sizeToScene();
            stage.centerOnScreen();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "No se pudo abrir la pantalla: " + e.getMessage()).showAndWait();
        }
    }
}
