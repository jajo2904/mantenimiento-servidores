package com.mantenimiento.servidores;
import java.nio.file.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
public final class SessionLog {
 private static Path archivo;
 private static final String NOMBRE="Sesion_"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))+"_"+ProcessHandle.current().pid()+".log";
 public static synchronized Path escribir(String contexto,String detalle,Throwable error) throws IOException {
  if(archivo==null){
   Path base=Path.of("C:\\MantenimientoServidores\\Logs");
   try{Files.createDirectories(base);archivo=Files.createFile(base.resolve(NOMBRE));}
   catch(IOException e){
    String local=System.getenv("LOCALAPPDATA");
    if(local==null||local.isBlank())throw new IOException("No hay una ubicacion disponible para el log de sesion.",e);
    base=Path.of(local,"MantenimientoServidores","Logs");
    try{Files.createDirectories(base);archivo=Files.createFile(base.resolve(NOMBRE));}
    catch(IOException alternativo){alternativo.addSuppressed(e);throw alternativo;}
   }
  }
  StringWriter traza=new StringWriter();if(error!=null)error.printStackTrace(new PrintWriter(traza));
  Files.writeString(archivo,LocalDateTime.now()+" ["+contexto+"] "+detalle+System.lineSeparator()+traza,StandardOpenOption.APPEND);
  return archivo;
 }
}
