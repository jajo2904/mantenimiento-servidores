package com.mantenimiento.servidores;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
public class ParadaAfisTest {
 private String mock(){return """
  $global:datos=@();
  function Proceso($id,$nombre,$linea){
   $p=[pscustomobject]@{ProcessId=$id;Name=$nombre;CommandLine=$linea;CreationDate='fecha'};
   $p | Add-Member -MemberType ScriptMethod -Name Terminate -Value {
    if($this.ProcessId -ge 90){throw 'No tocar proceso ajeno'};
    $id=$this.ProcessId;$global:datos=@($global:datos | Where-Object {$_.ProcessId -ne $id});
    [pscustomobject]@{ReturnValue=0}
   };$global:datos+=,$p;
  }
  Proceso 1 'APPRunnera.exe' 'APP';Proceso 2 'DisRunnera.exe' 'Dis';Proceso 3 'ManagerRunnera.exe' 'Manager';
  Proceso 4 'python.exe' 'python.exe "C:\\AFIS\\app_runner.py" Distribution.xml';
  Proceso 90 'java.exe' 'Tomcat';Proceso 91 'python.exe' 'python.exe C:\\Otra\\tarea.py';
  function Get-WmiObject {
   param([string]$Class,[string]$ComputerName,[string]$Filter)
   if($Filter){$id=[int]($Filter.Split('=')[1]);$global:datos | Where-Object {$_.ProcessId -eq $id}}
   else{$global:datos}
  }
  """;}
 private ComandoWindows.Resultado ejecutar(String previo)throws Exception {
  String script=mock()+previo+ParadaAfisService.scriptParada("192.0.2.1");
  return ComandoWindows.ejecutar(List.of(ComandoWindows.sistema("WindowsPowerShell/v1.0/powershell.exe"),"-NoProfile","-NonInteractive","-OutputFormat","Text","-EncodedCommand",Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE))),10,StandardCharsets.UTF_8);
 }
 @Test public void terminaSoloAfisYVerificaAusencia()throws Exception {
  var r=ejecutar("");assertEquals(r.salida(),0,r.codigo());assertTrue(r.salida().contains("AFIS_DETENIDO"));
  assertEquals(4,r.salida().lines().filter(l->l.startsWith("PARADA ")).count());
  assertFalse(r.salida().contains("PID=90"));assertFalse(r.salida().contains("PID=91"));
 }
 @Test public void pythonSinIdentificarImpideParada()throws Exception {
  var r=ejecutar("$global:datos[5].CommandLine=$null;\n");assertNotEquals(0,r.codigo());assertFalse(r.salida().contains("PARADA "));
 }
 @Test public void errorDeTerminacionNoConfirmaExito()throws Exception {
  var r=ejecutar("$global:datos[3] | Add-Member -Force -MemberType ScriptMethod -Name Terminate -Value {[pscustomobject]@{ReturnValue=2}};\n");
  assertNotEquals(0,r.codigo());assertFalse(r.salida().contains("AFIS_DETENIDO"));
 }
}
