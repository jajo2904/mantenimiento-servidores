package com.mantenimiento.servidores;
import java.util.*;
import java.util.concurrent.*;
final class OperacionesEquipos {
 private static final ExecutorService COLA=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256),r->{Thread t=new Thread(r,"operacion-equipo");t.setDaemon(true);return t;});
 @FunctionalInterface interface Accion<T>{void ejecutar(T elemento)throws Exception;}
 static <T> void todos(Collection<T> elementos,Accion<T> accion)throws Exception {
  List<Future<?>> pendientes=new ArrayList<>();
  Exception fallo=null;
  try{for(T elemento:elementos)pendientes.add(COLA.submit(()->{accion.ejecutar(elemento);return null;}));}
  catch(RejectedExecutionException e){fallo=e;}
  boolean interrumpido=false;
  for(var pendiente:pendientes){
   boolean terminado=false;
   while(!terminado)try{pendiente.get();terminado=true;}
   catch(InterruptedException e){interrumpido=true;}
   catch(ExecutionException e){terminado=true;if(fallo==null)fallo=e;else fallo.addSuppressed(e);}
  }
  if(interrumpido){Thread.currentThread().interrupt();if(fallo==null)fallo=new InterruptedException("Operacion interrumpida; tareas iniciadas finalizadas.");}
  if(fallo!=null)throw fallo;
 }
 static <T> T uno(Callable<T> accion)throws Exception{return COLA.submit(accion).get();}
}
