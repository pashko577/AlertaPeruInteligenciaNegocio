package pe.alertaperu.infrastructure.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import pe.alertaperu.application.service.CatalogoService;
import pe.alertaperu.application.service.ConsultarEmergenciasService;
import pe.alertaperu.application.service.EstadisticasService;
import pe.alertaperu.application.service.Fakes;
import pe.alertaperu.domain.model.Emergencia;
import pe.alertaperu.domain.model.Indicadores;
import pe.alertaperu.domain.model.NivelTerritorial;
import pe.alertaperu.domain.model.Peligro;
import pe.alertaperu.domain.model.TotalesIndicadores;
import pe.alertaperu.domain.model.Ubicacion;

/**
 * Pruebas del adaptador web con casos de uso reales y puertos de salida en memoria
 * (sin MongoDB ni contexto completo de Spring).
 */
class ApiWebTest {

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        var emergencias = new Fakes.Emergencias();
        emergencias.datos.put("351-2003-02-11-010101", new Emergencia(
                "351-2003-02-11-010101", "351", LocalDate.of(2003, 2, 11),
                new Ubicacion("010101", "01", "0101", "AMAZONAS", "CHACHAPOYAS", "CHACHAPOYAS", "SIERRA"),
                new Peligro("LLUVIA INTENSA", "ORIGEN NATURAL"),
                new Indicadores(Map.of(
                        "personas", Map.of("fallecidos", 0, "damnificados", 4),
                        "educacion", Map.of("centrosDestruidos", 0),
                        "ayuda", Map.of("peso", 363.24)))));

        var estadisticas = new Fakes.Estadisticas();
        estadisticas.totales = new TotalesIndicadores(10, 1, 0, 0, 25, 0, 0, 0, 0, 0);
        estadisticas.peligros = List.of(Fakes.conteo("zOTROS", 10));
        estadisticas.territorios.put(NivelTerritorial.DEPARTAMENTO, List.of(Fakes.conteo("AMAZONAS", 10)));

        var catalogo = new Fakes.Catalogo().conDepartamento("AMAZONAS", "01").conProvincia("0101", "CHACHAPOYAS", "AMAZONAS");

        mvc = MockMvcBuilders.standaloneSetup(
                        new EmergenciaController(new ConsultarEmergenciasService(emergencias)),
                        new EstadisticasController(new EstadisticasService(estadisticas, catalogo)),
                        new CatalogoController(new CatalogoService(catalogo)))
                .setControllerAdvice(new ManejadorErrores())
                .build();
    }

    @Test
    void listadoPaginadoConValoresSinDatoEnNull() throws Exception {
        mvc.perform(get("/api/emergencias").param("departamento", "AMAZONAS").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].fecha").value("2003-02-11"))
                .andExpect(jsonPath("$.contenido[0].ubigeo").value("010101"))
                .andExpect(jsonPath("$.contenido[0].damnificados").value(4))
                // "afectados" no viene en la fuente: se envía null ("sin dato"), no 0
                .andExpect(jsonPath("$.contenido[0].afectados").doesNotExist());
    }

    @Test
    void detalleIncluyeNotasDeCalidad() throws Exception {
        mvc.perform(get("/api/emergencias/351-2003-02-11-010101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores.ayuda.peso").value(363.24))
                .andExpect(jsonPath("$.notas.length()").value(2));
    }

    @Test
    void detalleInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/emergencias/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.estado").value(404));
    }

    @Test
    void filtroInvalidoDevuelve400ConMensaje() throws Exception {
        mvc.perform(get("/api/estadisticas/resumen").param("anioDesde", "2020").param("anioHasta", "2010"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("anioDesde no puede ser mayor que anioHasta"));
        mvc.perform(get("/api/emergencias").param("distrito", "10101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/emergencias").param("anioDesde", "abc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/emergencias").param("size", "500"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/emergencias").param("sort", "$where"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordenarPorViviendasDestruidasEnVariasEscrituras() throws Exception {
        for (String valor : List.of("viviendasDestruidas", "viviendas_destruidas", "VIVIENDAS_DESTRUIDAS")) {
            mvc.perform(get("/api/emergencias").param("sort", valor).param("dir", "desc"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void resumenConKpis() throws Exception {
        mvc.perform(get("/api/estadisticas/resumen").param("departamento", "AMAZONAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.emergencias").value(10))
                .andExpect(jsonPath("$.damnificadosPorEmergencia").value(2.5))
                .andExpect(jsonPath("$.peligroMasFrecuente").value("OTROS"))
                .andExpect(jsonPath("$.filtro.departamento").value("AMAZONAS"));
    }

    @Test
    void porTerritorioYNivelInvalido() throws Exception {
        mvc.perform(get("/api/estadisticas/por-territorio").param("nivel", "departamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("AMAZONAS"));
        mvc.perform(get("/api/estadisticas/por-territorio").param("nivel", "continente"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void catalogos() throws Exception {
        mvc.perform(get("/api/catalogos/provincias").param("departamento", "AMAZONAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("0101"));
        mvc.perform(get("/api/catalogos/provincias"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalogos/peligros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].peligros[0].valor").value("SISMO"));
    }
}
