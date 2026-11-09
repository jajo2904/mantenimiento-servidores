package com.mantenimiento.servidores;
import org.junit.Test;
import static org.junit.Assert.*;
public class EstadoSimaTest {
 @Test public void estadosSeparanPingServiciosYErrores(){
  var e=new EstadoSima();assertEquals(EstadoSima.Resultado.PENDIENTE,e.pendiente());
  assertEquals(EstadoSima.Resultado.SIN_PING,e.sinPing());
  assertEquals(EstadoSima.Resultado.SIN_SERVICIOS,e.completar(false));
  assertEquals(EstadoSima.Resultado.CORRECTO,e.completar(true));
  assertEquals(EstadoSima.Resultado.CORRECTO,e.pendiente());
  assertEquals(EstadoSima.Resultado.ERROR,e.error());
  assertEquals(EstadoSima.Resultado.ERROR,e.pendiente());
 }
}
