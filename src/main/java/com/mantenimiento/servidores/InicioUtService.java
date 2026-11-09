package com.mantenimiento.servidores;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
public class InicioUtService {
 private static final Set<String> OCUPADAS=java.util.concurrent.ConcurrentHashMap.newKeySet();
 static boolean reservar(String destino){return OCUPADAS.add(destino.toLowerCase(Locale.ROOT));}
 static void liberar(String destino){OCUPADAS.remove(destino.toLowerCase(Locale.ROOT));}
 private static String literal(String s){return "'"+s.replace("'","''")+"'";}
 record Inicio(String python,String script) {}
 static Inicio configuracionInicio(String texto) throws IOException {
  String python="python.exe",script="\\\\central\\facil\\runafiswks.py";boolean sima=false;
  for(String raw:texto.replace("\uFEFF", "").split("\\R")) {
   String linea=raw.trim();
   if(linea.startsWith("[")){sima=linea.equals("[SIMA]");continue;}
   if(!sima||linea.startsWith("#"))continue;
   String[] par=linea.split("=",2);if(par.length!=2)continue;
   switch(par[0].trim()) {case "inicioPython"->python=par[1].trim();case "inicioScript"->script=par[1].trim();default->{}}
  }
  validarRuta(python);validarRuta(script);
  Path nombre=Path.of(python).getFileName();
  if(nombre==null)throw new IOException("Falta el nombre del ejecutable Python.");
  String ejecutable=nombre.toString();
  if(!ejecutable.equalsIgnoreCase("python.exe")&&!ejecutable.equalsIgnoreCase("pythonw.exe"))
   throw new IOException("inicioPython debe indicar python.exe o pythonw.exe.");
  if(!python.equalsIgnoreCase(ejecutable)&&!Path.of(python).isAbsolute())throw new IOException("La ruta de Python debe ser absoluta.");
  if(!Path.of(script).isAbsolute()||!script.toLowerCase(Locale.ROOT).endsWith(".py"))throw new IOException("inicioScript debe ser una ruta absoluta a un archivo .py.");
  return new Inicio(python,script);
 }
 private static void validarRuta(String valor) throws IOException {
  if(valor.isBlank()||valor.contains("\"")||valor.chars().anyMatch(Character::isISOControl))throw new IOException("Ruta de inicio no valida.");
  try{Path.of(valor);}catch(InvalidPathException e){throw new IOException("Ruta de inicio no valida.",e);}
 }
 public static String iniciar(SimaConfig.Equipo equipo) throws IOException {
  if(!reservar(equipo.ip()))return "Ya hay una solicitud en curso para este equipo.";
  try{return iniciarReservado(equipo);}finally{liberar(equipo.ip());}
 }
 static String iniciarReservado(SimaConfig.Equipo equipo) throws IOException {
  String texto=Files.readString(SimaConfig.ARCHIVO);
  SimaConfig.verificarEquipo(texto,equipo);
  var inicio=configuracionInicio(texto);
   var estado=ServiciosSimaService.consultar(equipo);
   SessionLog.escribir("Antes del inicio "+equipo.nombre(),estado.detalle(),null);
   if(estado.completo())return "Los procesos y servicios configurados ya estan activos; no se solicito otro inicio.";
   if(estado.lanzadorActivo())return "El lanzador esta activo o no se puede descartar su ejecucion; no se solicito otra copia.";
   String python=inicio.python(),script=inicio.script();
   String comando="\""+python+"\" \""+script+"\"";
   String ps="$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; [Console]::OutputEncoding=[Text.UTF8Encoding]::new(); try { $equipo="+literal(equipo.ip())+"; $script="+literal(script)+"; $procesos=@(Get-WmiObject Win32_Process -ComputerName $equipo -Filter \"Name='python.exe' OR Name='pythonw.exe'\"); foreach($p in $procesos){if(!$p.CommandLine){throw 'No se puede comprobar si el script ya esta ejecutandose'};if($p.CommandLine.IndexOf($script,[StringComparison]::OrdinalIgnoreCase) -ge 0){Write-Output 'YA_EN_EJECUCION';exit 0}}; $clase=Get-WmiObject -List Win32_Process -ComputerName $equipo; $r=$clase.Create("+literal(comando)+"); Write-Output ('ReturnValue='+$r.ReturnValue+'; PID='+$r.ProcessId); if($r.ReturnValue -ne 0){exit 1}; Write-Output 'SOLICITUD_ACEPTADA' }catch{Write-Output $_.Exception.ToString();exit 1}";
   String encoded=Base64.getEncoder().encodeToString(ps.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
   SessionLog.escribir("Inicio equipo", "equipo="+equipo.nombre()+" IP="+equipo.ip()+" comando="+comando,null);
   var r=ComandoWindows.ejecutar(List.of(ComandoWindows.sistema("WindowsPowerShell\\v1.0\\powershell.exe"),"-NoProfile","-NonInteractive","-OutputFormat","Text","-EncodedCommand",encoded),30,java.nio.charset.StandardCharsets.UTF_8);
   SessionLog.escribir("Inicio equipo " + equipo.nombre(),"codigo="+r.codigo()+" respuesta="+r.salida(),null);
   if(r.codigo()!=0)throw new IOException("Windows no acepto el inicio remoto: "+r.salida());
   if(r.salida().contains("YA_EN_EJECUCION"))return "El script ya se esta ejecutando; no se inicio otra copia.";
   if(!r.salida().contains("SOLICITUD_ACEPTADA"))throw new IOException("Respuesta remota no reconocida: "+r.salida());
   return "Solicitud aceptada en "+equipo.nombre()+". "+r.salida().strip()+"\nLa aceptacion no confirma el arranque; SIMA verificara los procesos configurados.";
 }
}
