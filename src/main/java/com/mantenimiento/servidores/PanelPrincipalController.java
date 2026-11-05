package com.mantenimiento.servidores;
import java.io.IOException;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.concurrent.Task;
import javafx.scene.control.Button;

public class PanelPrincipalController {
    @FXML private Button VolverButton;

    @FXML private void volverMenu() throws IOException {
        javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("Menu.fxml"));
        javafx.scene.Scene scene = new javafx.scene.Scene(loader.load());
        MenuController menu = loader.getController();
        menu.setUsuario(usuarioActual);
        javafx.stage.Stage stage = (javafx.stage.Stage) VolverButton.getScene().getWindow();
        stage.setScene(scene);
        stage.sizeToScene();
        stage.centerOnScreen();
    }

    @FXML
    private Label UsuarioLabel;

    public void setUsuario(String usuario) {
        UsuarioLabel.setText(usuario);
        this.usuarioActual = usuario;
    }
@FXML private CheckBox portal001Check;
@FXML private CheckBox portal002Check;
@FXML private CheckBox portal003Check;
@FXML private CheckBox portal004Check;
@FXML private CheckBox portal005Check;
@FXML private CheckBox portal006Check;
@FXML private CheckBox portal012Check;
@FXML private CheckBox portal015Check;
@FXML private CheckBox portal024Check;
@FXML private CheckBox seleccionarTodoCheck;
@FXML
private Button EjecutarButton;
@FXML
private void seleccionarTodos() {
    boolean seleccionar = seleccionarTodoCheck.isSelected();
    portal001Check.setSelected(seleccionar);
    portal002Check.setSelected(seleccionar);
    portal003Check.setSelected(seleccionar);
    portal004Check.setSelected(seleccionar);
    portal005Check.setSelected(seleccionar);
    portal006Check.setSelected(seleccionar);
    portal012Check.setSelected(seleccionar);
    portal015Check.setSelected(seleccionar);
    portal024Check.setSelected(seleccionar);
}

private final ConfiguracionPortalesService configuracionService =
        new ConfiguracionPortalesService();

@FXML
private void ejecutar() {
    var checks = java.util.List.of(portal001Check, portal002Check, portal003Check, portal004Check, portal005Check, portal006Check, portal012Check, portal015Check, portal024Check);
    String[] ids = {"001","002","003","004","005","006","012","015","024"};
    var seleccion = new java.util.ArrayList<String>();
    for (int i=0;i<ids.length;i++) if(checks.get(i).isSelected()) seleccion.add(ids[i]);
    if(seleccion.isEmpty()) { EstadoLabel.setText("Seleccione al menos un portal."); return; }
    EjecutarButton.setDisable(true); VolverButton.setDisable(true); HistorialArea.clear();
    Task<Void> tarea = new Task<>() {
      private void mostrar(String texto) {
        javafx.application.Platform.runLater(() -> { EstadoLabel.setText(texto); HistorialArea.appendText(texto + "\n"); });
      }
      @Override protected Void call() throws Exception {
        var portales = configuracionService.cargarPortales();
        int fallos=0;
        for(String id: seleccion) {
          String nombre="Portal"+id;
          var portal=portales.get("SVR-APL-"+id);
          if(portal==null) portal=portales.get("SRV-APL-"+id);
          try {
            recrearPortalesService.ejecutar(portal, usuarioActual, nombre, this::mostrar);
            mostrar(nombre+" recreado.");
          } catch(Exception e) {
            fallos++;
            mostrar(nombre+" no se pudo recrear porque " + (e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
          }
        }
        mostrar("Finalizado: "+(seleccion.size()-fallos)+" correctos; "+fallos+" con error.");
        return null;
      }
    };
    tarea.setOnSucceeded(event -> { VolverButton.setDisable(false); EjecutarButton.setDisable(false); });
    tarea.setOnFailed(event -> {
      VolverButton.setDisable(false); EjecutarButton.setDisable(false);
      String error="No se pudo iniciar: "+tarea.getException().getMessage();
      EstadoLabel.setText(error); HistorialArea.appendText(error+"\n");
    });
    Thread hilo=new Thread(tarea); hilo.setDaemon(true); hilo.start();
}
@FXML private javafx.scene.control.TextArea HistorialArea;

private String usuarioActual;

private final RecrearPortalesService recrearPortalesService =
         new RecrearPortalesService();
@FXML private Label EstadoLabel;
}
