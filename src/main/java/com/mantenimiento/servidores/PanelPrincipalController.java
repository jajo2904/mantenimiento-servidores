package com.mantenimiento.servidores;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;

public class PanelPrincipalController {
    @FXML private Button VolverButton, EjecutarButton;
    @FXML private Label UsuarioLabel, EstadoLabel;
    @FXML private TextArea HistorialArea;
    @FXML private CheckBox portal001Check, portal002Check, portal003Check, portal004Check,
            portal005Check, portal006Check, portal012Check, portal015Check, portal024Check,
            seleccionarTodoCheck;

    private final Map<String, CheckBox> portales = new LinkedHashMap<>();
    private final ConfiguracionPortalesService configuracionService = new ConfiguracionPortalesService();
    private final RecrearPortalesService recrearPortalesService = new RecrearPortalesService();
    private String usuarioActual;
    private boolean ejecutando;
    private final Map<String,String> estadosPortales=new LinkedHashMap<>();
    private static final java.util.regex.Pattern PORTAL_MENSAJE=java.util.regex.Pattern.compile("^(Portal[0-9]+)\\b");
    private String resumenGeneral="";
    private boolean renderPendiente;
    private final Tooltip ayudaEstado=new Tooltip();

    @FXML private void initialize() {
        String[] ids = {"001", "002", "003", "004", "005", "006", "012", "015", "024"};
        CheckBox[] checks = {portal001Check, portal002Check, portal003Check, portal004Check,
                portal005Check, portal006Check, portal012Check, portal015Check, portal024Check};
        for (int i = 0; i < ids.length; i++) {
            portales.put(ids[i], checks[i]);
            checks[i].selectedProperty().addListener((o, antes, despues) -> actualizarSeleccion());
        }
    }

    public void setUsuario(String usuario) {
        usuarioActual = usuario;
        UsuarioLabel.setText(usuario);
    }

    @FXML private void volverMenu() throws IOException {
        if (ejecutando) return;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("Menu.fxml"));
        Scene scene = new Scene(loader.load());
        ((MenuController) loader.getController()).setUsuario(usuarioActual);
        Stage stage = (Stage) VolverButton.getScene().getWindow();
        stage.setScene(scene);
        stage.sizeToScene();
        stage.centerOnScreen();
    }

    @FXML private void seleccionarTodos() {
        boolean seleccionar = seleccionarTodoCheck.isSelected();
        portales.values().forEach(check -> check.setSelected(seleccionar));
        actualizarSeleccion();
    }

    private void actualizarSeleccion() {
        long elegidos = portales.values().stream().filter(CheckBox::isSelected).count();
        seleccionarTodoCheck.setSelected(elegidos == portales.size());
        seleccionarTodoCheck.setIndeterminate(elegidos > 0 && elegidos < portales.size());
    }

    private void bloquearControles(boolean bloquear) {
        ejecutando = bloquear;
        EjecutarButton.setDisable(bloquear);
        VolverButton.setDisable(bloquear);
        seleccionarTodoCheck.setDisable(bloquear);
        portales.values().forEach(check -> check.setDisable(bloquear));
    }

    private void mostrar(String texto) {
        String linea=texto.lines().findFirst().orElse("");
        EstadoLabel.setText(linea);
        ayudaEstado.setText(linea);
        if(EstadoLabel.getTooltip()!=ayudaEstado)EstadoLabel.setTooltip(ayudaEstado);
        var portal=PORTAL_MENSAJE.matcher(linea);
        if(portal.find()){
            estadosPortales.put(portal.group(1),linea);
            if(resumenGeneral.equals("Cargando configuracion de portales..."))resumenGeneral="Mantenimiento en curso...";
        }
        else resumenGeneral=linea;
        if(renderPendiente)return;
        renderPendiente=true;
        Platform.runLater(()->{
            renderPendiente=false;
            String filas=String.join("\n",estadosPortales.values());
            String contenido=filas+(filas.isEmpty()||resumenGeneral.isEmpty()?"":"\n")+resumenGeneral;
            if(!contenido.equals(HistorialArea.getText()))HistorialArea.setText(contenido);
        });
    }

    private void registrarFallo(String contexto, Throwable error) {
        try {
            SessionLog.escribir(contexto, "Fallo durante la recreacion de portales", error);
        } catch (IOException falloLog) {
            error.addSuppressed(falloLog);
            mostrar("No se pudo guardar el detalle en el log de la sesion.");
            error.printStackTrace();
        }
    }

    @FXML private void ejecutar() {
        if (ejecutando) return;
        var seleccion = portales.entrySet().stream().filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey).toList();
        if (seleccion.isEmpty()) {
            EstadoLabel.setText("Seleccione al menos un portal.");
            return;
        }
        bloquearControles(true);
        estadosPortales.clear();
        seleccion.forEach(id->estadosPortales.put("Portal"+id,"Portal"+id+" - En espera"));
        resumenGeneral="";
        HistorialArea.clear();
        mostrar("Cargando configuracion de portales...");
        Stage ventana = (Stage) EjecutarButton.getScene().getWindow();
        var cierreAnterior = ventana.getOnCloseRequest();
        ventana.setOnCloseRequest(event -> {
            event.consume();
            EstadoLabel.setText("Espere a que termine el mantenimiento antes de cerrar.");
        });
        Task<Void> tarea = new Task<>() {
            private void progreso(String mensaje) {
                Platform.runLater(() -> mostrar(mensaje));
            }
            @Override protected Void call() throws Exception {
                var configuracion = configuracionService.cargarPortales();
                var fallos = new java.util.concurrent.atomic.AtomicInteger();
                OperacionesEquipos.todos(seleccion, id -> {
                    String nombre = "Portal" + id;
                    var portal = configuracion.get("SVR-APL-" + id);
                    if (portal == null) portal = configuracion.get("SRV-APL-" + id);
                    try {
                        recrearPortalesService.ejecutar(portal, usuarioActual, nombre, this::progreso);
                        progreso(nombre + " completado.");
                    } catch (Exception e) {
                        fallos.incrementAndGet();
                        progreso(nombre + " - Fallido. Consulte el log.");
                        // Tambien registra fallos previos a crear el log del portal.
                        try { SessionLog.escribir(nombre, "Recreacion fallida", e); }
                        catch (IOException logError) {
                            e.addSuppressed(logError);
                            progreso("No se pudo guardar el detalle en el log de la sesion.");
                            e.printStackTrace();
                        }
                    }
                });
                progreso("Finalizado: " + (seleccion.size() - fallos.get()) + " correctos; " + fallos.get() + " con error.");
                return null;
            }
        };
        Runnable finalizar = () -> {
            ventana.setOnCloseRequest(cierreAnterior);
            bloquearControles(false);
        };
        tarea.setOnSucceeded(event -> finalizar.run());
        tarea.setOnCancelled(event -> finalizar.run());
        tarea.setOnFailed(event -> {
            finalizar.run();
            mostrar("No se pudo iniciar el mantenimiento. Consulte el log de la sesion.");
            registrarFallo("Inicio del mantenimiento", tarea.getException());
        });
        Thread hilo = new Thread(tarea, "recrear-portales");
        hilo.setDaemon(true);
        hilo.start();
    }
}
