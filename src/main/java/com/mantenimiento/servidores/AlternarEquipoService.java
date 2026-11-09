package com.mantenimiento.servidores;
import java.io.IOException;
public final class AlternarEquipoService {
 @FunctionalInterface interface Confirmacion {boolean confirmar() throws Exception;}
 interface Operaciones {
  ServiciosSimaService.Estado consultar(SimaConfig.Equipo equipo) throws IOException;
  String iniciar(SimaConfig.Equipo equipo) throws IOException;
  String detener(SimaConfig.Equipo equipo) throws IOException;
 }
 static String ejecutar(SimaConfig.Equipo equipo,Confirmacion confirmacion) throws Exception {
  return ejecutar(equipo,confirmacion,new Operaciones(){
   public ServiciosSimaService.Estado consultar(SimaConfig.Equipo e)throws IOException{return ServiciosSimaService.consultar(e);}
   public String iniciar(SimaConfig.Equipo e)throws IOException{return InicioUtService.iniciarReservado(e);}
   public String detener(SimaConfig.Equipo e)throws IOException{return ParadaAfisService.detener(e);}
  });
 }
 static String ejecutar(SimaConfig.Equipo equipo,Confirmacion confirmacion,Operaciones operaciones) throws Exception {
  if(!InicioUtService.reservar(equipo.ip()))return "Ya hay una solicitud en curso para este equipo.";
  try {
   var estado=operaciones.consultar(equipo);
   if(estado.activosDetectados()){
    if(!confirmacion.confirmar())return "Parada cancelada. No se ejecuto ninguna accion.";
    return operaciones.detener(equipo);
   }
   return operaciones.iniciar(equipo);
  }finally{InicioUtService.liberar(equipo.ip());}
 }
}
