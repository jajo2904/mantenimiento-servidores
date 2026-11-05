package com.mantenimiento.servidores;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

public class RecrearPortalesService {

    public void ejecutar(
            ConfiguracionPortalesService.PortalConfig portal,
            String usuario) throws IOException {

        ejecutar(portal, usuario, "Portal", mensaje -> {});
    }
    public void ejecutar(ConfiguracionPortalesService.PortalConfig portal, String usuario,
                         String nombre, java.util.function.Consumer<String> progreso) throws IOException {
        if (portal == null) throw new IOException("No se encontro la configuracion del equipo seleccionado.");
        Path log = crearRutaLog(usuario);

        escribirLog(log, "");
        escribirLog(log, "Usuario: " + usuario);
        escribirLog(log, "Fecha y hora: " +
                LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        escribirLog(log, "Operacion realizada: Recrear Portales");
        escribirLog(log, "");
        escribirLog(log, "IP: " + portal.getIp());
        if (portal.getInstancias().isEmpty()) throw new IOException("no hay instancias configuradas.");
        progreso.accept("Validando ejecucion de servicios");
        try {
            new ProcesosRemotosService().detener(portal.getIp(), mensaje -> escribirLog(log, mensaje));
        } catch (IOException e) {
            escribirLog(log, "Mantenimiento cancelado antes de borrar carpetas: " + e.getMessage());
            throw e;
        }

        progreso.accept("Recreando " + nombre);
        for (String instancia : portal.getInstancias()) {

            String rutaRemota = convertirRutaRemota(
                    portal.getIp(),
                    instancia
            );

            eliminarCarpeta(
                    Path.of(rutaRemota, "webapps", "CRC"),
                    log
            );

            eliminarCarpeta(
                    Path.of(rutaRemota, "webapps", "birt-viewer"),
                    log
            );

            eliminarCarpeta(
                    Path.of(rutaRemota, "webapps", "portal"),
                    log
            );

            eliminarCarpeta(
                    Path.of(rutaRemota, "webapps", "probe"),
                    log
            );

            eliminarCarpeta(
                    Path.of(rutaRemota, "work"),
                    log
            );
        }
    }

    private String convertirRutaRemota(String ip, String rutaLocal) {

        String rutaSinUnidad = rutaLocal.substring(3);

        return "\\\\" + ip + "\\C$\\" + rutaSinUnidad;
    }

private void eliminarCarpeta(Path carpeta, Path log) throws IOException {
    try {

        Files.readAttributes(
                carpeta,
                java.nio.file.attribute.BasicFileAttributes.class
        );

        try (var archivos = Files.walk(carpeta)) {

            archivos
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        escribirLog(log, carpeta + " -> eliminada correctamente.");
    } catch (java.nio.file.NoSuchFileException e) {
        escribirLog(
                log,
                carpeta + " -> carpeta no encontrada, no se elimino."
        );
    } catch (java.nio.file.AccessDeniedException e) {
        escribirLog(
                log,
                carpeta + " -> acceso denegado."
        );
    } catch (java.nio.file.FileSystemException e) {
        escribirLog(
                log,
                carpeta + " -> error de acceso al servidor o ruta de red."
        );
        throw new IOException("error de acceso al servidor o ruta: " + carpeta, e);
    }
}

    private Path crearRutaLog(String usuario) throws IOException {

        Path carpetaLogs =
                Path.of("C:\\MantenimientoServidores\\Logs");

        Files.createDirectories(carpetaLogs);

        String fecha = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        return carpetaLogs.resolve(
                usuario + "_" + fecha + "_RecreaPortales.log"
        );
    }

    private void escribirLog(Path log, String mensaje) throws IOException {

        Files.writeString(
                log,
                mensaje + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }
}
