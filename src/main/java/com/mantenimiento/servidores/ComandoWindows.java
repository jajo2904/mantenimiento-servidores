package com.mantenimiento.servidores;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;
public class ComandoWindows {
 public record Resultado(int codigo,String salida){}
 static final int LIMITE_SALIDA=1024*1024;
 public static Resultado ejecutar(List<String> args,int segundos) throws IOException {
  return ejecutar(args,segundos,Charset.forName("IBM850"));
 }
 public static Resultado ejecutar(List<String> args,int segundos,Charset charset) throws IOException {
  if(args.isEmpty()||segundos<1)throw new IllegalArgumentException("Comando o tiempo de espera no valido");
  Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
  var resultado=new CompletableFuture<String>();
  Thread lector=Thread.ofVirtual().start(()->{
   try(var in=p.getInputStream();var salida=new ByteArrayOutputStream()){
    byte[] bloque=new byte[8192];int leidos;boolean recortada=false;
    while((leidos=in.read(bloque))!=-1){
     int guardar=Math.min(leidos,LIMITE_SALIDA-salida.size());
     salida.write(bloque,0,guardar);recortada|=guardar<leidos;
    }
    resultado.complete(salida.toString(charset)+(recortada?"\n[Salida truncada: limite de 1 MiB]":""));
   }catch(Exception e){resultado.completeExceptionally(e);}
  });
  try {
   // Ningun comando de SIMA solicita entrada interactiva.
   p.getOutputStream().close();
   if(!p.waitFor(segundos,TimeUnit.SECONDS))throw new IOException("Tiempo agotado ("+segundos+" s): "+args.get(0)+". El resultado remoto no esta confirmado.");
   return new Resultado(p.exitValue(),resultado.get(3,TimeUnit.SECONDS));
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Operacion interrumpida; resultado remoto no confirmado",e);}
  catch(ExecutionException|TimeoutException e){throw new IOException("No se pudo leer la respuesta del comando; resultado remoto no confirmado",e);}
  finally {
   if(p.isAlive()){p.descendants().forEach(ProcessHandle::destroyForcibly);p.destroyForcibly();}
   lector.interrupt();
   try{p.getInputStream().close();}catch(IOException ignored){}
  }
 }
 public static String sistema(String nombre){return java.nio.file.Path.of(System.getenv("SystemRoot"),"System32",nombre).toString();}
}
