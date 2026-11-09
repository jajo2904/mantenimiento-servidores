package com.mantenimiento.servidores;
final class EstadoSima {
 enum Resultado { PENDIENTE, CORRECTO, SIN_PING, SIN_SERVICIOS, ERROR }
 private Resultado resultado=Resultado.PENDIENTE;
 synchronized Resultado pendiente(){return resultado;}
 synchronized Resultado completar(boolean correcto){return resultado=correcto?Resultado.CORRECTO:Resultado.SIN_SERVICIOS;}
 synchronized Resultado sinPing(){return resultado=Resultado.SIN_PING;}
 synchronized Resultado error(){return resultado=Resultado.ERROR;}
 synchronized Resultado resultado(){return resultado;}
}
