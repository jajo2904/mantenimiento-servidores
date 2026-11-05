# Aplicacion portable para Windows x64

Copiar la carpeta **MantenimientoServidores completa** al equipo de destino y abrir
**MantenimientoServidores.exe**. Conservar `app` y `runtime` junto al ejecutable.
Si se entrega como ZIP, extraerlo completo antes de abrir la aplicacion.
No requiere instalar Java, Maven, JavaFX ni ejecutar un BAT o PowerShell en destino.

El runtime incluye Temurin **21.0.13+9** y JavaFX **21.0.8** (base, graphics,
controls y fxml). Las DLL de JavaFX, incluida `prism_sw.dll`, estan en
`runtime/bin/javafx`; no se distribuyen JAR de JavaFX que necesiten extraerlas a
`.openjfx/cache`. Tambien se incluyen los modulos Java SE, proveedores de
criptografia, caracteres y servicios enlazados, y las licencias del runtime.
Las bibliotecas propias de Windows siguen siendo responsabilidad del sistema.

Esto resuelve la dependencia del Java instalado y la extraccion de DLL de JavaFX.
Una politica corporativa que bloquee este EXE o sus DLL puede requerir autorizacion
de TI. El paquete no cambia politicas de Windows ni permisos de los servidores.

## Reconstruir en el PC de desarrollo

Requisitos: Windows x64, PowerShell, Maven y acceso a Internet la primera vez.
Desde la carpeta del proyecto: `powershell -File .\empaquetar.ps1`.
El script obtiene JDK 21 y JavaFX JMODS con versiones y SHA256 fijados, compila
con JDK 21, ejecuta las pruebas de Maven, crea el runtime con jlink y genera
la aplicacion con jpackage. No utiliza el Java 25 del equipo.
Las herramientas quedan en `.packaging-tools` y cada entrega en `dist/fecha-hora`.
No se cambia la logica de negocio. No se necesita instalar las herramientas en destino.

El proceso tambien verifica el EXE con Java fuera del PATH, JAVA_HOME inexistente,
el directorio `.openjfx` ocupado por un archivo y una cache explicita inutilizable.
Prueba el renderizado normal y por software; comprueba que la JVM y las DLL de
JavaFX se cargan desde el runtime empaquetado. Guarda `verificacion.json` y crea
un ZIP para trasladar la carpeta completa. Esta prueba abre la pantalla de acceso;
no ejecuta operaciones de mantenimiento ni valida los permisos del PC de trabajo.
Para repetir solo esta prueba: `powershell -File .\verificar-paquete.ps1 -AppDirectory RUTA_DE_LA_APP`.

Documentacion: https://openjfx.io/openjfx-docs/

