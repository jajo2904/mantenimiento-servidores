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
 private final TreeView<String> destinos=new TreeView<>();
 private final Map<SimaConfig.Equipo,CheckBoxTreeItem<String>> opciones=new LinkedHashMap<>();
 private static final String ROJO="#DCA6A4", AMARILLO="#E5D19A", VERDE="#A9C9B3", PALIDO="#F3EEE3", GRIS="#BDC5CF";
 private final TextArea resultadoAcciones=new TextArea();
 private boolean accionEnCurso;
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
 private final javafx.animation.PauseTransition limpiarResultados=new javafx.animation.PauseTransition(javafx.util.Duration.minutes(2));

 public SimaWindow(){
  limpiarSeleccion.setOnFinished(e->{
   if(accionEnCurso||hayInicios())return;
   limpiandoSeleccion=true;
   try{opciones.values().forEach(nodo->nodo.setSelected(false));}
   finally{limpiandoSeleccion=false;limpiarSeleccion.stop();}
  });
 }
 public void mostrar(){
  limpiarResultados.setOnFinished(e->resultadoAcciones.clear());
  resultadoAcciones.textProperty().addListener((o,a,b)->{limpiarResultados.stop();if(!b.isEmpty())limpiarResultados.playFromStart();});
  Button configurar=new Button("Configurar equipos");configurar.setOnAction(e->editar());
  Label titulo=new Label("SIMA");titulo.getStyleClass().add("title");
  HBox cabecera=new HBox(14,titulo,configurar,estado);cabecera.setAlignment(Pos.CENTER_LEFT);cabecera.getStyleClass().add("sima-cabecera");
  ScrollPane scroll=new ScrollPane(contenido);scroll.getStyleClass().add("sima-tablero");scroll.setFitToHeight(true);scroll.setPannable(true);
  scroll.viewportBoundsProperty().addListener((o,a,b)->contenido.setPrefWrapLength(Math.max(96,b.getHeight()-2)));
  HBox cuerpo=new HBox(8,scroll,acciones());HBox.setHgrow(scroll,Priority.ALWAYS);scroll.setMinWidth(200);
  VBox root=new VBox(4,cabecera,cuerpo);root.setPadding(new Insets(6));VBox.setVgrow(cuerpo,Priority.ALWAYS);
  Scene scene=new Scene(root,1100,700);scene.getStylesheets().add(getClass().getResource("panel.css").toExternalForm());scene.getStylesheets().add(getClass().getResource("sima.css").toExternalForm());
  stage.setScene(scene);stage.setTitle("SIMA");stage.setMaximized(true);
  stage.setOnCloseRequest(e->{if(accionEnCurso||hayInicios()){e.consume();resultadoAcciones.appendText("Espere a que termine la accion.\n");}});
  stage.setOnHidden(e->{cerrada=true;limpiarSeleccion.stop();limpiarResultados.stop();inicios.values().forEach(i->i.limite.stop());reloj.shutdownNow();});stage.show();
  reloj.execute(this::actualizar);
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
    texto.setStyle("-fx-text-fill: #142130; -fx-font-size: 10; -fx-padding: 0;");
   }
   nombre.setStyle("-fx-text-fill: #172c42; -fx-font-size: 10; -fx-font-weight: bold; -fx-padding: 0;");
   ip.setStyle("-fx-text-fill: #283b4d; -fx-font-size: 9; -fx-padding: 0;");
   ajustarNombre(nombre);
   nombre.setTooltip(new Tooltip(eq.grupo()+" / "+eq.nombre()+" / "+eq.ip()));
   Button subir=new Button();subir.setMinSize(18,18);subir.setPrefSize(18,18);subir.setMaxSize(18,18);subir.setAccessibleText("Ejecutar inicio en "+eq.nombre());
   subir.getStyleClass().add("sima-boton-equipo");
   HBox.setMargin(subir,new Insets(2,2,2,2));
   EstadoInicio inicio=inicios.computeIfAbsent(eq.ip().toLowerCase(Locale.ROOT),ipEquipo->new EstadoInicio());
   subir.disableProperty().bind(inicio.activo);
   ProgressIndicator circulo=new ProgressIndicator();
   circulo.setMinSize(12,12);circulo.setPrefSize(12,12);circulo.setMaxSize(12,12);
   circulo.visibleProperty().bind(inicio.indicador);circulo.managedProperty().bind(inicio.indicador);
   subir.setGraphic(circulo);
   subir.setOnAction(e->{
    if(accionEnCurso||inicio.activo.get())return;
    limpiarSeleccion.stop();inicio.activo.set(true);inicio.indicador.set(true);inicio.limite.playFromStart();
    resultadoAcciones.appendText(eq.nombre()+": ejecutando comando de inicio.\n");
    Thread.ofVirtual().start(()->{
     String detalle; Throwable causa=null;
     try{detalle=InicioUtService.iniciar(eq);}catch(Exception ex){detalle="No se pudo iniciar en "+eq.nombre();causa=ex;}
     String registro=registrar("Inicio equipo "+eq.nombre()+" / "+eq.ip(),detalle,causa);
     String resumen=causa!=null?"No se pudo confirmar el inicio. Consulte el log de la sesion.":
      (detalle.contains("ya se esta ejecutando")?"El script ya se encuentra en ejecucion.":
       detalle.startsWith("Ya hay")?"Ya hay una solicitud en curso.":"Solicitud aceptada. Servicios no verificados.");
     Platform.runLater(()->{
      inicio.limite.stop();inicio.indicador.set(false);inicio.activo.set(false);actividadSeleccion();
      if(!cerrada)resultadoAcciones.appendText(eq.nombre()+": "+resumen+"\n"+registro+"\n");
     });
    });
   });
   VBox datos=new VBox(0,nombre,ip);datos.setMinWidth(0);datos.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(datos,Priority.ALWAYS);
   HBox card=new HBox(2,datos,subir);card.setAlignment(Pos.CENTER_LEFT);card.setPadding(new Insets(4,5,4,5));
   card.setMinSize(108,48);card.setPrefSize(108,48);card.setMaxSize(108,48);
   Tooltip.install(card,new Tooltip("Grupo: "+eq.grupo()+". Subir solicita ejecutar el script en este equipo."));
   color(card,GRIS);tarjetas.put(eq,card);contenido.getChildren().add(card);
  }
 }
 private void color(HBox card,String color){card.setStyle("-fx-background-insets: 2; -fx-background-radius: 5; -fx-border-insets: 2; -fx-border-color: rgba(20,40,60,0.18); -fx-border-width: 0.5; -fx-border-radius: 5; -fx-background-color: linear-gradient(to bottom right, derive("+color+", 8%), "+color+");");}
 private void ui(Runnable accion) throws Exception {
  CompletableFuture<Void> listo=new CompletableFuture<>();
  Platform.runLater(()->{try{if(!cerrada)accion.run();listo.complete(null);}catch(Exception e){listo.completeExceptionally(e);}});
  listo.get(5,TimeUnit.SECONDS);
 }
 private void actualizar(){
  long inicio=System.nanoTime();
  try {
   var equipos=SimaConfig.cargar();erroresPing.keySet().retainAll(equipos.stream().map(SimaConfig.Equipo::ip).toList());ui(()->construir(equipos));
   for(var eq:equipos){
    if(cerrada||Thread.currentThread().isInterrupted())return;
    ui(()->{color(tarjetas.get(eq),PALIDO);estado.setText("Consultando "+eq.nombre());});
    long antes=System.nanoTime();String resultado;
    try {
     var respuesta=ComandoWindows.ejecutar(List.of(ComandoWindows.sistema("ping.exe"),"-n","1","-w","1350",eq.ip()),4);
     resultado=respuesta.codigo()==0&&respuesta.salida().toUpperCase(Locale.ROOT).contains("TTL=")?AMARILLO:ROJO;
     erroresPing.remove(eq.ip());
    }catch(Exception ex){
     if(Thread.currentThread().isInterrupted())return;
     resultado=GRIS;
     String fallo=ex.toString();
     if(!fallo.equals(erroresPing.put(eq.ip(),fallo))){
      String registro=registrar("Ping "+eq.nombre()+" / "+eq.ip(),"No se pudo consultar el equipo",ex);
      Platform.runLater(()->resultadoAcciones.appendText(eq.nombre()+": error al consultar.\n"+registro+"\n"));
     }
    }
    // Mantener visible el paso incluso con respuestas inmediatas, sin bloquear JavaFX.
    long pausa=135-TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-antes);if(pausa>0)Thread.sleep(pausa);
    String tono=resultado;ui(()->color(tarjetas.get(eq),tono));
   }
   ui(()->estado.setText("Actualizado: "+java.time.LocalTime.now().withNano(0)+" | Ciclo: 30 s minimo"));
  }catch(Exception ex){if(!cerrada)Platform.runLater(()->{registrar("Actualizar SIMA","Fallo de actualizacion",ex);estado.setText("No se pudo actualizar SIMA. Consulte el log de la sesion.");tarjetas.values().forEach(c->color(c,GRIS));});}
  finally {
   // Si el recorrido tarda mas de 30 s, no se acumulan ni solapan ciclos.
   long espera=Math.max(0,30000-TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-inicio));
   if(!cerrada)try{reloj.schedule(this::actualizar,espera,TimeUnit.MILLISECONDS);}catch(RejectedExecutionException ignored){}
  }
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
  destinos.setCellFactory(javafx.scene.control.cell.CheckBoxTreeCell.forTreeView());
  destinos.setPrefHeight(190);destinos.getStyleClass().add("sima-destinos");
  Label ayuda=new Label("Marque un grupo completo o despliegue sus equipos.");ayuda.setWrapText(true);
  Button bajar=new Button("Bajar servicios"),reiniciar=new Button("Reiniciar maquinas"),fabrica=new Button("Bajar fabrica");
  for(Button b:List.of(bajar,reiniciar,fabrica))b.setMaxWidth(Double.MAX_VALUE);
  bajar.setOnAction(e->pendiente(false));fabrica.setOnAction(e->pendiente(true));reiniciar.setOnAction(e->reiniciar());
  resultadoAcciones.getStyleClass().add("sima-resultados");
  resultadoAcciones.setEditable(false);resultadoAcciones.setWrapText(true);resultadoAcciones.setPrefHeight(180);
  VBox panel=new VBox(10,titulo,ayuda,destinos,bajar,reiniciar,fabrica,resultadoAcciones);panel.setMinWidth(240);panel.setPrefWidth(260);panel.setMaxWidth(260);panel.setPadding(new Insets(10));VBox.setVgrow(resultadoAcciones,Priority.ALWAYS);
  panel.getStyleClass().add("sima-acciones");return panel;
 }
 private boolean confirmar(String titulo,List<SimaConfig.Equipo> equipos,String explicacion){
  if(equipos.isEmpty()){resultadoAcciones.setText("Seleccione al menos un equipo.");return false;}
  Alert dialogo=new Alert(Alert.AlertType.CONFIRMATION);dialogo.initOwner(stage);dialogo.setTitle(titulo);dialogo.setHeaderText(titulo+" - "+equipos.size()+" equipo(s)");
  TextArea detalle=new TextArea(explicacion+"\n\n"+String.join("\n",equipos.stream().map(eq->eq.nombre()+" - "+eq.ip()).toList()));detalle.setEditable(false);detalle.setWrapText(true);detalle.setPrefSize(480,260);dialogo.getDialogPane().setContent(detalle);
  ButtonType confirmar=new ButtonType("Confirmar",ButtonBar.ButtonData.OK_DONE);dialogo.getButtonTypes().setAll(confirmar,ButtonType.CANCEL);
  limpiarSeleccion.stop();
  try{return dialogo.showAndWait().orElse(ButtonType.CANCEL)==confirmar;}
  finally{actividadSeleccion();}
 }
 private void pendiente(boolean fabrica){
  if(accionEnCurso||hayInicios()){resultadoAcciones.appendText("Espere a que terminen los inicios en curso.\n");return;}
  var equipos=fabrica?inventario.stream().filter(e->Set.of("PCPIS","FCPIS","RCPIS","PCTL").contains(e.grupo())).toList():seleccionados();
  if(fabrica && !confirmar("Bajar y subir fabrica",equipos,"Grupos: PCPIS, FCPIS, RCPIS y PCTL.\nSecuencia prevista: detener servicios, esperar 2 minutos y solicitar el inicio.\n\nPENDIENTE DE CONFIGURACION: confirmar ahora no ejecuta servicios ni inicia la espera."))return;
  String alcance=String.join("\n",equipos.stream().map(e->e.grupo()+" / "+e.nombre()+" / "+e.ip()).toList());
  String registro=registrar(fabrica?"Bajar fabrica":"Bajar servicios","Pendiente de configurar; ninguna accion ejecutada. Destinos:\n"+alcance,null);
  resultadoAcciones.setText("Pendiente de configurar los servicios. No se ejecuto ninguna accion.\n"+registro);
  actividadSeleccion();
 }
 private void reiniciar(){
  if(accionEnCurso||hayInicios()){resultadoAcciones.appendText("Espere a que terminen los inicios en curso.\n");return;}
  var equipos=seleccionados();
  if(!confirmar("Reiniciar maquinas",equipos,"Se solicitara el reinicio inmediato de los equipos indicados. Puede interrumpir sesiones y trabajos en curso."))return;
  limpiarSeleccion.stop();accionEnCurso=true;resultadoAcciones.clear();
  Thread.ofVirtual().start(()->{
   try{for(var eq:equipos){
    String mensaje;boolean reservado=false;
    try{
     SimaConfig.verificarEquipo(Files.readString(SimaConfig.ARCHIVO),eq);
     reservado=InicioUtService.reservar(eq.ip());
     if(!reservado)throw new java.io.IOException("El equipo ya tiene una operacion en curso.");
     registrar("Reinicio "+eq.nombre()+" / "+eq.ip(),"Solicitud confirmada por el operador",null);
     var r=ComandoWindows.ejecutar(List.of(ComandoWindows.sistema("shutdown.exe"),"/r","/m","\\"+"\\"+eq.ip(),"/t","0"),30);
     registrar("Reinicio "+eq.nombre()+" / "+eq.ip(),"Codigo="+r.codigo()+" Respuesta="+r.salida(),null);
     mensaje=eq.nombre()+": "+(r.codigo()==0?"solicitud aceptada; reinicio no verificado":"fallo. Consulte el log de la sesion.");
    }catch(Exception ex){registrar("Reinicio "+eq.nombre(),"Fallo",ex);mensaje=eq.nombre()+": reinicio no confirmado. Consulte el log de la sesion.";}
    finally{if(reservado)InicioUtService.liberar(eq.ip());}
    String linea=mensaje;Platform.runLater(()->resultadoAcciones.appendText(linea+"\n"));
   }}finally{Platform.runLater(()->{accionEnCurso=false;actividadSeleccion();});}
  });
 }
 private void ajustarNombre(Label label){
  label.setMinWidth(0);label.setTextOverrun(OverrunStyle.CLIP);
  Runnable adaptar=()->{
   double ancho=label.getWidth()-label.getInsets().getLeft()-label.getInsets().getRight()-1;
   if(ancho<=0)return;
   javafx.scene.text.Text medida=new javafx.scene.text.Text(label.getText());
   medida.setFont(javafx.scene.text.Font.font("Segoe UI",javafx.scene.text.FontWeight.BOLD,10));
   double tamano=Math.min(10,10*ancho/Math.max(1,medida.getLayoutBounds().getWidth()));
   label.setStyle("-fx-text-fill: #172c42; -fx-font-family: 'Segoe UI'; -fx-font-weight: bold; -fx-padding: 0; -fx-font-size: "+tamano+"px;");
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
  }catch(Exception ex){resultadoAcciones.appendText("No se pudo abrir la configuracion.\n"+registrar("Abrir configuracion","Fallo",ex)+"\n");}
 }
}
