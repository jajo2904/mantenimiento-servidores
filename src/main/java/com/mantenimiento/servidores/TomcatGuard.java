package com.mantenimiento.servidores;
import java.util.*;
import java.io.IOException;
public class TomcatGuard {
 private static String literal(String s){return "'"+s.replace("'","''")+"'";}
 public static void comprobar(String equipo,List<String> instancias,ProcesosRemotosService.Registro log) throws IOException {
  // Solo lectura. No se ejecutan BAT remotos ni se matan procesos Java sin identificar.
  String rutas=String.join(",",instancias.stream().map(TomcatGuard::literal).toList());
  String script="$ErrorActionPreference='Stop'; try { $rutas=@("+rutas+"); $ps=@(Get-WmiObject Win32_Process -ComputerName "+literal(equipo)+" -Filter \"Name='java.exe' OR Name='javaw.exe' OR Name LIKE 'tomcat%'\"); foreach($p in $ps) { if(!$p.CommandLine) { throw ('No se puede identificar PID '+$p.ProcessId) }; foreach($r in $rutas) { if($p.CommandLine.IndexOf($r,[StringComparison]::OrdinalIgnoreCase) -ge 0) { throw ('Tomcat activo: PID '+$p.ProcessId+'; instancia '+$r+'; comando '+$p.CommandLine) } }; if($p.Name -like 'tomcat*' -or $p.CommandLine -match 'org.apache.catalina|catalina.base|catalina.home'){throw ('Servicio Tomcat sin asociacion segura: PID '+$p.ProcessId)} }; Write-Output 'TOMCAT_VERIFICADO_AUSENTE' } catch { Write-Output $_.Exception.ToString(); exit 1 }";
  String encoded=Base64.getEncoder().encodeToString(script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
  var resultado=ComandoWindows.ejecutar(List.of(ComandoWindows.sistema("WindowsPowerShell\\v1.0\\powershell.exe"),"-NoProfile","-NonInteractive","-EncodedCommand",encoded),25);
  log.escribir("Consulta Tomcat/WMI en "+equipo+"; codigo="+resultado.codigo()+"; respuesta="+resultado.salida());
  if(resultado.codigo()!=0||!resultado.salida().trim().equals("TOMCAT_VERIFICADO_AUSENTE"))throw new IOException("No se confirma la parada de Tomcat. "+resultado.salida().strip()+". Detenga las instancias con su procedimiento autorizado; no se iniciara el borrado.");
 }
}
