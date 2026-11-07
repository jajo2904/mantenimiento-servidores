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
  try { assertThrows(IOException.class, () -> RecrearPortalesService.eliminarCarpeta(bloqueado,log)); }
  finally { Files.setAttribute(archivo,"dos:readonly",false); }
 }
 @Test public void usuarioDominioSeguro() { assertEquals("DOMINIO_usuario",RecrearPortalesService.nombreSeguro("DOMINIO\\usuario")); }
}
