package com.mantenimiento.servidores;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.xml.parsers.DocumentBuilderFactory;

public class TextosTest {
 @Test public void recursosSinTextoCorrupto() throws Exception {
  for(String archivo : new String[]{"Menu.fxml","Pendiente.fxml","PanelPrincipal.fxml","PanelError.fxml","login.fxml"}) {
   try(var in=getClass().getResourceAsStream("/com/mantenimiento/servidores/"+archivo)) {
    assertNotNull(in);
    String texto=new String(in.readAllBytes(),StandardCharsets.UTF_8);
    assertFalse(archivo,texto.contains("\u00c3"));
    assertFalse(archivo,texto.contains("\ufffd"));
    assertFalse(archivo,texto.contains("\u00c2"));
   }
  }
 }
 @Test public void menuTieneTildesCorrectas() throws Exception {
  try(var in=getClass().getResourceAsStream("/com/mantenimiento/servidores/Menu.fxml")) {
   var doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
   var botones=doc.getElementsByTagName("Button");
   var textos=new java.util.ArrayList<String>();
   for(int i=0;i<botones.getLength();i++) textos.add(((org.w3c.dom.Element)botones.item(i)).getAttribute("text"));
   assertTrue(textos.contains("SIMA")); assertTrue(textos.contains("RECREAR PORTALES")); assertTrue(textos.contains("FUNCION 3")); assertTrue(textos.contains("Cerrar sesi\u00f3n"));
  }
 }
}
