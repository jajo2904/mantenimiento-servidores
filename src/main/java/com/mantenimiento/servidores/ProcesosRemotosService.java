package com.mantenimiento.servidores;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ProcesosRemotosService {
    static final List<String> PROCESOS = List.of(
            "DisRunnera.exe", "ManagerRunnera.exe", "APPRunnera.exe", "python.exe");
    record Resultado(int codigo, String salida) {}
    @FunctionalInterface interface Comando { Resultado ejecutar(List<String> argumentos) throws IOException; }
    @FunctionalInterface public interface Registro { void escribir(String mensaje) throws IOException; }
    private final Comando comando;

    public ProcesosRemotosService() { this(ProcesosRemotosService::ejecutarWindows); }
    ProcesosRemotosService(Comando comando) { this.comando = comando; }

    public void detener(String equipo, Registro log) throws IOException {
        if (equipo == null || !equipo.matches("[A-Za-z0-9][A-Za-z0-9.:-]*")) {
            throw new IOException("Equipo remoto no valido; no se ejecutara mantenimiento.");
        }
        for (String nombre : PROCESOS) {
            Resultado resultado = comando.ejecutar(List.of("taskkill.exe", "/S", equipo, "/F", "/IM", nombre));
            // Un proceso ausente tambien produce codigo no cero: la consulta posterior decide.
            log.escribir(equipo + " -> " + nombre + " -> cierre (codigo " + resultado.codigo() + "): " + resultado.salida().strip());
        }
        Resultado consulta = comando.ejecutar(List.of("tasklist.exe", "/S", equipo, "/FO", "CSV", "/NH"));
        comprobar(consulta);
        log.escribir(equipo + " -> verificado: los cuatro procesos estan detenidos.");
    }

    static void comprobar(Resultado resultado) throws IOException {
        if (resultado.codigo() != 0) throw new IOException("No se pudo verificar el equipo remoto: " + resultado.salida());
        boolean filas = false;
        for (String linea : resultado.salida().lines().toList()) {
            if (linea.isBlank()) continue;
            // Consulta sin filtros: debe devolver filas CSV, nunca interpretar un error como lista vacia.
            if (!linea.matches("^\"[^\"]+\",\"[0-9]+\",.*")) {
                throw new IOException("Respuesta de procesos no reconocida; se cancela el mantenimiento.");
            }
            filas = true;
            String nombre = linea.substring(1, linea.indexOf('\"', 1));
            if (PROCESOS.stream().anyMatch(p -> p.equalsIgnoreCase(nombre))) {
                throw new IOException("no se pudo detener el proceso " + nombre);
            }
        }
        if (!filas) throw new IOException("La consulta remota no devolvio procesos; no se borraran carpetas.");
    }

    private static Resultado ejecutarWindows(List<String> argumentos) throws IOException {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) throw new IOException("Se requiere Windows.");
        var comando = new java.util.ArrayList<>(argumentos);
        comando.set(0, Path.of(System.getenv("SystemRoot"), "System32", argumentos.get(0)).toString());
        Process proceso = new ProcessBuilder(comando).redirectErrorStream(true).start();
        try (var lector = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var salida = lector.submit(() -> {
                try (var entrada = proceso.getInputStream()) {
                    return new String(entrada.readAllBytes(), Charset.forName("IBM850"));
                }
            });
            try {
                if (!proceso.waitFor(30, TimeUnit.SECONDS)) {
                    throw new IOException("Tiempo agotado al contactar " + argumentos.get(2));
                }
                return new Resultado(proceso.exitValue(), salida.get(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Operacion interrumpida", e);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                throw new IOException("No se pudo leer la respuesta de " + argumentos.get(2), e);
            } finally {
                if (proceso.isAlive()) proceso.destroyForcibly();
                salida.cancel(true);
            }
        }
    }
}