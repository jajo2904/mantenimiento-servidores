package com.mantenimiento.servidores;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfiguracionPortalesService {

    private static final Path RUTA_CONFIG =
            Path.of("C:\\MantenimientoServidores\\portales.txt");

    public Map<String, PortalConfig> cargarPortales() throws IOException {

        Map<String, PortalConfig> portales = new LinkedHashMap<>();

        PortalConfig portalActual = null;

        for (String linea : Files.readAllLines(RUTA_CONFIG)) {

            linea = linea.trim();

              if (linea.isBlank() || linea.startsWith("#")) {
                continue;
            }

            if (linea.startsWith("[") && linea.endsWith("]")) {

                String nombre =
                        linea.substring(1, linea.length() - 1);

                portalActual = new PortalConfig(nombre);
                portales.put(nombre, portalActual);

                continue;
            }

            if (portalActual == null) {
                continue;
            }

            if (linea.startsWith("ip=")) {
                portalActual.ip = linea.substring(3);
            }
            else if (linea.startsWith("I")) {

                String[] partes = linea.split("=", 2);

                if (partes.length == 2) {
                    portalActual.instancias.add(partes[1]);
                }
            }
        }

        return portales;
    }

    public static class PortalConfig {

        private final String nombre;
        private String ip;
        private final List<String> instancias = new ArrayList<>();

        public PortalConfig(String nombre) {
            this.nombre = nombre;
        }

        public String getNombre() {
            return nombre;
        }

        public String getIp() {
            return ip;
        }

        public List<String> getInstancias() {
            return instancias;
        }
    }
}