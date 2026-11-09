package com.mantenimiento.servidores;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class RecrearPortalesService {
    private static final java.util.List<String> CARPETAS=java.util.List.of("webapps/CRC","webapps/birt-viewer","webapps/portal","webapps/probe","work");

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

        boolean reservado=false;
        try {
            if(portal==null)throw new IOException("No se encontro la configuracion del portal.");
            escribirLog(log,"IP="+portal.getIp()+" instancias="+portal.getInstancias());
            if(portal.getInstancias().isEmpty())throw new IOException("No hay instancias configuradas.");
            for(String instancia:portal.getInstancias()) {
                Path raiz=Path.of(convertirRutaRemota(portal.getIp(),instancia));
                for(String carpeta:CARPETAS)resultados.put(raiz.resolve(carpeta),"NO INTENTADA");
            }
            var vigente=new ConfiguracionPortalesService().cargarPortales().get(portal.getNombre());
            if(vigente==null||!java.util.Objects.equals(vigente.getIp(),portal.getIp())||!vigente.getInstancias().equals(portal.getInstancias()))
                throw new IOException("La configuracion del portal cambio. Actualice la seleccion antes de continuar.");
            SimaConfig.validarDestino(portal.getIp());
            reservado=InicioUtService.reservar(portal.getIp());
            if(!reservado)throw new IOException("El equipo tiene otra operacion en curso en SIMA o Recrear Portales.");
            progreso.accept(nombre+" · Validando servicios");
            ParadaAfisService.detener(portal.getIp(),m->escribirLog(log,m));
            progreso.accept("Recreando "+nombre);
            java.util.List<Path> raices=new java.util.ArrayList<>();
            for(String instancia:portal.getInstancias())raices.add(Path.of(convertirRutaRemota(portal.getIp(),instancia)));
            limpiarInstancias(raices,log,resultados,ruta->progreso.accept("Recreando "+nombre+" · "+ruta));
            escribirLog(log,nombre+" COMPLETADO: limpieza de todas las instancias verificada.");
        } catch(Exception e) {
            java.io.StringWriter traza=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(traza));
            escribirLog(log,"FALLO id="+ejecucion+" "+detalle(e)+System.lineSeparator()+traza);
            String resumen=nombre+" ¡Fallido!\n"+detalle(e);
            for(var item:resultados.entrySet())resumen+="\n"+item.getKey()+" -> "+item.getValue();
            throw new IOException(resumen,e);
        } finally {
            if(reservado)InicioUtService.liberar(portal.getIp());
            for(var item:resultados.entrySet())escribirLog(log,"RESULTADO ruta="+item.getKey()+" estado="+item.getValue());
            escribirLog(log,"FIN id="+ejecucion+" duracionMs="+(System.nanoTime()-inicio)/1_000_000);
        }
    }
    static void limpiarInstancias(java.util.List<Path> raices,Path log,java.util.LinkedHashMap<Path,String> resultados,
                                   java.util.function.Consumer<Path> progreso) throws IOException {
        java.util.List<IOException> errores=new java.util.ArrayList<>();
        for(Path raiz:raices) {
            Path base=raiz.toAbsolutePath().normalize();
            for(String relativo:CARPETAS) {
                Path carpeta=raiz.resolve(relativo);
                progreso.accept(carpeta);
                try {
                    if(!carpeta.toAbsolutePath().normalize().startsWith(base))throw new IOException("Ruta fuera de la instancia: "+carpeta);
                    validarDirectorio(raiz);
                    if(relativo.startsWith("webapps/")) {
                        try {validarDirectorio(raiz.resolve("webapps"));}
                        catch(java.nio.file.NoSuchFileException ausente) {}
                    }
                    escribirLog(log,"BORRADO INICIO ruta="+carpeta);
                    eliminarConReintentos(carpeta,log);
                    confirmarAusente(carpeta);
                    resultados.put(carpeta,"AUSENCIA VERIFICADA");
                } catch(IOException e) {
                    resultados.put(carpeta,"FALLIDA O PARCIAL: "+detalle(e));errores.add(e);
                    java.io.StringWriter traza=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(traza));
                    escribirLog(log,"BORRADO FALLO ruta="+carpeta+" "+traza);
                }
            }
        }
        for(Path carpeta:resultados.keySet()) {
            try {confirmarAusente(carpeta);}
            catch(IOException e) {resultados.merge(carpeta,"PRESENTE O NO VERIFICABLE: "+detalle(e),(anterior,nuevo)->anterior+"; "+nuevo);errores.add(e);}
        }
        if(!errores.isEmpty()) {
            IOException fallo=new IOException("Limpieza incompleta. Se intentaron las carpetas de todas las instancias.");
            errores.forEach(fallo::addSuppressed);throw fallo;
        }
    }
    private static void validarDirectorio(Path ruta) throws IOException {
        var atributos=Files.readAttributes(ruta,java.nio.file.attribute.BasicFileAttributes.class,java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if(!atributos.isDirectory()||atributos.isOther()||atributos.isSymbolicLink())throw new IOException("Directorio no permitido: "+ruta);
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

    private static void eliminarConReintentos(Path carpeta,Path log)throws IOException {
        for(int intento=1;intento<=3;intento++) {
            try {eliminarCarpeta(carpeta,log);return;}
            catch(java.nio.file.FileSystemException fallo) {
                escribirLog(log,"BORRADO intento="+intento+" ruta="+carpeta+" detalle="+detalle(fallo));
                if(intento==3)throw fallo;
                try {Thread.sleep(1000);}
                catch(InterruptedException interrumpido){Thread.currentThread().interrupt();throw new IOException("Borrado interrumpido: "+carpeta,interrumpido);}
            }
        }
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
                eliminarArchivo(file,log);
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

    private static void eliminarArchivo(Path archivo,Path log) throws IOException {
        try {Files.delete(archivo);}
        catch(java.nio.file.AccessDeniedException original) {
            boolean soloLectura=false;
            try {
                var atributos=Files.readAttributes(archivo,java.nio.file.attribute.DosFileAttributes.class,java.nio.file.LinkOption.NOFOLLOW_LINKS);
                soloLectura=atributos.isReadOnly();
                escribirLog(log,"ACCESO DENEGADO archivo="+archivo+" soloLectura="+soloLectura+" oculto="+atributos.isHidden()+" sistema="+atributos.isSystem());
                if(soloLectura&&!atributos.isSymbolicLink()&&!atributos.isOther()) {
                    Files.setAttribute(archivo,"dos:readonly",false,java.nio.file.LinkOption.NOFOLLOW_LINKS);
                    try {Files.delete(archivo);escribirLog(log,"BORRADO REINTENTO correcto tras quitar solo lectura: "+archivo);return;}
                    catch(IOException fallo) {
                        try {Files.setAttribute(archivo,"dos:readonly",true,java.nio.file.LinkOption.NOFOLLOW_LINKS);}
                        catch(IOException restauracion) {fallo.addSuppressed(restauracion);}
                        original.addSuppressed(fallo);
                    }
                }
            } catch(IOException | UnsupportedOperationException diagnostico) {original.addSuppressed(diagnostico);}
            escribirLog(log,"BORRADO NO RESUELTO archivo="+archivo+"; revisar permisos o bloqueo de archivo; soloLectura="+soloLectura);
            throw original;
        }
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
    private static synchronized void escribirLog(Path log, String mensaje) throws IOException {

        Files.writeString(
                log,
                java.time.LocalDateTime.now() + " " + mensaje + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }
}
