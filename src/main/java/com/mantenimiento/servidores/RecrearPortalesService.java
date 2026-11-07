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
        Path log = crearRutaLog(usuario);
        String ejecucion=java.util.UUID.randomUUID().toString(); long inicio=System.nanoTime();
        java.util.LinkedHashMap<Path,String> resultados=new java.util.LinkedHashMap<>();
        escribirLog(log,"INICIO id="+ejecucion+" portal="+nombre+" usuario="+usuario+" cuentaWindows="+System.getProperty("user.name")+" version=desarrollo-SIMA-1");
        progreso.accept("Registro: "+log);
        try {
            if(portal==null)throw new IOException("No se encontro la configuracion del portal.");
            escribirLog(log,"IP="+portal.getIp()+" instancias="+portal.getInstancias());
            if(portal.getInstancias().isEmpty())throw new IOException("No hay instancias configuradas.");
            for(String instancia:portal.getInstancias()) {
                Path raiz=Path.of(convertirRutaRemota(portal.getIp(),instancia));
                for(String carpeta:java.util.List.of("webapps/CRC","webapps/birt-viewer","webapps/portal","webapps/probe","work"))resultados.put(raiz.resolve(carpeta),"NO INTENTADA");
            }
            // Preparar todas las instancias del portal antes de modificar la primera.
            for(String instancia:portal.getInstancias()) {
                Path raiz=Path.of(convertirRutaRemota(portal.getIp(),instancia));
                var attrs=Files.readAttributes(raiz,java.nio.file.attribute.BasicFileAttributes.class,java.nio.file.LinkOption.NOFOLLOW_LINKS);
                if(!attrs.isDirectory()||attrs.isOther()||attrs.isSymbolicLink())throw new IOException("Instancia no valida: "+raiz);
            }
            progreso.accept("Validando ejecucion de servicios");
            // La parada especifica de Tomcat sigue pendiente de validar sus BAT.
            TomcatGuard.comprobar(portal.getIp(),portal.getInstancias(),m->escribirLog(log,m));
            new ProcesosRemotosService().detener(portal.getIp(),m->escribirLog(log,m));
            TomcatGuard.comprobar(portal.getIp(),portal.getInstancias(),m->escribirLog(log,m));
            progreso.accept("Recreando "+nombre);
            for(Path carpeta:resultados.keySet()) {
                try {
                    TomcatGuard.comprobar(portal.getIp(),portal.getInstancias(),m->escribirLog(log,m));
                    escribirLog(log,"BORRADO INICIO ruta="+carpeta);
                    eliminarCarpeta(carpeta,log);
                    confirmarAusente(carpeta);
                    resultados.put(carpeta,"AUSENCIA VERIFICADA");
                } catch(Exception e) {
                    resultados.put(carpeta,"FALLIDA O PARCIAL: "+detalle(e));
                    throw e;
                }
            }
            // Verificacion final conjunta: detectar carpetas recreadas durante la operacion.
            for(Path carpeta:resultados.keySet()) {
                try {confirmarAusente(carpeta);}catch(IOException e){resultados.put(carpeta,"PRESENTE O NO VERIFICABLE: "+detalle(e));throw e;}
            }
            escribirLog(log,nombre+" COMPLETADO: limpieza de todas las instancias verificada.");
        } catch(Exception e) {
            java.io.StringWriter traza=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(traza));
            escribirLog(log,"FALLO id="+ejecucion+" "+detalle(e)+System.lineSeparator()+traza);
            String resumen=nombre+" ¡Fallido!\n"+detalle(e);
            for(var item:resultados.entrySet())resumen+="\n"+item.getKey()+" -> "+item.getValue();
            throw new IOException(resumen,e);
        } finally {
            for(var item:resultados.entrySet())escribirLog(log,"RESULTADO ruta="+item.getKey()+" estado="+item.getValue());
            escribirLog(log,"FIN id="+ejecucion+" duracionMs="+(System.nanoTime()-inicio)/1_000_000);
        }
    }
    private static void confirmarAusente(Path carpeta) throws IOException {
        try {Files.readAttributes(carpeta,java.nio.file.attribute.BasicFileAttributes.class,java.nio.file.LinkOption.NOFOLLOW_LINKS);}
        catch(java.nio.file.NoSuchFileException e){return;}
        throw new IOException("La carpeta permanece o fue recreada: "+carpeta);
    }
    private static String detalle(Exception e) {
        String texto=e.getClass().getName()+": "+e.getMessage();
        if(e instanceof java.nio.file.FileSystemException f)texto+="; archivo="+f.getFile()+"; otro="+f.getOtherFile()+"; motivo="+f.getReason();
        return texto;
    }

    static String convertirRutaRemota(String ip, String rutaLocal) throws IOException {
        if (ip == null || !ip.matches("[A-Za-z0-9][A-Za-z0-9.-]*")) throw new IOException("IP o equipo no valido.");
        if (rutaLocal == null || !rutaLocal.matches("[A-Za-z]:\\\\[^\\\\].*") || rutaLocal.contains("..") || rutaLocal.substring(2).contains(":"))
            throw new IOException("Ruta de instancia no valida: " + rutaLocal);
        return "\\\\" + ip + "\\" + Character.toUpperCase(rutaLocal.charAt(0)) + "$\\" + rutaLocal.substring(3);
    }

    static void eliminarCarpeta(Path carpeta, Path log) throws IOException {
        try {
            Files.readAttributes(carpeta, java.nio.file.attribute.BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (java.nio.file.NoSuchFileException e) {
            escribirLog(log, carpeta + " -> ya ausente.");
            return;
        }
        // No seguir enlaces ni junctions que puedan salir de la carpeta prevista.
        Files.walkFileTree(carpeta, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                if (attrs.isOther() || attrs.isSymbolicLink()) throw new IOException("Enlace de directorio no permitido: " + dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                if (attrs.isOther() || attrs.isSymbolicLink()) throw new IOException("Enlace no permitido: " + file);
                Files.delete(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        escribirLog(log, carpeta + " -> eliminada correctamente.");
    }

    private Path crearRutaLog(String usuario) throws IOException {

        Path carpetaLogs =
                Path.of("C:\\MantenimientoServidores\\Logs");

        try {
            Files.createDirectories(carpetaLogs);
            Path prueba = Files.createTempFile(carpetaLogs, "escritura-", ".tmp");
            Files.delete(prueba);
        } catch (IOException e) {
            String local = System.getenv("LOCALAPPDATA");
            if (local == null) throw new IOException("No hay carpeta disponible para registros.", e);
            carpetaLogs = Path.of(local, "MantenimientoServidores", "Logs");
            Files.createDirectories(carpetaLogs);
        }
        usuario = nombreSeguro(usuario);

        String fecha = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        return carpetaLogs.resolve(
                usuario + "_" + fecha + "_RecreaPortales.log"
        );
    }

    static String nombreSeguro(String usuario) {
        return usuario == null || usuario.isBlank() ? "usuario" : usuario.replaceAll("[^A-Za-z0-9._-]", "_");
    }
    private static void escribirLog(Path log, String mensaje) throws IOException {

        Files.writeString(
                log,
                java.time.LocalDateTime.now() + " " + mensaje + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }
}
