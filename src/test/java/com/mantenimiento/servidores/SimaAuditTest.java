package com.mantenimiento.servidores;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SimaAuditTest {
 private static final String CONFIG="\uFEFF[UT:AFIS-01]\nip=192.0.2.1\ngrupo=Prueba\norden=1\n";
 @Test public void configuracionConEspaciosYTipoUt() throws Exception {
  var equipos=SimaConfig.cargar(CONFIG.replace("ip=","ip = "));
  assertEquals(1,equipos.size());assertTrue(equipos.getFirst().ut());
  assertEquals("192.0.2.1",equipos.getFirst().ip());
  assertFalse(SimaConfig.cargar(CONFIG.replace("UT:","MONITOR:")).getFirst().ut());
 }
 @Test public void rechazaDestinoManipulado() {
  for(String host:List.of("-n", "host & calc", "host;cmd", "256.1.2.3", "1.2.3", "host..local"))
   assertThrows(host,IOException.class,()->SimaConfig.validarDestino(host));
 }
 @Test public void rechazaDuplicadosYGrupoVacio() {
  for(String texto:List.of(CONFIG+"ip=192.0.2.2\n",CONFIG+"[ut:afis-01]\n",CONFIG.replace("grupo=Prueba","grupo=")))
   assertThrows(IOException.class,()->SimaConfig.cargar(texto));
 }
 @Test public void noEjecutaConConfiguracionAntigua() throws Exception {
  var equipo=SimaConfig.cargar(CONFIG).getFirst();
  SimaConfig.verificarEquipo(CONFIG,equipo);
  for(String texto:List.of(CONFIG.replace("192.0.2.1","192.0.2.2"), CONFIG+"monitor=false\n", CONFIG.replace("UT:","MONITOR:")))
   assertThrows(IOException.class,()->SimaConfig.verificarEquipo(texto,equipo));
 }
 @Test public void validaPythonYScript() throws Exception {
  assertEquals("python.exe",InicioUtService.configuracionInicio(CONFIG).python());
  assertEquals("C:\\Scripts\\inicio.py",InicioUtService.configuracionInicio("[SIMA]\ninicioScript=C:\\Scripts\\inicio.py").script());
  for(String linea:List.of("inicioPython=cmd.exe","inicioPython=python.exe -c algo", "inicioScript=relativo.py", "inicioScript=C:\\inicio.bat", "inicioScript=C:\\a\".py"))
   assertThrows(IOException.class,()->InicioUtService.configuracionInicio("[SIMA]\n"+linea));
 }
 private List<String> comando(String modo){
  return List.of(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",System.getProperty("java.class.path"),getClass().getName(),modo);
 }
 @Test public void capturaSalidaYCodigo() throws Exception {
  var resultado=ComandoWindows.ejecutar(comando("fallo"),10);
  assertEquals(7,resultado.codigo());assertTrue(resultado.salida().contains("error controlado"));
 }
 @Test public void limitaSalidaSinBloquear() throws Exception {
  var resultado=ComandoWindows.ejecutar(comando("salida"),10);
  assertEquals(0,resultado.codigo());assertTrue(resultado.salida().contains("Salida truncada"));
  assertTrue(resultado.salida().length()<ComandoWindows.LIMITE_SALIDA+100);
 }
 @Test public void cortaComandoPorTiempo() {
  long inicio=System.nanoTime();
  assertThrows(IOException.class,()->ComandoWindows.ejecutar(comando("espera"),1));
  assertTrue((System.nanoTime()-inicio)/1_000_000<5000);
 }
 @Test public void respetaInterrupcion() throws Exception {
  Thread.currentThread().interrupt();
  try {assertThrows(IOException.class,()->ComandoWindows.ejecutar(comando("espera"),10));assertTrue(Thread.currentThread().isInterrupted());}
  finally {Thread.interrupted();}
 }
 @Test public void reservaPorEquipoEvitaOperacionesSolapadas() {
  assertTrue(InicioUtService.reservar("UT-PRUEBA"));
  try {
   assertFalse(InicioUtService.reservar("ut-prueba"));
   assertTrue(InicioUtService.reservar("UT-OTRA"));
   InicioUtService.liberar("UT-OTRA");
  } finally {InicioUtService.liberar("ut-prueba");}
  assertTrue(InicioUtService.reservar("UT-PRUEBA"));
  InicioUtService.liberar("UT-PRUEBA");
 }
 public static void main(String[] args) throws Exception {
  switch(args[0]) {
   case "fallo" -> {System.err.println("error controlado");System.exit(7);}
   case "salida" -> {for(int i=0;i<2048;i++)System.out.print("x".repeat(1024));}
   case "espera" -> Thread.sleep(10000);
   default -> throw new IllegalArgumentException();
  }
 }
}
