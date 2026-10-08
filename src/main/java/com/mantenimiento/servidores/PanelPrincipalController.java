package com.mantenimiento.servidores;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;

public class PanelPrincipalController {

    @FXML
    private Label UsuarioLabel;

    public void setUsuario(String usuario) {
        UsuarioLabel.setText(usuario);
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
}
