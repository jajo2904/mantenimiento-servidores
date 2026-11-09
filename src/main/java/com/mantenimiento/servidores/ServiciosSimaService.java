package com.mantenimiento.servidores;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public final class ServiciosSimaService {
 public record Estado(boolean configurado,boolean completo,boolean lanzadorActivo,boolean activosDetectados,String detalle) {}
 public static Estado consultar(SimaConfig.Equipo equipo) throws IOException {
  SimaConfig.verificarEquipo(Files.readString(SimaConfig.ARCHIVO),equipo);
  String ps=scriptConsulta(equipo.ip(),!equipo.servicios().isEmpty());
  String encoded=Base64.getEncoder().encodeToString(ps.getBytes(StandardCharsets.UTF_16LE));
  var r=ComandoWindows.ejecutar(List.of(ComandoWindows.sistema("WindowsPowerShell\\v1.0\\powershell.exe"),
    "-NoProfile","-NonInteractive","-OutputFormat","Text","-EncodedCommand",encoded),8,StandardCharsets.UTF_8);
  if(r.codigo()!=0)throw new IOException("Consulta WMI rechazada, codigo="+r.codigo()+": "+r.salida());
  return evaluar(equipo,r.salida());
 }
 static String scriptConsulta(String destino,boolean servicios) throws IOException {
  SimaConfig.validarDestino(destino);
  return """
   $ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; [Console]::OutputEncoding=[Text.UTF8Encoding]::new();
   function B64($s){[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string]$s))}
   try {
    $hostRemoto='%s';
    $procesos=@(Get-WmiObject Win32_Process -ComputerName $hostRemoto);
    foreach($p in $procesos){
     Write-Output ('P|'+(B64 $p.Name));
     if($p.Name -match '^pythonw?\\.exe$'){
      if(!$p.CommandLine){Write-Output 'R|DESCONOCIDO'}
      elseif($p.CommandLine -match '(?i)(?:^|[\\\\/\\s"])(app_runner|runafiswks|runafis)\\.py(?=["\\s]|$)'){Write-Output 'R|ACTIVO'}
     }
    }
    %s
    Write-Output 'SIMA_OK';
   }catch{Write-Output $_.Exception.ToString();exit 1}
   """.formatted(destino,servicios?"Get-WmiObject Win32_Service -ComputerName $hostRemoto | ForEach-Object { Write-Output ('S|'+(B64 $_.Name)+'|'+$_.State) }":"");
 }
 static Estado evaluar(SimaConfig.Equipo equipo,String salida) throws IOException {
  Map<String,Integer> procesos=new TreeMap<>();Set<String> servicios=new TreeSet<>();boolean activo=false,desconocido=false,fin=false;
  for(String linea:salida.strip().split("\\R")){
   if(fin)throw new IOException("Datos despues del fin de consulta WMI.");
   if(linea.equals("SIMA_OK")){fin=true;continue;}
   String[] partes=linea.split("\\|",-1);
   try{
    if(partes.length==2&&partes[0].equals("P"))procesos.merge(decodificar(partes[1]),1,Integer::sum);
    else if(partes.length==3&&partes[0].equals("S")){if(partes[2].equals("Running"))servicios.add(decodificar(partes[1]));}
    else if(linea.equals("R|ACTIVO"))activo=true;
    else if(linea.equals("R|DESCONOCIDO"))desconocido=true;
    else throw new IOException("Formato de respuesta WMI no reconocido.");
   }catch(IllegalArgumentException e){throw new IOException("Respuesta WMI mal codificada.",e);}
  }
  if(!fin)throw new IOException("Respuesta WMI incompleta.");
  List<String> faltan=new ArrayList<>();
  for(String nombre:equipo.procesos())if(!procesos.containsKey(nombre.toLowerCase(Locale.ROOT)))faltan.add("proceso "+nombre);
  for(String nombre:equipo.servicios())if(!servicios.contains(nombre.toLowerCase(Locale.ROOT)))faltan.add("servicio "+nombre);
  boolean configurado=!equipo.procesos().isEmpty()||!equipo.servicios().isEmpty();
  String detalle=(configurado?(faltan.isEmpty()?"Todos los procesos/servicios configurados activos.":"Faltan: "+String.join(", ",faltan)):
    "Pendiente de configurar procesos/servicios esperados; no se puede confirmar salud completa.")+
    " Lanzador="+(activo?"activo":desconocido?"no verificable":"no detectado")+"; procesos observados="+procesos;
  return new Estado(configurado,configurado&&faltan.isEmpty(),activo||desconocido,activo||SimaConfig.PROCESOS_AFIS.stream().anyMatch(n->procesos.containsKey(n.toLowerCase(Locale.ROOT))),detalle);
 }
 private static String decodificar(String valor){return new String(Base64.getDecoder().decode(valor),StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);}
}
