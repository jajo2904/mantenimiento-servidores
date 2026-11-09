# Configuracion externa y alcance

Archivo activo: `C:\MantenimientoServidores\portales.txt`.
Los portales existentes se muestran en SIMA automaticamente; `monitor=false`
los excluye del monitoreo sin deshabilitar su mantenimiento.
Para agregar una UT, agregue `[UT:NOMBRE]`, `ip=IP` y `monitor=true`.
Se puede editar desde SIMA > Configurar equipos. Al guardar se valida el contenido
y se reemplaza el archivo activo mediante un temporal; no se generan archivos .bak.
Los cambios se aplican al inicio del siguiente recorrido. El ciclo tiene un minimo
de 30 segundos y puede tardar mas cuando varios equipos no responden.
El inventario conserva 79 equipos activos (64 UT) y tres UT deshabilitadas sin IP.
Las selecciones de acciones rapidas se desmarcan tras dos minutos de inactividad;
el plazo se reinicia al cambiar la seleccion y al terminar una accion.

SIMA: rojo sin ping, amarillo si faltan procesos/servicios o reglas, gris si la consulta esta pendiente o falla.
Verde confirma presencia de todos los procesos y estado Running de los servicios configurados; no es una prueba funcional del portal.
Las reglas `procesos=` y `servicios=` se definen por equipo, separadas por comas. Se entregan vacias hasta confirmar `app_runner.py` y `Distribution.xml`.
Se consulta WMI en segundo plano con un maximo de seis consultas simultaneas, cada una limitada a ocho segundos.
La pausa visual es de 108 ms y la espera del ping es de 1080 ms; el recorrido real depende de la red.

La limpieza ahora requiere consultar procesos remotos mediante Windows PowerShell
5.1 / WMI (solo lectura). Si falla la consulta o Tomcat sigue activo, no borra.
Todavia no se ejecutan los BAT de apagado: faltan setenv, procedimiento remoto
y verificacion del proceso correspondiente. Detener Tomcat sigue siendo manual.
Una instancia Java sin linea de comandos legible bloquea la limpieza.
La identificacion actual usa la ruta en la linea de comandos: debe validarse con
los procesos reales antes de produccion; no garantiza detectar rutas alternativas.

Se realizaron pruebas locales de SIMA; la ejecucion remota requiere validacion en el entorno de trabajo. No se genero un paquete nuevo.
El resultado completado corresponde a limpieza, no a reinicio ni disponibilidad web.

El boton de inicio esta habilitado para todos los equipos activos de SIMA: UT, servidores de aplicaciones y otros equipos. Ejecuta remotamente el mismo `inicioScript` de la seccion `[SIMA]`, mediante `inicioPython`. Se conserva la comprobacion de configuracion vigente y la exclusion de operaciones simultaneas sobre el mismo destino.
