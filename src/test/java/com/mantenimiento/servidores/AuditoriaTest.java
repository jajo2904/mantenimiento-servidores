package com.mantenimiento.servidores;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.io.IOException;

public class AuditoriaTest {
 @Rule public TemporaryFolder temporal = new TemporaryFolder();
 @Test public void rutasYUnidades() throws Exception {
  assertEquals("\\\\192.0.2.1\\C$\\Cots\\tomcat", RecrearPortalesService.convertirRutaRemota("192.0.2.1", "C:\\Cots\\tomcat"));
  assertEquals("\\\\servidor\\D$\\Tomcat", RecrearPortalesService.convertirRutaRemota("servidor", "D:\\Tomcat"));
  for(String ruta : new String[]{"C:\\", "C:\\Cots\\..\\Windows", "relativa", "\\\\otro\\C$\\dir", "C:\\dir:stream"})
   assertThrows(IOException.class, () -> RecrearPortalesService.convertirRutaRemota("192.0.2.1", ruta));
 }
 @Test public void configuracionBOMYDuplicados() throws Exception {
  Path archivo=temporal.newFile().toPath();
  Files.writeString(archivo,"\uFEFF[SVR-APL-001]\nip= 192.0.2.1 \nI1= C:\\Tomcat \n");
  var config=new ConfiguracionPortalesService().cargarPortales(archivo).get("SVR-APL-001");
  assertEquals("192.0.2.1",config.getIp()); assertEquals("C:\\Tomcat",config.getInstancias().get(0));
  Files.writeString(archivo,"[uno]\n[uno]\n");
  assertThrows(IOException.class, () -> new ConfiguracionPortalesService().cargarPortales(archivo));
 }
 @Test public void borradoLocalAcotadoYErrorPropagado() throws Exception {
  Path base=temporal.newFolder().toPath(), log=base.resolve("registro.txt"), objetivo=Files.createDirectories(base.resolve("portal/sub"));
  Files.writeString(objetivo.resolve("archivo"),"prueba");
  RecrearPortalesService.eliminarCarpeta(base.resolve("portal"),log);
  assertFalse(Files.exists(base.resolve("portal"))); assertTrue(Files.exists(log));
  RecrearPortalesService.eliminarCarpeta(base.resolve("ausente"),log);
  Path bloqueado=Files.createDirectories(base.resolve("bloqueado"));
  Path archivo=Files.writeString(bloqueado.resolve("solo-lectura"),"prueba");
  Files.setAttribute(archivo,"dos:readonly",true);
  RecrearPortalesService.eliminarCarpeta(bloqueado,log);
  assertFalse(Files.exists(bloqueado));
  assertTrue(Files.readString(log).contains("BORRADO REINTENTO correcto"));
 }
 @Test public void continuaConOtrasCarpetasEInstanciasSiUnaFalla() throws Exception {
  Path base=temporal.newFolder().toPath(), log=base.resolve("log.txt");
  Path i1=Files.createDirectories(base.resolve("I1")),i2=Files.createDirectories(base.resolve("I2"));
  Files.writeString(i1.resolve("webapps"),"no es directorio");
  Files.createDirectories(i1.resolve("work"));Files.createDirectories(i2.resolve("webapps/portal"));
  var resultados=new java.util.LinkedHashMap<Path,String>();var intentadas=new java.util.ArrayList<Path>();
  assertThrows(IOException.class,()->RecrearPortalesService.limpiarInstancias(java.util.List.of(i1,i2),log,resultados,intentadas::add));
  assertEquals(10,intentadas.size());assertEquals(10,resultados.size());
  assertFalse(Files.exists(i1.resolve("work")));assertFalse(Files.exists(i2.resolve("webapps/portal")));
  assertTrue(resultados.get(i1.resolve("webapps/portal")).contains("FALLIDA"));
 }
 @Test public void limpiezaCompletaVerificaTodasLasCarpetas() throws Exception {
  Path raiz=temporal.newFolder().toPath(),log=raiz.resolve("registro.txt");
  Files.createDirectories(raiz.resolve("webapps/portal"));Files.createDirectories(raiz.resolve("work"));
  var resultados=new java.util.LinkedHashMap<Path,String>();
  RecrearPortalesService.limpiarInstancias(java.util.List.of(raiz),log,resultados,p->{});
  assertEquals(5,resultados.size());assertTrue(resultados.values().stream().allMatch("AUSENCIA VERIFICADA"::equals));
 }
 @Test public void usuarioDominioSeguro() { assertEquals("DOMINIO_usuario",RecrearPortalesService.nombreSeguro("DOMINIO\\usuario")); }
}
