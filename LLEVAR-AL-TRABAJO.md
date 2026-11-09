# Llevar al trabajo

1. Descomprima el ZIP completo en una carpeta local permitida.
2. Copie `Configuracion/portales.txt` a `C:\MantenimientoServidores\portales.txt`. Conserve cambios de IP propios del trabajo si difieren de esta copia.
3. Abra `MantenimientoServidores/MantenimientoServidores.exe`. Mantenga juntas las carpetas `app` y `runtime`.

Java 21 y JavaFX nativo estan incluidos. No se requiere instalar Java en el PC operador.

## SIMA
- Palido: consultando ping. Rojo: sin respuesta. Gris: consulta de servicios pendiente o fallida.
- Amarillo: responde, pero faltan procesos/servicios esperados o las reglas estan vacias.
- Verde: todos los nombres configurados se detectaron activos. No garantiza que la aplicacion o el portal funcionen correctamente.
- Configure `procesos=` y/o `servicios=` por equipo en `portales.txt`. Use nombres internos de servicios Windows. No se asume que todos los equipos usan los mismos Runner.
- Los logs de sesion muestran procesos observados y detalles de errores en `C:\MantenimientoServidores\Logs` (alternativa: `%LOCALAPPDATA%\MantenimientoServidores\Logs`).
- El boton solicita ejecutar el script compartido en el destino y evita repetirlo si detecta el lanzador activo o todos los procesos esperados. No se ejecuta nada automaticamente al monitorear.

## Dependencias del entorno
Las UT y servidores necesitan su Python 2, acceso a `\\central\facil`, permisos de ejecucion y consulta WMI/RPC. El script original sincroniza archivos y crea una tarea programada; no se incluye ni se modifica en este paquete. Su contenido contiene una credencial incrustada que debe gestionar el administrador.

Faltan `app_runner.py` y `Distribution.xml` para identificar los procesos exactos por equipo. Las reglas se entregan vacias para evitar falsos verdes. Las acciones Bajar servicios/Bajar fabrica siguen pendientes de definir. No se probo contra produccion.
