package com.mantenimiento.servidores;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
final class ParadaAfisService {
 @FunctionalInterface interface Registro {void escribir(String mensaje)throws IOException;}
 static String detener(SimaConfig.Equipo equipo)throws IOException {
  SimaConfig.verificarEquipo(Files.readString(SimaConfig.ARCHIVO),equipo);
  return detener(equipo.ip(),mensaje->SessionLog.escribir("Parada AFIS "+equipo.nombre(),mensaje,null));
 }
 static String detener(String destino,Registro log)throws IOException {
  SimaConfig.validarDestino(destino);
  log.escribir("Parada AFIS: primero Python identificado, despues Runner. Destino="+destino);
  String script=scriptParada(destino);
  var r=ComandoWindows.ejecutar(List.of(ComandoWindows.sistema("WindowsPowerShell/v1.0/powershell.exe"),"-NoProfile","-NonInteractive","-OutputFormat","Text","-EncodedCommand",Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE))),30,StandardCharsets.UTF_8);
  log.escribir("Codigo="+r.codigo()+"; "+r.salida());
  if(r.codigo()!=0||!r.salida().lines().anyMatch("AFIS_DETENIDO"::equals))throw new IOException("Parada incompleta o no confirmada: "+r.salida());
  return "Runner y lanzadores AFIS detenidos; ausencia verificada.";
 }
 static String scriptParada(String destino)throws IOException {
  SimaConfig.validarDestino(destino);
  return """
   $ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.UTF8Encoding]::new();
   $destino='%s';
   function Objetivos {
    $lista=@(Get-WmiObject Win32_Process -ComputerName $destino);
    foreach($p in $lista){if($p.Name -match '^pythonw?\\.exe$' -and !$p.CommandLine){throw 'No se puede identificar un proceso Python. No se confirma la parada.'}}
    $lista | Where-Object {($_.Name -match '^(APPRunnera|DisRunnera|ManagerRunnera)\\.exe$') -or
      ($_.Name -match '^pythonw?\\.exe$' -and $_.CommandLine -match '(?i)(?:^|[\\\\/\\s"])(app_runner|runafiswks|runafis)\\.py(?=["\\s]|$)')}
   }
   try {
    foreach($fase in @('Python','Runner')){
    $objetivos=@(Objetivos | Where-Object {if($fase -eq 'Python'){$_.Name -match '^python'}else{$_.Name -notmatch '^python'}});
    Write-Output ('FASE '+$fase+' objetivos='+$objetivos.Count);
    foreach($p in $objetivos){
     $actual=Get-WmiObject Win32_Process -ComputerName $destino -Filter ('ProcessId='+$p.ProcessId);
     if(!$actual){continue}
     if($actual.CreationDate -ne $p.CreationDate -or $actual.Name -ne $p.Name){throw ('El PID cambio: '+$p.ProcessId)}
     $r=$actual.Terminate();
     Write-Output ('PARADA '+$actual.Name+' PID='+$actual.ProcessId+' ReturnValue='+$r.ReturnValue);
     if($r.ReturnValue -ne 0){throw ('No se pudo detener '+$actual.Name)}
    }
    $libre=$false;
    for($intento=0;$intento -lt 5;$intento++){
     $restantes=@(Objetivos | Where-Object {if($fase -eq 'Python'){$_.Name -match '^python'}else{$true}});
     if($restantes.Count -eq 0){$libre=$true;break};Start-Sleep -Milliseconds 400;
    }
    if(!$libre){throw ('Parada incompleta en fase '+$fase+': '+(($restantes | ForEach-Object {$_.Name+' PID='+$_.ProcessId}) -join ', '))}
    }
    $ausencias=0;
    for($intento=0;$intento -lt 5;$intento++){
     Start-Sleep -Milliseconds 500;
     $restantes=@(Objetivos);
     if($restantes.Count -ne 0){break}
     $ausencias++;if($ausencias -ge 3){Write-Output 'AFIS_DETENIDO';exit 0}
    }
    throw ('Permanecen activos: '+(($restantes | ForEach-Object {$_.Name+' PID='+$_.ProcessId}) -join ', '));
   }catch{Write-Output $_.Exception.ToString();exit 1}
   """.formatted(destino);
 }
}
