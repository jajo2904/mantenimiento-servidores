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

        return cargarPortales(RUTA_CONFIG);
    }
    Map<String, PortalConfig> cargarPortales(Path archivo) throws IOException {
        if (!Files.isRegularFile(archivo)) throw new IOException("No se encontro la configuracion: " + archivo);
        Map<String, PortalConfig> portales = new LinkedHashMap<>();

        PortalConfig portalActual = null;
        java.util.Set<String> claves=new java.util.HashSet<>();

        for (String linea : Files.readAllLines(archivo)) {

            linea = linea.replace("\uFEFF", "").trim();

              if (linea.isBlank() || linea.startsWith("#")) {
                continue;
            }

            if (linea.startsWith("[") && linea.endsWith("]")) {

                claves.clear();
                String nombre =
                        linea.substring(1, linea.length() - 1);

                if (nombre.equals("SIMA") || nombre.startsWith("UT:") || nombre.startsWith("MONITOR:")) { portalActual = null; continue; }
                if (portales.containsKey(nombre)) throw new IOException("Portal duplicado en configuracion: " + nombre);
                portalActual = new PortalConfig(nombre);
                portales.put(nombre, portalActual);

                continue;
            }

            if (portalActual == null) {
                continue;
            }

            int separador=linea.indexOf('=');
            if(separador<1)throw new IOException("Linea de configuracion no valida: "+linea);
            String clave=linea.substring(0,separador).trim();
            if(!claves.add(clave.toLowerCase(java.util.Locale.ROOT)))throw new IOException("Clave duplicada en "+portalActual.nombre+": "+clave);
            linea=clave+"="+linea.substring(separador+1).trim();
            if (linea.startsWith("ip=")) {
                portalActual.ip = linea.substring(3).trim();
            }
            else if (clave.matches("I[0-9]+")) {

                String[] partes = linea.split("=", 2);

                if (partes.length == 2) {
                    portalActual.instancias.add(partes[1].trim());
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
            return List.copyOf(instancias);
        }
    }
}