package com.mantenimiento.servidores;
public class ActiveDirectoryService {

    public boolean autenticar(String usuario, String password) {

        if (usuario == null || usuario.isBlank()) {
            return false;
        }
        if (password == null || password.isBlank()) {
            return false;
        }

        // Temporal:
        // después aquí irá la validación contra AD.
        return true;
    }
}