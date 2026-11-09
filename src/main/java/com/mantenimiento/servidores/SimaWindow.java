package com.mantenimiento.servidores;

import javafx.application.Platform;

import javafx.scene.Scene;

import javafx.scene.control.*;

import javafx.scene.layout.*;

import javafx.geometry.Insets;

import javafx.geometry.Pos;

import javafx.geometry.Orientation;

import javafx.stage.Stage;

import java.nio.file.Files;

import java.util.*;

import java.util.concurrent.*;

public class SimaWindow {

 private final Stage stage=new Stage();

 private final FlowPane contenido=new FlowPane(Orientation.VERTICAL,0,0);

 private final Label estado=new Label();

 private final Map<SimaConfig.Equipo,HBox> tarjetas=new LinkedHashMap<>();

 private List<SimaConfig.Equipo> inventario=List.of();

 private final ScheduledExecutorService reloj=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"sima-secuencia");t.setDaemon(true);return t;});

 private volatile boolean cerrada;

 private final ThreadPoolExecutor consultasServicios=new ThreadPoolExecutor(6,6,30,TimeUnit.SECONDS,

   new ArrayBlockingQueue<>(128),r->{Thread t=new Thread(r,"sima-servicios");t.setDaemon(true);return t;});

 private final Set<SimaConfig.Equipo> pendientesServicios=ConcurrentHashMap.newKeySet();

 private final Map<SimaConfig.Equipo,EstadoSima> estadosSima=new ConcurrentHashMap<>();

 private final Map<SimaConfig.Equipo,Boolean> equiposEnLinea=new ConcurrentHashMap<>();

 private final Map<SimaConfig.Equipo,String> ultimoServicio=new ConcurrentHashMap<>();

 private final TreeView<String> destinos=new TreeView<>();

 private final Map<SimaConfig.Equipo,CheckBoxTreeItem<String>> opciones=new LinkedHashMap<>();

 private static final String ROJO="#DCA6A4", AMARILLO="#E5D19A", VERDE="#A9C9B3", GRIS="#BDC5CF";

 private final TextArea resultadoAcciones=new TextArea();

 private boolean accionEnCurso;

 private final Set<SimaConfig.Equipo> equiposEnOperacion=ConcurrentHashMap.newKeySet();

 private final Map<String,String> erroresPing=new HashMap<>();

 private final Map<String,EstadoInicio> inicios=new HashMap<>();

 private static class EstadoInicio {

  final javafx.beans.property.BooleanProperty activo=new javafx.beans.property.SimpleBooleanProperty();

  final javafx.beans.property.BooleanProperty indicador=new javafx.beans.property.SimpleBooleanProperty();

  final javafx.animation.PauseTransition limite=new javafx.animation.PauseTransition(javafx.util.Duration.minutes(2));

  EstadoInicio(){limite.setOnFinished(e->indicador.set(false));}

 }

 private boolean hayInicios(){return inicios.values().stream().anyMatch(i->i.activo.get());}

 private boolean limpiandoSeleccion;

 private final javafx.animation.PauseTransition limpiarSeleccion=new javafx.animation.PauseTransition(javafx.util.Duration.minutes(2));

 private void actividadSeleccion(){

  limpiarSeleccion.stop();

  if(!limpiandoSeleccion&&!accionEnCurso&&!hayInicios()&&!seleccionados().isEmpty())limpiarSeleccion.playFromStart();

 }

 private record MensajeTemporal(long vence,String texto) {}
 private final Map<String,MensajeTemporal> mensajesTemporales=new LinkedHashMap<>();
 private boolean renderPendiente;
 private final Set<String> mensajesActivos=new HashSet<>();
 private final javafx.animation.Timeline limpiarResultados=new javafx.animation.Timeline(
  new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1),e->retirarMensajesVencidos()));
 private void mensaje(String texto){
  if(cerrada)return;
  long vence=System.nanoTime()+TimeUnit.MINUTES.toNanos(2);
  for(String linea:texto.split("\\R")){
   if(linea.isBlank()||linea.startsWith("Log:"))continue;
   int separador=linea.indexOf(':');
   String clave=separador>0?linea.substring(0,separador):"__resumen";
   var anterior=mensajesTemporales.get(clave);
   if(anterior==null||!anterior.texto().equals(linea))mensajesTemporales.put(clave,new MensajeTemporal(mensajesActivos.contains(clave)||((accionEnCurso||hayInicios())&&(clave.equals("__resumen")||clave.equals("Fabrica")))?Long.MAX_VALUE:vence,linea));
  }
  while(mensajesTemporales.size()>256)mensajesTemporales.remove(mensajesTemporales.keySet().iterator().next());
  programarMensajes();
 }
 private void limpiarMensajes(){mensajesTemporales.clear();programarMensajes();}
 private void prepararMensajes(List<SimaConfig.Equipo> equipos){
  equipos.forEach(eq->{mensajesActivos.add(eq.nombre());mensaje(eq.nombre()+": En espera");});
 }
 private void finalizarMensajes(List<SimaConfig.Equipo> equipos){
  long vence=System.nanoTime()+TimeUnit.MINUTES.toNanos(2);
  for(var eq:equipos){
   mensajesActivos.remove(eq.nombre());
   mensajesTemporales.computeIfPresent(eq.nombre(),(k,m)->new MensajeTemporal(vence,m.texto()));
  }
  if(!accionEnCurso&&!hayInicios())mensajesTemporales.replaceAll((k,m)->m.vence()==Long.MAX_VALUE?new MensajeTemporal(vence,m.texto()):m);
 }
 private void retirarMensajesVencidos(){
  long ahora=System.nanoTime();
  if(mensajesTemporales.values().removeIf(m->m.vence()<=ahora))programarMensajes();
 }
 private void programarMensajes(){
  if(renderPendiente||cerrada)return;
  renderPendiente=true;
  Platform.runLater(()->{
   renderPendiente=false;if(cerrada)return;
   String texto=String.join("\n",mensajesTemporales.values().stream().map(MensajeTemporal::texto).toList());
   if(!texto.equals(resultadoAcciones.getText()))resultadoAcciones.setText(texto);
  });
 }

 public SimaWindow(){

  consultasServicios.allowCoreThreadTimeOut(true);

  limpiarSeleccion.setOnFinished(e->{

   if(accionEnCurso||hayInicios())return;

   limpiandoSeleccion=true;

   try{opciones.values().forEach(nodo->nodo.setSelected(false));}

   finally{limpiandoSeleccion=false;limpiarSeleccion.stop();}

  });

 }

 public void mostrar(){

  limpiarResultados.setCycleCount(javafx.animation.Animation.INDEFINITE);

  limpiarResultados.play();

  Button configurar=new Button("Configurar equipos");configurar.setOnAction(e->editar());

  Label titulo=new Label("SIMA");titulo.getStyleClass().add("title");

  HBox cabecera=new HBox(14,titulo,configurar,estado);cabecera.setAlignment(Pos.CENTER_LEFT);cabecera.getStyleClass().add("sima-cabecera");

  ScrollPane scroll=new ScrollPane(contenido);scroll.getStyleClass().add("sima-tablero");scroll.setFitToHeight(true);scroll.setPannable(false);

  scroll.viewportBoundsProperty().addListener((o,a,b)->contenido.setPrefWrapLength(Math.max(96,b.getHeight()-2)));

  HBox cuerpo=new HBox(8,scroll,acciones());HBox.setHgrow(scroll,Priority.ALWAYS);scroll.setMinWidth(200);

  VBox root=new VBox(4,cabecera,cuerpo);root.setPadding(new Insets(6));VBox.setVgrow(cuerpo,Priority.ALWAYS);

  Scene scene=new Scene(root,1100,700);scene.getStylesheets().add(getClass().getResource("panel.css").toExternalForm());scene.getStylesheets().add(getClass().getResource("sima.css").toExternalForm());

  stage.setScene(scene);stage.setTitle("SIMA");stage.setMaximized(false);restaurarTamano();

  stage.setOnCloseRequest(e->{if(accionEnCurso||hayInicios()){e.consume();mensaje("Espere a que termine la accion.\n");}});

  stage.setOnHidden(e->{guardarTamano();cerrada=true;limpiarSeleccion.stop();limpiarResultados.stop();inicios.values().forEach(i->i.limite.stop());reloj.shutdownNow();consultasServicios.shutdownNow();});stage.show();

  reloj.execute(this::actualizar);

 }

 private java.util.prefs.Preferences preferenciasVentana(){
  return java.util.prefs.Preferences.userRoot().node("com/mantenimiento/servidores/sima");
 }
 private void restaurarTamano(){
  double ancho=1100,alto=825;
  try{
   var preferencias=preferenciasVentana();ancho=preferencias.getDouble("ancho",ancho);alto=preferencias.getDouble("alto",alto);
   if(!preferencias.getBoolean("ajusteAltura15",false)){
    if(preferencias.get("alto",null)!=null&&Double.isFinite(alto)&&alto>=300)alto+=15;
    preferencias.putDouble("alto",alto);preferencias.putBoolean("ajusteAltura15",true);
   }
  }
  catch(RuntimeException ex){registrar("Tamano SIMA","Se usara el tamano predeterminado",ex);}
  var pantalla=javafx.stage.Screen.getPrimary().getVisualBounds();
  if(!Double.isFinite(ancho)||ancho<400)ancho=1100;
  if(!Double.isFinite(alto)||alto<300)alto=825;
  stage.setWidth(Math.min(ancho,pantalla.getWidth()));
  stage.setHeight(Math.min(alto,pantalla.getHeight()));
  stage.centerOnScreen();
 }
 private void guardarTamano(){
  if(stage.isMaximized()||stage.isIconified()||stage.isFullScreen())return;
  double ancho=stage.getWidth(),alto=stage.getHeight();
  if(!Double.isFinite(ancho)||!Double.isFinite(alto)||ancho<400||alto<300)return;
  try{var preferencias=preferenciasVentana();preferencias.putDouble("ancho",ancho);preferencias.putDouble("alto",alto);preferencias.flush();}
  catch(java.util.prefs.BackingStoreException|RuntimeException ex){registrar("Tamano SIMA","No se pudo guardar el tamano de la ventana",ex);}
 }
 private void construir(List<SimaConfig.Equipo> equipos){

  if(equipos.equals(inventario))return;

  inicios.entrySet().removeIf(e->!e.getValue().activo.get()&&equipos.stream().noneMatch(eq->eq.ip().equalsIgnoreCase(e.getKey())));

  reconstruirGrupos(equipos);

  inventario=List.copyOf(equipos);contenido.getChildren().clear();tarjetas.clear();String anterior=null;

  for(var eq:equipos){

   if(!eq.grupo().equals(anterior)){

    Label encabezado=new Label(eq.grupo());encabezado.setAlignment(Pos.CENTER_LEFT);encabezado.setMinSize(108,24);encabezado.setPrefSize(108,24);encabezado.setMaxSize(108,24);

    encabezado.setStyle("-fx-background-color: linear-gradient(to right, #456987, #345574); -fx-background-insets: 1 2; -fx-background-radius: 4; -fx-text-fill: white; -fx-font-size: 10; -fx-font-weight: bold; -fx-padding: 2 6;");

    encabezado.setTooltip(new Tooltip(eq.grupo()));contenido.getChildren().add(encabezado);anterior=eq.grupo();

   }

   Label nombre=new Label(eq.nombre()),ip=new Label(eq.ip());

   for(Label texto:List.of(nombre,ip)){

    texto.setMaxWidth(Double.MAX_VALUE);texto.setAlignment(Pos.CENTER_LEFT);

    texto.setStyle("-fx-font-size: 10; -fx-padding: 0;");

   }

   nombre.setStyle("-fx-font-size: 10; -fx-font-weight: bold; -fx-padding: 0;");

   ip.setStyle("-fx-font-size: 9; -fx-padding: 0;");

   ajustarNombre(nombre);

   nombre.setTooltip(new Tooltip(eq.grupo()+" / "+eq.nombre()+" / "+eq.ip()));

   Button subir=new Button();subir.setMinSize(18,18);subir.setPrefSize(18,18);subir.setMaxSize(18,18);subir.setAccessibleText("Subir o bajar AFIS en "+eq.nombre());

   subir.getStyleClass().add("sima-boton-equipo");

   HBox.setMargin(subir,new Insets(2,2,2,2));

   EstadoInicio inicio=inicios.computeIfAbsent(eq.ip().toLowerCase(Locale.ROOT),ipEquipo->new EstadoInicio());

   subir.disableProperty().bind(inicio.activo);

   ProgressIndicator circulo=new ProgressIndicator();

   circulo.setMinSize(12,12);circulo.setPrefSize(12,12);circulo.setMaxSize(12,12);

   circulo.visibleProperty().bind(inicio.indicador);circulo.setMouseTransparent(true);

   subir.setPickOnBounds(true);subir.setFocusTraversable(false);

   subir.setGraphic(circulo);

   subir.setOnAction(e->{

    if(accionEnCurso||inicio.activo.get())return;

    limpiarSeleccion.stop();inicio.activo.set(true);inicio.indicador.set(true);inicio.limite.playFromStart();

    prepararMensajes(List.of(eq));

    Thread.ofVirtual().start(()->{

     String detalle; Throwable causa=null;

     try{detalle=OperacionesEquipos.uno(()->{ui(()->mensaje(eq.nombre()+": Comprobando servicios"));return AlternarEquipoService.ejecutar(eq,()->confirmarParada(eq));});}catch(Exception ex){detalle="No se pudo completar la accion en "+eq.nombre();causa=ex;}

     String registro=registrar("Accion AFIS "+eq.nombre()+" / "+eq.ip(),detalle,causa);

     String resumen=causa!=null?"Accion fallida. Consulte el log.":detalle.contains("cancelada")?"Parada cancelada.":detalle.contains("ausencia verificada")?"Servicios detenidos.":detalle.contains("Solicitud aceptada")?"Inicio solicitado.":"Sin cambios. Consulte el log.";

     Platform.runLater(()->{

      inicio.limite.stop();inicio.indicador.set(false);inicio.activo.set(false);actividadSeleccion();

      if(!cerrada)mensaje(eq.nombre()+": "+resumen+"\n");
      finalizarMensajes(List.of(eq));

     });

    });

   });

   VBox datos=new VBox(0,nombre,ip);datos.setMinWidth(0);datos.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(datos,Priority.ALWAYS);

   HBox card=new HBox(2,datos,subir);card.setAlignment(Pos.CENTER_LEFT);card.setPadding(new Insets(4,5,4,5));

   card.setMinSize(108,48);card.setPrefSize(108,48);card.setMaxSize(108,48);

   Tooltip ayuda=new Tooltip("Grupo: "+eq.grupo()+". El boton consulta el estado: subir o bajar AFIS con confirmacion.");

   ayuda.setWrapText(true);ayuda.setMaxWidth(380);card.setUserData(ayuda);Tooltip.install(card,ayuda);

   card.getStyleClass().add("sima-tarjeta");

   color(card,AMARILLO);tarjetas.put(eq,card);contenido.getChildren().add(card);

  }

 }

 private boolean confirmarParada(SimaConfig.Equipo eq)throws Exception {

  CompletableFuture<Boolean> decision=new CompletableFuture<>();

  Platform.runLater(()->{

   try{

    if(cerrada){decision.complete(false);return;}

    decision.complete(confirmar("Bajar AFIS",List.of(eq),"Se detendran APPRunnera.exe, DisRunnera.exe, ManagerRunnera.exe y los lanzadores Python AFIS identificados. Puede interrumpir trabajos en curso. No se detendra Tomcat ni otros procesos Python."));

   }catch(Exception e){decision.completeExceptionally(e);}

  });

  return decision.get();

 }

 private void color(HBox card,String color){card.setStyle("-fx-background-insets: 2; -fx-background-radius: 5; -fx-border-insets: 2; -fx-border-color: rgba(20,40,60,0.18); -fx-border-width: 0.5; -fx-border-radius: 5; -fx-background-color: linear-gradient(to bottom right, derive("+color+", 8%), "+color+");");}

 private void pintarResultado(SimaConfig.Equipo eq){

  var card=tarjetas.get(eq);if(card==null||cerrada)return;

  if(equiposEnOperacion.contains(eq)){color(card,GRIS);return;}

  var estadoEquipo=estadosSima.computeIfAbsent(eq,k->new EstadoSima());

  color(card,switch(estadoEquipo.resultado()){

   case CORRECTO->VERDE;case SIN_PING->ROJO;case ERROR->GRIS;default->AMARILLO;

  });

 }

 private void ui(Runnable accion) throws Exception {

  CompletableFuture<Void> listo=new CompletableFuture<>();

  Platform.runLater(()->{try{if(!cerrada)accion.run();listo.complete(null);}catch(Exception e){listo.completeExceptionally(e);}});

  listo.get(5,TimeUnit.SECONDS);

 }

 private void actualizar(){

  long inicio=System.nanoTime();

  try {

   var equipos=SimaConfig.cargar();erroresPing.keySet().retainAll(equipos.stream().map(SimaConfig.Equipo::ip).toList());ui(()->construir(equipos));

   estadosSima.keySet().retainAll(equipos);equiposEnLinea.keySet().retainAll(equipos);ultimoServicio.keySet().retainAll(equipos);

   for(var eq:equipos){

    if(cerrada||Thread.currentThread().isInterrupted())return;

    if(pendientesServicios.contains(eq))continue;

    EstadoSima estadoEquipo=estadosSima.computeIfAbsent(eq,k->new EstadoSima());estadoEquipo.pendiente();

    ui(()->{pintarResultado(eq);tarjetas.values().forEach(c->c.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("consultando"),false));tarjetas.get(eq).pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("consultando"),true);estado.setText("Consultando "+eq.nombre());});

    long antes=System.nanoTime();String resultado;

    try {

     var respuesta=ComandoWindows.ejecutar(List.of(ComandoWindows.sistema("ping.exe"),"-n","1","-w","1080",eq.ip()),4);

     resultado=respuesta.codigo()==0&&respuesta.salida().toUpperCase(Locale.ROOT).contains("TTL=")?AMARILLO:ROJO;

     erroresPing.remove(eq.ip());

    }catch(Exception ex){

     if(Thread.currentThread().isInterrupted())return;

     resultado=GRIS;

     String fallo=ex.toString();

     if(!fallo.equals(erroresPing.put(eq.ip(),fallo))){

      String registro=registrar("Ping "+eq.nombre()+" / "+eq.ip(),"No se pudo consultar el equipo",ex);

      Platform.runLater(()->{if(!mensajesTemporales.containsKey(eq.nombre()))mensaje(eq.nombre()+": error al consultar.\n");});

     }

    }

    // Mantener visible el paso incluso con respuestas inmediatas, sin bloquear JavaFX.

    long pausa=108-TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-antes);if(pausa>0)Thread.sleep(pausa);

    boolean responde=resultado.equals(AMARILLO);

    equiposEnLinea.put(eq,responde);

    if(resultado.equals(GRIS))estadoEquipo.error();

    else if(!responde)estadoEquipo.sinPing();

    ui(()->pintarResultado(eq));

    if(responde)consultarServicios(eq);

   }

   ui(()->{tarjetas.values().forEach(c->c.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("consultando"),false));estado.setText("Actualizado: "+java.time.LocalTime.now().withNano(0)+" | Ciclo: 30 s minimo");});

  }catch(Exception ex){if(!cerrada)Platform.runLater(()->{registrar("Actualizar SIMA","Fallo de actualizacion",ex);estado.setText("No se pudo actualizar SIMA. Consulte el log de la sesion.");estadosSima.values().forEach(EstadoSima::error);tarjetas.values().forEach(c->color(c,GRIS));});}

  finally {

   // Si el recorrido tarda mas de 30 s, no se acumulan ni solapan ciclos.

   long espera=Math.max(0,30000-TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-inicio));

   if(!cerrada)try{reloj.schedule(this::actualizar,espera,TimeUnit.MILLISECONDS);}catch(RejectedExecutionException ignored){}

  }

 }

 private void consultarServicios(SimaConfig.Equipo eq){

  if(!pendientesServicios.add(eq))return;

  try{consultasServicios.execute(()->{

   try{

    if(cerrada)return;

    var verificacion=ServiciosSimaService.consultar(eq);

    String detalle=verificacion.detalle();

    boolean cambio=!detalle.equals(ultimoServicio.put(eq,detalle));

    if(cambio)registrar("Servicios "+eq.nombre()+" / "+eq.ip(),detalle,null);

    Platform.runLater(()->{

     if(!cerrada&&tarjetas.containsKey(eq)&&Boolean.TRUE.equals(equiposEnLinea.get(eq))){

      estadosSima.computeIfAbsent(eq,k->new EstadoSima()).completar(verificacion.completo());pintarResultado(eq);

      if(cambio&&!verificacion.completo()&&!mensajesTemporales.containsKey(eq.nombre()))mensaje(eq.nombre()+": "+detalle.split(" Lanzador=",2)[0]+"\n");

      Tooltip ayuda=(Tooltip)tarjetas.get(eq).getUserData();ayuda.setText(eq.nombre()+" / "+eq.ip()+"\n"+detalle.split("; procesos observados=",2)[0]);

     }

    });

   }catch(Exception ex){

    if(cerrada||Thread.currentThread().isInterrupted())return;

    String detalle=ex.toString();boolean nuevo=!detalle.equals(ultimoServicio.put(eq,detalle));

    if(nuevo)registrar("Servicios "+eq.nombre()+" / "+eq.ip(),"No se pudo verificar",ex);

    Platform.runLater(()->{

     if(!cerrada&&tarjetas.containsKey(eq)&&Boolean.TRUE.equals(equiposEnLinea.get(eq))){

      estadosSima.computeIfAbsent(eq,k->new EstadoSima()).error();pintarResultado(eq);

      if(nuevo&&!mensajesTemporales.containsKey(eq.nombre()))mensaje(eq.nombre()+": servicios no verificables. Consulte el log.\n");

     }

    });

   }finally{pendientesServicios.remove(eq);}

  });}catch(RejectedExecutionException ex){pendientesServicios.remove(eq);if(!cerrada){

   estadosSima.computeIfAbsent(eq,k->new EstadoSima()).error();Platform.runLater(()->pintarResultado(eq));

   registrar("Servicios "+eq.nombre(),"No se pudo programar la consulta",ex);

  }}

 }

 private List<SimaConfig.Equipo> seleccionados(){

  return opciones.entrySet().stream().filter(e->e.getValue().isSelected()).map(Map.Entry::getKey).toList();

 }

 private void reconstruirGrupos(List<SimaConfig.Equipo> equipos){

  var seleccion=new HashSet<>(seleccionados());

  Set<String> abiertos=new HashSet<>();

  if(destinos.getRoot()!=null)for(var grupo:destinos.getRoot().getChildren())if(grupo.isExpanded())abiertos.add(grupo.getValue());

  CheckBoxTreeItem<String> raiz=new CheckBoxTreeItem<>("Equipos");raiz.setExpanded(true);

  Map<String,CheckBoxTreeItem<String>> grupos=new LinkedHashMap<>();opciones.clear();

  for(var eq:equipos){

   var grupo=grupos.computeIfAbsent(eq.grupo(),nombre->{var nodo=new CheckBoxTreeItem<String>(nombre);nodo.setExpanded(abiertos.contains(nombre));raiz.getChildren().add(nodo);return nodo;});

   var hoja=new CheckBoxTreeItem<String>(eq.nombre()+" - "+eq.ip());

   grupo.getChildren().add(hoja);opciones.put(eq,hoja);

  }

  // Restaurar al terminar de construir: los estados parciales se calculan sobre todos los hijos.

  opciones.forEach((eq,nodo)->nodo.setSelected(seleccion.contains(eq)));

  destinos.setRoot(raiz);

  opciones.values().forEach(nodo->nodo.selectedProperty().addListener((o,a,b)->actividadSeleccion()));

  actividadSeleccion();

 }

 private VBox acciones(){

  Label titulo=new Label("Acciones rapidas");titulo.setStyle("-fx-font-weight: bold; -fx-font-size: 16;");

  destinos.setShowRoot(false);

  destinos.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED,e->actividadSeleccion());

  destinos.addEventHandler(javafx.scene.input.KeyEvent.KEY_RELEASED,e->actividadSeleccion());

  destinos.setCellFactory(javafx.scene.control.cell.CheckBoxTreeCell.forTreeView());

  destinos.setPrefHeight(190);destinos.getStyleClass().add("sima-destinos");

  Label ayuda=new Label("Marque un grupo completo o despliegue sus equipos.");ayuda.setWrapText(true);

  Button bajar=new Button("Bajar servicios"),reiniciar=new Button("Reinicio de Equipos"),fabrica=new Button("Bajar fabrica");

  for(Button b:List.of(bajar,reiniciar,fabrica))b.setMaxWidth(Double.MAX_VALUE);

  bajar.setOnAction(e->pendiente(false));fabrica.setOnAction(e->pendiente(true));reiniciar.setOnAction(e->reiniciar());

  resultadoAcciones.getStyleClass().add("sima-resultados");

  resultadoAcciones.setEditable(false);resultadoAcciones.setWrapText(true);resultadoAcciones.setPrefHeight(180);

  VBox panel=new VBox(10,titulo,ayuda,destinos,bajar,reiniciar,fabrica,resultadoAcciones);panel.setMinWidth(240);panel.setPrefWidth(260);panel.setMaxWidth(260);panel.setPadding(new Insets(10));VBox.setVgrow(resultadoAcciones,Priority.ALWAYS);

  panel.getStyleClass().add("sima-acciones");return panel;

 }

 private boolean confirmar(String titulo,List<SimaConfig.Equipo> equipos,String explicacion){

  if(equipos.isEmpty()){mensaje("Seleccione al menos un equipo.");return false;}

  Alert dialogo=new Alert(Alert.AlertType.CONFIRMATION);dialogo.initOwner(stage);dialogo.setTitle(titulo);dialogo.setHeaderText(titulo+" - "+equipos.size()+" equipo(s)");

  TextArea detalle=new TextArea(explicacion+"\n\n"+String.join("\n",equipos.stream().map(eq->eq.nombre()+" - "+eq.ip()).toList()));detalle.setEditable(false);detalle.setWrapText(true);detalle.setPrefSize(340,130);dialogo.getDialogPane().setContent(detalle);

  ButtonType confirmar=new ButtonType("Confirmar",ButtonBar.ButtonData.OK_DONE);dialogo.getButtonTypes().setAll(confirmar,ButtonType.CANCEL);

  limpiarSeleccion.stop();

  try{return dialogo.showAndWait().orElse(ButtonType.CANCEL)==confirmar;}

  finally{actividadSeleccion();}

 }

 private void pendiente(boolean fabrica){

  if(accionEnCurso||hayInicios()){mensaje("Espere a que terminen las acciones en curso.\n");return;}

  var equipos=fabrica?inventario.stream().filter(e->Set.of("PCPIS","FCPIS","RCPIS","PCTL").contains(e.grupo())).toList():seleccionados();

  if(!confirmar(fabrica?"Bajar y subir fabrica":"Bajar servicios",equipos,

    fabrica?"¿Desea detener los servicios de los equipos de PCTL, RCPIS, FCPIS y PCPIS?":"Desea detener los servicios de los equipos seleccionados?"))return;

  accionEnCurso=true;limpiarSeleccion.stop();prepararMensajes(equipos);

  Thread.ofVirtual().start(()->{

   List<SimaConfig.Equipo> reservados=new ArrayList<>(),detenidos=Collections.synchronizedList(new ArrayList<>());

   Set<String> ips=new HashSet<>();

   try{

    for(var eq:equipos){

     if(!ips.add(eq.ip().toLowerCase(Locale.ROOT)))continue;

     if(!InicioUtService.reservar(eq.ip()))throw new java.io.IOException("Equipo ocupado: "+eq.nombre());

     reservados.add(eq);

    }

    OperacionesEquipos.todos(reservados,eq->{

     equiposEnOperacion.add(eq);

     ui(()->{pintarResultado(eq);mensaje(eq.nombre()+": deteniendo servicios.\n");});

     try{

      String mensaje=ParadaAfisService.detener(eq);detenidos.add(eq);

      ui(()->mensaje(eq.nombre()+": "+mensaje+"\n"));

     }catch(Exception ex){

      registrar("Bajar servicios "+eq.nombre(),"Parada fallida; no se programara inicio automatico",ex);

      ui(()->mensaje(eq.nombre()+": parada fallida. Consulte el log.\n"));

     }

    });

    if(fabrica&&!detenidos.isEmpty()){

     ui(()->mensaje("Fabrica: espera de 2 minutos antes del inicio de los equipos detenidos.\n"));

     Thread.sleep(TimeUnit.MINUTES.toMillis(2));

     OperacionesEquipos.todos(detenidos,eq->{

      ui(()->mensaje(eq.nombre()+": solicitando inicio.\n"));

      try{

       String mensaje=InicioUtService.iniciarReservado(eq);

       registrar("Inicio fabrica "+eq.nombre(),mensaje,null);

       ui(()->mensaje(eq.nombre()+": solicitud de inicio procesada.\n"));

      }catch(Exception ex){

       registrar("Inicio fabrica "+eq.nombre(),"Fallo despues de la parada",ex);

       ui(()->mensaje(eq.nombre()+": inicio fallido. Requiere revision. Consulte el log.\n"));

      }

     });

    }

   }catch(Exception ex){

    registrar("Accion de grupo", "Operacion incompleta",ex);

    Platform.runLater(()->mensaje("Operacion incompleta. Consulte el log de la sesion.\n"));

   }finally{

    reservados.forEach(eq->InicioUtService.liberar(eq.ip()));

    equiposEnOperacion.removeAll(reservados);

    Platform.runLater(()->{

     for(var eq:reservados){estadosSima.computeIfAbsent(eq,k->new EstadoSima()).completar(false);pintarResultado(eq);}

     accionEnCurso=false;actividadSeleccion();finalizarMensajes(equipos);

     mensaje("Accion finalizada. SIMA actualizara el estado en su recorrido.\n");

    });

   }

  });

 }

 private void reiniciar(){

  if(accionEnCurso||hayInicios()){mensaje("Espere a que terminen los inicios en curso.\n");return;}

  var equipos=seleccionados();

  if(!confirmar("Reinicio de Equipos",equipos,"¿Desea reiniciar las maquinas seleccionadas? Esta operacion no se puede detener."))return;

  limpiarSeleccion.stop();accionEnCurso=true;prepararMensajes(equipos);

  Thread.ofVirtual().start(()->{

   try{OperacionesEquipos.todos(equipos.stream().collect(java.util.stream.Collectors.toMap(e->e.ip().toLowerCase(Locale.ROOT),e->e,(a,b)->a,LinkedHashMap::new)).values(),eq->{

    String mensaje;boolean reservado=false;

    try{

     SimaConfig.verificarEquipo(Files.readString(SimaConfig.ARCHIVO),eq);

     reservado=InicioUtService.reservar(eq.ip());

     if(!reservado)throw new java.io.IOException("El equipo ya tiene una operacion en curso.");

     ui(()->mensaje(eq.nombre()+": Reiniciando equipo"));
     registrar("Reinicio "+eq.nombre()+" / "+eq.ip(),"Solicitud confirmada por el operador",null);

     try{ParadaAfisService.detener(eq);}catch(Exception parada){registrar("Reinicio "+eq.nombre(),"Parada AFIS incompleta; se solicitara reinicio forzado",parada);}

     var r=ComandoWindows.ejecutar(List.of(ComandoWindows.sistema("shutdown.exe"),"/r","/m","\\"+"\\"+eq.ip(),"/f","/t","0"),30);

     registrar("Reinicio "+eq.nombre()+" / "+eq.ip(),"Codigo="+r.codigo()+" Respuesta="+r.salida(),null);

     mensaje=eq.nombre()+": "+(r.codigo()==0?"solicitud aceptada; reinicio no verificado":"fallo. Consulte el log de la sesion.");

    }catch(Exception ex){registrar("Reinicio "+eq.nombre(),"Fallo",ex);mensaje=eq.nombre()+": reinicio no confirmado. Consulte el log de la sesion.";}

    finally{if(reservado)InicioUtService.liberar(eq.ip());}

    String linea=mensaje;Platform.runLater(()->mensaje(linea+"\n"));

   });}catch(Exception ex){registrar("Reinicio de Equipos","Operacion incompleta",ex);Platform.runLater(()->mensaje("Reinicio · Operacion incompleta. Consulte el log.\n"));}finally{Platform.runLater(()->{accionEnCurso=false;actividadSeleccion();finalizarMensajes(equipos);});}

  });

 }

 private void ajustarNombre(Label label){

  label.setMinWidth(0);label.setTextOverrun(OverrunStyle.CLIP);

  javafx.scene.text.Text medida=new javafx.scene.text.Text();

  medida.setFont(javafx.scene.text.Font.font("Segoe UI",javafx.scene.text.FontWeight.BOLD,10));

  Runnable adaptar=()->{

   double ancho=label.getWidth()-label.getInsets().getLeft()-label.getInsets().getRight()-1;

   if(ancho<=0)return;

   medida.setText(label.getText());

   double tamano=Math.min(10,10*ancho/Math.max(1,medida.getLayoutBounds().getWidth()));

   label.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-weight: bold; -fx-padding: 0; -fx-font-size: "+tamano+"px;");

  };

  label.widthProperty().addListener((o,a,b)->adaptar.run());label.textProperty().addListener((o,a,b)->adaptar.run());

 }

 private String registrar(String contexto,String detalle,Throwable error){

  try{return "Log: "+SessionLog.escribir(contexto,detalle,error);}

  catch(Exception ex){System.err.println(contexto+": "+detalle);if(error!=null)error.printStackTrace();ex.printStackTrace();return "No se pudo guardar el log de la sesion.";}

 }

 private void editar(){

  try {

   Stage editor=new Stage();editor.initOwner(stage);String[] original={Files.readString(SimaConfig.ARCHIVO)};TextArea texto=new TextArea(original[0]);texto.setStyle("-fx-font-family: Consolas;");

   Button guardar=new Button("Guardar configuracion");Label info=new Label(SimaConfig.ARCHIVO.toString());

   guardar.setOnAction(e->{try{SimaConfig.guardar(texto.getText(),original[0]);original[0]=texto.getText();info.setText("Configuracion guardada. Se aplicara en el siguiente ciclo.");}catch(Exception ex){String registro=registrar("Guardar configuracion","Fallo",ex);info.setText("No se pudo guardar. Consulte el log de la sesion.");info.setTooltip(new Tooltip(registro));}});

   VBox box=new VBox(12,info,texto,guardar);box.setPadding(new Insets(20));VBox.setVgrow(texto,Priority.ALWAYS);

   Scene escenaEditor=new Scene(box,850,600);escenaEditor.getStylesheets().add(getClass().getResource("panel.css").toExternalForm());

   editor.setScene(escenaEditor);editor.setTitle("Configuracion de portales y SIMA");editor.show();

  }catch(Exception ex){mensaje("No se pudo abrir la configuracion.\n"+registrar("Abrir configuracion","Fallo",ex)+"\n");}

 }

}

