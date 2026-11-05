package com.mantenimiento.servidores;
import java.io.IOException;
import javafx.fxml.*;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
public class PendienteController {
 @FXML private Label tituloLabel, usuarioLabel;
 private String usuario;
 public void configurar(String titulo, String usuario) { this.usuario=usuario; tituloLabel.setText(titulo); usuarioLabel.setText(usuario); }
 @FXML private void volver() throws IOException {
  FXMLLoader loader=new FXMLLoader(getClass().getResource("Menu.fxml"));
  Scene scene=new Scene(loader.load());
  ((MenuController)loader.getController()).setUsuario(usuario);
  Stage stage=(Stage)usuarioLabel.getScene().getWindow(); stage.setScene(scene); stage.sizeToScene(); stage.centerOnScreen();
 }
}
