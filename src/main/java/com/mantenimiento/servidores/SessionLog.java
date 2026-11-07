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
   catch(IOException e){base=Path.of(System.getenv("LOCALAPPDATA"),"MantenimientoServidores","Logs");Files.createDirectories(base);archivo=Files.createFile(base.resolve(NOMBRE));}
  }
  StringWriter traza=new StringWriter();if(error!=null)error.printStackTrace(new PrintWriter(traza));
  Files.writeString(archivo,LocalDateTime.now()+" ["+contexto+"] "+detalle+System.lineSeparator()+traza,StandardOpenOption.APPEND);
  return archivo;
 }
}
