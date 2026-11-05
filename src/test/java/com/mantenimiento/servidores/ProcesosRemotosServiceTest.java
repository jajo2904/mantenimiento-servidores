package com.mantenimiento.servidores;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProcesosRemotosServiceTest {
    @Test public void cierraSoloLosCuatroProcesosEnElDestinoYVerifica() throws Exception {
        List<List<String>> llamadas = new ArrayList<>();
        new ProcesosRemotosService(args -> {
            llamadas.add(args);
            return args.get(0).equals("taskkill.exe")
                    ? new ProcesosRemotosService.Resultado(128, "Ausente")
                    : new ProcesosRemotosService.Resultado(0, "\"System\",\"4\",\"Services\",\"0\",\"100 K\"");
        }).detener("192.0.2.10", mensaje -> {});
        assertEquals(5, llamadas.size());
        for (int i=0;i<4;i++) assertEquals(List.of("taskkill.exe", "/S", "192.0.2.10", "/F", "/IM", ProcesosRemotosService.PROCESOS.get(i)), llamadas.get(i));
        assertEquals("tasklist.exe", llamadas.get(4).get(0));
    }
    @Test public void bloqueaSiSigueActivoOConsultaFalla() {
        for (String nombre : ProcesosRemotosService.PROCESOS) {
            assertThrows(IOException.class, () -> ProcesosRemotosService.comprobar(new ProcesosRemotosService.Resultado(0, "\"" + nombre.toUpperCase() + "\",\"123\",\"Services\"")));
        }
        assertThrows(IOException.class, () -> ProcesosRemotosService.comprobar(new ProcesosRemotosService.Resultado(1, "Acceso denegado")));
        assertThrows(IOException.class, () -> ProcesosRemotosService.comprobar(new ProcesosRemotosService.Resultado(0, "ERROR remoto")));
        assertThrows(IOException.class, () -> ProcesosRemotosService.comprobar(new ProcesosRemotosService.Resultado(0, "")));
    }
    @Test public void destinoVacioNoEjecutaComandos() {
        assertThrows(IOException.class, () -> new ProcesosRemotosService(args -> { fail("No ejecutar"); return null; }).detener("", mensaje -> {}));
    }
}
