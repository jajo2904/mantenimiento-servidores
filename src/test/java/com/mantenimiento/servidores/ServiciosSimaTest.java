package com.mantenimiento.servidores;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.*;
import java.nio.charset.StandardCharsets;
public class ServiciosSimaTest {
 private String b64(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
 private SimaConfig.Equipo equipo(){return new SimaConfig.Equipo("UT","192.0.2.1","G",0,true,List.of("APPRunnera.exe","DisRunnera.exe"),List.of("ServicioPrueba"));}
 private String respuesta(String estado){return "P|"+b64("apprunnera.EXE")+"\nP|"+b64("DisRunnera.exe")+"\nS|"+b64("ServicioPrueba")+"|"+estado+"\nSIMA_OK";}
 @Test public void verdeSoloConTodosActivos() throws Exception {
  assertTrue(ServiciosSimaService.evaluar(equipo(),respuesta("Running")).completo());
  assertFalse(ServiciosSimaService.evaluar(equipo(),respuesta("Stopped")).completo());
  assertFalse(ServiciosSimaService.evaluar(equipo(),"P|"+b64("APPRunnera.exe")+"\nSIMA_OK").completo());
 }
 @Test public void sinReglasNoConfirmaServicios() throws Exception {
  var eq=new SimaConfig.Equipo("UT","192.0.2.1","G",0,true);
  var estado=ServiciosSimaService.evaluar(eq,"R|ACTIVO\nSIMA_OK");
  assertFalse(estado.completo());assertFalse(estado.configurado());assertTrue(estado.lanzadorActivo());
 }
 @Test public void perfilAfisPorDefectoExigeLosTresRunner() throws Exception {
  var eq=SimaConfig.cargar("[MONITOR:SVR-APL-012]\nip=192.0.2.12\nprocesos=\n").getFirst();
  assertEquals(SimaConfig.PROCESOS_AFIS,eq.procesos());
  String salida="R|ACTIVO\nP|"+b64("python.exe")+"\n";
  assertFalse(ServiciosSimaService.evaluar(eq,salida+"SIMA_OK").completo());
  salida+="P|"+b64("APPRunnera.exe")+"\nP|"+b64("APPRunnera.exe")+"\nP|"+b64("DisRunnera.exe")+"\n";
  var incompleto=ServiciosSimaService.evaluar(eq,salida+"SIMA_OK");
  assertFalse(incompleto.completo());assertTrue(incompleto.detalle().contains("ManagerRunnera.exe"));
  var completo=ServiciosSimaService.evaluar(eq,salida+"P|"+b64("ManagerRunnera.exe")+"\nSIMA_OK");
  assertTrue(completo.completo());assertTrue(completo.detalle().contains("apprunnera.exe=2"));
 }
 @Test public void perfilPersonalizadoNoExigeRunnerAdicionales() throws Exception {
  var eq=SimaConfig.cargar("[UT:A]\nip=192.0.2.1\nprocesos=DisRunnera.exe\n").getFirst();
  assertTrue(ServiciosSimaService.evaluar(eq,"P|"+b64("DisRunnera.exe")+"\nSIMA_OK").completo());
 }
 @Test public void perfilUt34NoExigeDistribuidorNiAceptaSoloPython() throws Exception {
  var eq=SimaConfig.cargar("[UT:AFIS-AWKS-34]\nip=10.120.13.86\nprocesos=APPRunnera.exe,ManagerRunnera.exe\n").getFirst();
  String python="P|"+b64("python.exe")+"\n";
  assertFalse(ServiciosSimaService.evaluar(eq,python+"SIMA_OK").completo());
  assertTrue(ServiciosSimaService.evaluar(eq,python+"P|"+b64("APPRunnera.exe")+"\nP|"+b64("ManagerRunnera.exe")+"\nSIMA_OK").completo());
 }
 @Test public void respuestaIncompletaOInvalidaNoSeAcepta() {
  for(String s:List.of("","ERROR","SIMA_OK\nP|YQ==","P|***\nSIMA_OK"))assertThrows(IOException.class,()->ServiciosSimaService.evaluar(equipo(),s));
 }
 @Test public void pythonIlegibleBloqueaInicioDuplicado() throws Exception {
  assertTrue(ServiciosSimaService.evaluar(equipo(),"R|DESCONOCIDO\nSIMA_OK").lanzadorActivo());
 }
 @Test public void cargaReglasPorEquipo() throws Exception {
  var equipos=SimaConfig.cargar("[UT:A]\nip=192.0.2.1\nprocesos=APPRunnera.exe, DisRunnera.exe\nservicios=ServicioPrueba\n[MONITOR:B]\nip=192.0.2.2\n");
  assertEquals(2,equipos.getFirst().procesos().size());assertEquals(1,equipos.getFirst().servicios().size());
  assertEquals(SimaConfig.PROCESOS_AFIS,equipos.getLast().procesos());
  assertThrows(IOException.class,()->SimaConfig.listaEsperada("cmd.exe;malicioso",true));
  assertThrows(IOException.class,()->SimaConfig.listaEsperada("python.exe,PYTHON.EXE",true));
 }
 @Test public void consultaConWmiSimuladoDetectaLanzadorYServicios() throws Exception {
  String mock="""
   function Get-WmiObject {
    param([string]$Class,[string]$ComputerName)
    if($Class -eq 'Win32_Process'){
     [pscustomobject]@{Name='python.exe';CommandLine='python.exe "C:\\Archivos de programa\\Sagem\\mu\\Lib\\app_runner.py" Distribution.xml'}
     [pscustomobject]@{Name='APPRunnera.exe';CommandLine='APPRunnera.exe'}
     [pscustomobject]@{Name='DisRunnera.exe';CommandLine='DisRunnera.exe'}
    }else{[pscustomobject]@{Name='ServicioPrueba';State='Running'}}
   }
   """;
  String ps=mock+ServiciosSimaService.scriptConsulta("192.0.2.1",true);
  var r=ComandoWindows.ejecutar(List.of(ComandoWindows.sistema("WindowsPowerShell\\v1.0\\powershell.exe"),"-NoProfile","-NonInteractive","-EncodedCommand",Base64.getEncoder().encodeToString(ps.getBytes(StandardCharsets.UTF_16LE))),10,StandardCharsets.UTF_8);
  assertEquals(r.salida(),0,r.codigo());var estado=ServiciosSimaService.evaluar(equipo(),r.salida());
  assertTrue(estado.completo());assertTrue(estado.lanzadorActivo());
 }
 @Test public void scriptPowerShellValidoSinConsultasRemotas() throws Exception {
  String script=ServiciosSimaService.scriptConsulta("192.0.2.1",true);
  assertTrue(script.contains("\\s"));
  String encoded=Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_8));
  String validar="$s=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('"+encoded+"')); $t=$null; $e=$null; [void][System.Management.Automation.Language.Parser]::ParseInput($s,[ref]$t,[ref]$e); if($e.Count){$e | Out-String;exit 1}; 'OK'";
  var r=ComandoWindows.ejecutar(List.of(ComandoWindows.sistema("WindowsPowerShell\\v1.0\\powershell.exe"),"-NoProfile","-NonInteractive","-EncodedCommand",Base64.getEncoder().encodeToString(validar.getBytes(StandardCharsets.UTF_16LE))),10);
  assertEquals(r.salida(),0,r.codigo());
 }
}
