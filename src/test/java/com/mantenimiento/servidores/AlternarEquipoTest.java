package com.mantenimiento.servidores;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
class OperacionesSimuladas implements AlternarEquipoService.Operaciones {
 boolean activo;int subidas,bajadas;
 OperacionesSimuladas(boolean activo){this.activo=activo;}
 public ServiciosSimaService.Estado consultar(SimaConfig.Equipo e){return new ServiciosSimaService.Estado(true,activo,false,activo,"");}
 public String iniciar(SimaConfig.Equipo e){subidas++;return "inicio";}
 public String detener(SimaConfig.Equipo e){bajadas++;return "parada";}
}
public class AlternarEquipoTest {
 private final SimaConfig.Equipo equipo=new SimaConfig.Equipo("PRUEBA","192.0.2.240","G",0,true);
 @Test public void abajoSubeSinPreguntar()throws Exception{
  var op=new OperacionesSimuladas(false);
  assertEquals("inicio",AlternarEquipoService.ejecutar(equipo,()->{fail("No pedir confirmacion para subir");return false;},op));
  assertEquals(1,op.subidas);assertEquals(0,op.bajadas);
 }
 @Test public void arribaSoloBajaSiConfirma()throws Exception{
  var op=new OperacionesSimuladas(true);
  assertEquals("parada",AlternarEquipoService.ejecutar(equipo,()->true,op));assertEquals(1,op.bajadas);assertEquals(0,op.subidas);
 }
 @Test public void cancelarNoEjecutaYLiberaEquipo()throws Exception{
  var op=new OperacionesSimuladas(true);AlternarEquipoService.ejecutar(equipo,()->false,op);
  assertEquals(0,op.subidas+op.bajadas);assertTrue(InicioUtService.reservar(equipo.ip()));InicioUtService.liberar(equipo.ip());
 }
 @Test public void falloNoCausaInicioAlternativo()throws Exception{
  var op=new OperacionesSimuladas(true){public String detener(SimaConfig.Equipo e){throw new IllegalStateException("Fallo");}};
  assertThrows(IllegalStateException.class,()->AlternarEquipoService.ejecutar(equipo,()->true,op));assertEquals(0,op.subidas);
  assertTrue(InicioUtService.reservar(equipo.ip()));InicioUtService.liberar(equipo.ip());
 }
}
