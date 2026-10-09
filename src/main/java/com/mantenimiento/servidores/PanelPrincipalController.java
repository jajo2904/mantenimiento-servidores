package com.mantenimiento.servidores;
import java.io.IOException;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.concurrent.Task;

public class PanelPrincipalController {

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

    boolean p001 = portal001Check.isSelected();
    boolean p002 = portal002Check.isSelected();
    boolean p003 = portal003Check.isSelected();
    boolean p004 = portal004Check.isSelected();
    boolean p005 = portal005Check.isSelected();
    boolean p006 = portal006Check.isSelected();
    boolean p012 = portal012Check.isSelected();
    boolean p015 = portal015Check.isSelected();
    boolean p024 = portal024Check.isSelected();

    EstadoLabel.setText("Ejecutando mantenimiento...");

    Task<Void> tarea = new Task<>() {

        @Override
        protected Void call() throws Exception {

            Map<String, ConfiguracionPortalesService.PortalConfig> portales =
                    configuracionService.cargarPortales();

            if (p001) {
                recrearPortalesService.ejecutar(
                        portales.get("SVR-APL-001"), usuarioActual);
            }

            if (p002) {
                recrearPortalesService.ejecutar(
                        portales.get("SRV-APL-002"), usuarioActual);
            }

            if (p003) {
                recrearPortalesService.ejecutar(
                        portales.get("SVR-APL-003"), usuarioActual);
            }

            if (p004) {
                recrearPortalesService.ejecutar(
                        portales.get("SVR-APL-004"), usuarioActual);
            }

            if (p005) {
                recrearPortalesService.ejecutar(
                        portales.get("SVR-APL-005"), usuarioActual);
            }

            if (p006) {
                recrearPortalesService.ejecutar(
                        portales.get("SVR-APL-006"), usuarioActual);
            }

            if (p012) {
                recrearPortalesService.ejecutar(
                        portales.get("SRV-APL-012"), usuarioActual);
            }

            if (p015) {
                recrearPortalesService.ejecutar(
                        portales.get("SRV-APL-015"), usuarioActual);
            }

            if (p024) {
                recrearPortalesService.ejecutar(
                        portales.get("SRV-APL-024"), usuarioActual);
            }

            return null;
        }
    };

    tarea.setOnSucceeded(event ->
            EstadoLabel.setText("Mantenimiento finalizado"));

    tarea.setOnFailed(event -> {
        EstadoLabel.setText("Error durante el mantenimiento");
        tarea.getException().printStackTrace();
    });

    Thread hilo = new Thread(tarea);
    hilo.setDaemon(true);
    hilo.start();
}


private void mostrarPortal(
         ConfiguracionPortalesService.PortalConfig portal) {

     if (portal == null) {
         return;
     }

     System.out.println("Portal: " + portal.getNombre());
     System.out.println("IP: " + portal.getIp());

     for (String instancia : portal.getInstancias()) {
         System.out.println("Instancia: " + instancia);
     }

     System.out.println("-------------------------");
 }

private String usuarioActual;

private final RecrearPortalesService recrearPortalesService =
         new RecrearPortalesService();


@FXML private Label EstadoLabel;

}
