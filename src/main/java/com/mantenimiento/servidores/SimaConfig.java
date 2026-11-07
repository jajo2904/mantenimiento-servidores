package com.mantenimiento.servidores;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
public class SimaConfig {
 public static final Path ARCHIVO=Path.of("C:\\MantenimientoServidores\\portales.txt");
 public record Equipo(String nombre,String ip,String grupo,int orden,boolean ut) {}
 public static List<Equipo> cargar(String texto) throws IOException {
  List<Equipo> equipos=new ArrayList<>(); String nombre=null,ip="",grupo=""; boolean activo=true,ut=false; int orden=99999;
  Set<String> nombres=new HashSet<>(), claves=new HashSet<>();
  for(String raw:(texto+"\n[FIN]").split("\\R")) {
   String s=raw.replace("\uFEFF","").trim(); if(s.isEmpty()||s.startsWith("#"))continue;
   if(s.startsWith("[")&&s.endsWith("]")) {
    if(nombre!=null && activo) {
     validarDestino(ip);
     if(nombre.isBlank()||grupo.isBlank())throw new IOException("Nombre o grupo vacio: "+nombre);
     equipos.add(new Equipo(nombre,ip,grupo,orden,ut));
    }
    String seccion=s.substring(1,s.length()-1).trim();
    if(!nombres.add(seccion.toUpperCase(Locale.ROOT)))throw new IOException("Seccion duplicada: "+seccion);
    nombre=null;ip="";activo=true;ut=false;orden=99999;claves.clear();
    if(seccion.startsWith("UT:")){nombre=seccion.substring(3).trim();grupo="UT";ut=true;}
    else if(seccion.startsWith("MONITOR:")){nombre=seccion.substring(8).trim();grupo="Otros equipos";}
    else if(seccion.startsWith("SVR-APL-")||seccion.startsWith("SRV-APL-")){nombre=seccion;grupo="Portales";}
   } else {
    int igual=s.indexOf('=');
    if(igual<1)throw new IOException("Linea de configuracion no valida: "+s);
    String clave=s.substring(0,igual).trim();
    if(!claves.add(clave.toLowerCase(Locale.ROOT)))throw new IOException("Clave duplicada: "+clave);
    s=clave+"="+s.substring(igual+1).trim();
    if(nombre==null)continue;
    if(s.startsWith("grupo="))grupo=s.substring(6).trim();
    if(s.startsWith("orden=")){try{orden=Integer.parseInt(s.substring(6).trim());}catch(NumberFormatException e){throw new IOException("Orden no valido en "+nombre,e);}}
    if(s.startsWith("ip="))ip=s.substring(3).trim();
    if(s.startsWith("monitor=")){String v=s.substring(8).trim();if(!v.equals("true")&&!v.equals("false"))throw new IOException("monitor debe ser true o false");activo=Boolean.parseBoolean(v);}
   }
  }
  equipos.sort(Comparator.comparingInt(Equipo::orden));
  return equipos;
 }
 public static void validarDestino(String destino) throws IOException {
  if(destino==null || destino.length()>253 || !destino.matches("[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*"))
   throw new IOException("IP o nombre de equipo no valido: "+destino);
  if(destino.matches("[0-9.]+")) {
   String[] partes=destino.split("\\.");
   if(partes.length!=4)throw new IOException("IPv4 no valida: "+destino);
   for(String parte:partes)if(parte.length()>3 || Integer.parseInt(parte)>255)throw new IOException("IPv4 no valida: "+destino);
  }
 }
 public static void verificarEquipo(String texto,Equipo equipo) throws IOException {
  if(!cargar(texto).contains(equipo))throw new IOException("La configuracion del equipo cambio. Espere la actualizacion de SIMA.");
 }
 public static List<Equipo> cargar() throws IOException {return cargar(Files.readString(ARCHIVO));}
 public static void guardar(String texto) throws IOException {guardar(texto,Files.readString(ARCHIVO));}
 public static synchronized void guardar(String texto,String original) throws IOException {
  if(!Files.readString(ARCHIVO).equals(original))throw new IOException("La configuracion cambio desde que se abrio. Cierre y vuelva a abrir el editor.");
  cargar(texto);
  InicioUtService.configuracionInicio(texto);
  Path temporal=Files.createTempFile(ARCHIVO.getParent(),"config-",".txt");
  try {
   Files.writeString(temporal,texto);
   new ConfiguracionPortalesService().cargarPortales(temporal);
   try {Files.move(temporal,ARCHIVO,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
   catch(AtomicMoveNotSupportedException e){Files.move(temporal,ARCHIVO,StandardCopyOption.REPLACE_EXISTING);}
  } finally {Files.deleteIfExists(temporal);}
 }
}
